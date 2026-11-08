package com.osintgram.chronos.ui.screens.wellness

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.screens.WellnessLabel
import com.osintgram.chronos.ui.screens.WellnessNote
import com.osintgram.chronos.ui.theme.ClockTextStyle
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.wellness.WellnessPlans
import com.osintgram.chronos.wellness.WellnessSound
import com.osintgram.chronos.wellness.WellnessStore
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Meditation: a breathing timer that follows a real pattern, with the bundled
 * alarm bell closing the session. The pattern drives both the circle and the
 * on-screen cue, so the screen and the breathing stay in step.
 */
@Composable
fun MeditateSection() {
    val context = LocalContext.current
    var patternId by rememberSaveable { mutableStateOf(WellnessPlans.patterns.first().id) }
    val pattern = WellnessPlans.pattern(patternId)
    var minutes by rememberSaveable { mutableIntStateOf(Settings.meditationMinutes.value.coerceIn(1, 60)) }
    var running by remember { mutableStateOf(false) }
    var remaining by remember { mutableIntStateOf(minutes * 60) }
    var phaseIndex by remember { mutableIntStateOf(0) }
    var phaseRemaining by remember { mutableIntStateOf(pattern.phases.first().seconds) }
    var completedOnce by remember { mutableStateOf(false) }

    // Changing the length or the pattern resets a session that is not running.
    LaunchedEffect(minutes, patternId) {
        if (!running) {
            remaining = minutes * 60
            phaseIndex = 0
            phaseRemaining = pattern.phases.first().seconds
            completedOnce = false
        }
    }

    LaunchedEffect(running, patternId) {
        if (!running) return@LaunchedEffect
        while (running && remaining > 0) {
            delay(1000)
            remaining -= 1
            phaseRemaining -= 1
            if (phaseRemaining <= 0) {
                phaseIndex = (phaseIndex + 1) % pattern.phases.size
                phaseRemaining = pattern.phases[phaseIndex].seconds
            }
        }
        if (remaining <= 0 && running) {
            running = false
            if (!completedOnce) {
                completedOnce = true
                WellnessStore.logMeditation(minutes, patternId)
                WellnessSound.playBell(context)
                Haptics.success(context)
            }
        }
    }

    val phase = pattern.phases[phaseIndex.coerceIn(pattern.phases.indices)]
    val inhaling = phase.label.contains("in", ignoreCase = true) && !phase.label.contains("out", ignoreCase = true)
    val targetScale = if (inhaling) 1f else 0.62f
    val scale by animateFloatAsState(
        targetValue = if (running) targetScale else 0.8f,
        animationSpec = tween(durationMillis = phase.seconds * 1000, easing = com.osintgram.chronos.ui.theme.Motion.easeInOut),
        label = "breath",
    )
    val todayMinutes = WellnessStore.allMeditations()
        .filter { isToday(it.completedAt) }
        .sumOf { it.minutes }
    val recent = WellnessStore.allMeditations().take(5)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 22.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(196.dp)
                                .scale(scale)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    CircleShape,
                                ),
                        )
                        Canvas(modifier = Modifier.size(196.dp)) {
                            drawCircle(
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0f),
                                radius = size.minDimension / 2f,
                                style = Stroke(width = 1f),
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format(
                                    "%02d:%02d",
                                    remaining / 60,
                                    remaining % 60,
                                ),
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                style = ClockTextStyle,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = when {
                                    remaining == 0 -> "Session complete"
                                    running -> phase.label
                                    else -> "${phase.seconds} s · ${pattern.subtitle}"
                                },
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (remaining == 0) {
                                    remaining = minutes * 60
                                    phaseIndex = 0
                                    phaseRemaining = pattern.phases.first().seconds
                                    completedOnce = false
                                }
                                running = !running
                                Haptics.tick(context)
                            },
                            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp),
                        ) {
                            Icon(
                                if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (running) "Pause" else if (remaining == 0) "Again" else "Begin", fontSize = 14.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                Haptics.tick(context)
                                running = false
                                remaining = minutes * 60
                                phaseIndex = 0
                                phaseRemaining = pattern.phases.first().seconds
                                completedOnce = false
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reset", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        item { WellnessLabel("PATTERN") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(WellnessPlans.patterns, key = { it.id }) { option ->
                    Pill(
                        text = option.title,
                        selected = option.id == patternId,
                        onClick = {
                            if (!running) {
                                Haptics.tick(context)
                                patternId = option.id
                            }
                        },
                    )
                }
            }
        }
        item {
            WellnessNote(
                "${pattern.title}: ${pattern.subtitle}." +
                    if (pattern.caution.isNotBlank()) " ${pattern.caution}" else "",
            )
        }

        item { WellnessLabel("LENGTH") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(3, 5, 10, 15, 20), key = { it }) { option ->
                    Pill(
                        text = "$option min",
                        selected = option == minutes,
                        onClick = {
                            if (!running) {
                                Haptics.tick(context)
                                minutes = option
                                Settings.meditationMinutes.set(option)
                            }
                        },
                    )
                }
            }
        }

        item { WellnessLabel("TODAY") }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = if (todayMinutes == 0) "No minutes yet today"
                        else "${todayMinutes} min in ${WellnessStore.allMeditations().count { isToday(it.completedAt) }} session(s) today",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        if (recent.isNotEmpty()) {
            item { WellnessLabel("RECENT") }
            items(recent, key = { it.id }) { session ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "${session.minutes} min · ${WellnessPlans.pattern(session.patternId).title}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = Instant.ofEpochMilli(session.completedAt)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                                .format(DateTimeFormatter.ofPattern("d MMM")),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            WellnessNote(
                "The bell that closes a session uses the tone chosen in Settings → Wellness. " +
                    "Sit or lie down somewhere you can breathe comfortably; stop if you feel dizzy.",
            )
        }
    }
}

private fun isToday(millis: Long): Boolean =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate() ==
        java.time.LocalDate.now()
