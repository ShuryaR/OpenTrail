package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AuthMode
import com.example.data.local.AuthSession
import com.example.data.repository.CrmRepository
import com.example.data.repository.RepoResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Error(val message: String) : AuthUiState
    data object Authenticated : AuthUiState
}

class AuthViewModel(
    private val repository: CrmRepository
) : ViewModel() {

    val session: StateFlow<AuthSession?> = repository.credentialStore.authSessionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val onboardingCompleted: StateFlow<Boolean> = repository.credentialStore.settingsFlow
        .let { flow ->
            kotlinx.coroutines.flow.flow {
                flow.collect { emit(it.onboardingCompleted) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _authMode = MutableStateFlow(AuthMode.API_TOKEN)
    val authMode: StateFlow<AuthMode> = _authMode.asStateFlow()

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun setAuthMode(mode: AuthMode) {
        _authMode.value = mode
        _uiState.value = AuthUiState.Idle
    }

    fun clearError() {
        if (_uiState.value is AuthUiState.Error) {
            _uiState.value = AuthUiState.Idle
        }
    }

    fun connectWithApiToken(url: String, apiKey: String, apiSecret: String) {
        if (url.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            _uiState.value = AuthUiState.Error("Enter your Frappe CRM URL, API Key, and API Secret.")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val res = repository.loginWithApiToken(url, apiKey, apiSecret)) {
                is RepoResult.Success -> {
                    repository.refreshAllPipeline()
                    _uiState.value = AuthUiState.Authenticated
                }
                is RepoResult.Error -> {
                    _uiState.value = AuthUiState.Error(res.message)
                }
            }
        }
    }

    fun connectWithPassword(url: String, email: String, password: String) {
        if (url.isBlank() || email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Enter your Frappe CRM URL, email, and password.")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val res = repository.loginWithPassword(url, email, password)) {
                is RepoResult.Success -> {
                    repository.refreshAllPipeline()
                    _uiState.value = AuthUiState.Authenticated
                }
                is RepoResult.Error -> {
                    _uiState.value = AuthUiState.Error(res.message)
                }
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            repository.credentialStore.setOnboardingCompleted(true)
        }
    }

    fun dismissVoWifiTip() {
        viewModelScope.launch {
            repository.credentialStore.setVowifiTipDismissed(true)
        }
    }

    class Factory(private val repository: CrmRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(repository) as T
        }
    }
}
