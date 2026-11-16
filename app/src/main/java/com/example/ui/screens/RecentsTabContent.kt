package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CallDirection
import com.example.data.local.CallLogEntity
import com.example.data.local.SyncStatus
import com.example.service.PlaybackUiState
import com.example.ui.components.InlineAudioPlayer
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.SignalGreen
import com.example.util.PhoneNormalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun RecentsTabContent(
    calls: List<CallLogEntity>,
    playbackState: PlaybackUiState,
    onCall: (String) -> Unit,
    onOpenRecord: (doctype: String, docname: String) -> Unit,
    onAddNote: (Long) -> Unit,
    onRetrySync: (Long) -> Unit,
    onToggleAudio: (CallLogEntity) -> Unit,
    onSeekAudio: (Int) -> Unit,
    onCycleSpeed: () -> Unit = {},
    onOpenDialer: () -> Unit
) {
    if (calls.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Text(
                text = "No Recent Calls Yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Tap the green Dialer button to place a native SIM call, or pull down your Frappe CRM call logs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                onClick = onOpenDialer,
                shape = RoundedCornerShape(14.dp),
                color = SignalGreen,
                modifier = Modifier.testTag("recents_empty_dialer_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open SIM Dialer",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
        }
        return
    }

    val groupedCalls = remember(calls) {
        calls.groupBy { formatDayGroup(it.timestampMs) }
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        groupedCalls.forEach { (dayLabel, dayCalls) ->
            item(key = "day_$dayLabel") {
                Text(
                    text = dayLabel.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp, start = 4.dp)
                )
            }

            items(dayCalls, key = { "recent_${it.id}_${it.frappeDocName}_${it.timestampMs}" }) { callLog ->
                RecentCallCard(
                    callLog = callLog,
                    playbackState = playbackState,
                    onCall = { onCall(callLog.phoneNumber) },
                    onCardClick = {
                        if (!callLog.referenceDoctype.isNullOrBlank() && !callLog.referenceDocname.isNullOrBlank()) {
                            onOpenRecord(callLog.referenceDoctype, callLog.referenceDocname)
                        } else if (callLog.id > 0L) {
                            onAddNote(callLog.id)
                        }
                    },
                    onAddNote = { if (callLog.id > 0L) onAddNote(callLog.id) },
                    onRetrySync = { if (callLog.id > 0L) onRetrySync(callLog.id) },
                    onToggleAudio = { onToggleAudio(callLog) },
                    onSeekAudio = onSeekAudio,
                    onCycleSpeed = onCycleSpeed
                )
            }
        }
    }
}

@Composable
private fun RecentCallCard(
    callLog: CallLogEntity,
    playbackState: PlaybackUiState,
    onCall: () -> Unit,
    onCardClick: () -> Unit,
    onAddNote: () -> Unit,
    onRetrySync: () -> Unit,
    onToggleAudio: () -> Unit,
    onSeekAudio: (Int) -> Unit,
    onCycleSpeed: () -> Unit = {}
) {
    val direction = runCatching { CallDirection.valueOf(callLog.direction) }
        .getOrDefault(CallDirection.OUTGOING)
    val syncStatus = runCatching { SyncStatus.valueOf(callLog.syncStatus) }
        .getOrDefault(SyncStatus.PENDING)

    val (dirIcon, dirBg, dirTint) = when (direction) {
        CallDirection.OUTGOING -> Triple(
            Icons.AutoMirrored.Filled.CallMade,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        CallDirection.INCOMING -> Triple(
            Icons.AutoMirrored.Filled.CallReceived,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        CallDirection.MISSED -> Triple(
            Icons.AutoMirrored.Filled.CallMissed,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    val timeStr = remember(callLog.timestampMs) {
        SimpleDateFormat("h:mm a", Locale.US).format(Date(callLog.timestampMs))
    }
    val formattedPhone = remember(callLog.phoneNumber) {
        PhoneNormalizer.formatDisplay(callLog.phoneNumber)
    }
    val needsWrapUp = callLog.id > 0L &&
        callLog.note.isNullOrBlank() &&
        callLog.disposition.isNullOrBlank() &&
        direction != CallDirection.MISSED

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recent_call_card_${callLog.id}")
            .clickable(onClick = onCardClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = dirBg,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = dirIcon,
                            contentDescription = direction.label,
                            tint = dirTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Miller's Law chunked 3-line summary
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = callLog.matchedName?.takeIf { it.isNotBlank() } ?: formattedPhone,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (!callLog.referenceDoctype.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = callLog.referenceDoctype.removePrefix("CRM "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    val subLine = buildList {
                        if (!callLog.matchedName.isNullOrBlank()) add(formattedPhone)
                        if (!callLog.matchedCompany.isNullOrBlank()) add(callLog.matchedCompany)
                        add(timeStr)
                        if (callLog.durationSeconds > 0) {
                            add(PhoneNormalizer.formatDuration(callLog.durationSeconds))
                        }
                    }.joinToString(" • ")

                    Text(
                        text = subLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        SyncStatePill(
                            status = syncStatus,
                            onRetry = onRetrySync
                        )

                        // Zeigarnik Effect: Surface unfinished call notes cleanly
                        if (needsWrapUp) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onAddNote)
                                    .testTag("recent_add_note_${callLog.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.EditNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Add note",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                FilledIconButton(
                    onClick = onCall,
                    enabled = callLog.phoneNumber.isNotBlank(),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = SignalGreen,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("recent_call_${callLog.id}")
                ) {
                    Icon(Icons.Filled.Call, contentDescription = "Redial ${callLog.matchedName ?: formattedPhone}")
                }
            }

            if (!callLog.note.isNullOrBlank()) {
                Text(
                    text = callLog.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val hasAudio = !callLog.localAudioPath.isNullOrBlank() || !callLog.remoteAudioUrl.isNullOrBlank()
            if (hasAudio) {
                InlineAudioPlayer(
                    itemKey = "recent_${callLog.id}_${callLog.frappeDocName}",
                    fallbackDurationSec = callLog.durationSeconds,
                    playbackState = playbackState,
                    onTogglePlay = onToggleAudio,
                    onSeek = onSeekAudio,
                    onCycleSpeed = onCycleSpeed
                )
            }
        }
    }
}

@Composable
private fun SyncStatePill(
    status: SyncStatus,
    onRetry: () -> Unit
) {
    val (label, icon, bg, fg) = when (status) {
        SyncStatus.SYNCED -> Quadruple(
            "Synced",
            Icons.Filled.CheckCircle,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        SyncStatus.SYNCING -> Quadruple(
            "Syncing…",
            Icons.Filled.CloudQueue,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        SyncStatus.PENDING -> Quadruple(
            "Queued",
            Icons.Filled.CloudQueue,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        SyncStatus.FAILED -> Quadruple(
            "Retry sync",
            Icons.Filled.ErrorOutline,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = if (status == SyncStatus.FAILED) {
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onRetry)
        } else {
            Modifier
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                color = fg
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun formatDayGroup(timestampMs: Long): String {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterday = today - 86_400_000L
    return when {
        timestampMs >= today -> "Today"
        timestampMs >= yesterday -> "Yesterday"
        else -> SimpleDateFormat("EEE, MMM d", Locale.US).format(Date(timestampMs))
    }
}
