package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.viewmodel.WrapUpContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CallLogEntity
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.SignalGreen
import com.example.util.PhoneNormalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CALL_DISPOSITIONS = listOf(
    "Connected",
    "Busy",
    "Callback Later",
    "Not Interested"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostCallNoteSheet(
    callLog: CallLogEntity,
    wrapUpContext: WrapUpContext,
    onSave: (
        disposition: String?,
        note: String?,
        nextFollowUpDate: String?,
        completeTaskNames: Set<String>,
        newLeadStatus: String?
    ) -> Unit,
    onSkip: () -> Unit,
    onCreateLead: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedDisposition by remember(callLog.id) { mutableStateOf(callLog.disposition) }
    var noteText by remember(callLog.id) { mutableStateOf(callLog.note.orEmpty()) }
    var followUpDate by remember(callLog.id) { mutableStateOf(callLog.nextFollowUpDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var newLeadName by remember(callLog.id) { mutableStateOf("") }
    var doneTasks by remember(callLog.id) { mutableStateOf(setOf<String>()) }

    // Lead status the call suggests. Only moves a brand-new lead forward, or closes it on request.
    val suggestedStatus: String? = when {
        wrapUpContext.leadStatus == null || selectedDisposition == null -> null
        selectedDisposition == "Not Interested" -> "Unqualified"
        wrapUpContext.leadStatus.equals("New", ignoreCase = true) -> "Contacted"
        else -> null
    }
    var applyLeadStatus by remember(callLog.id, suggestedStatus) { mutableStateOf(suggestedStatus == "Contacted") }

    // A conclusive outcome pre-ticks the open tasks for this record; the rep can untick them.
    LaunchedEffect(selectedDisposition) {
        if (selectedDisposition == "Connected" || selectedDisposition == "Not Interested") {
            doneTasks = wrapUpContext.openTasks.map { it.name }.toSet()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            if (wrapUpContext.backlogActive) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = if (wrapUpContext.backlogRemaining == 0) "Last unlogged call"
                        else "${wrapUpContext.backlogRemaining} more unlogged after this",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "Completed call",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.post_call_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val callerLabel = callLog.matchedName ?: PhoneNormalizer.formatDisplay(callLog.phoneNumber)
                        Text(
                            text = "$callerLabel • ${PhoneNormalizer.formatDuration(callLog.durationSeconds)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("wrap_up_dismiss_button")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss wrap-up")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Call Disposition",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CALL_DISPOSITIONS.forEach { disp ->
                    val selected = selectedDisposition == disp
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedDisposition = if (selected) null else disp
                        },
                        label = { Text(disp) },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        modifier = Modifier.testTag("disposition_chip_${disp.lowercase().replace(' ', '_')}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (callLog.referenceDocname.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Not in your CRM", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Save this number as a lead so the call and recording attach to someone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = newLeadName,
                            onValueChange = { newLeadName = it },
                            label = { Text("Lead name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("wrap_up_new_lead_name")
                        )
                        OutlinedButton(
                            onClick = { onCreateLead(newLeadName) },
                            enabled = newLeadName.isNotBlank() && !wrapUpContext.creatingLead,
                            modifier = Modifier.fillMaxWidth().testTag("wrap_up_create_lead_button")
                        ) {
                            if (wrapUpContext.creatingLead) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create lead")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Call Notes") },
                placeholder = { Text(stringResource(R.string.post_call_note_hint)) },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wrap_up_note_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wrap_up_schedule_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (followUpDate.isNullOrBlank()) {
                        stringResource(R.string.post_call_schedule_label)
                    } else {
                        "Follow-up: $followUpDate"
                    }
                )
            }

            if (suggestedStatus != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = applyLeadStatus,
                        onCheckedChange = { applyLeadStatus = it },
                        modifier = Modifier.testTag("wrap_up_lead_status_check")
                    )
                    Text(
                        text = "Move lead ${wrapUpContext.leadStatus} → $suggestedStatus",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            wrapUpContext.openTasks.forEach { task ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = task.name in doneTasks,
                        onCheckedChange = { on ->
                            doneTasks = if (on) doneTasks + task.name else doneTasks - task.name
                        }
                    )
                    Text(
                        text = "Mark task done: ${task.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    onSave(
                        selectedDisposition,
                        noteText,
                        followUpDate,
                        doneTasks,
                        if (applyLeadStatus) suggestedStatus else null
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SignalGreen,
                    contentColor = androidx.compose.ui.graphics.Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("wrap_up_save_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.post_call_save),
                    style = MaterialTheme.typography.titleSmall
                )
            }

            if (wrapUpContext.backlogActive) {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth().testTag("wrap_up_skip_button")
                ) { Text("Skip this call") }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            followUpDate = fmt.format(Date(millis))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
