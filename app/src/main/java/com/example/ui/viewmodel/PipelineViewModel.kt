package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppSettings
import com.example.data.local.CallDirection
import com.example.data.local.CallLogDao
import com.example.data.local.CallLogEntity
import com.example.data.local.ContactEntity
import com.example.data.local.DealEntity
import com.example.data.local.LeadEntity
import com.example.data.local.isUnlogged
import com.example.data.local.TaskEntity
import com.example.data.repository.CrmRepository
import com.example.data.repository.RepoResult
import com.example.service.AudioPlaybackManager
import com.example.service.PlaybackUiState
import com.example.ui.navigation.HomeTab
import com.example.util.PhoneNormalizer
import com.example.util.SimManager
import com.example.util.WorkSimPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class DialerCrmMatch(
    val doctype: String,
    val docname: String,
    val displayName: String,
    val subtitle: String,
    val phoneNumber: String
)

/** Extra context shown with the wrap-up sheet: what else this call can update in the CRM. */
data class WrapUpContext(
    val leadDocname: String? = null,
    val leadStatus: String? = null,
    val openTasks: List<TaskEntity> = emptyList(),
    val backlogActive: Boolean = false,
    val backlogRemaining: Int = 0,
    val creatingLead: Boolean = false
)

data class PipelineUiState(
    val selectedTab: HomeTab = HomeTab.RECENTS,
    val searchQuery: String = "",
    val selectedLeadStatus: String = "All",
    val recentCalls: List<CallLogEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val leads: List<LeadEntity> = emptyList(),
    val deals: List<DealEntity> = emptyList(),
    val contacts: List<ContactEntity> = emptyList(),
    val pendingSyncCount: Int = 0,
    val callsTodayCount: Int = 0,
    val unloggedCallsCount: Int = 0,
    val settings: AppSettings = AppSettings(),
    val isRefreshing: Boolean = false,
    val syncStage: String? = null,
    val syncConfirmedBanner: String? = null,
    val statusMessage: String? = null,
    val playbackState: PlaybackUiState = PlaybackUiState(),
    val dialerDigits: String = "",
    val dialerMatches: List<DialerCrmMatch> = emptyList(),
    val dialerRecents: List<DialerCrmMatch> = emptyList()
)

