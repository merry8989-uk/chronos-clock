package com.osintgram.chronos.ui.screens.wellness

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
import com.osintgram.chronos.ui.components.ProgressLine
import com.osintgram.chronos.ui.screens.MetricRow
import com.osintgram.chronos.ui.screens.WellnessLabel
import com.osintgram.chronos.ui.screens.WellnessNote
import com.osintgram.chronos.wellness.WellnessLogic
import com.osintgram.chronos.wellness.WellnessStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Progress: the last seven days, counted from what was actually logged. Days
 * with nothing recorded show as empty bars rather than being filled in.
 */
@Composable
fun ProgressSection() {
    val totals = WellnessStore.weekTotals(7)
    val days = WellnessStore.recentDays(7)
    val streak = WellnessStore.streak()
    val maxSteps = days.maxOfOrNull { it.steps }?.coerceAtLeast(1) ?: 1
    val goal = com.osintgram.chronos.data.Settings.stepGoal.value

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
            MetricRow(
                leftValue = WellnessLogic.formatSteps(totals.steps),
                leftLabel = "steps",
                rightValue = "${totals.workoutCount}",
                rightLabel = "workouts",
            )
        }
        item {
            MetricRow(
                leftValue = WellnessLogic.formatMinutes(totals.meditationMinutes),
                leftLabel = "meditation",
                rightValue = WellnessLogic.formatMinutes(totals.workoutMinutes),
                rightLabel = "workout time",
            )
        }
        item {
            MetricRow(
                leftValue = WellnessLogic.formatLitres(totals.waterMl),
                leftLabel = "water",
                rightValue = if (totals.sleepMinutesAverage == 0) "—"
                else WellnessLogic.formatMinutes(totals.sleepMinutesAverage),
                rightLabel = "sleep / night",
            )
        }
        if (totals.volumeKg > 0) {
            item {
                MetricRow(
                    leftValue = "${totals.volumeKg.toInt()} kg",
                    rightValue = "${totals.daysWithSteps}/7",
                    leftLabel = "volume lifted",
                    rightLabel = "days with steps",
                )
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        days.forEach { day ->
                            val fraction = (day.steps.toFloat() / maxSteps).coerceIn(0f, 1f)
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height((fraction * 96f).dp.coerceAtLeast(2.dp))
                                        .background(
                                            if (day.steps >= goal) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                            RoundedCornerShape(6.dp),
                                        ),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        days.forEach { day ->
                            val label = runCatching {
                                LocalDate.parse(day.date).format(DateTimeFormatter.ofPattern("EE"))
                            }.getOrDefault("")
                            Text(
                                text = label.take(1),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ProgressLine(WellnessLogic.progress(totals.steps, goal * 7))
                    WellnessNote(
                        "Filled bars reached the $goal step goal; lighter bars did not. " +
                            "Total ${WellnessLogic.formatSteps(totals.steps)} steps this week.",
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
