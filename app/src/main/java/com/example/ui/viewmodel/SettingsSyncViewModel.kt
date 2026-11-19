package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppSettings
import com.example.data.local.AudioSourceOption
import com.example.data.local.AuthSession
import com.example.data.local.CallLogEntity
import com.example.data.local.ExcludedNumberEntity
import com.example.data.local.PrivacyFilterMode
import com.example.data.repository.CrmRepository
import com.example.util.SimManager
import com.example.util.SimSlotInfo
import com.example.util.WorkSimPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsSyncUiState(
    val session: AuthSession = AuthSession(),
    val settings: AppSettings = AppSettings(),
    val callQueue: List<CallLogEntity> = emptyList(),
    val excludedNumbers: List<ExcludedNumberEntity> = emptyList(),
    val activeSims: List<SimSlotInfo> = emptyList(),
    val isSyncingNow: Boolean = false,
    val message: String? = null
)

class SettingsSyncViewModel(
    private val context: Context,
    private val repository: CrmRepository
) : ViewModel() {

    private val _isSyncingNow = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)
    private val _activeSims = MutableStateFlow<List<SimSlotInfo>>(emptyList())

    val uiState: StateFlow<SettingsSyncUiState> = combine(
        repository.credentialStore.authSessionFlow,
        repository.credentialStore.settingsFlow,
        repository.allCallLogsFlow,
        repository.excludedNumbersFlow,
        _activeSims,
        _isSyncingNow,
        _message
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        SettingsSyncUiState(
            session = values[0] as AuthSession,
            settings = values[1] as AppSettings,
            callQueue = values[2] as List<CallLogEntity>,
            excludedNumbers = values[3] as List<ExcludedNumberEntity>,
            activeSims = values[4] as List<SimSlotInfo>,
            isSyncingNow = values[5] as Boolean,
            message = values[6] as String?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsSyncUiState())

    init {
        refreshSims()
    }

    fun refreshSims() {
        _activeSims.value = SimManager.getActiveSims(context)
    }

    fun syncNow() {
        viewModelScope.launch {
            _isSyncingNow.value = true
            val ok = repository.syncPendingQueueOnce()
            repository.refreshAllPipeline()
            _isSyncingNow.value = false
            _message.value = if (ok) {
                "All pending call logs synced to Frappe CRM"
            } else {
                "Some uploads failed — check network or credentials"
            }
        }
    }

    fun retryCallLog(id: Long) {
        viewModelScope.launch {
            repository.retryCallLog(id)
            _message.value = "Retry queued"
        }
    }

    fun setAudioSource(option: AudioSourceOption) {
        viewModelScope.launch { repository.credentialStore.setAudioSource(option) }
    }

    fun setLayoutDensity(density: com.example.ui.theme.LayoutDensity) {
        viewModelScope.launch { repository.credentialStore.setLayoutDensity(density) }
    }

    fun setOemWatcherEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.credentialStore.setOemWatcherEnabled(enabled) }
    }

    fun setPrivacyFilterMode(mode: PrivacyFilterMode) {
        viewModelScope.launch { repository.credentialStore.setPrivacyFilterMode(mode) }
    }

    fun setWorkSimPreference(pref: WorkSimPreference) {
        viewModelScope.launch { repository.credentialStore.setWorkSimPreference(pref) }
    }

    fun setDailyCallGoal(goal: Int) {
        viewModelScope.launch { repository.credentialStore.setDailyCallGoal(goal) }
    }

    fun addExcludedNumber(phone: String, label: String) {
        if (phone.isBlank()) return
        viewModelScope.launch {
            repository.addExcludedNumber(phone, label)
            _message.value = "Personal number excluded from CRM recording"
        }
    }

    fun removeExcludedNumber(normalizedPhone: String) {
        viewModelScope.launch { repository.removeExcludedNumber(normalizedPhone) }
    }

    fun signOut() {
        viewModelScope.launch { repository.credentialStore.clearSession() }
    }

    fun clearMessage() {
        _message.value = null
    }

    class Factory(
        private val context: Context,
        private val repository: CrmRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsSyncViewModel(context, repository) as T
        }
    }
}
