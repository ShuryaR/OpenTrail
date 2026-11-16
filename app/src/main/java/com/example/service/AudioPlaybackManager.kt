package com.example.service

import android.content.Context
import android.media.MediaPlayer
import com.example.data.local.SecureCredentialStore
import com.example.data.remote.FrappeClientFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

data class PlaybackUiState(
    val activeKey: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val error: String? = null,
    val speed: Float = 1f,
    val waveform: List<Float> = emptyList()
)

class AudioPlaybackManager(
    private val context: Context,
    private val credentialStore: SecureCredentialStore
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var speed: Float = 1f

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    fun togglePlay(key: String, localPath: String?, remoteUrl: String?) {
        val current = _state.value
        if (current.activeKey == key && mediaPlayer != null) {
            if (current.isPlaying) {
                mediaPlayer?.pause()
                _state.value = current.copy(isPlaying = false)
            } else {
                mediaPlayer?.start()
                applySpeed()
                _state.value = current.copy(isPlaying = true)
                startProgressLoop()
            }
            return
        }

        stop()
        _state.value = PlaybackUiState(activeKey = key, isLoading = true)

        scope.launch {
            val playableFile = withContext(Dispatchers.IO) {
                resolveAudioFile(localPath, remoteUrl)
            }
            if (playableFile == null || !playableFile.exists()) {
                _state.value = PlaybackUiState(
                    activeKey = key,
                    error = "Recording audio unavailable"
                )
                return@launch
            }
            startMediaPlayer(key, playableFile)
            // Waveform is decoded after playback starts so it never delays the first sound.
            val peaks = withContext(Dispatchers.IO) { WaveformExtractor.extract(playableFile) }
            if (_state.value.activeKey == key && peaks.isNotEmpty()) {
                _state.value = _state.value.copy(waveform = peaks)
            }
        }
    }

    /** Cycles 1x, 1.5x, 2x. Applied only while playing: setting speed on a paused player can resume it. */
    fun cycleSpeed() {
        speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
        _state.value = _state.value.copy(speed = speed)
        if (_state.value.isPlaying) applySpeed()
    }

    private fun applySpeed() {
        runCatching {
            mediaPlayer?.let { it.playbackParams = it.playbackParams.setSpeed(speed) }
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.let { mp ->
            runCatching {
                mp.seekTo(positionMs)
                _state.value = _state.value.copy(positionMs = positionMs)
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        _state.value = PlaybackUiState(speed = speed)
    }

    private fun startMediaPlayer(key: String, file: File) {
        runCatching {
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    progressJob?.cancel()
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        positionMs = 0
                    )
                }
                start()
            }
            mediaPlayer = mp
            applySpeed()
            _state.value = PlaybackUiState(
                activeKey = key,
                isPlaying = true,
                isLoading = false,
                positionMs = 0,
                durationMs = mp.duration.coerceAtLeast(1),
                speed = speed
            )
            startProgressLoop()
        }.onFailure { e ->
            _state.value = PlaybackUiState(
                activeKey = key,
                error = e.localizedMessage ?: "Playback failed"
            )
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (_state.value.isPlaying) {
                val mp = mediaPlayer ?: break
                _state.value = _state.value.copy(
                    positionMs = runCatching { mp.currentPosition }.getOrDefault(0),
                    durationMs = runCatching { mp.duration }.getOrDefault(_state.value.durationMs).coerceAtLeast(1)
                )
                delay(250)
            }
        }
    }

    private suspend fun resolveAudioFile(localPath: String?, remoteUrl: String?): File? {
        if (!localPath.isNullOrBlank()) {
            val f = File(localPath)
            if (f.exists() && f.length() > 0L) return f
        }
        if (remoteUrl.isNullOrBlank()) return null

        val session = credentialStore.getAuthSession()
        if (!session.isAuthenticated) return null

        val fullUrl = if (remoteUrl.startsWith("http://") || remoteUrl.startsWith("https://")) {
            remoteUrl
        } else {
            "${session.baseUrl.trimEnd('/')}/${remoteUrl.trimStart('/')}"
        }

        val cacheDir = File(context.cacheDir, "audio_cache").apply { mkdirs() }
        val safeName = remoteUrl.hashCode().toUInt().toString() + ".m4a"
        val cached = File(cacheDir, safeName)
        if (cached.exists() && cached.length() > 1024L) return cached

        val client = FrappeClientFactory.buildOkHttpClient(session)
        return runCatching {
            val req = Request.Builder().url(fullUrl).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body ?: return null
                cached.outputStream().use { out -> body.byteStream().copyTo(out) }
                cached
            }
        }.getOrNull()
    }
}
