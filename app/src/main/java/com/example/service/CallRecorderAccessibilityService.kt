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

    /**
     * Records about three seconds from the microphone path and reports the loudest level heard, so a
     * rep can confirm the service, permission and audio source work before a real call. It cannot
     * hear the other side of a call; that depends on the phone's call routing.
     */
    suspend fun runSelfTest(audioSource: AudioSourceOption): SelfTestResult {
        if (isRecording) return SelfTestResult(false, 0, "A call recording is in progress. Try again after the call.")
        val file = File(cacheDir, "selftest_${System.currentTimeMillis()}.m4a")
        val recorder = runCatching {
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder())
                .apply {
                    setAudioSource(audioSource.mediaRecorderSource)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(file.absolutePath)
                    prepare()
                    start()
                }
        }.getOrElse { e ->
            file.delete()
            Log.w(TAG, "Self-test could not start: ${e.message}")
            return SelfTestResult(false, 0, "Android would not let the recorder start. Check the microphone permission and that no other app is using the mic.")
        }
        var peak = 0
        try {
            repeat(12) {
                kotlinx.coroutines.delay(250)
                peak = maxOf(peak, runCatching { recorder.maxAmplitude }.getOrDefault(0))
            }
        } finally {
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
            file.delete()
        }
        return when {
            peak < SILENCE_PEAK -> SelfTestResult(
                false, peak,
                "The recorder works but heard almost nothing. Speak near the phone and test again, or switch Audio Capture Source."
            )
            else -> SelfTestResult(
                true, peak,
                "Recorder works with ${audioSource.name}. During a real call, if the other person is silent, turn off Wi-Fi calling."
            )
        }
    }

    companion object {
        private const val SILENCE_PEAK = 400
        private const val TAG = "OneBookRecorderSvc"
        private const val RECORDER_NOTIFICATION_ID = 4001

        @Volatile
        var instance: CallRecorderAccessibilityService? = null
            private set

        /**
         * Fallback OEM Recording Folder Watcher for Samsung One UI, Xiaomi HyperOS, OnePlus,
         * and Oppo dialers that store native call recordings on internal storage.
         */
        fun findRecentOemRecording(context: Context, callStartMs: Long, callEndedMs: Long): String? {
            val root = Environment.getExternalStorageDirectory() ?: return null
            val candidateDirs = listOf(
                File(root, "Recordings/Call"),
                File(root, "Recordings/Record/Call"),
                File(root, "MIUI/sound_recorder/call_rec"),
                File(root, "Call"),
                File(root, "Sounds/CallRecordings"),
                File(root, "Music/Recordings/Call Recordings")
            )
            val files = candidateDirs
                .filter { it.exists() && it.isDirectory }
                .flatMap { dir -> dir.listFiles()?.toList().orEmpty() }
                .filter { it.isFile }
                .map { OemFile(it.name, it.length(), it.lastModified(), it) }
            val newest = OemRecordingPicker.pick(files, callStartMs, callEndedMs)?.file ?: return null

            val destDir = File(context.filesDir, "recordings").apply { mkdirs() }
            val destFile = File(destDir, "oem_${System.currentTimeMillis()}.${newest.extension}")
            return runCatching {
                newest.copyTo(destFile, overwrite = true)
                destFile.absolutePath
            }.getOrNull()
        }
    }
}


data class SelfTestResult(val ok: Boolean, val peakAmplitude: Int, val message: String)


data class OemFile(val name: String, val sizeBytes: Long, val modifiedMs: Long, val file: File)

object OemRecordingPicker {
    private val AUDIO = setOf("m4a", "amr", "mp3", "wav", "aac", "ogg")

    /**
     * The phone's recorder closes its file when the call ends, so the right file was last modified
     * between the start and shortly after the end of the call. Newest such file wins.
     */
    fun pick(files: List<OemFile>, callStartMs: Long, callEndedMs: Long): OemFile? =
        files.filter {
            it.sizeBytes > 2048L &&
                it.name.substringAfterLast('.', "").lowercase() in AUDIO &&
                it.modifiedMs >= callStartMs - 5_000L &&
                it.modifiedMs <= callEndedMs + 30_000L
        }.maxByOrNull { it.modifiedMs }
}
