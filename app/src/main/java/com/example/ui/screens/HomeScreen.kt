package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ContactEntity
import com.example.data.local.DealEntity
import com.example.data.local.LeadEntity
import com.example.data.local.TaskEntity
import com.example.ui.components.HealthStatusBanner
import com.example.ui.components.PostCallNoteSheet
import com.example.ui.navigation.HomeTab
import com.example.ui.theme.JetBrainsMono
import com.example.ui.viewmodel.PipelineViewModel
import com.example.util.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PipelineViewModel,
    onOpenWizard: () -> Unit,
    onOpenSettingsSync: () -> Unit,
    onOpenRecord: (doctype: String, docname: String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wrapUpCall by viewModel.wrapUpCallLog.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var health by remember { mutableStateOf(PermissionHelper.checkHealth(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                health = PermissionHelper.checkHealth(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.navigationBars,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "OneBook Dialer",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = state.selectedTab.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.refreshPipeline() },
                            enabled = !state.isRefreshing,
                            modifier = Modifier.testTag("home_refresh_button")
                        ) {
                            if (state.isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = "Sync Frappe pipeline")
                            }
                        }
                        IconButton(
                            onClick = onOpenSettingsSync,
                            modifier = Modifier.testTag("home_settings_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (state.pendingSyncCount > 0) {
                                        Badge { Text("${state.pendingSyncCount}") }
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = "Sync queue & settings")
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        HomeTab.entries.forEach { tab ->
                            val selected = state.selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab, selected),
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HomeTab.entries.forEach { tab ->
                            val selected = state.selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab, selected),
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        HealthStatusBanner(
                            isHealthy = health.recorderHealthy,
                            pendingSyncCount = state.pendingSyncCount,
                            onClick = onOpenWizard
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Search name, company, or number...") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pipeline_search_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        when (state.selectedTab) {
                            HomeTab.TASKS -> TasksTabContent(
                                tasks = state.tasks,
                                onCall = { phone -> viewModel.placeCall(context, phone) },
                                onToggleDone = { task -> viewModel.toggleTaskDone(task) },
                                onOpenRecord = onOpenRecord,
                                onRefresh = { viewModel.refreshPipeline() }
                            )

                            HomeTab.LEADS -> LeadsTabContent(
                                leads = state.leads,
                                selectedStatus = state.selectedLeadStatus,
                                onSelectStatus = { viewModel.selectLeadStatus(it) },
                                onCall = { phone -> viewModel.placeCall(context, phone) },
                                onOpenLead = { lead -> onOpenRecord("CRM Lead", lead.name) },
                                onRefresh = { viewModel.refreshPipeline() }
                            )

                            HomeTab.DEALS -> DealsTabContent(
                                deals = state.deals,
                                onCall = { phone -> viewModel.placeCall(context, phone) },
                                onOpenDeal = { deal -> onOpenRecord("CRM Deal", deal.name) },
                                onRefresh = { viewModel.refreshPipeline() }
                            )

                            HomeTab.CONTACTS -> ContactsTabContent(
                                contacts = state.contacts,
                                onCall = { phone -> viewModel.placeCall(context, phone) },
                                onOpenContact = { c -> onOpenRecord("Contact", c.name) },
                                onRefresh = { viewModel.refreshPipeline() }
                            )
                        }
                    }
                }
            }
        }
    }

    wrapUpCall?.let { callLog ->
        PostCallNoteSheet(
            callLog = callLog,
            onSave = { disp, note, followUp ->
                viewModel.submitWrapUp(callLog.id, disp, note, followUp)
            },
            onDismiss = { viewModel.dismissWrapUp() }
        )
    }
}

private fun tabIcon(tab: HomeTab, selected: Boolean) = when (tab) {
    HomeTab.TASKS -> if (selected) Icons.Filled.TaskAlt else Icons.Outlined.TaskAlt
    HomeTab.LEADS -> if (selected) Icons.Filled.PersonSearch else Icons.Outlined.PersonSearch
    HomeTab.DEALS -> if (selected) Icons.Filled.Handshake else Icons.Outlined.Handshake
    HomeTab.CONTACTS -> if (selected) Icons.Filled.Contacts else Icons.Outlined.Contacts
}

