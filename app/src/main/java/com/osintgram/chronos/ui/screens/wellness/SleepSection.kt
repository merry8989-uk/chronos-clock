package com.osintgram.chronos.ui.screens.wellness

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.ui.components.BarChart
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.components.RingStat
import com.osintgram.chronos.ui.components.StatTile
import com.osintgram.chronos.ui.screens.WellnessLabel
import com.osintgram.chronos.ui.screens.WellnessNote
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.wellness.WellnessLogic
import com.osintgram.chronos.wellness.WellnessStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The night, read honestly.
 *
 * Sleep is the one wellness number people misremember most, so this section
 * exists to show what was actually logged and nothing beyond it: last night as
 * hours and minutes, the same night as a ring against the user's own sleep goal,
 * the two stage totals the store really holds, a seven-night shape and the
 * average and best night of the week.
 *
 * Two things it deliberately does not do. It does not print a sleep score,
 * because the store records a single sleep duration per day and a score built
 * from that one number would be a made-up figure wearing a percentage sign. And
 * it does not draw deep, light and REM bars: the Health Connect read in
 * `HealthConnectSource` asks for `SLEEP_DURATION_TOTAL` only, so those stages
 * are not in the data this app receives — a watch that writes stages would have
 * to be read for them first. Anything the store has no value for renders as an
 * em dash rather than a zero.
 */
@Composable
fun SleepSection() {
    val context = LocalContext.current
    val days = WellnessStore.recentDays(7)
    // recentDays is oldest-first and fills missing days with zeros, so the last
    // entry is today even when today has not been logged yet.
    val today = days.lastOrNull() ?: WellnessStore.day()
    val goal = Settings.sleepGoalMinutes.value
    val todayKey = WellnessLogic.todayKey()
    // Match on the date rather than the position, so the highlighted bar stays
    // right if the window ever changes.
    val todayIndex = days.indexOfFirst { it.date == todayKey }.takeIf { it >= 0 }

    val loggedNights = days.filter { it.sleepMinutes > 0 }
    val average = if (loggedNights.isEmpty()) 0 else loggedNights.sumOf { it.sleepMinutes } / loggedNights.size
    val best = loggedNights.maxByOrNull { it.sleepMinutes }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { WellnessLabel("LAST NIGHT") }
        item {
            SleepHero(
                sleepMinutes = today.sleepMinutes,
                goalMinutes = goal,
            )
        }

        item { WellnessLabel("STAGES") }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            label = "asleep",
                            value = if (today.sleepMinutes > 0) {
                                WellnessLogic.formatMinutes(today.sleepMinutes)
                            } else {
                                DASH
                            },
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "awake",
                            // The store keeps one sleep duration per day; how
                            // much of the night was spent awake is not part of
                            // it, so this stays an em dash instead of a zero
                            // that would read as a perfect night.
                            value = if (today.awakeMin > 0) {
                                WellnessLogic.formatMinutes(today.awakeMin)
                            } else {
                                DASH
                            },
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "score",
                            value = WellnessLogic.sleepScore(
                                deepMin = today.deepMin,
                                lightMin = today.lightMin,
                                remMin = today.remMin,
                                awakeMin = today.awakeMin,
                            )?.let { "$it" } ?: DASH,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    WellnessNote(
                        "Stage detail needs a watch that writes stages. The Health Connect read " +
                            "behind this app returns total sleep time only, so the night is shown " +
                            "as asleep and awake, not as deep, light and REM.",
                    )
                }
            }
        }

        item { WellnessLabel("SEVEN NIGHTS") }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    BarChart(
                        values = days.map { it.sleepMinutes.toFloat() },
                        labels = days.map { dayLetter(it.date) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        highlightIndex = todayIndex,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            label = "average",
                            value = if (loggedNights.isEmpty()) DASH
                            else WellnessLogic.formatMinutes(average),
                            caption = if (loggedNights.isEmpty()) "no nights logged"
                            else "${loggedNights.size} of ${days.size} nights",
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "best night",
                            value = best?.let { WellnessLogic.formatMinutes(it.sleepMinutes) } ?: DASH,
                            caption = best?.let { dayLetter(it.date) } ?: "no nights logged",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item { WellnessLabel("LOG SLEEP") }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Last night was",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (today.sleepMinutes > 0) {
                            WellnessLogic.formatMinutes(today.sleepMinutes)
                        } else {
                            "not logged yet"
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SLEEP_PRESETS_HOURS, key = { it }) { hours ->
                            val minutes = hours * 60
                            Pill(
                                text = "$hours h",
                                selected = today.sleepMinutes == minutes,
                                onClick = {
                                    Haptics.tick(context)
                                    WellnessStore.setSleepMinutes(minutes)
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                Haptics.tick(context)
                                WellnessStore.setSleepMinutes((today.sleepMinutes - 15).coerceAtLeast(0))
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) { Text("\u221215 min", fontSize = 12.sp) }
                        OutlinedButton(
                            onClick = {
                                Haptics.tick(context)
                                WellnessStore.setSleepMinutes(today.sleepMinutes + 15)
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) { Text("+15 min", fontSize = 12.sp) }
                    }
                    WellnessNote(
                        "A preset writes the whole night, so it replaces what was there. " +
                            "The buttons step from the current value instead.",
                    )
                }
            }
        }

        item {
            WellnessNote(
                "The score weighs how long you slept and how much of it was restorative. " +
                    "Health Connect on this version carries asleep and awake only, so a night " +
                    "with no stage detail is scored on duration alone rather than being marked " +
                    "down for data the phone never had. It is a guide, not a medical measure.",
            )
        }
    }
}

