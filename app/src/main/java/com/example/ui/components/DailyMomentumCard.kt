package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JetBrainsMono

@Composable
fun DailyMomentumCard(
    callsTodayCount: Int,
    dailyCallGoal: Int,
    pendingSyncCount: Int,
    unloggedCallsCount: Int,
    syncStage: String?,
    syncConfirmedBanner: String?,
    onLogUnlogged: () -> Unit,
    onOpenSyncQueue: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val safeGoal = dailyCallGoal.coerceAtLeast(1)
    val progress = (callsTodayCount.toFloat() / safeGoal.toFloat()).coerceIn(0f, 1f)
    val goalReached = callsTodayCount >= safeGoal

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Peak-End Rule confirmation pill when sync or wrap-up finishes
        AnimatedVisibility(visible = !syncConfirmedBanner.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sync_confirmed_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = syncConfirmedBanner.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        // Goal-Gradient + Labor Illusion + Loss Aversion strip
        if (compact) {
            CompactGoalRow(
                callsTodayCount = callsTodayCount,
                goal = safeGoal,
                progress = progress,
                goalReached = goalReached,
                pendingSyncCount = pendingSyncCount,
                unloggedCallsCount = unloggedCallsCount,
                syncStage = syncStage,
                onLogUnlogged = onLogUnlogged,
                onOpenSyncQueue = onOpenSyncQueue
            )
        } else Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("daily_momentum_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.TrendingUp,
                            contentDescription = null,
                            tint = if (goalReached) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (syncStage.isNullOrBlank()) "Daily Conversations" else syncStage,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "$callsTodayCount / $safeGoal",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                        fontWeight = FontWeight.Bold,
                        color = if (goalReached) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    color = if (goalReached) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(99.dp))
                )

                if (pendingSyncCount > 0 || unloggedCallsCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (unloggedCallsCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(onClickLabel = "Log unlogged calls", onClick = onLogUnlogged)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.EditNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$unloggedCallsCount to log",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        if (pendingSyncCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(onClick = onOpenSyncQueue)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CloudUpload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$pendingSyncCount queued",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


/** One-line goal strip for tabs where the list matters more than the pep talk. */
@Composable
private fun CompactGoalRow(
    callsTodayCount: Int,
    goal: Int,
    progress: Float,
    goalReached: Boolean,
    pendingSyncCount: Int,
    unloggedCallsCount: Int,
    syncStage: String?,
    onLogUnlogged: () -> Unit,
    onOpenSyncQueue: () -> Unit
) {
    val tint = if (goalReached) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("daily_momentum_card_compact")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.TrendingUp, contentDescription = "Daily call goal", tint = tint, modifier = Modifier.size(18.dp))
            Text(
                text = if (syncStage.isNullOrBlank()) "$callsTodayCount/$goal" else syncStage,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                fontWeight = FontWeight.Bold,
                color = tint,
                maxLines = 1
            )
            LinearProgressIndicator(
                progress = { progress },
                color = tint,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp))
            )
            if (unloggedCallsCount > 0) {
                // The outer box gives a full 48dp touch target around the smaller visible chip.
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClickLabel = "Log unlogged calls", onClick = onLogUnlogged),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$unloggedCallsCount to log",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
            if (pendingSyncCount > 0) {
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClickLabel = "Open sync queue", onClick = onOpenSyncQueue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.CloudUpload,
                        contentDescription = "$pendingSyncCount calls queued",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(6.dp)
                            .size(18.dp)
                    )
                }
            }
        }
    }
}