@Composable
private fun TasksTabContent(
    tasks: List<TaskEntity>,
    onCall: (String) -> Unit,
    onToggleDone: (TaskEntity) -> Unit,
    onOpenRecord: (String, String) -> Unit,
    onRefresh: () -> Unit
) {
    if (tasks.isEmpty()) {
        EmptyPipelineState(
            title = "No Follow-Up Tasks",
            subtitle = "Tasks assigned to you in Frappe CRM will appear here for one-tap calling.",
            onRefresh = onRefresh
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(tasks, key = { it.name }) { task ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = task.referenceDoctype.isNotBlank() && task.referenceDocname.isNotBlank()) {
                        onOpenRecord(task.referenceDoctype, task.referenceDocname)
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onToggleDone(task) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("task_done_${task.name}")
                    ) {
                        Icon(
                            imageVector = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                            contentDescription = "Mark task done",
                            tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        val meta = listOf(
                            task.dueDate.ifBlank { "Today" },
                            task.priority,
                            task.referenceDocname
                        ).filter { it.isNotBlank() }.joinToString(" • ")
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (task.phone.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = { onCall(task.phone) },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("task_call_${task.name}")
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = "Call task contact")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeadsTabContent(
    leads: List<LeadEntity>,
    selectedStatus: String,
    onSelectStatus: (String) -> Unit,
    onCall: (String) -> Unit,
    onOpenLead: (LeadEntity) -> Unit,
    onRefresh: () -> Unit
) {
    val statuses = listOf("All", "New", "Contacted", "Qualified", "Converted", "Lost")

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            items(statuses) { status ->
                FilterChip(
                    selected = selectedStatus.equals(status, ignoreCase = true),
                    onClick = { onSelectStatus(status) },
                    label = { Text(status) },
                    leadingIcon = if (selectedStatus.equals(status, ignoreCase = true)) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }

        if (leads.isEmpty()) {
            EmptyPipelineState(
                title = "No Matching Leads",
                subtitle = "Pull down or tap Sync to fetch your assigned CRM Leads from Frappe.",
                onRefresh = onRefresh
            )
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(leads, key = { it.name }) { lead ->
                val callableNumber = lead.mobileNo.ifBlank { lead.phone }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lead_card_${lead.name}")
                        .clickable { onOpenLead(lead) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = lead.leadName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                StatusPill(label = lead.status)
                            }
                            if (lead.organization.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Business,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = lead.organization,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (callableNumber.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = callableNumber,
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))
                        FilledIconButton(
                            onClick = { onCall(callableNumber) },
                            enabled = callableNumber.isNotBlank(),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("lead_call_${lead.name}")
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = "Call ${lead.leadName}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DealsTabContent(
    deals: List<DealEntity>,
    onCall: (String) -> Unit,
    onOpenDeal: (DealEntity) -> Unit,
    onRefresh: () -> Unit
) {
    if (deals.isEmpty()) {
        EmptyPipelineState(
            title = "No Active Deals",
            subtitle = "Deals from Frappe CRM will group automatically by Deal Stage here.",
            onRefresh = onRefresh
        )
        return
    }

    val grouped = remember(deals) { deals.groupBy { it.dealStage.ifBlank { "Qualification" } } }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        grouped.forEach { (stage, stageDeals) ->
            item(key = "header_$stage") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stage.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${stageDeals.size} deals",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(stageDeals, key = { it.name }) { deal ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("deal_card_${deal.name}")
                        .clickable { onOpenDeal(deal) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = deal.dealTitle,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val sub = listOf(deal.organization, deal.contactName)
                                .filter { it.isNotBlank() }
                                .joinToString(" • ")
                            if (sub.isNotBlank()) {
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (deal.dealValue > 0.0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${deal.currency} ${"%,.0f".format(deal.dealValue)}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))
                        FilledIconButton(
                            onClick = { onCall(deal.phone) },
                            enabled = deal.phone.isNotBlank(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("deal_call_${deal.name}")
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = "Call ${deal.dealTitle}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactsTabContent(
    contacts: List<ContactEntity>,
    onCall: (String) -> Unit,
    onOpenContact: (ContactEntity) -> Unit,
    onRefresh: () -> Unit
) {
    if (contacts.isEmpty()) {
        EmptyPipelineState(
            title = "No Contacts Synced",
            subtitle = "Frappe Contacts sync locally for instant search and incoming Caller ID matching.",
            onRefresh = onRefresh
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(contacts, key = { it.name }) { contact ->
            val callable = contact.mobileNo.ifBlank { contact.phone }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("contact_card_${contact.name}")
                    .clickable { onOpenContact(contact) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = contact.fullName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contact.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val meta = listOf(contact.designation, contact.companyName)
                            .filter { it.isNotBlank() }
                            .joinToString(" • ")
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (callable.isNotBlank()) {
                            Text(
                                text = callable,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { onCall(callable) },
                        enabled = callable.isNotBlank(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("contact_call_${contact.name}")
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = "Call ${contact.fullName}")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun EmptyPipelineState(
    title: String,
    subtitle: String,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.testTag("empty_state_refresh_button")
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Sync from Frappe CRM")
        }
    }
}
