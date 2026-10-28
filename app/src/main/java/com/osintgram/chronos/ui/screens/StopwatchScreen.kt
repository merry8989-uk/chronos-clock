package com.osintgram.chronos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.stopwatch.StopwatchEngine
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
        Text(
            text = StopwatchEngine.format(elapsed),
            fontSize = 60.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        )

        Spacer(Modifier.height(24.dp))

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
            Spacer(Modifier.size(24.dp))
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
            Spacer(Modifier.size(24.dp))
            ControlButton(
                icon = Icons.Filled.Refresh,
                description = "Reset",
                enabled = elapsed > 0,
                onClick = { StopwatchEngine.reset() },
            )
        }

        Spacer(Modifier.height(20.dp))

        if (laps.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "LAP",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(width = 44.dp, height = 16.dp),
                )
                Text(
                    text = "SPLIT",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "TOTAL",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(laps.reversed()) { lap ->
                    val color = when (lap.splitMs) {
                        fastest -> MaterialTheme.colorScheme.primary
                        slowest -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 9.dp),
                    ) {
                        Text(
                            text = "${lap.index}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(width = 44.dp, height = 20.dp),
                        )
                        Text(
                            text = StopwatchEngine.format(lap.splitMs),
                            fontSize = 14.sp,
                            color = color,
                            fontWeight = if (lap.splitMs == fastest || lap.splitMs == slowest) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = StopwatchEngine.format(lap.totalMs),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Text(
                    text = "Tap the flag to record a lap. The fastest and slowest laps are\nhighlighted once you have two or more.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 24.dp),
                )
            }
        }
    }
}

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
