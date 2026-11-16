package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CallLog
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.OneBookApplication
import com.example.R
import com.example.data.local.CallDirection
import com.example.util.PhoneNormalizer
import com.example.util.SimManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return

        @Suppress("DEPRECATION")
        val phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        val app = context.applicationContext as? OneBookApplication ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleStateChange(app, state, phoneNumber)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleStateChange(
        app: OneBookApplication,
        state: String,
        incomingNumber: String?
    ) {
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prevState = prefs.getString(KEY_LAST_STATE, TelephonyManager.EXTRA_STATE_IDLE)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (!incomingNumber.isNullOrBlank()) {
                    prefs.edit()
                        .putString(KEY_ACTIVE_NUMBER, incomingNumber)
                        .putString(KEY_CALL_DIRECTION, CallDirection.INCOMING.name)
                        .apply()

                    val match = app.repository.matchPhoneToCrm(incomingNumber)
                    if (match != null) {
                        val overlayIntent = Intent(app, CallerIdOverlayService::class.java).apply {
                            putExtra(CallerIdOverlayService.EXTRA_NAME, match.displayName)
                            putExtra(CallerIdOverlayService.EXTRA_COMPANY, match.company)
                            putExtra(CallerIdOverlayService.EXTRA_STAGE, match.stageOrStatus)
                            putExtra(CallerIdOverlayService.EXTRA_NOTE, match.lastNote)
                            putExtra(CallerIdOverlayService.EXTRA_PHONE, incomingNumber)
                        }
                        runCatching { app.startService(overlayIntent) }
                    }
                }
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                val number = incomingNumber?.takeIf { it.isNotBlank() }
                    ?: prefs.getString(KEY_ACTIVE_NUMBER, "").orEmpty()
                val direction = if (prevState == TelephonyManager.EXTRA_STATE_RINGING) {
                    CallDirection.INCOMING.name
                } else {
                    CallDirection.OUTGOING.name
                }
                prefs.edit()
                    .putString(KEY_ACTIVE_NUMBER, number)
                    .putString(KEY_CALL_DIRECTION, direction)
                    .putLong(KEY_OFFHOOK_START_MS, System.currentTimeMillis())
                    .apply()

                app.stopService(Intent(app, CallerIdOverlayService::class.java))

                val settings = app.credentialStore.getSettings()
                val shouldRecord = number.isBlank() || app.repository.shouldRecordAndSyncCall(number, -1)
                if (shouldRecord) {
                    CallRecorderAccessibilityService.instance?.startRecording(
                        audioSource = settings.audioSource,
                        displayTarget = number.ifBlank { "CRM Call" }
                    )
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                app.stopService(Intent(app, CallerIdOverlayService::class.java))
                var audioPath = CallRecorderAccessibilityService.instance?.stopActiveRecording()

                if (prevState == TelephonyManager.EXTRA_STATE_OFFHOOK ||
                    prevState == TelephonyManager.EXTRA_STATE_RINGING
                ) {
                    val endedAtMs = System.currentTimeMillis()
                    // Allow Android system CallLog provider 1.2s to commit the final row
                    delay(1200)

                    val settings = app.credentialStore.getSettings()
                    if (audioPath == null && settings.oemWatcherEnabled) {
                        audioPath = CallRecorderAccessibilityService.findRecentOemRecording(app, endedAtMs)
                    }

                    val sysEntry = queryLatestSystemCallLog(app)
                    val fallbackNumber = prefs.getString(KEY_ACTIVE_NUMBER, "").orEmpty()
                    val finalNumber = sysEntry?.number?.takeIf { it.isNotBlank() } ?: fallbackNumber

                    if (finalNumber.isNotBlank()) {
                        val direction = sysEntry?.direction ?: runCatching {
                            CallDirection.valueOf(
                                prefs.getString(KEY_CALL_DIRECTION, CallDirection.OUTGOING.name)
                                    ?: CallDirection.OUTGOING.name
                            )
                        }.getOrDefault(CallDirection.OUTGOING)

                        val startMs = prefs.getLong(KEY_OFFHOOK_START_MS, endedAtMs)
                        val fallbackDur = ((endedAtMs - startMs) / 1000L).toInt().coerceAtLeast(0)
                        val durationSec = sysEntry?.durationSeconds ?: fallbackDur
                        val simSlot = sysEntry?.simSlotIndex ?: -1

                        if (app.repository.shouldRecordAndSyncCall(finalNumber, simSlot)) {
                            val callLogId = app.repository.queueCallLog(
                                phoneNumber = finalNumber,
                                direction = direction,
                                durationSeconds = durationSec,
                                timestampMs = sysEntry?.timestampMs ?: endedAtMs,
                                localAudioPath = audioPath,
                                simSlotIndex = simSlot
                            )
                            app.triggerPostCallWrapUp(callLogId)
                            postWrapUpNotification(app, callLogId, finalNumber, durationSec)
                        } else {
                            // Filtered out by privacy / SIM rules: clean up temporary file
                            audioPath?.let { java.io.File(it).delete() }
                        }
                    } else {
                        audioPath?.let { java.io.File(it).delete() }
                    }

                    prefs.edit()
                        .remove(KEY_ACTIVE_NUMBER)
                        .remove(KEY_CALL_DIRECTION)
                        .remove(KEY_OFFHOOK_START_MS)
                        .apply()
                }
            }
        }

        prefs.edit().putString(KEY_LAST_STATE, state).apply()
    }

    private data class SystemCallEntry(
        val number: String,
        val direction: CallDirection,
        val durationSeconds: Int,
        val timestampMs: Long,
        val simSlotIndex: Int
    )

    @SuppressLint("MissingPermission")
    private fun queryLatestSystemCallLog(context: Context): SystemCallEntry? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val sims = SimManager.getActiveSims(context)
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE,
            CallLog.Calls.PHONE_ACCOUNT_ID
        )
        return runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 1"
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val number = cursor.getString(0).orEmpty()
                val type = cursor.getInt(1)
                val duration = cursor.getInt(2)
                val date = cursor.getLong(3)
                val accountId = cursor.getString(4).orEmpty()

                // Only accept entries from the last 2 minutes
                if (System.currentTimeMillis() - date > 120_000L) return@use null

                val direction = when (type) {
                    CallLog.Calls.INCOMING_TYPE -> CallDirection.INCOMING
                    CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> CallDirection.MISSED
                    else -> CallDirection.OUTGOING
                }

                val slot = sims.firstOrNull { accountId.contains(it.subscriptionId.toString()) }?.slotIndex ?: -1
                SystemCallEntry(
                    number = number,
                    direction = direction,
                    durationSeconds = duration,
                    timestampMs = date,
                    simSlotIndex = slot
                )
            }
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private fun postWrapUpNotification(
        app: OneBookApplication,
        callLogId: Long,
        phoneNumber: String,
        durationSeconds: Int
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val tapIntent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(OneBookApplication.EXTRA_WRAP_UP_CALL_ID, callLogId)
        }
        val pi = PendingIntent.getActivity(
            app,
            callLogId.toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(app, OneBookApplication.CHANNEL_POST_CALL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Call Ended (${PhoneNormalizer.formatDuration(durationSeconds)})")
            .setContentText("Tap to add disposition & note for $phoneNumber")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .build()

        runCatching {
            NotificationManagerCompat.from(app).notify(5000 + (callLogId % 1000).toInt(), notif)
        }
    }

    companion object {
        private const val PREFS_NAME = "onebook_call_tracker"
        private const val KEY_LAST_STATE = "last_state"
        private const val KEY_ACTIVE_NUMBER = "active_number"
        private const val KEY_CALL_DIRECTION = "call_direction"
        private const val KEY_OFFHOOK_START_MS = "offhook_start_ms"
    }
}
