package com.osintgram.chronos.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The small set of data shapes the wellness screens share. Everything here is
 * drawn on a Canvas from theme colours, so it costs no extra dependency and it
 * follows the user's accent, light/dark mode and text scale without any extra
 * wiring. Each shape is happy at zero and at its maximum: a ring at 0 draws
 * only its track, a ring at 1 draws a closed ring, and a bar chart with no
 * data at all draws a flat baseline instead of seven identical stubs.
 */

/**
 * A ring that shows one value against its goal: the track is the whole circle,
 * the arc is the part that has been reached, and the ends are rounded so it
 * reads as a stroke rather than a pie slice.
 *
 * Used behind the step count on Today. [progress] is a fraction, 0f..1f;
 * anything outside that range is clamped, so an overshot goal simply draws a
 * complete ring and a zero goal draws none of it.
 *
 * @param progress how much of the ring is filled, 0f..1f.
 * @param trackColor the unfilled ring; defaults to the theme's variant surface.
 * @param progressColor the filled arc; defaults to the theme accent.
 * @param strokeWidth thickness of the ring, in dp.
 */
@Composable
fun RingStat(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 10.dp,
) {
    Canvas(modifier = modifier) {
        val stroke = strokeWidth.toPx().coerceAtMost(size.minDimension / 2f)
        if (stroke <= 0f || size.minDimension <= 0f) return@Canvas
        val inset = stroke / 2f
        val arcSize = Size(
            width = (size.width - stroke).coerceAtLeast(0f),
            height = (size.height - stroke).coerceAtLeast(0f),
        )
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke),
        )
        val fraction = progress.coerceIn(0f, 1f)
        if (fraction > 0f) {
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * A week of values as evenly spaced rounded bars, scaled against the tallest
 * value in the list rather than against a fixed maximum, so a quiet week still
 * reads as a shape.
 *
 * Used for steps on Progress. A bar at [highlightIndex] (today) is drawn in the
 * full accent colour and the rest sit back at low alpha. A value of zero draws
 * a two-pixel stub in the track colour so the column is visibly empty rather
 * than missing, and when every value is zero the whole chart collapses to a
 * single flat baseline — that is the "nothing logged yet" state.
 *
 * @param values one value per bar, drawn left to right.
 * @param labels optional short captions under the bars ("M", "T", ...); the
 *   list is matched by position, and missing labels are simply not drawn.
 * @param highlightIndex the bar to emphasise, usually today; null for none.
 */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>? = null,
    modifier: Modifier = Modifier,
    highlightIndex: Int? = null,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    val highlightColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        if (values.isEmpty() || size.width <= 0f || size.height <= 0f) return@Canvas

        val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
        val labelLayouts = labels.orEmpty().map { measurer.measure(text = it, style = labelStyle) }
        val labelHeight = labelLayouts.maxOfOrNull { it.size.height }?.toFloat() ?: 0f
        val labelGap = if (labelLayouts.isEmpty()) 0f else 6.dp.toPx()
        val chartHeight = (size.height - labelHeight - labelGap).coerceAtLeast(0f)

        val slot = size.width / values.size
        val barWidth = minOf(slot * 0.92f, maxOf(slot * 0.52f, 1f))
        val barRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        val stubHeight = 2.dp.toPx().coerceAtMost(chartHeight)
        val minBarHeight = 3.dp.toPx().coerceAtMost(chartHeight)
        val maxValue = values.maxOrNull()?.coerceAtLeast(0f) ?: 0f

        if (maxValue <= 0f) {
            // Nothing recorded at all: one flat line, not a row of stubs.
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, chartHeight - stubHeight),
                size = Size(size.width, stubHeight),
                cornerRadius = CornerRadius(stubHeight / 2f, stubHeight / 2f),
            )
        } else {
            values.forEachIndexed { index, raw ->
                val value = raw.coerceAtLeast(0f)
                val fraction = (value / maxValue).coerceIn(0f, 1f)
                val barHeight = if (value <= 0f) {
                    stubHeight
                } else {
                    (fraction * chartHeight).coerceAtLeast(minBarHeight).coerceAtMost(chartHeight)
                }
                drawRoundRect(
                    color = when {
                        value <= 0f -> trackColor
                        highlightIndex == index -> highlightColor
                        else -> barColor
                    },
                    topLeft = Offset(slot * index + (slot - barWidth) / 2f, chartHeight - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = barRadius,
                )
            }
        }

        labelLayouts.forEachIndexed { index, layout ->
            if (index < values.size) {
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = slot * index + (slot - layout.size.width) / 2f,
                        y = chartHeight + labelGap,
                    ),
                )
            }
        }
    }
}

/**
 * One number in a small card: the value first, its label under it, and an
 * optional caption for a qualifier such as a unit or a period.
 *
 * Used for the week totals on Progress, where a grid of these reads faster than
 * a paragraph of figures. The value and its label are announced as a single
 * node, so a screen reader says "12,400 steps" rather than two fragments.
 *
 * @param label what the number counts, e.g. "steps".
 * @param value the number itself, already formatted for display.
 * @param caption optional quiet second line under the label.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    caption: String? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (caption != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = caption,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
            }
        }
    }
}
