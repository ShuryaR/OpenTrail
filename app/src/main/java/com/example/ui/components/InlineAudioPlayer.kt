package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.material3.TextButton
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.service.PlaybackUiState
import com.example.ui.theme.JetBrainsMono
import com.example.util.PhoneNormalizer

@Composable
fun InlineAudioPlayer(
    itemKey: String,
    fallbackDurationSec: Int,
    playbackState: PlaybackUiState,
    onTogglePlay: () -> Unit,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onCycleSpeed: () -> Unit = {}
) {
    val isThisActive = playbackState.activeKey == itemKey
    val isPlaying = isThisActive && playbackState.isPlaying
    val isLoading = isThisActive && playbackState.isLoading
    val durationMs = if (isThisActive && playbackState.durationMs > 0) {
        playbackState.durationMs
    } else {
        (fallbackDurationSec * 1000).coerceAtLeast(1000)
    }
    val positionMs = if (isThisActive) playbackState.positionMs else 0

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onTogglePlay,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("audio_play_button_$itemKey")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause recording" else "Play recording"
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    WaveformSeekBar(
                        peaks = if (isThisActive) playbackState.waveform else emptyList(),
                        progress = positionMs.toFloat() / durationMs.toFloat(),
                        enabled = isThisActive,
                        description = "Recording position ${PhoneNormalizer.formatDuration(positionMs / 1000)} of ${PhoneNormalizer.formatDuration(durationMs / 1000)}",
                        onSeekFraction = { onSeek((it * durationMs).toInt()) },
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = PhoneNormalizer.formatDuration(positionMs / 1000),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = PhoneNormalizer.formatDuration(durationMs / 1000),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))
                TextButton(
                    onClick = onCycleSpeed,
                    enabled = isThisActive,
                    modifier = Modifier.testTag("audio_speed_$itemKey")
                ) {
                    Text(
                        text = (if (playbackState.speed == 1f) "1" else playbackState.speed.toString().removeSuffix(".0")) + "×",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono)
                    )
                }
            }

            if (isThisActive && !playbackState.error.isNullOrBlank()) {
                Text(
                    text = playbackState.error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                )
            }
        }
    }
}


/**
 * Bars drawn from the decoded recording. Before the audio has loaded it shows flat placeholder bars
 * instead of invented peaks. Tap or drag to seek.
 */
@Composable
private fun WaveformSeekBar(
    peaks: List<Float>,
    progress: Float,
    enabled: Boolean,
    description: String,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val played = MaterialTheme.colorScheme.primary
    val unplayed = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val bars = if (peaks.isEmpty()) List(56) { 0.18f } else peaks
    Canvas(
        modifier = modifier
            .semantics { contentDescription = description }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset -> onSeekFraction((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures { change, _ ->
                    onSeekFraction((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        val n = bars.size
        val slot = size.width / n
        val barW = (slot * 0.62f).coerceAtLeast(1.5f)
        bars.forEachIndexed { i, h ->
            val barH = (size.height * h).coerceAtLeast(3f)
            val x = i * slot + (slot - barW) / 2f
            drawRoundRect(
                color = if ((i + 0.5f) / n <= progress) played else unplayed,
                topLeft = Offset(x, (size.height - barH) / 2f),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f)
            )
        }
    }
}
