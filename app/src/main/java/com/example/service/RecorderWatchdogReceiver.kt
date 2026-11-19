package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.OneBookApplication
import com.example.R
import com.example.util.PermissionHelper
import kotlinx.coroutines.launch

/**
 * Android switches an accessibility service off when its app is updated, and some phones also do it
 * after a restart. Nothing then records, and the only sign is a banner inside the app. This puts an
 * alert on the phone itself when the app is updated or the phone restarts with the recorder off.
 */
class RecorderWatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_BOOT_COMPLETED -> {
                RecorderAlert.check(context, afterUpdate = intent.action == Intent.ACTION_MY_PACKAGE_REPLACED)
            }
        }
    }
}

object RecorderAlert {
    private const val ID = 6001

    @SuppressLint("MissingPermission")
    fun show(context: Context, afterUpdate: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (afterUpdate)
            "OneBook was updated and Android switched the recorder off. Tap, open OneBook Call Recorder and turn it on."
        else "The recorder is off, so calls are not being recorded. Tap, open OneBook Call Recorder and turn it on."
        val n = NotificationCompat.Builder(context, OneBookApplication.CHANNEL_POST_CALL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Call recording is off")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID, n) }
    }

    fun clear(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID) }
    }

    /** Only warn when the rep is signed in and the recorder genuinely is off. */
    fun check(context: Context, afterUpdate: Boolean) {
        val app = context.applicationContext as? OneBookApplication ?: return
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val signedIn = app.credentialStore.getAuthSession().isAuthenticated
            if (!signedIn) return@launch
            if (PermissionHelper.isAccessibilityServiceEnabled(context)) clear(context) else show(context, afterUpdate)
        }
    }
}
