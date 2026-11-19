package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.WifiCalling3
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material.icons.filled.Warning
import kotlinx.coroutines.launch
import com.example.R
import com.example.ui.theme.HealthGreen
import com.example.util.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionWizardScreen(
    showBackButton: Boolean,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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

    val phonePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        health = PermissionHelper.checkHealth(context)
    }

    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        health = PermissionHelper.checkHealth(context)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.wizard_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("wizard_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
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
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.wizard_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val completedCount = listOf(
                    health.phoneAndCallLogGranted,
                    health.micAndNotificationsGranted,
                    health.accessibilityEnabled,
                    health.batteryOptimizationIgnored
                ).count { it }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "$completedCount of 4 core steps completed",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                // Step 1: Phone & Call Log
                WizardStepCard(
                    stepNumber = 1,
                    title = "Phone & Call Log Access",
                    description = "Required to trigger native SIM calls and read call duration, direction, and timestamps.",
                    icon = Icons.Filled.Phone,
                    isGranted = health.phoneAndCallLogGranted,
                    actionLabel = "Grant Phone Access",
                    testTag = "wizard_step_phone",
                    onAction = { phonePermLauncher.launch(PermissionHelper.phonePermissions) }
                )

                // Step 2: Microphone & Notifications
                WizardStepCard(
                    stepNumber = 2,
                    title = "Microphone & Notifications",
                    description = "Required to capture call audio locally and display the active recording indicator.",
                    icon = Icons.Filled.Mic,
                    isGranted = health.micAndNotificationsGranted,
                    actionLabel = "Grant Mic & Alerts",
                    testTag = "wizard_step_mic",
                    onAction = { audioPermLauncher.launch(PermissionHelper.audioAndNotificationPermissions) }
                )

                // Step 3: Restricted Settings + Accessibility Service
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        1.dp,
                        if (health.accessibilityEnabled) HealthGreen.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccessibilityNew,
                                    contentDescription = null,
                                    tint = if (health.accessibilityEnabled) HealthGreen else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "3. Restricted Settings & Accessibility",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            if (health.accessibilityEnabled) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Enabled",
                                    tint = HealthGreen
                                )
                            }
                        }

                        Text(
                            text = "For sideloaded APKs on Android 13+: first open App Info, tap the top-right ⋮ menu → 'Allow restricted settings', then enable 'OneBook Call Recorder' in Accessibility.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!health.accessibilityEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { PermissionHelper.openAppInfoForRestrictedSettings(context) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("wizard_open_app_info")
                                ) {
                                    Text("1. App Info")
                                }
                                FilledTonalButton(
                                    onClick = { PermissionHelper.openAccessibilitySettings(context) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("wizard_open_accessibility")
                                ) {
                                    Text("2. Accessibility")
                                }
                            }
                        }
                    }
                }

                // Step 4: Unrestricted Battery Usage
                WizardStepCard(
                    stepNumber = 4,
                    title = "Unrestricted Battery Usage",
                    description = "Prevents Android from sleeping the background call tracker and offline sync queue.",
                    icon = Icons.Filled.BatteryChargingFull,
                    isGranted = health.batteryOptimizationIgnored,
                    actionLabel = "Allow Unrestricted",
                    testTag = "wizard_step_battery",
                    onAction = { PermissionHelper.requestIgnoreBatteryOptimizations(context) }
                )

                // Optional Caller ID Overlay
                WizardStepCard(
                    stepNumber = 5,
                    title = "Caller ID Floating Banner (Optional)",
                    description = "Shows CRM Lead & Deal context over the dialer when a customer calls your SIM.",
                    icon = Icons.Filled.Layers,
                    isGranted = health.overlayGranted,
                    actionLabel = "Allow Overlay",
                    testTag = "wizard_step_overlay",
                    onAction = { PermissionHelper.openOverlayPermissionSettings(context) }
                )

                OemTipsCard(context)

                WizardStepCard(
                    stepNumber = 6,
                    title = "Use the phone's own call recorder (Optional)",
                    description = "Samsung and some other phones record both sides of a call themselves. Allow file access so OneBook can attach that recording, and turn on automatic call recording in the Phone app settings.",
                    icon = Icons.Filled.Layers,
                    isGranted = health.allFilesAccess,
                    actionLabel = "Allow file access",
                    testTag = "wizard_step_allfiles",
                    onAction = { PermissionHelper.openAllFilesAccessSettings(context) }
                )

                SelfTestCard(context, accessibilityReady = health.accessibilityEnabled)

                // VoWiFi Warning Tip
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WifiCalling3,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.wizard_vowifi_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.wizard_vowifi_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onComplete,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("wizard_continue_button")
                ) {
                    Text(
                        text = if (health.allCoreReady) {
                            stringResource(R.string.wizard_continue)
                        } else {
                            "Continue to Pipeline (${4 - completedCount} pending)"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun WizardStepCard(
    stepNumber: Int,
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    actionLabel: String,
    testTag: String,
    onAction: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (isGranted) HealthGreen.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) HealthGreen else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "$stepNumber. $title",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (isGranted) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Granted",
                        tint = HealthGreen
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isGranted) {
                FilledTonalButton(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(testTag)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}


@Composable
private fun OemTipsCard(context: android.content.Context) {
    val guide = remember { com.example.util.OemGuide.forManufacturer(android.os.Build.MANUFACTURER) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.testTag("wizard_oem_tips")
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tips for ${guide.brand}", style = MaterialTheme.typography.titleMedium)
            Text(
                "These settings are easy to miss and are the usual reason recordings stop.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            guide.steps.forEachIndexed { i, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(step, style = MaterialTheme.typography.bodyMedium)
                }
            }
            androidx.compose.material3.OutlinedButton(
                onClick = { PermissionHelper.openAppInfoForRestrictedSettings(context) },
                modifier = Modifier.fillMaxWidth().testTag("wizard_oem_open_app_info")
            ) { Text("Open OneBook app settings") }
        }
    }
}

@Composable
private fun SelfTestCard(context: android.content.Context, accessibilityReady: Boolean) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<com.example.service.SelfTestResult?>(null) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.testTag("wizard_self_test")
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Test my setup", style = MaterialTheme.typography.titleMedium)
            Text(
                "Records three seconds from the microphone and reports what it heard. Say a few words while it runs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.material3.FilledTonalButton(
                onClick = {
                    running = true
                    result = null
                    scope.launch {
                        val service = com.example.service.CallRecorderAccessibilityService.instance
                        val source = (context.applicationContext as com.example.OneBookApplication)
                            .credentialStore.getSettings().audioSource
                        result = if (service == null) {
                            com.example.service.SelfTestResult(
                                false, 0,
                                "The recorder service is off. Finish step 3 and switch OneBook Call Recorder on in Accessibility."
                            )
                        } else {
                            service.runSelfTest(source)
                        }
                        running = false
                    }
                },
                enabled = !running,
                modifier = Modifier.fillMaxWidth().testTag("wizard_self_test_button")
            ) {
                if (running) {
                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Listening…")
                } else {
                    Text("Run 3-second test")
                }
            }
            result?.let { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = if (r.ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = if (r.ok) "Test passed" else "Test needs attention",
                        tint = if (r.ok) HealthGreen else MaterialTheme.colorScheme.error
                    )
                    Text(r.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("wizard_self_test_result"))
                }
            }
        }
    }
}
