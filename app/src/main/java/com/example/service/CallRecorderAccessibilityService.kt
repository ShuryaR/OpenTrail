package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.OneBookApplication
import com.example.R
import com.example.data.local.AudioSourceOption
import java.io.File

class CallRecorderAccessibilityService : AccessibilityService() {

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var isRecording: Boolean = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Bound AccessibilityService provides background audio capture privilege during active calls.
    }

    override fun onInterrupt() {
        stopActiveRecording()
    }

    override fun onDestroy() {
        stopActiveRecording()
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    fun startRecording(audioSource: AudioSourceOption, displayTarget: String) {
        if (isRecording) return
        val dir = File(filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "call_${System.currentTimeMillis()}.m4a")

        promoteToForeground(displayTarget)

        val started = tryStartMediaRecorder(file, audioSource.mediaRecorderSource) ||
            (audioSource != AudioSourceOption.MIC && tryStartMediaRecorder(file, MediaRecorder.AudioSource.MIC))

        if (started) {
            currentOutputFile = file
            isRecording = true
        } else {
            runCatching { file.delete() }
            demoteForeground()
        }
    }

    fun stopActiveRecording(): String? {
        if (!isRecording) return null
        isRecording = false
        val file = currentOutputFile
        currentOutputFile = null

        runCatching {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        }.onFailure {
            Log.w(TAG, "Error stopping MediaRecorder cleanly: ${it.message}")
            runCatching { mediaRecorder?.release() }
        }
        mediaRecorder = null
        demoteForeground()

        return if (file != null && file.exists() && file.length() > 1024L) {
            file.absolutePath
        } else {
            file?.delete()
            null
        }
    }

    private fun tryStartMediaRecorder(outputFile: File, source: Int): Boolean {
        return runCatching {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.apply {
                setAudioSource(source)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            true
        }.getOrElse { e ->
            Log.w(TAG, "MediaRecorder failed for source $source: ${e.message}")
            runCatching { mediaRecorder?.release() }
            mediaRecorder = null
            false
        }
    }

    private fun promoteToForeground(target: String) {
        val notification = NotificationCompat.Builder(this, OneBookApplication.CHANNEL_RECORDER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("OneBook Call Recorder Active")
            .setContentText("Recording CRM call with $target")
            .setOngoing(true)
            .setSilent(true)
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceCompat.startForeground(
                    this,
                    RECORDER_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(RECORDER_NOTIFICATION_ID, notification)
            }
        }
    }

    private fun demoteForeground() {
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
    }

    companion object {
        private const val TAG = "OneBookRecorderSvc"
        private const val RECORDER_NOTIFICATION_ID = 4001

        @Volatile
        var instance: CallRecorderAccessibilityService? = null
            private set

        /**
         * Fallback OEM Recording Folder Watcher for Samsung One UI, Xiaomi HyperOS, OnePlus,
         * and Oppo dialers that store native call recordings on internal storage.
         */
        fun findRecentOemRecording(context: Context, callEndedMs: Long): String? {
            val root = Environment.getExternalStorageDirectory() ?: return null
            val candidateDirs = listOf(
                File(root, "Recordings/Call"),
                File(root, "Recordings/Record/Call"),
                File(root, "MIUI/sound_recorder/call_rec"),
                File(root, "Call"),
                File(root, "Sounds/CallRecordings"),
                File(root, "Music/Recordings/Call Recordings")
            )
            val windowStart = callEndedMs - 120_000L
            val newest = candidateDirs
                .filter { it.exists() && it.isDirectory }
                .flatMap { dir -> dir.listFiles()?.toList().orEmpty() }
                .filter { file ->
                    file.isFile &&
                        file.length() > 2048L &&
                        file.lastModified() >= windowStart &&
                        file.extension.lowercase() in setOf("m4a", "amr", "mp3", "wav", "aac", "ogg")
                }
                .maxByOrNull { it.lastModified() } ?: return null

            val destDir = File(context.filesDir, "recordings").apply { mkdirs() }
            val destFile = File(destDir, "oem_${System.currentTimeMillis()}.${newest.extension}")
            return runCatching {
                newest.copyTo(destFile, overwrite = true)
                destFile.absolutePath
            }.getOrNull()
        }
    }
}