class PipelineViewModel(
    private val repository: CrmRepository,
    private val callLogDao: CallLogDao,
    private val audioPlaybackManager: AudioPlaybackManager
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(HomeTab.RECENTS)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedLeadStatus = MutableStateFlow("All")
    private val _isRefreshing = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _syncConfirmedBanner = MutableStateFlow<String?>(null)
    private val _dialerDigits = MutableStateFlow("")

    private val _wrapUpCallLog = MutableStateFlow<CallLogEntity?>(null)
    val wrapUpCallLog: StateFlow<CallLogEntity?> = _wrapUpCallLog.asStateFlow()

    private val _wrapUpContext = MutableStateFlow(WrapUpContext())
    val wrapUpContext: StateFlow<WrapUpContext> = _wrapUpContext.asStateFlow()

    /** Set when the rep returns to the app with a recent call still unlogged. */
    private val _logPrompt = MutableStateFlow<CallLogEntity?>(null)
    val logPrompt: StateFlow<CallLogEntity?> = _logPrompt.asStateFlow()

    private var backlogActive = false
    private val skippedIds = mutableSetOf<Long>()

    private val filtersFlow = combine(
        _selectedTab,
        _searchQuery,
        _selectedLeadStatus,
        _isRefreshing,
        _statusMessage,
        _syncConfirmedBanner,
        _dialerDigits
    ) { values ->
        FilterSlice(
            tab = values[0] as HomeTab,
            query = values[1] as String,
            leadStatus = values[2] as String,
            refreshing = values[3] as Boolean,
            message = values[4] as String?,
            confirmedBanner = values[5] as String?,
            dialerDigits = values[6] as String
        )
    }

    private val pipelineDataFlow = combine(
        repository.allCallLogsFlow,
        repository.tasksFlow,
        repository.leadsFlow,
        repository.dealsFlow,
        repository.contactsFlow
    ) { calls, tasks, leads, deals, contacts ->
        PipelineDataSlice(calls, tasks, leads, deals, contacts)
    }

    val uiState: StateFlow<PipelineUiState> = combine(
        filtersFlow,
        pipelineDataFlow,
        repository.pendingCountFlow,
        repository.credentialStore.settingsFlow,
        repository.syncStageFlow,
        audioPlaybackManager.state
    ) { values ->
        val filters = values[0] as FilterSlice
        val data = values[1] as PipelineDataSlice
        val pendingCount = values[2] as Int
        val settings = values[3] as AppSettings
        val syncStage = values[4] as String?
        val playback = values[5] as PlaybackUiState

        val q = filters.query.trim().lowercase()

        val startOfDayMs = startOfTodayMillis()
        val callsTodayCount = data.calls.count { it.timestampMs >= startOfDayMs }
        val unloggedCount = data.calls.count { it.isUnlogged() }

        val filteredCalls = if (q.isBlank()) data.calls else data.calls.filter {
            it.matchedName?.lowercase()?.contains(q) == true ||
                it.matchedCompany?.lowercase()?.contains(q) == true ||
                it.phoneNumber.lowercase().contains(q) ||
                it.note?.lowercase()?.contains(q) == true
        }

        val filteredTasks = if (q.isBlank()) data.tasks else data.tasks.filter {
            it.title.lowercase().contains(q) ||
                it.referenceDocname.lowercase().contains(q) ||
                it.phone.lowercase().contains(q)
        }

        val filteredLeads = data.leads.filter { lead ->
            val matchesStatus = filters.leadStatus == "All" ||
                lead.status.equals(filters.leadStatus, ignoreCase = true)
            val matchesQuery = q.isBlank() ||
                lead.leadName.lowercase().contains(q) ||
                lead.organization.lowercase().contains(q) ||
                lead.mobileNo.lowercase().contains(q) ||
                lead.phone.lowercase().contains(q)
            matchesStatus && matchesQuery
        }

        val filteredDeals = if (q.isBlank()) data.deals else data.deals.filter {
            it.dealTitle.lowercase().contains(q) ||
                it.organization.lowercase().contains(q) ||
                it.dealStage.lowercase().contains(q) ||
                it.phone.lowercase().contains(q)
        }

        val filteredContacts = if (q.isBlank()) data.contacts else data.contacts.filter {
            it.fullName.lowercase().contains(q) ||
                it.companyName.lowercase().contains(q) ||
                it.mobileNo.lowercase().contains(q) ||
                it.phone.lowercase().contains(q)
        }

        val dialerMatches = computeDialerSuggestions(
            digits = filters.dialerDigits,
            leads = data.leads,
            deals = data.deals,
            contacts = data.contacts
        )

        PipelineUiState(
            selectedTab = filters.tab,
            searchQuery = filters.query,
            selectedLeadStatus = filters.leadStatus,
            recentCalls = filteredCalls,
            tasks = filteredTasks,
            leads = filteredLeads,
            deals = filteredDeals,
            contacts = filteredContacts,
            pendingSyncCount = pendingCount,
            callsTodayCount = callsTodayCount,
            unloggedCallsCount = unloggedCount,
            settings = settings,
            isRefreshing = filters.refreshing,
            syncStage = syncStage,
            syncConfirmedBanner = filters.confirmedBanner,
            statusMessage = filters.message,
            playbackState = playback,
            dialerDigits = filters.dialerDigits,
            dialerMatches = dialerMatches,
            dialerRecents = recentDialerNumbers(data.calls, System.currentTimeMillis())
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PipelineUiState())

    private data class FilterSlice(
        val tab: HomeTab,
        val query: String,
        val leadStatus: String,
        val refreshing: Boolean,
        val message: String?,
        val confirmedBanner: String?,
        val dialerDigits: String
    )

    private data class PipelineDataSlice(
        val calls: List<CallLogEntity>,
        val tasks: List<TaskEntity>,
        val leads: List<LeadEntity>,
        val deals: List<DealEntity>,
        val contacts: List<ContactEntity>
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

    fun dismissSyncConfirmedBanner() {
        _syncConfirmedBanner.value = null
    }

    fun appendDialerDigit(char: Char) {
        if (_dialerDigits.value.length >= 20) return
        if (char == '+' && _dialerDigits.value.isNotEmpty()) return
        _dialerDigits.value = _dialerDigits.value + char
    }

    fun backspaceDialerDigit() {
        if (_dialerDigits.value.isNotEmpty()) {
            _dialerDigits.value = _dialerDigits.value.dropLast(1)
        }
    }

    fun clearDialerDigits() {
        _dialerDigits.value = ""
    }

    fun setDialerInput(raw: String) {
        _dialerDigits.value = PhoneNormalizer.sanitizeDialInput(raw)
    }

    fun refreshPipeline() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val queueOk = repository.syncPendingQueueOnce()
            when (val res = repository.refreshAllPipeline()) {
                is RepoResult.Success -> {
                    val label = if (queueOk) {
                        "All calls & CRM pipeline synced"
                    } else {
                        "CRM refreshed; some calls remain queued"
                    }
                    showPeakEndConfirmation(label)
                }
                is RepoResult.Error -> _statusMessage.value = res.message
            }
            _isRefreshing.value = false
        }
    }

    fun retryCallLog(callLogId: Long) {
        viewModelScope.launch {
            repository.retryCallLog(callLogId)
            _statusMessage.value = "Retry queued for Frappe CRM"
        }
    }

    fun placeCall(
        context: Context,
        rawPhone: String,
        overrideSimSlot: WorkSimPreference? = null
    ) {
        if (rawPhone.isBlank()) {
            _statusMessage.value = "No phone number on record"
            return
        }
        val simPref = overrideSimSlot ?: uiState.value.settings.workSimPreference
        val launched = SimManager.placeNativeSimCall(
            context = context,
            phoneNumber = rawPhone,
            preferredSimSlot = simPref
        )
        if (!launched) {
            _statusMessage.value = "Unable to launch cellular dialer"
        }
    }

    fun toggleAudioPlayback(callLog: CallLogEntity) {
        val key = "recent_${callLog.id}_${callLog.frappeDocName}"
        audioPlaybackManager.togglePlay(key, callLog.localAudioPath, callLog.remoteAudioUrl)
    }

    fun seekAudio(positionMs: Int) {
        audioPlaybackManager.seekTo(positionMs)
    }

    fun cycleAudioSpeed() {
        audioPlaybackManager.cycleSpeed()
    }

    fun toggleTaskDone(task: TaskEntity) {
        viewModelScope.launch {
            val res = repository.toggleTaskDone(task)
            if (res is RepoResult.Error) {
                _statusMessage.value = res.message
            } else if (!task.isCompleted) {
                showPeakEndConfirmation("Task marked Done in Frappe CRM")
            }
        }
    }

    fun openWrapUpForCall(callLogId: Long) {
        viewModelScope.launch {
            val call = callLogDao.getById(callLogId)
            _wrapUpCallLog.value = call
            _wrapUpContext.value = if (call == null) WrapUpContext() else buildWrapUpContext(call)
        }
    }

    private suspend fun buildWrapUpContext(call: CallLogEntity): WrapUpContext {
        val doctype = call.referenceDoctype
        val docname = call.referenceDocname
        val isLead = !docname.isNullOrBlank() && (doctype == "CRM Lead" || doctype == "Lead")
        val openTasks = if (docname.isNullOrBlank()) emptyList() else {
            repository.tasksFlow.first().filter {
                !it.isCompleted && it.referenceDocname == docname
            }
        }
        return WrapUpContext(
            leadDocname = if (isLead) docname else null,
            leadStatus = if (isLead) repository.leadStatusOf(docname!!) else null,
            openTasks = openTasks,
            backlogActive = backlogActive,
            backlogRemaining = if (backlogActive) remainingBacklog(excluding = call.id) else 0
        )
    }

    private suspend fun remainingBacklog(excluding: Long): Int =
        repository.allCallLogsFlow.first().count {
            it.isUnlogged() && it.id != excluding && it.id !in skippedIds
        }

    /** Steps through every unlogged call, newest first, one wrap-up sheet at a time. */
    fun startBacklog() {
        viewModelScope.launch {
            skippedIds.clear()
            backlogActive = true
            openNextInBacklog()
        }
    }

    private suspend fun openNextInBacklog() {
        val next = repository.allCallLogsFlow.first()
            .firstOrNull { it.isUnlogged() && it.id !in skippedIds }
        if (next == null) {
            backlogActive = false
            _wrapUpCallLog.value = null
            _wrapUpContext.value = WrapUpContext()
            showPeakEndConfirmation("All calls logged")
        } else {
            _wrapUpCallLog.value = next
            _wrapUpContext.value = buildWrapUpContext(next)
        }
    }

    fun skipWrapUp() {
        val current = _wrapUpCallLog.value ?: return
        viewModelScope.launch {
            skippedIds += current.id
            if (backlogActive) openNextInBacklog() else dismissWrapUp()
        }
    }

    fun dismissWrapUp() {
        backlogActive = false
        _wrapUpCallLog.value = null
        _wrapUpContext.value = WrapUpContext()
    }

    fun submitWrapUp(
        callLogId: Long,
        disposition: String?,
        note: String?,
        nextFollowUpDate: String?,
        completeTaskNames: Set<String> = emptySet(),
        newLeadStatus: String? = null
    ) {
        viewModelScope.launch {
            repository.updatePostCallWrapUp(callLogId, disposition, note, nextFollowUpDate)

            val ctx = _wrapUpContext.value
            if (newLeadStatus != null && ctx.leadDocname != null) {
                val res = repository.updateLeadStatus(ctx.leadDocname, newLeadStatus)
                if (res is RepoResult.Error) _statusMessage.value = res.message
            }
            ctx.openTasks.filter { it.name in completeTaskNames }.forEach { task ->
                val res = repository.toggleTaskDone(task)
                if (res is RepoResult.Error) _statusMessage.value = res.message
            }

            if (backlogActive) {
                openNextInBacklog()
            } else {
                _wrapUpCallLog.value = null
                _wrapUpContext.value = WrapUpContext()
                showPeakEndConfirmation("Wrap-up saved & queued for Frappe CRM")
            }
        }
    }

    fun createLeadForWrapUp(fullName: String) {
        val call = _wrapUpCallLog.value ?: return
        if (fullName.isBlank()) return
        viewModelScope.launch {
            _wrapUpContext.value = _wrapUpContext.value.copy(creatingLead = true)
            when (val res = repository.createLeadFromCall(call.id, fullName)) {
                is RepoResult.Success -> {
                    val updated = callLogDao.getById(call.id)
                    _wrapUpCallLog.value = updated
                    _wrapUpContext.value = if (updated != null) buildWrapUpContext(updated) else WrapUpContext()
                    showPeakEndConfirmation("Lead created in Frappe CRM")
                }
                is RepoResult.Error -> {
                    _wrapUpContext.value = _wrapUpContext.value.copy(creatingLead = false)
                    _statusMessage.value = res.message
                }
            }
        }
    }

    /** On return to the app: offer to log a call made in the last 15 minutes that has no wrap-up. */
    fun checkRecentUnlogged() {
        if (_wrapUpCallLog.value != null) return
        viewModelScope.launch {
            val cutoff = System.currentTimeMillis() - 15 * 60_000L
            _logPrompt.value = repository.allCallLogsFlow.first()
                .firstOrNull { it.isUnlogged() && it.timestampMs >= cutoff }
        }
    }

    fun dismissLogPrompt() {
        _logPrompt.value = null
    }

    private fun showPeakEndConfirmation(message: String) {
        viewModelScope.launch {
            _syncConfirmedBanner.value = message
            delay(3600)
            if (_syncConfirmedBanner.value == message) {
                _syncConfirmedBanner.value = null
            }
        }
    }

    private fun computeDialerSuggestions(
        digits: String,
        leads: List<LeadEntity>,
        deals: List<DealEntity>,
        contacts: List<ContactEntity>
    ): List<DialerCrmMatch> {
        val cleanDigits = digits.filter { it.isDigit() }
        if (cleanDigits.length < 2) return emptyList()

        val results = mutableListOf<DialerCrmMatch>()
        leads.forEach { lead ->
            val raw = lead.mobileNo.ifBlank { lead.phone }
            val rawDigits = raw.filter { it.isDigit() }
            if (raw.isNotBlank() && (rawDigits.contains(cleanDigits) || lead.normalizedPhone.contains(cleanDigits))) {
                results.add(
                    DialerCrmMatch(
                        doctype = "CRM Lead",
                        docname = lead.name,
                        displayName = lead.leadName,
                        subtitle = lead.organization.ifBlank { lead.status },
                        phoneNumber = raw
                    )
                )
            }
        }
        deals.forEach { deal ->
            val rawDigits = deal.phone.filter { it.isDigit() }
            if (deal.phone.isNotBlank() && (rawDigits.contains(cleanDigits) || deal.normalizedPhone.contains(cleanDigits))) {
                results.add(
                    DialerCrmMatch(
                        doctype = "CRM Deal",
                        docname = deal.name,
                        displayName = deal.dealTitle,
                        subtitle = deal.organization.ifBlank { deal.dealStage },
                        phoneNumber = deal.phone
                    )
                )
            }
        }
        contacts.forEach { contact ->
            val raw = contact.mobileNo.ifBlank { contact.phone }
            val rawDigits = raw.filter { it.isDigit() }
            if (raw.isNotBlank() && (rawDigits.contains(cleanDigits) || contact.normalizedPhone.contains(cleanDigits))) {
                results.add(
                    DialerCrmMatch(
                        doctype = "Contact",
                        docname = contact.name,
                        displayName = contact.fullName,
                        subtitle = contact.companyName.ifBlank { contact.designation.ifBlank { "Contact" } },
                        phoneNumber = raw
                    )
                )
            }
        }
        return results.distinctBy { it.phoneNumber to it.displayName }.take(5)
    }

    private fun startOfTodayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    override fun onCleared() {
        audioPlaybackManager.stop()
        super.onCleared()
    }

    class Factory(
        private val repository: CrmRepository,
        private val callLogDao: CallLogDao,
        private val audioPlaybackManager: AudioPlaybackManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PipelineViewModel(repository, callLogDao, audioPlaybackManager) as T
        }
    }
}

/** The last few distinct numbers dialled or received, newest first, for the dialer's idle state. */
fun recentDialerNumbers(calls: List<CallLogEntity>, nowMs: Long, limit: Int = 6): List<DialerCrmMatch> =
    calls.asSequence()
        .filter { it.normalizedPhone.isNotBlank() }
        .distinctBy { it.normalizedPhone }
        .take(limit)
        .map { call ->
            val dir = when (call.direction) {
                CallDirection.INCOMING.name -> "Incoming"
                CallDirection.MISSED.name -> "Missed"
                else -> "Outgoing"
            }
            DialerCrmMatch(
                doctype = "Recent",
                docname = call.normalizedPhone,
                displayName = call.matchedName?.takeIf { it.isNotBlank() }
                    ?: PhoneNormalizer.formatDisplay(call.phoneNumber),
                subtitle = "$dir · " + com.example.util.RelativeTime.format(nowMs, call.timestampMs),
                phoneNumber = call.phoneNumber
            )
        }
        .toList()
