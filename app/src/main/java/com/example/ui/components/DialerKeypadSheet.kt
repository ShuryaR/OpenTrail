package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.SignalGreen
import com.example.ui.viewmodel.DialerCrmMatch
import com.example.util.PhoneNormalizer
import com.example.util.SimSlotInfo
import com.example.util.WorkSimPreference

private data class DialKey(
    val digit: Char,
    val subLabel: String,
    val tag: String
)

private val DIAL_ROWS = listOf(
    listOf(
        DialKey('1', "", "1"),
        DialKey('2', "ABC", "2"),
        DialKey('3', "DEF", "3")
    ),
    listOf(
        DialKey('4', "GHI", "4"),
        DialKey('5', "JKL", "5"),
        DialKey('6', "MNO", "6")
    ),
    listOf(
        DialKey('7', "PQRS", "7"),
        DialKey('8', "TUV", "8"),
        DialKey('9', "WXYZ", "9")
    ),
    listOf(
        DialKey('*', "", "star"),
        DialKey('0', "+", "0"),
        DialKey('#', "", "hash")
    )
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DialerKeypadSheet(
    digits: String,
    matches: List<DialerCrmMatch>,
    recents: List<DialerCrmMatch>,
    activeSims: List<SimSlotInfo>,
    defaultSimPreference: WorkSimPreference,
    onAppendDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClearAll: () -> Unit,
    onPasteNumber: (String) -> Unit,
    onSelectMatch: (DialerCrmMatch) -> Unit,
    onPlaceCall: (String, WorkSimPreference) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var selectedSim by remember(defaultSimPreference) { mutableStateOf(defaultSimPreference) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("dialer_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Header + Postel's Law Paste Shortcut
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SIM Dialer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            clipboard.getText()?.text?.let { raw ->
                                onPasteNumber(raw)
                            }
                        },
                        modifier = Modifier.testTag("dialer_paste_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste")
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("dialer_close_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close dialer")
                    }
                }
            }

            // Doherty Threshold: Instant live CRM match chips as user types
            val idle = digits.isBlank()
            val chips = if (idle) recents else matches
            if (idle && chips.isNotEmpty()) {
                Text(
                    text = "Recent",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = chips.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(chips, key = { "${it.doctype}_${it.docname}_${it.phoneNumber}" }) { match ->
                        Surface(
                            onClick = { onSelectMatch(match) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = match.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (idle) match.subtitle
                                    else "${match.doctype} • ${PhoneNormalizer.formatDisplay(match.phoneNumber)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Number Display + Chunked Formatting + Backspace/Clear
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val formatted = if (digits.isBlank()) {
                        "Enter phone number"
                    } else {
                        PhoneNormalizer.formatDisplay(digits)
                    }
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = JetBrainsMono,
                            fontSize = if (digits.length > 13) 22.sp else 27.sp
                        ),
                        color = if (digits.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialer_display")
                    )

                    if (digits.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onBackspace()
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onClearAll()
                                    }
                                )
                                .testTag("dialer_backspace_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Backspace or hold to clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3x4 Fitts's Law Keypad
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DIAL_ROWS.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { key ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(58.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .combinedClickable(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onAppendDigit(key.digit)
                                        },
                                        onLongClick = {
                                            if (key.digit == '0' && digits.isEmpty()) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onAppendDigit('+')
                                            }
                                        }
                                    )
                                    .testTag("dialer_key_${key.tag}")
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = key.digit.toString(),
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontFamily = JetBrainsMono,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (key.subLabel.isNotEmpty()) {
                                        Text(
                                            text = key.subLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Optional Dual-SIM quick switch when device has 2 active SIMs
            if (activeSims.size >= 2) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        Icon(
                            imageVector = Icons.Filled.SimCard,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(WorkSimPreference.entries) { pref ->
                        val simLabel = when (pref) {
                            WorkSimPreference.ANY -> "Auto SIM"
                            WorkSimPreference.SIM_1 -> activeSims.firstOrNull { it.slotIndex == 0 }?.carrierName
                                ?.let { "SIM 1 ($it)" } ?: "SIM 1"
                            WorkSimPreference.SIM_2 -> activeSims.firstOrNull { it.slotIndex == 1 }?.carrierName
                                ?.let { "SIM 2 ($it)" } ?: "SIM 2"
                        }
                        FilterChip(
                            selected = selectedSim == pref,
                            onClick = { selectedSim = pref },
                            label = { Text(simLabel) }
                        )
                    }
                }
            }

            // Von Restorff Signal Green Call CTA
            Button(
                onClick = {
                    if (digits.isNotBlank()) {
                        onPlaceCall(digits, selectedSim)
                    }
                },
                enabled = digits.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SignalGreen,
                    contentColor = Color.White,
                    disabledContainerColor = SignalGreen.copy(alpha = 0.28f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("dialer_call_button")
            ) {
                Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Call via SIM",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
