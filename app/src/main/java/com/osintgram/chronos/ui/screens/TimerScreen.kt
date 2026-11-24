package com.osintgram.chronos.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.timer.TimerEngine
import com.osintgram.chronos.timer.TimerPresets
import com.osintgram.chronos.ui.components.CircleProgress
import com.osintgram.chronos.ui.components.EmptyState
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.components.TickerText
import com.osintgram.chronos.ui.components.WheelPicker
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Motion
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing
import com.osintgram.chronos.ui.theme.heroSurfaceBrush
import com.osintgram.chronos.ui.theme.pressable

@Composable
fun TimerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by TimerEngine.state.collectAsState()
    val presets = TimerPresets.all()
    val step = Settings.timerStepMinutes.value.coerceIn(1, 30)

    // The wheels are a view of the engine's duration — the engine is the single
    // source of truth, which is what was broken before: the wheels changed the
    // display but never the timer that actually ran.
    var hours by remember { mutableIntStateOf((state.durationMs / 3_600_000L).toInt()) }
    var minutes by remember { mutableIntStateOf(((state.durationMs / 60_000L) % 60).toInt()) }
    var seconds by remember { mutableIntStateOf(((state.durationMs / 1_000L) % 60).toInt()) }

    fun syncWheels(ms: Long) {
        hours = (ms / 3_600_000L).toInt().coerceIn(0, 23)
        minutes = ((ms / 60_000L) % 60).toInt().coerceIn(0, 59)
        seconds = ((ms / 1_000L) % 60).toInt().coerceIn(0, 59)
    }

    fun applyWheels() {
        TimerEngine.setDuration(((hours * 3600L) + (minutes * 60L) + seconds) * 1000L)
    }

    var showSavePreset by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }

    val running = state.running
    val displayMs = state.remainingMs
    val progress = if (state.durationMs > 0) {
        state.remainingMs.toFloat() / state.durationMs.toFloat()
    } else {
        0f
    }

    val breathe = rememberInfiniteTransition(label = "breathe")
    val pulse by breathe.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = Motion.easeInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Column(modifier = modifier.fillMaxSize()) {
        // ---- Hero: the countdown is the running value ----------------------
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (state.finished) "Timer finished" else state.label,
                style = ChronosType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md),
            )

            Spacer(Modifier.height(Spacing.md))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.xl))
                    .background(heroSurfaceBrush())
                    .padding(vertical = Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                CircleProgress(
                    progress = if (running || state.finished) progress else 1f,
                    modifier = Modifier
                        .size(250.dp)
                        .graphicsLayer {
                            val scale = if (running) pulse else 1f
                            scaleX = scale
                            scaleY = scale
                        },
                    strokeWidth = Spacing.md,
                    color = MaterialTheme.colorScheme.primary.copy(
                        alpha = if (running || state.finished) 1f else 0.35f,
                    ),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TickerText(
                            text = TimerEngine.format(displayMs),
                            style = ChronosType.display,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = when {
                                state.finished -> "tap reset to clear"
                                running -> "running"
                                else -> "ready"
                            },
                            style = ChronosType.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            if (!running) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WheelPicker(
                        values = (0..23).map { String.format("%02d", it) },
                        selectedIndex = hours,
                        onSelected = {
                            hours = it
                            applyWheels()
                        },
                    )
                    WheelPicker(
                        values = (0..59).map { String.format("%02d", it) },
                        selectedIndex = minutes,
                        onSelected = {
                            minutes = it
                            applyWheels()
                        },
                    )
                    WheelPicker(
                        values = (0..59).map { String.format("%02d", it) },
                        selectedIndex = seconds,
                        onSelected = {
                            seconds = it
                            applyWheels()
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    listOf("hours", "minutes", "seconds").forEach {
                        Text(
                            text = it,
                            style = ChronosType.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 26.dp),
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.md))
            }

            // ---- Presets: one scrolling row of pills -----------------------
            Text(
                text = "Presets",
                style = ChronosType.label,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.sm),
            )

            if (presets.isEmpty()) {
                // A list with nothing in it still has to say what to do next.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                ) {
                    EmptyState(
                        title = "No presets yet",
                        subtitle = "Set a duration with the wheels, then save it here for one-tap reuse.",
                    )
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    contentPadding = PaddingValues(horizontal = Spacing.xs),
                ) {
                    items(presets, key = { "${it.name}-${it.seconds}" }) { preset ->
                        Pill(
                            text = "${preset.name} · " +
                                TimerEngine.format(preset.seconds * 1000, showHours = true),
                            selected = !running && state.durationMs == preset.seconds * 1000,
                            onClick = {
                                TimerEngine.setDuration(preset.seconds * 1000)
                                TimerEngine.setLabel(preset.name)
                                syncWheels(preset.seconds * 1000)
                            },
                        )
                    }
                    item(key = "save-preset") {
                        Pill(
                            text = "Save this timer",
                            selected = false,
                            onClick = {
                                presetName = if (minutes > 0) "${minutes}m timer" else "${seconds}s timer"
                                showSavePreset = true
                            },
                        )
                    }
                }

                TextButton(onClick = { TimerPresets.remove(presets.last()) }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "  Remove last preset",
                        style = ChronosType.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))
        }

        // ---- Controls: one dominant action, sitting under the thumb --------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundControl(
                        icon = Icons.Filled.Refresh,
                        description = "Reset timer",
                        onClick = {
                            TimerEngine.reset(context)
                            syncWheels(TimerEngine.state.value.remainingMs)
                        },
                    )
                    RoundControl(
                        icon = Icons.Filled.Remove,
                        description = "Remove $step minutes",
                        onClick = {
                            TimerEngine.extend(-step * 60_000L)
                            syncWheels(TimerEngine.state.value.remainingMs)
                        },
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(84.dp)
                        .pressable(scale = 0.92f) {
                            if (running) {
                                TimerEngine.pause(context)
                            } else {
                                if (TimerEngine.state.value.remainingMs <= 0L) {
                                    TimerEngine.setDuration(60_000L)
                                    syncWheels(60_000L)
                                }
                                TimerEngine.start(context)
                            }
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
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundControl(
                        icon = Icons.Filled.Add,
                        description = "Add $step minutes",
                        onClick = {
                            TimerEngine.extend(step * 60_000L)
                            syncWheels(TimerEngine.state.value.remainingMs)
                        },
                    )
                    // Keeps the start button centred against the pair on the left.
                    Spacer(Modifier.width(52.dp))
                }
            }

            Text(
                text = "− / + by $step min · change in Settings → Timer",
                style = ChronosType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }

    if (showSavePreset) {
        AlertDialog(
            onDismissRequest = { showSavePreset = false },
            title = { Text("Save preset", style = ChronosType.title) },
            text = {
                Column {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it.take(24) },
                        singleLine = true,
                        shape = RoundedCornerShape(Radii.sm),
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = "Duration: ${TimerEngine.format(state.durationMs)}",
                        style = ChronosType.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val totalSeconds = state.durationMs / 1000
                    if (presetName.isNotBlank() && totalSeconds > 0) {
                        TimerPresets.add(TimerPresets.Preset(presetName.trim(), totalSeconds))
                    }
                    showSavePreset = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSavePreset = false }) { Text("Cancel") }
            },
        )
    }
}

/** A quiet, secondary control: the primary action is the filled circle above. */
@Composable
private fun RoundControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
