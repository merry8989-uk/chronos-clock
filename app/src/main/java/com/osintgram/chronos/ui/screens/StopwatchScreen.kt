package com.osintgram.chronos.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.osintgram.chronos.stopwatch.StopwatchEngine
import com.osintgram.chronos.ui.components.EmptyState
import com.osintgram.chronos.ui.components.staggerIn
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing
import com.osintgram.chronos.ui.theme.StatusColors
import com.osintgram.chronos.ui.theme.heroSurfaceBrush
import kotlinx.coroutines.delay

@Composable
fun StopwatchScreen(modifier: Modifier = Modifier) {
    var tick by remember { mutableLongStateOf(0L) }
    val laps = StopwatchEngine.allLaps()

    LaunchedEffect(Unit) {
        while (true) {
            StopwatchEngine.refresh()
            tick++
            delay(31)
        }
    }

    val elapsed = StopwatchEngine.state.value.elapsedMs
    val running = StopwatchEngine.state.value.running

    // Fastest and slowest lap, only meaningful once there are two or more.
    val splits = remember(laps, tick) { laps.map { it.splitMs } }
    val fastest = if (laps.size >= 2) splits.minOrNull() else null
    val slowest = if (laps.size >= 2) splits.maxOrNull() else null

    Column(modifier = modifier.fillMaxSize()) {
        // ---- Hero: the elapsed time is the running value -------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                .clip(RoundedCornerShape(Radii.xl))
                .background(heroSurfaceBrush())
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = StopwatchEngine.format(elapsed),
                    style = ChronosType.display,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = when {
                        running -> "running"
                        elapsed > 0L -> "paused"
                        else -> "ready"
                    },
                    style = ChronosType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- Controls: start/pause is filled, lap and reset are quiet ------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton(
                icon = Icons.Filled.Flag,
                description = "Lap",
                enabled = running,
                onClick = { StopwatchEngine.lap() },
            )
            Spacer(Modifier.size(Spacing.xl))
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.size(84.dp).clickable {
                    if (running) StopwatchEngine.pause() else StopwatchEngine.start()
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (running) "Pause" else "Start",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            Spacer(Modifier.size(Spacing.xl))
            ControlButton(
                icon = Icons.Filled.Refresh,
                description = "Reset",
                enabled = elapsed > 0,
                onClick = { StopwatchEngine.reset() },
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        // ---- Laps ----------------------------------------------------------
        if (laps.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm),
            ) {
                Text(
                    text = "LAP",
                    style = ChronosType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(40.dp),
                )
                Text(
                    text = "SPLIT",
                    style = ChronosType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "TOTAL",
                    style = ChronosType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    bottom = Spacing.lg,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                items(laps.reversed()) { lap ->
                    val mark = when (lap.splitMs) {
                        fastest -> "Fastest"
                        slowest -> "Slowest"
                        else -> null
                    }
                    val markColor = when (lap.splitMs) {
                        fastest -> StatusColors.success
                        slowest -> StatusColors.warning
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(Radii.md),
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggerIn(lap.index),
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = Spacing.md,
                                vertical = Spacing.md,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${lap.index}",
                                style = ChronosType.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(40.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = StopwatchEngine.format(lap.splitMs),
                                    style = ChronosType.body,
                                    color = if (mark == null) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        markColor
                                    },
                                )
                                // The word carries the meaning; colour only reinforces it.
                                if (mark != null) {
                                    Text(
                                        text = mark,
                                        style = ChronosType.label,
                                        color = markColor,
                                    )
                                }
                            }
                            Text(
                                text = StopwatchEngine.format(lap.totalMs),
                                style = ChronosType.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        } else {
            // Empty state: what to do next, not just a blank area.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            ) {
                EmptyState(
                    title = "No laps yet",
                    subtitle = "Tap Lap while the stopwatch runs to record a split. " +
                        "The fastest and slowest laps are named once you have two.",
                )
            }
        }
    }
}

/** A quiet, secondary control: the filled circle is the only primary action. */
@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        )
    }
}
