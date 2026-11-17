package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AudioSourceOption
import com.example.data.local.CallLogEntity
import com.example.data.local.PrivacyFilterMode
import com.example.data.local.SyncStatus
import com.example.ui.theme.JetBrainsMono
import com.example.ui.viewmodel.SettingsSyncViewModel
import com.example.util.PhoneNormalizer
import com.example.util.WorkSimPreference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSyncScreen(
    viewModel: SettingsSyncViewModel,
    onBack: () -> Unit,
    onOpenPermissionWizard: () -> Unit,
    onSignedOut: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddExclusionDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Sync Queue & Settings",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { SettingsSectionHeader("Sync", "Calls waiting to upload and your daily target.") }

                // Sync Queue Card
                item {
                    val pendingList = state.callQueue.filter { it.syncStatus != SyncStatus.SYNCED.name }
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Offline-First Upload Queue",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${pendingList.size} pending • ${state.callQueue.size} total logged",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { viewModel.syncNow() },
                                    enabled = !state.isSyncingNow,
                                    modifier = Modifier.testTag("settings_sync_now_button")
                                ) {
                                    if (state.isSyncingNow) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sync Now", maxLines = 1, softWrap = false)
                                    }
                                }
                            }

                            if (pendingList.isEmpty()) {
                                Text(
                                    text = "All recorded calls are synced to Frappe CRM.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                pendingList.forEach { item ->
                                    SyncQueueRow(
                                        item = item,
                                        onRetry = { viewModel.retryCallLog(item.id) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Daily Call Goal Selector (Goal-Gradient Effect)
                item {
                    SectionCard(title = "Daily Conversation Goal") {
                        Text(
                            text = "How many conversations you aim for each day. A conversation is a call that was answered and lasted 10 seconds or more, or one you mark Connected. Missed and unanswered calls do not count.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val goalOptions = listOf(10, 25, 50, 75, 100)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            goalOptions.forEach { target ->
                                FilterChip(
                                    selected = state.settings.dailyCallGoal == target,
                                    onClick = { viewModel.setDailyCallGoal(target) },
                                    label = { Text("$target") },
                                    modifier = Modifier.testTag("daily_goal_chip_$target")
                                )
                            }
                        }
                    }
                }

                item { SettingsSectionHeader("Call capture", "How calls are recorded and which SIM counts as work.") }

                // Audio Source Selector
                item {
                    SectionCard(title = "Audio Capture Source") {
                        Text(
                            text = "Switch audio source if your hardware (Samsung, Xiaomi, OnePlus, Motorola) mutes call downlink.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AudioSourceOption.entries.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setAudioSource(option) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = state.settings.audioSource == option,
                                    onClick = { viewModel.setAudioSource(option) },
                                    modifier = Modifier.testTag("audio_source_${option.name.lowercase()}")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(option.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(option.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "OEM Recording Folder Watcher",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Fallback watcher for Samsung One UI / Xiaomi HyperOS native call recording folders.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.settings.oemWatcherEnabled,
                                onCheckedChange = { viewModel.setOemWatcherEnabled(it) },
                                modifier = Modifier.testTag("oem_watcher_switch")
                            )
                        }
                    }
                }

                // Dual-SIM Work Slot Selector
                item {
                    SectionCard(title = "Work SIM Slot (Dual-SIM)") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.SimCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.activeSims.isEmpty()) {
                                    "Choose which SIM slot is used for work CRM calls."
                                } else {
                                    "Detected: " + state.activeSims.joinToString(" | ") {
                                        "SIM ${it.slotIndex + 1} (${it.carrierName})"
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        WorkSimPreference.entries.forEach { simPref ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setWorkSimPreference(simPref) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = state.settings.workSimPreference == simPref,
                                    onClick = { viewModel.setWorkSimPreference(simPref) },
                                    modifier = Modifier.testTag("work_sim_${simPref.name.lowercase()}")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(simPref.label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                item { SettingsSectionHeader("Privacy", "Decide which calls are recorded and synced.") }

                // Personal Call Privacy Filter
                item {
                    SectionCard(title = "Personal Call Privacy Filter") {
                        PrivacyFilterMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setPrivacyFilterMode(mode) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = state.settings.privacyFilterMode == mode,
                                    onClick = { viewModel.setPrivacyFilterMode(mode) },
                                    modifier = Modifier.testTag("privacy_mode_${mode.name.lowercase()}")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(mode.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Excluded Personal Numbers (${state.excludedNumbers.size})",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            OutlinedButton(
                                onClick = { showAddExclusionDialog = true },
                                modifier = Modifier.testTag("add_excluded_number_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Exclude", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }

                items(state.excludedNumbers, key = { it.normalizedPhone }) { item ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(item.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = PhoneNormalizer.formatDisplay(item.displayPhone),
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { viewModel.removeExcludedNumber(item.normalizedPhone) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove excluded number")
                            }
                        }
                    }
                }

                item { SettingsSectionHeader("Account & device", "Your CRM connection and phone permissions.") }

                // Device Setup & Account Actions
                item {
                    SectionCard(title = "Frappe Connection & Device Access") {
                        Text(
                            text = "Connected as ${state.session.loggedInUser.ifBlank { "CRM Rep" }}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = state.session.baseUrl,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedButton(
                            onClick = onOpenPermissionWizard,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_open_wizard_button")
                        ) {
                            Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Review Permissions & Accessibility")
                        }

                    }
                }

                // Destructive action lives apart from the settings above, behind a confirmation.
                item {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    OutlinedButton(
                        onClick = { showSignOutDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign out of this phone")
                    }
                }
            }
        }
    }

    if (showSignOutDialog) {
        val waiting = state.callQueue.count { it.syncStatus != SyncStatus.SYNCED.name }
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out?") },
            text = {
                Text(
                    if (waiting > 0) {
                        "$waiting call${if (waiting == 1) "" else "s"} have not uploaded yet. They stay on this phone, but nothing syncs until you sign in again. Your saved login is erased from this phone."
                    } else {
                        "Your saved login is erased from this phone. You will need your CRM address and credentials to sign back in."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        viewModel.signOut()
                        onSignedOut()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("settings_sign_out_confirm")
                ) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAddExclusionDialog) {
        AddExcludedNumberDialog(
            onDismiss = { showAddExclusionDialog = false },
            onSave = { phone, label ->
                viewModel.addExcludedNumber(phone, label)
                showAddExclusionDialog = false
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

@Composable
private fun SyncQueueRow(
    item: CallLogEntity,
    onRetry: () -> Unit
) {
    val dateStr = remember(item.timestampMs) {
        SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(item.timestampMs))
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.matchedName ?: PhoneNormalizer.formatDisplay(item.phoneNumber),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$dateStr • ${PhoneNormalizer.formatDuration(item.durationSeconds)} • ${item.syncStatus}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!item.syncError.isNullOrBlank()) {
                    Text(
                        text = item.syncError,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (item.syncStatus == SyncStatus.FAILED.name) {
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Retry upload")
                }
            }
        }
    }
}

@Composable
private fun AddExcludedNumberDialog(
    onDismiss: () -> Unit,
    onSave: (phone: String, label: String) -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exclude Personal Number") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exclude_phone_input")
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (e.g., Family)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(phone, label) },
                enabled = phone.isNotBlank(),
                modifier = Modifier.testTag("exclude_confirm_button")
            ) {
                Text("Add Exclusion")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
