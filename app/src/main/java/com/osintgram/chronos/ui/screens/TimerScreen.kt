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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.timer.TimerEngine
import com.osintgram.chronos.timer.TimerPresets
import com.osintgram.chronos.ui.components.ProgressLine
import com.osintgram.chronos.ui.components.WheelPicker

@Composable
fun TimerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by TimerEngine.state.collectAsState()
    val presets = TimerPresets.all()

    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(15) }
    var seconds by remember { mutableIntStateOf(0) }
    var showSavePreset by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }

    val pickedMs = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L
    val running = state.running
    val displayMs = if (running) state.remainingMs else pickedMs
    val progress = if (running && state.durationMs > 0) {
        state.remainingMs.toFloat() / state.durationMs.toFloat()
    } else {
        1f
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = if (state.finished) "Timer finished" else state.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp),
        )

        if (running || state.finished) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = TimerEngine.format(state.remainingMs),
                color = if (state.finished) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground,
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            ProgressLine(progress = progress, modifier = Modifier.padding(horizontal = 32.dp))
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WheelPicker(
                    values = (0..23).map { String.format("%02d", it) },
                    selectedIndex = hours,
                    onSelected = { hours = it },
                )
                WheelPicker(
                    values = (0..59).map { String.format("%02d", it) },
                    selectedIndex = minutes,
                    onSelected = { minutes = it },
                )
                WheelPicker(
                    values = (0..59).map { String.format("%02d", it) },
                    selectedIndex = seconds,
                    onSelected = { seconds = it },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                listOf("hours", "minutes", "seconds").forEach {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 26.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---- Presets -------------------------------------------------------
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.take(3).forEach { preset ->
                    PresetTile(
                        title = preset.name,
                        subtitle = TimerEngine.format(preset.seconds * 1000, showHours = true),
                        selected = !running && pickedMs == preset.seconds * 1000,
                        onClick = {
                            TimerEngine.setDuration(preset.seconds * 1000)
                            hours = (preset.seconds / 3600).toInt()
                            minutes = ((preset.seconds % 3600) / 60).toInt()
                            seconds = (preset.seconds % 60).toInt()
                            TimerEngine.setLabel(preset.name)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.drop(3).forEach { preset ->
                    PresetTile(
                        title = preset.name,
                        subtitle = TimerEngine.format(preset.seconds * 1000, showHours = true),
                        selected = !running && pickedMs == preset.seconds * 1000,
                        onClick = {
                            TimerEngine.setDuration(preset.seconds * 1000)
                            hours = (preset.seconds / 3600).toInt()
                            minutes = ((preset.seconds % 3600) / 60).toInt()
                            seconds = (preset.seconds % 60).toInt()
                            TimerEngine.setLabel(preset.name)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                PresetTile(
                    title = "Save",
                    subtitle = "this timer",
                    selected = false,
                    onClick = {
                        presetName = if (minutes > 0) "${minutes}m timer" else "${seconds}s timer"
                        showSavePreset = true
                    },
                    modifier = Modifier.weight(1f),
                    icon = true,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---- Controls ------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { TimerEngine.reset(context) },
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Reset timer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(28.dp))
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.size(84.dp).clickable {
                    if (running) {
                        TimerEngine.pause(context)
                    } else {
                        if (pickedMs <= 0L && !state.finished) {
                            TimerEngine.setDuration(60_000)
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
            Spacer(Modifier.size(28.dp))
            IconButton(
                onClick = { TimerEngine.extend(60_000) },
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add a minute",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        if (presets.isNotEmpty()) {
            TextButton(
                onClick = { TimerPresets.remove(presets.last()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("  Remove last preset", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showSavePreset) {
        AlertDialog(
            onDismissRequest = { showSavePreset = false },
            title = { Text("Save preset") },
            text = {
                Column {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it.take(24) },
                        singleLine = true,
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Duration: ${TimerEngine.format(pickedMs)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val totalSeconds = pickedMs / 1000
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

@Composable
private fun PresetTile(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Boolean = false,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (icon) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
