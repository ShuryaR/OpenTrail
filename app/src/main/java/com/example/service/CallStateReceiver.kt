package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CallLog
import com.example.util.CallLogRow
import com.example.util.CallLogPicker
import android.util.Log
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
                markCallStart(prefs, prevState)
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
                markCallStart(prefs, prevState)
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
                    val callStartMs = prefs.getLong(KEY_CALL_START_MS, endedAtMs - 60_000L)
                    val activeNumber = prefs.getString(KEY_ACTIVE_NUMBER, "").orEmpty()

                    // Android writes the call-history row a moment after hang-up, and a missed call
                    // can take longer than an answered one. Poll briefly instead of guessing.
                    var entry: SystemCallEntry? = null
                    var foundOnAttempt = 0
                    for ((index, waitMs) in LOOKUP_WAITS_MS.withIndex()) {
                        delay(waitMs)
                        entry = readSystemCallLog(app, callStartMs, activeNumber)
                        if (entry != null) { foundOnAttempt = index + 1; break }
                    }
                    Log.i(TAG, "call history lookup: ${if (entry == null) "NOT FOUND" else "found on attempt $foundOnAttempt, type=${entry.type}, mapped=${entry.direction}"}, prev=$prevState")

                    val settings = app.credentialStore.getSettings()
                    if (audioPath == null && settings.oemWatcherEnabled) {
                        audioPath = CallRecorderAccessibilityService.findRecentOemRecording(app, endedAtMs)
                    }

                    // A type we do not log (voicemail, blocked, answered elsewhere, vendor-specific).
                    if (entry != null && entry.direction == null) {
                        audioPath?.let { java.io.File(it).delete() }
                    } else {
                        val fallbackNumber = activeNumber
                        val finalNumber = entry?.number?.takeIf { it.isNotBlank() } ?: fallbackNumber

                        if (finalNumber.isNotBlank()) {
                            // Without a history row, a call that rang and ended without being answered
                            // (RINGING straight to IDLE) is a missed call, not a completed one.
                            val storedDirection = runCatching {
                                CallDirection.valueOf(
                                    prefs.getString(KEY_CALL_DIRECTION, CallDirection.OUTGOING.name)
                                        ?: CallDirection.OUTGOING.name
                                )
                            }.getOrDefault(CallDirection.OUTGOING)
                            val direction = entry?.direction
                                ?: if (prevState == TelephonyManager.EXTRA_STATE_RINGING) CallDirection.MISSED else storedDirection

                            val startMs = prefs.getLong(KEY_OFFHOOK_START_MS, endedAtMs)
                            val fallbackDur = if (direction == CallDirection.MISSED) 0
                            else ((endedAtMs - startMs) / 1000L).toInt().coerceAtLeast(0)
                            val durationSec = entry?.durationSeconds ?: fallbackDur
                            val simSlot = entry?.simSlotIndex ?: -1

                            if (app.repository.shouldRecordAndSyncCall(finalNumber, simSlot)) {
                                val callLogId = app.repository.queueCallLog(
                                    phoneNumber = finalNumber,
                                    direction = direction,
                                    durationSeconds = durationSec,
                                    timestampMs = entry?.timestampMs ?: endedAtMs,
                                    localAudioPath = audioPath,
                                    simSlotIndex = simSlot
                                )
                                // A missed call has no conversation to wrap up, so no prompt.
                                if (direction != CallDirection.MISSED) {
                                    app.triggerPostCallWrapUp(callLogId)
                                    postWrapUpNotification(app, callLogId, finalNumber, durationSec)
                                }
                            } else {
                                // Filtered out by privacy / SIM rules: clean up temporary file
                                audioPath?.let { java.io.File(it).delete() }
                            }
                        } else {
                            audioPath?.let { java.io.File(it).delete() }
                        }
                    }

                    prefs.edit()
                        .remove(KEY_ACTIVE_NUMBER)
                        .remove(KEY_CALL_DIRECTION)
                        .remove(KEY_OFFHOOK_START_MS)
                        .remove(KEY_CALL_START_MS)
                        .apply()
                }
            }
        }

        prefs.edit().putString(KEY_LAST_STATE, state).apply()
    }

    /** [direction] is null for a call type the app deliberately does not log. */
    private data class SystemCallEntry(
        val number: String,
        val type: Int,
        val direction: CallDirection?,
        val durationSeconds: Int,
        val timestampMs: Long,
        val simSlotIndex: Int
    )

    private fun markCallStart(prefs: android.content.SharedPreferences, prevState: String?) {
        // The first transition out of IDLE is when the call began (ringing or dialling).
        if (prevState == null || prevState == TelephonyManager.EXTRA_STATE_IDLE) {
            prefs.edit().putLong(KEY_CALL_START_MS, System.currentTimeMillis()).apply()
        }
    }

    /**
     * Reads the newest rows of Android's call history and picks this call's row. Uses the
     * Bundle-based query: putting `LIMIT` in the sort order is rejected on newer Android versions,
     * which made every lookup fail silently.
     */
    @SuppressLint("MissingPermission")
    private fun readSystemCallLog(context: Context, callStartMs: Long, activeNumber: String): SystemCallEntry? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "READ_CALL_LOG not granted")
            return null
        }
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE,
            CallLog.Calls.PHONE_ACCOUNT_ID
        )
        val rows = runCatching {
            val cursor = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val args = android.os.Bundle().apply {
                    putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, LOOKUP_ROWS)
                    putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(CallLog.Calls.DATE))
                    putInt(
                        android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                        android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                    )
                }
                context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, args, null)
            } else {
                context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, "${CallLog.Calls.DATE} DESC")
            }
            buildList {
                cursor?.use {
                    while (it.moveToNext() && size < LOOKUP_ROWS) {
                        add(
                            CallLogRow(
                                number = it.getString(0).orEmpty(),
                                type = it.getInt(1),
                                durationSeconds = it.getInt(2),
                                dateMs = it.getLong(3),
                                accountId = it.getString(4).orEmpty()
                            )
                        )
                    }
                }
            }
        }.onFailure { Log.e(TAG, "call history query failed: ${it.javaClass.simpleName}: ${it.message}") }
            .getOrNull() ?: return null

        val row = CallLogPicker.pick(rows, callStartMs, activeNumber) ?: return null
        val sims = SimManager.getActiveSims(context)
        // The account id is the subscription id on most phones; some embed the SIM's ICCID instead.
        val slot = sims.firstOrNull { row.accountId == it.subscriptionId.toString() }?.slotIndex
            ?: sims.firstOrNull { row.accountId.contains(it.subscriptionId.toString()) }?.slotIndex
            ?: -1
        return SystemCallEntry(
            number = row.number,
            type = row.type,
            direction = CallLogPicker.directionFor(row.type),
            durationSeconds = row.durationSeconds,
            timestampMs = row.dateMs,
            simSlotIndex = slot
        )
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
        private const val KEY_CALL_START_MS = "call_start_ms"
        private const val TAG = "CallStateReceiver"
        private const val LOOKUP_ROWS = 8
        // Waits before each lookup, about 4.7 s in total, within the receiver's time budget.
        private val LOOKUP_WAITS_MS = listOf(1200L, 1500L, 2000L)
    }
}
