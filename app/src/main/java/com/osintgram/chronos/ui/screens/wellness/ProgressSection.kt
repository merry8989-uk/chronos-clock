package com.osintgram.chronos.ui.screens.wellness

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.osintgram.chronos.ui.components.BarChart
import com.osintgram.chronos.ui.components.EmptyState
import com.osintgram.chronos.ui.components.ProgressLine
import com.osintgram.chronos.ui.components.StatTile
import com.osintgram.chronos.ui.screens.WellnessLabel
import com.osintgram.chronos.ui.screens.WellnessNote
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing
import com.osintgram.chronos.wellness.WellnessLogic
import com.osintgram.chronos.wellness.WellnessStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Progress: the last seven days, counted from what was actually logged. Days
 * with nothing recorded show as empty bars rather than being filled in.
 *
 * The week totals are a two-column grid of [StatTile]s so the numbers can be
 * compared at a glance, and the seven days are one [BarChart] with today
 * picked out, instead of a row of hand-built boxes.
 *
 * Read-only by design: there is no action on this screen. Everything it shows
 * was entered on Today, Sleep or a workout, so it offers no button that would
 * duplicate one of those.
 */
@Composable
fun ProgressSection() {
    val totals = WellnessStore.weekTotals(7)
    val days = WellnessStore.recentDays(7)
    val streak = WellnessStore.streak()
    val goal = com.osintgram.chronos.data.Settings.stepGoal.value
    // recentDays is oldest-first, so today is the last bar — but match on the
    // date rather than the position, so the highlight is right even if the
    // window ever changes.
    val todayKey = WellnessLogic.todayKey()
    val todayIndex = days.indexOfFirst { it.date == todayKey }.takeIf { it >= 0 }

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
        // A week with nothing in it is a real state; the empty state names the
        // screens that fill it rather than leaving seven flat bars unexplained.
        if (days.all { it.isEmpty }) {
            item {
                EmptyState(
                    title = "Nothing to chart yet",
                    subtitle = "A day appears here once steps, water, sleep, a habit or a " +
                        "workout is recorded on Today, Sleep or Activity. Nothing is estimated.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.lg),
                )
            }
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(Radii.lg),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Spacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Column {
                            Text(
                                text = if (streak == 0) "No streak yet" else "$streak day streak",
                                style = ChronosType.title,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Days in a row with something logged",
                                style = ChronosType.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item { WellnessLabel("THIS WEEK") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile(
                    label = "steps",
                    value = WellnessLogic.formatSteps(totals.steps),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "workouts",
                    value = "${totals.workoutCount}",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile(
                    label = "meditation",
                    value = WellnessLogic.formatMinutes(totals.meditationMinutes),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "workout time",
                    value = WellnessLogic.formatMinutes(totals.workoutMinutes),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile(
                    label = "water",
                    value = WellnessLogic.formatLitres(totals.waterMl),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "sleep / night",
                    value = if (totals.sleepMinutesAverage == 0) "—"
                    else WellnessLogic.formatMinutes(totals.sleepMinutesAverage),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (totals.volumeKg > 0) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatTile(
                        label = "volume lifted",
                        value = "${totals.volumeKg.toInt()} kg",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "days with steps",
                        value = "${totals.daysWithSteps}/7",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item { WellnessLabel("STEPS, LAST 7 DAYS") }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(Radii.lg),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Spacing.lg)) {
                    BarChart(
                        values = days.map { it.steps.toFloat() },
                        labels = days.map { dayLetter(it.date) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        highlightIndex = todayIndex,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    ProgressLine(WellnessLogic.progress(totals.steps, goal * 7))
                    WellnessNote(
                        "Bars are scaled to the busiest day this week, and today's bar is the " +
                            "solid one. Goal ${WellnessLogic.formatSteps(goal)} steps a day · " +
                            "total ${WellnessLogic.formatSteps(totals.steps)} steps this week.",
                    )
                }
            }
        }

        item { WellnessLabel("HOW THIS IS COUNTED") }
        item {
            WellnessNote(
                "A week is the last seven calendar days including today. A day counts towards " +
                    "the streak when any step, water, sleep, habit or workout was recorded. " +
                    "Sleep averages only over nights that were entered.",
            )
        }
    }
}

/**
 * First letter of the weekday for a stored day key, or an empty string when the
 * key cannot be parsed — the bar chart simply leaves that label out.
 */
private fun dayLetter(dateKey: String): String = runCatching {
    LocalDate.parse(dateKey).format(DateTimeFormatter.ofPattern("EE")).take(1)
}.getOrDefault("")