/**
 * Last night as a ring against the user's sleep goal, with the goal relation
 * spelled out under it.
 *
 * The ring is [RingStat] filled by the same `WellnessLogic.progress` the rest of
 * the tab uses, so a night at or over the goal closes the ring and a night with
 * nothing logged leaves only the track. The caption says how far the night was
 * from the goal instead of printing a score, because the store holds no score to
 * print.
 */
@Composable
private fun SleepHero(sleepMinutes: Int, goalMinutes: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(112.dp),
                contentAlignment = Alignment.Center,
            ) {
                RingStat(
                    progress = WellnessLogic.progress(sleepMinutes, goalMinutes),
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 11.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (sleepMinutes > 0) WellnessLogic.formatMinutes(sleepMinutes) else DASH,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "of ${WellnessLogic.formatMinutes(goalMinutes)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Bedtime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Last night",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = goalBandText(sleepMinutes, goalMinutes),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = if (sleepMinutes >= goalMinutes && goalMinutes > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Ring fills against your sleep goal.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/**
 * How the night sat against the goal, in words: on it, over it, or short by so
 * much. Says so plainly when nothing was logged, so the card never claims a
 * night that was not recorded.
 */
private fun goalBandText(sleepMinutes: Int, goalMinutes: Int): String {
    if (sleepMinutes <= 0) return "No night logged yet."
    if (goalMinutes <= 0) return WellnessLogic.formatMinutes(sleepMinutes)
    val delta = sleepMinutes - goalMinutes
    return when {
        delta == 0 -> "Exactly on your goal."
        delta > 0 -> "Goal met, ${WellnessLogic.formatMinutes(delta)} over."
        else -> "${WellnessLogic.formatMinutes(-delta)} short of goal."
    }
}

/** The em dash every absent value in this section uses. */
private const val DASH = "\u2014"

/** The manual-entry presets, in hours. */
private val SLEEP_PRESETS_HOURS = listOf(6, 7, 8, 9)

/**
 * First letter of the weekday for a stored day key, or an empty string when the
 * key cannot be parsed — the bar chart simply leaves that label out.
 */
private fun dayLetter(dateKey: String): String = runCatching {
    LocalDate.parse(dateKey).format(DateTimeFormatter.ofPattern("EE")).take(1)
}.getOrDefault("")
