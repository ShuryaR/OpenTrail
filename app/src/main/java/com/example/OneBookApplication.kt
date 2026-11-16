package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.local.AppDatabase
import com.example.data.local.SecureCredentialStore
import com.example.data.repository.CrmRepository
import com.example.service.AudioPlaybackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OneBookApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var credentialStore: SecureCredentialStore
        private set

    lateinit var repository: CrmRepository
        private set

    lateinit var audioPlaybackManager: AudioPlaybackManager
        private set

    private val _pendingWrapUpCallId = MutableStateFlow<Long?>(null)
    val pendingWrapUpCallId: StateFlow<Long?> = _pendingWrapUpCallId.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        credentialStore = SecureCredentialStore(this)
        repository = CrmRepository(this, database, credentialStore)
        audioPlaybackManager = AudioPlaybackManager(this, credentialStore)
        createNotificationChannels()
    }

    fun triggerPostCallWrapUp(callLogId: Long) {
        _pendingWrapUpCallId.value = callLogId
    }

    fun clearPendingWrapUp() {
        _pendingWrapUpCallId.value = null
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return

        val recorderChannel = NotificationChannel(
            CHANNEL_RECORDER,
            getString(R.string.channel_recorder_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_recorder_desc)
            setShowBadge(false)
        }

        val postCallChannel = NotificationChannel(
            CHANNEL_POST_CALL,
            getString(R.string.channel_post_call_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.channel_post_call_desc)
        }

        val syncChannel = NotificationChannel(
            CHANNEL_SYNC,
            getString(R.string.channel_sync_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_sync_desc)
        }

        manager.createNotificationChannels(listOf(recorderChannel, postCallChannel, syncChannel))
    }

    companion object {
        const val CHANNEL_RECORDER = "onebook_recorder_channel"
        const val CHANNEL_POST_CALL = "onebook_post_call_channel"
        const val CHANNEL_SYNC = "onebook_sync_channel"
        const val EXTRA_WRAP_UP_CALL_ID = "extra_wrap_up_call_id"
    }
}
