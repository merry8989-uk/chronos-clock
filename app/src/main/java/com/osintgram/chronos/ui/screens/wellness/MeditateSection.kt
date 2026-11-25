package com.osintgram.chronos.ui.screens.wellness

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.ui.components.EmptyState
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.components.RingStat
import com.osintgram.chronos.ui.screens.WellnessLabel
import com.osintgram.chronos.ui.screens.WellnessNote
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing
import com.osintgram.chronos.ui.theme.heroSurfaceBrush
import com.osintgram.chronos.wellness.WellnessPlans
import com.osintgram.chronos.wellness.WellnessSound
import com.osintgram.chronos.wellness.WellnessStore
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Diameter of the breathing circle and of the ring that tracks its phase. */
private val BREATH_SIZE = 196.dp

/**
 * Meditation: a breathing timer that follows a real pattern, with the bundled
 * alarm bell closing the session. The pattern drives both the circle and the
 * on-screen cue, so the screen and the breathing stay in step.
 *
 * The circle is drawn from theme colours and its ring is [RingStat], so the
 * breath follows the user's accent; the start/pause control is the one filled
 * action, and the patterns are [Pill]s.
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
    // How far through the current phase the breath is, for the ring only.
    val phaseProgress = if (running) {
        ((phase.seconds - phaseRemaining).toFloat() / phase.seconds.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val todayMinutes = WellnessStore.allMeditations()
        .filter { isToday(it.completedAt) }
        .sumOf { it.minutes }
    val recent = WellnessStore.allMeditations().take(5)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.xs,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(MaterialTheme.colorScheme.surface)
                    .background(heroSurfaceBrush()),
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = Spacing.lg,
                        vertical = Spacing.xl,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // The breath itself: a disc in the accent that grows on
                        // the in-breath and settles on the out-breath.
                        Box(
                            modifier = Modifier
                                .size(BREATH_SIZE)
                                .scale(scale)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    CircleShape,
                                ),
                        )
                        // The ring tracks the phase the pattern is in, so the
                        // progress of one breath is visible without adding a
                        // second timer to read. Drawn over the disc so it stays
                        // legible when the breath is at its widest.
                        RingStat(
                            progress = phaseProgress,
                            modifier = Modifier.size(BREATH_SIZE),
                            strokeWidth = Spacing.xs,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            progressColor = MaterialTheme.colorScheme.primary,
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format(
                                    "%02d:%02d",
                                    remaining / 60,
                                    remaining % 60,
                                ),
                                style = ChronosType.numeric,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text = when {
                                    remaining == 0 -> "Session complete"
                                    running -> phase.label
                                    else -> "${phase.seconds} s · ${pattern.subtitle}"
                                },
                                style = ChronosType.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.lg))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        // The one filled action: begin, pause, begin again.
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
                            contentPadding = PaddingValues(
                                horizontal = Spacing.xl,
                                vertical = Spacing.md,
                            ),
                        ) {
                            Icon(
                                if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                if (running) "Pause" else if (remaining == 0) "Again" else "Begin",
                                style = ChronosType.label,
                            )
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
                            contentPadding = PaddingValues(
                                horizontal = Spacing.lg,
                                vertical = Spacing.md,
                            ),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(Spacing.xs))
                            Text("Reset", style = ChronosType.label)
                        }
                    }
                }
            }
        }

        item { WellnessLabel("PATTERN") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
                shape = RoundedCornerShape(Radii.md),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(Spacing.md)) {
                    Text(
                        text = if (todayMinutes == 0) "No minutes yet today"
                        else "${todayMinutes} min in ${WellnessStore.allMeditations().count { isToday(it.completedAt) }} session(s) today",
                        style = ChronosType.body,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        item { WellnessLabel("RECENT") }
        if (recent.isEmpty()) {
            item {
                // Says what a pattern is, because that is the one thing a first
                // visit here has not been told yet.
                EmptyState(
                    title = "No sessions yet",
                    subtitle = "A pattern is a fixed rhythm of breath phases — in, hold, out — " +
                        "that the circle above follows. Pick one and press Begin.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
            }
        } else {
            items(recent, key = { it.id }) { session ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(Radii.md),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${session.minutes} min · ${WellnessPlans.pattern(session.patternId).title}",
                            style = ChronosType.body,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = Instant.ofEpochMilli(session.completedAt)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                                .format(DateTimeFormatter.ofPattern("d MMM")),
                            style = ChronosType.label,
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
