package com.osintgram.chronos.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.ui.theme.ClockTextStyle
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Chronos logo: a real analog clock face whose hour, minute and second
 * hands are driven by the device clock, so it always shows the actual time.
 */
@Composable
fun AnalogClock(
    modifier: Modifier = Modifier,
    showSecondHand: Boolean = true,
    smoothSweep: Boolean = false,
    rimColor: Color = Color(0xFF4C8DFF),
    tickColor: Color = Color(0xFF9AA3B2),
    hourHandColor: Color = Color(0xFFEDEFF3),
    minuteHandColor: Color = Color(0xFFEDEFF3),
    secondHandColor: Color = Color(0xFF4C8DFF),
    faceColor: Color = Color.Transparent,
) {
    var time by remember { mutableStateOf(LocalTime.now()) }

    LaunchedEffect(smoothSweep, showSecondHand) {
        while (true) {
            time = LocalTime.now()
            delay(if (smoothSweep && showSecondHand) 40L else 500L)
        }
    }

    val hour = time.hour
    val minute = time.minute
    val second = time.second
    val millis = time.nano / 1_000_000

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val centre = Offset(size.width / 2f, size.height / 2f)

            if (faceColor != Color.Transparent) {
                drawCircle(color = faceColor, radius = radius, center = centre)
            }

            // Rim
            drawCircle(
                color = rimColor,
                radius = radius - radius * 0.06f,
                center = centre,
                style = Stroke(width = radius * 0.07f),
            )

            // Minute ticks, with longer ticks every five minutes.
            for (i in 0 until 60) {
                val angle = i * 6f
                val isHourTick = i % 5 == 0
                val inner = radius * if (isHourTick) 0.70f else 0.80f
                val outer = radius * 0.86f
                drawLine(
                    color = tickColor.copy(alpha = if (isHourTick) 0.95f else 0.45f),
                    start = pointOnCircle(centre, inner, angle),
                    end = pointOnCircle(centre, outer, angle),
                    strokeWidth = radius * if (isHourTick) 0.035f else 0.014f,
                    cap = StrokeCap.Round,
                )
            }

            // Hour hand: advances smoothly with the minutes.
            val hourAngle = ((hour % 12) + minute / 60f) * 30f
            drawHand(
                centre = centre,
                angleDeg = hourAngle,
                length = radius * 0.46f,
                width = radius * 0.085f,
                color = hourHandColor,
            )

            // Minute hand.
            val minuteAngle = (minute + second / 60f) * 6f
            drawHand(
                centre = centre,
                angleDeg = minuteAngle,
                length = radius * 0.66f,
                width = radius * 0.055f,
                color = minuteHandColor,
            )

            // Second hand: the one that makes it read as live.
            if (showSecondHand) {
                val secondAngle = if (smoothSweep) {
                    (second + millis / 1000f) * 6f
                } else {
                    second * 6f
                }
                drawHand(
                    centre = centre,
                    angleDeg = secondAngle,
                    length = radius * 0.74f,
                    width = radius * 0.022f,
                    color = secondHandColor,
                )
                // Counterweight on the far side of the second hand.
                drawHand(
                    centre = centre,
                    angleDeg = secondAngle + 180f,
                    length = radius * 0.18f,
                    width = radius * 0.022f,
                    color = secondHandColor,
                )
            }

            // Centre cap
            drawCircle(color = secondHandColor, radius = radius * 0.055f, center = centre)
            drawCircle(color = hourHandColor, radius = radius * 0.022f, center = centre)
        }
    }
}

/** Point at [angleDeg] clockwise from 12 o'clock, at [distance] from [centre]. */
private fun pointOnCircle(centre: Offset, distance: Float, angleDeg: Float): Offset {
    val rad = Math.toRadians((angleDeg - 90f).toDouble())
    return Offset(
        x = centre.x + (cos(rad) * distance).toFloat(),
        y = centre.y + (sin(rad) * distance).toFloat(),
    )
}

private fun DrawScope.drawHand(
    centre: Offset,
    angleDeg: Float,
    length: Float,
    width: Float,
    color: Color,
) {
    drawLine(
        color = color,
        start = centre,
        end = pointOnCircle(centre, length, angleDeg),
        strokeWidth = width,
        cap = StrokeCap.Round,
    )
}

/** Small digital companion used when the user prefers numbers. */
@Composable
fun DigitalClockLabel(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFEDEFF3),
) {
    var time by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = LocalTime.now()
            delay(500L)
        }
    }
    val use24 = Settings.use24Hour.value
    val hour = if (use24) time.hour else {
        val h = time.hour % 12
        if (h == 0) 12 else h
    }
    val text = if (use24) {
        String.format("%02d:%02d", hour, time.minute)
    } else {
        String.format("%d:%02d %s", hour, time.minute, if (time.hour < 12) "am" else "pm")
    }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = ClockTextStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    )
}
