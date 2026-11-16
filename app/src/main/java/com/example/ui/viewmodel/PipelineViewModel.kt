package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppSettings
import com.example.data.local.CallLogEntity
import com.example.data.local.ContactEntity
import com.example.data.local.DealEntity
import com.example.data.local.LeadEntity
import com.example.data.local.TaskEntity
import com.example.data.repository.CrmRepository
import com.example.data.repository.RepoResult
import com.example.ui.navigation.HomeTab
import com.example.util.SimManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PipelineUiState(
    val selectedTab: HomeTab = HomeTab.TASKS,
    val searchQuery: String = "",
    val selectedLeadStatus: String = "All",
    val tasks: List<TaskEntity> = emptyList(),
    val leads: List<LeadEntity> = emptyList(),
    val deals: List<DealEntity> = emptyList(),
    val contacts: List<ContactEntity> = emptyList(),
    val pendingSyncCount: Int = 0,
    val settings: AppSettings = AppSettings(),
    val isRefreshing: Boolean = false,
    val statusMessage: String? = null
)

class PipelineViewModel(
    private val repository: CrmRepository,
    private val callLogDao: com.example.data.local.CallLogDao
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(HomeTab.TASKS)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedLeadStatus = MutableStateFlow("All")
    private val _isRefreshing = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)

    private val _wrapUpCallLog = MutableStateFlow<CallLogEntity?>(null)
    val wrapUpCallLog: StateFlow<CallLogEntity?> = _wrapUpCallLog.asStateFlow()

    private val filtersFlow = combine(
        _selectedTab,
        _searchQuery,
        _selectedLeadStatus,
        _isRefreshing,
        _statusMessage
    ) { tab, query, leadStatus, refreshing, msg ->
        FilterSlice(tab, query, leadStatus, refreshing, msg)
    }

    val uiState: StateFlow<PipelineUiState> = combine(
        filtersFlow,
        repository.tasksFlow,
        repository.leadsFlow,
        repository.dealsFlow,
        repository.contactsFlow,
        repository.pendingCountFlow,
        repository.credentialStore.settingsFlow
    ) { values ->
        val filters = values[0] as FilterSlice
        @Suppress("UNCHECKED_CAST")
        val tasks = values[1] as List<TaskEntity>
        @Suppress("UNCHECKED_CAST")
        val leads = values[2] as List<LeadEntity>
        @Suppress("UNCHECKED_CAST")
        val deals = values[3] as List<DealEntity>
        @Suppress("UNCHECKED_CAST")
        val contacts = values[4] as List<ContactEntity>
        val pendingCount = values[5] as Int
        val settings = values[6] as AppSettings

        val q = filters.query.trim().lowercase()

        val filteredTasks = if (q.isBlank()) tasks else tasks.filter {
            it.title.lowercase().contains(q) ||
                it.referenceDocname.lowercase().contains(q) ||
                it.phone.lowercase().contains(q)
        }

        val filteredLeads = leads.filter { lead ->
            val matchesStatus = filters.leadStatus == "All" ||
                lead.status.equals(filters.leadStatus, ignoreCase = true)
            val matchesQuery = q.isBlank() ||
                lead.leadName.lowercase().contains(q) ||
                lead.organization.lowercase().contains(q) ||
                lead.mobileNo.lowercase().contains(q) ||
                lead.phone.lowercase().contains(q)
            matchesStatus && matchesQuery
        }

        val filteredDeals = if (q.isBlank()) deals else deals.filter {
            it.dealTitle.lowercase().contains(q) ||
                it.organization.lowercase().contains(q) ||
                it.dealStage.lowercase().contains(q) ||
                it.phone.lowercase().contains(q)
        }

        val filteredContacts = if (q.isBlank()) contacts else contacts.filter {
            it.fullName.lowercase().contains(q) ||
                it.companyName.lowercase().contains(q) ||
                it.mobileNo.lowercase().contains(q) ||
                it.phone.lowercase().contains(q)
        }

        PipelineUiState(
            selectedTab = filters.tab,
            searchQuery = filters.query,
            selectedLeadStatus = filters.leadStatus,
            tasks = filteredTasks,
            leads = filteredLeads,
            deals = filteredDeals,
            contacts = filteredContacts,
            pendingSyncCount = pendingCount,
            settings = settings,
            isRefreshing = filters.refreshing,
            statusMessage = filters.message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PipelineUiState())

    private data class FilterSlice(
        val tab: HomeTab,
        val query: String,
        val leadStatus: String,
        val refreshing: Boolean,
        val message: String?
    )

    fun selectTab(tab: HomeTab) {
        _selectedTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectLeadStatus(status: String) {
        _selectedLeadStatus.value = status
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun refreshPipeline() {
        viewModelScope.launch {
            _isRefreshing.value = true
            when (val res = repository.refreshAllPipeline()) {
                is RepoResult.Success -> _statusMessage.value = "CRM pipeline synced"
                is RepoResult.Error -> _statusMessage.value = res.message
            }
            _isRefreshing.value = false
        }
    }

    fun placeCall(context: Context, rawPhone: String) {
        if (rawPhone.isBlank()) {
            _statusMessage.value = "No phone number on record"
            return
        }
        val launched = SimManager.placeNativeSimCall(
            context = context,
            phoneNumber = rawPhone,
            preferredSimSlot = uiState.value.settings.workSimPreference
        )
        if (!launched) {
            _statusMessage.value = "Unable to launch cellular dialer"
        }
    }

    fun toggleTaskDone(task: TaskEntity) {
        viewModelScope.launch {
            val res = repository.toggleTaskDone(task)
            if (res is RepoResult.Error) {
                _statusMessage.value = res.message
            }
        }
    }

    fun openWrapUpForCall(callLogId: Long) {
        viewModelScope.launch {
            _wrapUpCallLog.value = callLogDao.getById(callLogId)
        }
    }

    fun dismissWrapUp() {
        _wrapUpCallLog.value = null
    }

    fun submitWrapUp(
        callLogId: Long,
        disposition: String?,
        note: String?,
        nextFollowUpDate: String?
    ) {
        viewModelScope.launch {
            repository.updatePostCallWrapUp(callLogId, disposition, note, nextFollowUpDate)
            _wrapUpCallLog.value = null
            _statusMessage.value = "Call note queued for Frappe CRM sync"
        }
    }

    class Factory(
        private val repository: CrmRepository,
        private val callLogDao: com.example.data.local.CallLogDao
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PipelineViewModel(repository, callLogDao) as T
        }
    }
}
