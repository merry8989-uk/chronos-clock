package com.osintgram.chronos.ui.screens.screentime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.osintgram.chronos.screentime.ScreenTimeLogic
import com.osintgram.chronos.screentime.WeekUsage
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing

// ---- Local sizes -----------------------------------------------------------
// Padding, gaps, radii and type sizes come from Spacing, Radii and ChronosType.
// The two heights below are plot areas, which those scales do not carry: the
// hourly chart is the taller of the two because it carries 24 bars.

private val HourlyPlotHeight = 104.dp
private val WeekPlotHeight = 64.dp

/**
 * Today hour by hour, one bar per hour, the hour we are in picked out.
 *
 * Screen time as a single total says how much, not when — and "when" is what
 * makes the number useful. The bars are drawn with Canvas rather than a chart
 * library so the tab stays dependency-free and matches the rest of the app's
 * surfaces, and so an empty hour can be drawn as a faint stub that reads as
 * "nothing here" instead of as a missing bar.
 *
 * The current hour is marked twice over — a wash behind its column and the word
 * "now" under it — so the mark does not depend on telling two shades apart.
 *
 * The caller passes the 24 buckets from [ScreenTimeLogic.hourlyBuckets]; the
 * scaling to the tallest bar is done in [ScreenTimeLogic.barFractions] so the
 * geometry here only lays out what the maths decided.
 */
@Composable
internal fun HourlyUsageChart(
    hours: List<Long>,
    currentHour: Int,
    modifier: Modifier = Modifier,
) {
    val fractions = ScreenTimeLogic.barFractions(hours)
    val barColour = MaterialTheme.colorScheme.primary
    val quietColour = MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
    val emptyColour = MaterialTheme.colorScheme.surfaceVariant
    val nowWash = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val labelColour = MaterialTheme.colorScheme.onSurfaceVariant
    val slots = maxOf(fractions.size, 1)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(HourlyPlotHeight),
        ) {
            val slotWidth = size.width / slots
            val barWidth = slotWidth * 0.60f
            // The design system's smallest radius, clamped to half the bar so a
            // narrow bar cannot come out as a lozenge.
            val radius = minOf(Radii.sm.toPx(), barWidth / 2f)
            val barRadius = CornerRadius(radius, radius)
            val usableHeight = size.height * 0.90f
            val stub = 2.dp.toPx()

            // A quiet column behind the current hour, so "now" reads at a glance
            // without colouring a bar that may be empty.
            if (currentHour in 0 until slots) {
                drawRoundRect(
                    color = nowWash,
                    topLeft = Offset(currentHour * slotWidth, 0f),
                    size = Size(slotWidth, size.height),
                    cornerRadius = CornerRadius(slotWidth * 0.3f, slotWidth * 0.3f),
                )
            }

            fractions.forEachIndexed { index, fraction ->
                val barHeight = if (fraction <= 0f) {
                    stub
                } else {
                    maxOf(usableHeight * fraction, stub * 2f)
                }
                drawRoundRect(
                    color = when {
                        fraction <= 0f -> emptyColour
                        index == currentHour -> barColour
                        else -> quietColour
                    },
                    topLeft = Offset(
                        index * slotWidth + (slotWidth - barWidth) / 2f,
                        size.height - barHeight,
                    ),
                    size = Size(barWidth, barHeight),
                    cornerRadius = barRadius,
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        // The word under the hour we are in, laid out on the same slots as the
        // bars above it, so it sits under the right column at any width.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val slotWidth = maxWidth / slots
            if (currentHour in 0 until slots) {
                Box(
                    modifier = Modifier
                        .offset(x = slotWidth * currentHour)
                        .width(slotWidth),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "now",
                        style = ChronosType.label,
                        color = barColour,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("12a", "6a", "12p", "6p").forEach { label ->
                Text(
                    text = label,
                    style = ChronosType.label,
                    color = labelColour,
                )
            }
        }
    }
}

/**
 * The last seven days as seven bars, the day letter under each, today marked.
 *
 * One heavy day means something different in a heavy week, and the tab could
 * not show that before. Today is drawn in the accent colour and in bold under
 * the bar so the eye lands on it first; days with no use keep a faint stub so
 * the row stays a row rather than collapsing.
 */
@Composable
internal fun WeekTrendChart(
    week: WeekUsage,
    modifier: Modifier = Modifier,
) {
    val fractions = ScreenTimeLogic.barFractions(week.days.map { it.foregroundMillis })
    val barColour = MaterialTheme.colorScheme.primary
    val quietColour = MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
    val emptyColour = MaterialTheme.colorScheme.surfaceVariant
    val labelColour = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        week.days.forEachIndexed { index, day ->
            val fraction = fractions.getOrElse(index) { 0f }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WeekPlotHeight),
                ) {
                    val stub = 2.dp.toPx()
                    val barWidth = size.width * 0.62f
                    // Same radius as the hourly bars, clamped to half the bar.
                    val radius = minOf(Radii.sm.toPx(), barWidth / 2f)
                    val barHeight = if (fraction <= 0f) {
                        stub
                    } else {
                        maxOf(size.height * 0.94f * fraction, stub * 2f)
                    }
                    drawRoundRect(
                        color = when {
                            fraction <= 0f -> emptyColour
                            day.isToday -> barColour
                            else -> quietColour
                        },
                        topLeft = Offset(
                            (size.width - barWidth) / 2f,
                            size.height - barHeight,
                        ),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = day.letter,
                    style = ChronosType.label.copy(
                        fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (day.isToday) barColour else labelColour,
                )
            }
        }
    }
}
