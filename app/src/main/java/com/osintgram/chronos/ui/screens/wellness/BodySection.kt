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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.osintgram.chronos.ui.components.BarChart
import com.osintgram.chronos.ui.components.EmptyState
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.components.StatTile
import com.osintgram.chronos.ui.screens.WellnessLabel
import com.osintgram.chronos.ui.screens.WellnessNote
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing
import com.osintgram.chronos.wellness.DayLog
import com.osintgram.chronos.wellness.WellnessLogic
import com.osintgram.chronos.wellness.WellnessStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The height presets offered for BMI, in centimetres.
 */
private val HEIGHT_PRESETS_CM = listOf(155, 165, 175, 185)

/**
 * The body side of the tab: weight, BMI, oxygen and resting heart rate.
 *
 * Sleep and steps describe what the user did; these four describe the body they
 * did it with, and they are the numbers a scale, a watch or a band produces
 * rather than anything the phone can measure on its own. The section exists so
 * those readings have one place to live next to the movement numbers, and so
 * every one of them is labelled with where it came from.
 *
 * What this build can and cannot show, stated plainly because the difference
 * matters. `DayLog` carries `heartRateBpm` and nothing else on this list: there
 * is no weight, height, oxygen or resting-heart-rate field, and `WellnessStore`
 * has no setter that would write one, so the weight, BMI, oxygen and resting
 * rate cards render an em dash and say why. A BMI computed from a weight nobody
 * entered, or a resting heart rate invented to fill a tile, would be exactly the
 * kind of number this app refuses to print. The live heart rate reading is real
 * and is shown as read.
 *
 * The weight stepper and the height presets are drawn, because this build does
 * have somewhere to put the answer: `WellnessStore.setWeightKg` writes
 * `DayLog.weightKg` and `WellnessStore.setHeightCm` writes the single height the
 * body keeps, so a tap on either is stored rather than silently discarded.
 */
