package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.CallLogEntity
import com.example.data.local.ContactEntity
import com.example.data.local.CrmNoteEntity
import com.example.data.local.DealEntity
import com.example.data.local.LeadEntity
import com.example.data.repository.CrmRepository
import com.example.data.repository.RepoResult
import com.example.service.AudioPlaybackManager
import com.example.service.PlaybackUiState
import com.example.util.PhoneNormalizer
import com.example.util.SimManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecordDetailUiState(
    val doctype: String = "",
    val docname: String = "",
    val title: String = "",
    val subtitle: String = "",
    val stageOrStatus: String = "",
    val primaryPhone: String = "",
    val secondaryPhone: String = "",
    val email: String = "",
    val valueText: String = "",
    val extraFields: List<Pair<String, String>> = emptyList(),
    val callLogs: List<CallLogEntity> = emptyList(),
    val notes: List<CrmNoteEntity> = emptyList(),
    val playbackState: PlaybackUiState = PlaybackUiState(),
    val feedbackMessage: String? = null
)

class RecordDetailViewModel(
    private val doctype: String,
    private val docname: String,
    private val repository: CrmRepository,
    private val audioPlaybackManager: AudioPlaybackManager
) : ViewModel() {

    private val _feedback = MutableStateFlow<String?>(null)

    private val headerFlow = when (doctype) {
        "CRM Lead", "Lead" -> repository.observeLead(docname).combine(MutableStateFlow(Unit)) { l, _ ->
            l?.toHeader()
        }
        "CRM Deal", "Opportunity" -> repository.observeDeal(docname).combine(MutableStateFlow(Unit)) { d, _ ->
            d?.toHeader()
        }
        else -> repository.observeContact(docname).combine(MutableStateFlow(Unit)) { c, _ ->
            c?.toHeader()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val callLogsFlow = headerFlow.flatMapLatest { header ->
        val norm = PhoneNormalizer.normalize(header?.primaryPhone)
        repository.observeCallLogsForRecord(doctype, docname, norm)
    }

    val uiState: StateFlow<RecordDetailUiState> = combine(
        headerFlow,
        callLogsFlow,
        repository.observeNotesForRecord(doctype, docname),
        audioPlaybackManager.state,
        _feedback
    ) { header, calls, notes, playback, msg ->
        (header ?: RecordDetailUiState(doctype = doctype, docname = docname, title = docname)).copy(
            callLogs = calls,
            notes = notes,
            playbackState = playback,
            feedbackMessage = msg
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        RecordDetailUiState(doctype = doctype, docname = docname, title = docname)
    )

    init {
        viewModelScope.launch {
            repository.fetchNotesForRecord(doctype, docname)
        }
    }

    fun placeCall(context: Context) {
        val phone = uiState.value.primaryPhone.ifBlank { uiState.value.secondaryPhone }
        if (phone.isBlank()) {
            _feedback.value = "No phone number attached to this record"
            return
        }
        viewModelScope.launch {
            val pref = repository.credentialStore.getSettings().workSimPreference
            if (!SimManager.placeNativeSimCall(context, phone, pref)) {
                _feedback.value = "Failed to launch cellular dialer"
            }
        }
    }

    fun toggleAudioPlayback(callLog: CallLogEntity) {
        val key = "call_${callLog.id}_${callLog.frappeDocName}"
        audioPlaybackManager.togglePlay(key, callLog.localAudioPath, callLog.remoteAudioUrl)
    }

    fun seekAudio(positionMs: Int) {
        audioPlaybackManager.seekTo(positionMs)
    }

    fun cycleAudioSpeed() {
        audioPlaybackManager.cycleSpeed()
    }

    fun addNote(title: String, content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            when (val res = repository.addNoteToRecord(doctype, docname, title.ifBlank { "Note" }, content)) {
                is RepoResult.Success -> _feedback.value = "Note saved to Frappe CRM"
                is RepoResult.Error -> _feedback.value = res.message
            }
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    override fun onCleared() {
        audioPlaybackManager.stop()
        super.onCleared()
    }

    private fun LeadEntity.toHeader(): RecordDetailUiState {
        val primary = mobileNo.ifBlank { phone }
        return RecordDetailUiState(
            doctype = doctype,
            docname = name,
            title = leadName,
            subtitle = organization.ifBlank { "Lead • $name" },
            stageOrStatus = status,
            primaryPhone = primary,
            secondaryPhone = if (phone != primary) phone else "",
            email = email,
            extraFields = buildList {
                if (source.isNotBlank()) add("Source" to source)
                add("CRM ID" to name)
                if (lastNote.isNotBlank()) add("Summary" to lastNote)
            }
        )
    }

    private fun DealEntity.toHeader(): RecordDetailUiState {
        return RecordDetailUiState(
            doctype = doctype,
            docname = name,
            title = dealTitle,
            subtitle = organization.ifBlank { contactName.ifBlank { name } },
            stageOrStatus = dealStage,
            primaryPhone = phone,
            valueText = if (dealValue > 0.0) "$currency ${"%,.0f".format(dealValue)}" else "",
            extraFields = buildList {
                if (contactName.isNotBlank()) add("Contact" to contactName)
                add("Deal ID" to name)
                if (lastNote.isNotBlank()) add("Next Step" to lastNote)
            }
        )
    }

    private fun ContactEntity.toHeader(): RecordDetailUiState {
        val primary = mobileNo.ifBlank { phone }
        return RecordDetailUiState(
            doctype = doctype,
            docname = name,
            title = fullName,
            subtitle = listOf(designation, companyName).filter { it.isNotBlank() }.joinToString(" • "),
            stageOrStatus = "Contact",
            primaryPhone = primary,
            secondaryPhone = if (phone != primary) phone else "",
            email = email,
            extraFields = listOf("Contact ID" to name)
        )
    }

    class Factory(
        private val doctype: String,
        private val docname: String,
        private val repository: CrmRepository,
        private val audioPlaybackManager: AudioPlaybackManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecordDetailViewModel(doctype, docname, repository, audioPlaybackManager) as T
        }
    }
}
