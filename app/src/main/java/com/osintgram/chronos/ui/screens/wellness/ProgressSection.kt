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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.ui.components.BarChart
import com.osintgram.chronos.ui.components.ProgressLine
import com.osintgram.chronos.ui.components.StatTile
import com.osintgram.chronos.ui.screens.WellnessLabel
import com.osintgram.chronos.ui.screens.WellnessNote
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (streak == 0) "No streak yet" else "$streak day streak",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Days in a row with something logged",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item { WellnessLabel("THIS WEEK") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    BarChart(
                        values = days.map { it.steps.toFloat() },
                        labels = days.map { dayLetter(it.date) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        highlightIndex = todayIndex,
                    )
                    Spacer(Modifier.height(10.dp))
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