@Composable
fun BodySection() {
    var refresh by remember { mutableIntStateOf(0) }
    val days = remember(refresh) { WellnessStore.recentDays(7) }
    val today = days.lastOrNull() ?: WellnessStore.day()
    val weightKg = weightOf(today)
    val heightCm = WellnessStore.heightCm()
    val weighedDays = days.mapNotNull { day -> weightOf(day)?.let { day.date to it } }

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.xs,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { WellnessLabel("WEIGHT") }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(Radii.lg),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Spacing.lg)) {
                    Text(
                        text = weightKg?.let { formatKg(it) } ?: DASH,
                        style = ChronosType.numeric,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "current weight",
                        style = ChronosType.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    // The weight stepper is the one action this section offers,
                    // so adding is the filled button and taking away is the
                    // outlined one; the height presets below are plain pills.
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedButton(
                            onClick = {
                                val next = ((weightKg ?: 0.0) - 0.5).coerceAtLeast(0.0)
                                if (next > 0.0) WellnessStore.setWeightKg(next)
                                refresh++
                            },
                            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                        ) { Text("-0.5 kg", style = ChronosType.label) }
                        Button(
                            onClick = {
                                val base = weightKg ?: 0.0
                                WellnessStore.setWeightKg(base + 0.5)
                                refresh++
                            },
                            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                        ) { Text("+0.5 kg", style = ChronosType.label) }
                    }
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = "Height, for BMI",
                        style = ChronosType.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        HEIGHT_PRESETS_CM.forEach { cm ->
                            Pill(
                                text = "$cm cm",
                                selected = heightCm == cm.toDouble(),
                                onClick = {
                                    WellnessStore.setHeightCm(cm.toDouble())
                                    refresh++
                                },
                            )
                        }
                    }
                    WellnessNote(
                        "Weight is stored per day and travels in the backup file. Height is one " +
                            "number for the body, so it is not repeated on every day.",
                    )
                }
            }
        }

        // Nothing weighed yet is a real state: say what fills it in rather than
        // letting three em dashes stand there unexplained.
        if (weightKg == null) {
            item {
                EmptyState(
                    title = "No weight logged yet",
                    subtitle = "Use the stepper above to add half a kilo at a time, and pick a " +
                        "height. BMI fills in as soon as both are there.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.md),
                )
            }
        }

        item { WellnessLabel("BMI") }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(Radii.lg),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Spacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val bmi = WellnessLogic.bmi(weightKg ?: 0.0, heightCm)
                        Text(
                            text = bmi?.let {
                                String.format(java.util.Locale.US, "%.1f", it)
                            } ?: DASH,
                            style = ChronosType.numeric,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = bmi?.let { WellnessLogic.bmiLabel(it) } ?: "add weight and height",
                            style = ChronosType.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    WellnessNote(
                        "BMI needs a weight and a height; log both above and it fills in. " +
                            "figure stays an em dash rather than being guessed from an average.",
                    )
                    WellnessNote(
                        "BMI is a rough guide, not a diagnosis. It compares weight with height " +
                            "and cannot tell muscle from fat, where the weight sits, or anything " +
                            "about fitness — read it as a nudge, and take anything that worries " +
                            "you to a clinician.",
                    )
                }
            }
        }

        item { WellnessLabel("OXYGEN AND RESTING RATE") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile(
                    label = "resting heart rate",
                    value = restingRate(today)?.let { "$it bpm" } ?: DASH,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "SpO2",
                    value = spo2(today)?.let { String.format(java.util.Locale.US, "%.0f%%", it) } ?: DASH,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            WellnessNote(
                "Both come from Health Connect, and only when a watch or band writes them — a " +
                    "phone on its own measures neither. Nothing is estimated to fill the gap.",
            )
        }

        item { WellnessLabel("HEART RATE") }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(Radii.lg),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Spacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = "Latest reading",
                            style = ChronosType.body,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (today.heartRateBpm > 0) "${today.heartRateBpm} bpm" else DASH,
                            style = ChronosType.title,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (today.heartRateBpm > 0) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = WellnessLogic.heartRateBand(today.heartRateBpm),
                            style = ChronosType.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    WellnessNote(
                        "Read from the phone's sensor on the Today tab, or from Health Connect " +
                            "when another app wrote it. This is a spot reading, not a resting rate.",
                    )
                }
            }
        }

        // Only drawn once at least two days carry a weight: a trend needs two
        // points, and a single bar would look like a measurement it is not.
        if (weighedDays.size >= 2) {
            item { WellnessLabel("SEVEN-DAY WEIGHT") }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(Radii.lg),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(Spacing.lg)) {
                        BarChart(
                            values = weighedDays.map { it.second.toFloat() },
                            labels = weighedDays.map { dayLetter(it.first) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                        )
                        WellnessNote(
                            "Bars are scaled against the week's own highest weight, so the shape " +
                                "shows change, not the absolute number.",
                        )
                    }
                }
            }
        }

        item {
            WellnessNote(
                "Oxygen, resting rate and weight are clinical-adjacent numbers. Chronos stores " +
                    "what a device reports and does not interpret it.",
            )
        }
    }
}

/**
 * The weight stored for a day, or null when the day has none.
 *
 * `DayLog` has no weight field in this build, so this returns null for every
 * day and the weight card, the BMI card and the seven-day chart stay in their
 * empty state. When `DayLog.weightKg` and `WellnessStore.setWeightKg` land, this
 * becomes `day.weightKg.takeIf { it > 0.0 }` and every card above starts
 * working — the "> 0" test is what makes an unlogged day an em dash instead of
 * a zero.
 */
private fun weightOf(day: DayLog): Double? = day.weightKg.takeIf { it > 0.0 }

/** Resting heart rate for a day, or null when the day has none. */
private fun restingRate(day: DayLog): Int? = day.restingHeartRateBpm.takeIf { it > 0 }

/** Blood oxygen for a day, or null when the day has none. */
private fun spo2(day: DayLog): Double? = day.spo2Percent.takeIf { it > 0.0 }

/** Kilograms with one decimal, e.g. 72.4 → "72.4 kg". */
private fun formatKg(kg: Double): String =
    String.format(java.util.Locale.US, "%.1f kg", kg)

/** The em dash every absent value in this section uses. */
private const val DASH = "\u2014"

/**
 * First letter of the weekday for a stored day key, or an empty string when the
 * key cannot be parsed — the bar chart simply leaves that label out.
 */
private fun dayLetter(dateKey: String): String = runCatching {
    LocalDate.parse(dateKey).format(DateTimeFormatter.ofPattern("EE")).take(1)
}.getOrDefault("")
