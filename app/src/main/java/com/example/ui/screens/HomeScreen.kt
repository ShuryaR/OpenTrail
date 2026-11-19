package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.DailyMomentumCard
import com.example.ui.components.DialerKeypadSheet
import com.example.ui.components.HealthStatusBanner
import com.example.ui.components.PostCallNoteSheet
import com.example.ui.navigation.HomeTab
import com.example.ui.theme.SignalGreen
import com.example.ui.viewmodel.PipelineViewModel
import com.example.util.PermissionHelper
import com.example.util.SimManager
import com.example.util.SimSlotInfo

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
    val wrapUpContext by viewModel.wrapUpContext.collectAsStateWithLifecycle()
    val logPrompt by viewModel.logPrompt.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var health by remember { mutableStateOf(PermissionHelper.checkHealth(context)) }
    var showDialerSheet by remember { mutableStateOf(false) }
    // The dialer button hides while the list scrolls up and returns on scroll down, so it never
    // sits on top of a row's call button.
    var fabVisible by remember { mutableStateOf(true) }
    val fabScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (available.y < -6f) fabVisible = false else if (available.y > 6f) fabVisible = true
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    var activeSims by remember { mutableStateOf<List<SimSlotInfo>>(emptyList()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                health = PermissionHelper.checkHealth(context)
                viewModel.checkRecentUnlogged()
                com.example.service.RecorderAlert.check(context, afterUpdate = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(showDialerSheet) {
        if (showDialerSheet) {
            activeSims = SimManager.getActiveSims(context)
        }
    }

    LaunchedEffect(logPrompt) {
        val call = logPrompt ?: return@LaunchedEffect
        val who = call.matchedName ?: com.example.util.PhoneNormalizer.formatDisplay(call.phoneNumber)
        val result = snackbarHostState.showSnackbar(
            message = "Log your call with $who?",
            actionLabel = "Log now",
            withDismissAction = true,
            duration = androidx.compose.material3.SnackbarDuration.Long
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            viewModel.openWrapUpForCall(call.id)
        }
        viewModel.dismissLogPrompt()
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
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
              Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "OneBook Dialer",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = state.syncStage ?: "${state.selectedTab.label} · synced " +
                                    com.example.util.RelativeTime.format(
                                        System.currentTimeMillis(),
                                        state.settings.lastSyncEpochMs
                                    ),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (state.syncStage != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
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
                Box(modifier = Modifier.fillMaxWidth().height(3.dp)) {
                    if (state.isRefreshing || state.syncStage != null) {
                        androidx.compose.material3.LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().testTag("sync_progress_bar")
                        )
                    }
                }
              }
            },
            floatingActionButton = {
                if (useRail) {
                    ExtendedFloatingActionButton(
                        onClick = { showDialerSheet = true },
                        icon = { Icon(Icons.Filled.Dialpad, contentDescription = null) },
                        text = { Text("Dialer") },
                        containerColor = SignalGreen,
                        contentColor = Color.White,
                        modifier = Modifier.testTag("home_dialer_fab")
                    )
                } else {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = fabVisible,
                        enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()
                    ) {
                        FloatingActionButton(
                            onClick = { showDialerSheet = true },
                            containerColor = SignalGreen,
                            contentColor = Color.White,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.testTag("home_dialer_fab")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Dialpad,
                                contentDescription = "Open SIM Dialer keypad"
                            )
                        }
                    }
                }
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
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
                                label = {
                                    Text(
                                        text = tab.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                alwaysShowLabel = true,
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
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
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
                            .nestedScroll(fabScrollConnection)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HealthStatusBanner(
                            isHealthy = health.recorderHealthy,
                            pendingSyncCount = state.pendingSyncCount,
                            onClick = onOpenWizard
                        )

                        DailyMomentumCard(
                            callsTodayCount = state.callsTodayCount,
                            dailyCallGoal = state.settings.dailyCallGoal,
                            pendingSyncCount = state.pendingSyncCount,
                            unloggedCallsCount = state.unloggedCallsCount,
                            syncStage = state.syncStage,
                            syncConfirmedBanner = state.syncConfirmedBanner,
                            onLogUnlogged = { viewModel.startBacklog() },
                            onOpenSyncQueue = onOpenSettingsSync,
                            compact = state.selectedTab != HomeTab.RECENTS
                        )

                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Search name, company, or number...") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pipeline_search_input")
                        )

                        val tabEmpty = when (state.selectedTab) {
                            HomeTab.RECENTS -> state.recentCalls.isEmpty()
                            HomeTab.TASKS -> state.tasks.isEmpty()
                            HomeTab.LEADS -> state.leads.isEmpty()
                            HomeTab.DEALS -> state.deals.isEmpty()
                            HomeTab.CONTACTS -> state.contacts.isEmpty()
                        }
                        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                            isRefreshing = state.isRefreshing,
                            onRefresh = { viewModel.refreshPipeline() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (tabEmpty && state.isRefreshing) {
                                com.example.ui.components.SkeletonList()
                            } else if (tabEmpty && state.searchQuery.isNotBlank()) {
                                com.example.ui.components.SearchEmptyState(
                                    query = state.searchQuery,
                                    onClear = { viewModel.updateSearchQuery("") }
                                )
                            } else {
                            when (state.selectedTab) {
                                HomeTab.RECENTS -> RecentsTabContent(
                                    calls = state.recentCalls,
                                    playbackState = state.playbackState,
                                    onCall = { phone -> viewModel.placeCall(context, phone) },
                                    onOpenRecord = onOpenRecord,
                                    onAddNote = { callId -> viewModel.openWrapUpForCall(callId) },
                                    onRetrySync = { callId -> viewModel.retryCallLog(callId) },
                                    onToggleAudio = { callLog -> viewModel.toggleAudioPlayback(callLog) },
                                    onSeekAudio = { pos -> viewModel.seekAudio(pos) },
                                    onCycleSpeed = { viewModel.cycleAudioSpeed() },
                                    onOpenDialer = { showDialerSheet = true }
                                )

                                HomeTab.TASKS -> TasksTabContent(
                                    tasks = remember(state.tasks, state.leads, state.deals, state.contacts) {
                                        enrichTasks(state.tasks, state.leads, state.deals, state.contacts)
                                    },
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
                                    onRefresh = { viewModel.refreshPipeline() },
                                    insights = remember(state.leads, state.recentCalls, state.tasks) {
                                        com.example.ui.screens.buildLeadInsights(
                                            state.leads, state.recentCalls, state.tasks, System.currentTimeMillis()
                                        )
                                    }
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
        }
    }

    if (showDialerSheet) {
        DialerKeypadSheet(
            digits = state.dialerDigits,
            matches = state.dialerMatches,
            recents = state.dialerRecents,
            activeSims = activeSims,
            defaultSimPreference = state.settings.workSimPreference,
            onAppendDigit = { viewModel.appendDialerDigit(it) },
            onBackspace = { viewModel.backspaceDialerDigit() },
            onClearAll = { viewModel.clearDialerDigits() },
            onPasteNumber = { viewModel.setDialerInput(it) },
            onSelectMatch = { match -> viewModel.setDialerInput(match.phoneNumber) },
            onPlaceCall = { number, simSlot ->
                showDialerSheet = false
                viewModel.clearDialerDigits()
                viewModel.placeCall(context, number, simSlot)
            },
            onDismiss = { showDialerSheet = false }
        )
    }

    wrapUpCall?.let { callLog ->
        PostCallNoteSheet(
            callLog = callLog,
            wrapUpContext = wrapUpContext,
            onSave = { disp, note, followUp, doneTasks, leadStatus ->
                viewModel.submitWrapUp(callLog.id, disp, note, followUp, doneTasks, leadStatus)
            },
            onSkip = { viewModel.skipWrapUp() },
            onCreateLead = { viewModel.createLeadForWrapUp(it) },
            onDismiss = { viewModel.dismissWrapUp() }
        )
    }
}

private fun tabIcon(tab: HomeTab, selected: Boolean) = when (tab) {
    HomeTab.RECENTS -> if (selected) Icons.Filled.History else Icons.Outlined.History
    HomeTab.TASKS -> if (selected) Icons.Filled.TaskAlt else Icons.Outlined.TaskAlt
    HomeTab.LEADS -> if (selected) Icons.Filled.PersonSearch else Icons.Outlined.PersonSearch
    HomeTab.DEALS -> if (selected) Icons.Filled.Handshake else Icons.Outlined.Handshake
    HomeTab.CONTACTS -> if (selected) Icons.Filled.Contacts else Icons.Outlined.Contacts
}
