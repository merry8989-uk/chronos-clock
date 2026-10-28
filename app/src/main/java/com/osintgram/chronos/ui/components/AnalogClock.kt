package com.osintgram.chronos.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.ui.theme.ClockTextStyle
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The Chronos logo: a real analog clock face whose hour, minute and second
 * hands are driven by the device clock, so it always shows the actual time.
 *
 * Stroke widths never drop below a visible minimum, and small sizes (the top
 * bar logo) switch to a simplified face, so the hands stay legible and clearly
 * moving even at 30dp.
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
            delay(if (smoothSweep && showSecondHand) 33L else 200L)
        }
    }

    // Reading these here ties this composable to the ticking state.
    val hour = time.hour
    val minute = time.minute
    val second = time.second
    val millis = time.nano / 1_000_000

    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val smallest = if (maxWidth < maxHeight) maxWidth else maxHeight
        val compact = with(density) { smallest.toPx() < 88.dp.toPx() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            if (radius <= 0f) return@Canvas
            val centre = Offset(size.width / 2f, size.height / 2f)

            // Minimum widths in pixels, so nothing shrinks into invisibility.
            val oneDp = 1.dp.toPx()
            val rimWidth = max(radius * 0.07f, oneDp * 1.5f)
            val hourWidth = max(radius * 0.10f, oneDp * 2.4f)
            val minuteWidth = max(radius * 0.062f, oneDp * 1.8f)
            val secondWidth = max(radius * 0.030f, oneDp * 1.3f)
            val hourTickWidth = max(radius * 0.035f, oneDp * 1.2f)
            val minuteTickWidth = max(radius * 0.016f, oneDp * 0.7f)

            if (faceColor != Color.Transparent) {
                drawCircle(color = faceColor, radius = radius, center = centre)
            }

            // Rim
            drawCircle(
                color = rimColor,
                radius = radius - rimWidth / 2f,
                center = centre,
                style = Stroke(width = rimWidth),
            )

            // Ticks are only worth drawing when there is room for them.
            if (!compact) {
                for (i in 0 until 60) {
                    val angle = i * 6f
                    val isHourTick = i % 5 == 0
                    val inner = radius * if (isHourTick) 0.68f else 0.79f
                    val outer = radius * 0.86f
                    drawLine(
                        color = tickColor.copy(alpha = if (isHourTick) 0.95f else 0.42f),
                        start = pointOnCircle(centre, inner, angle),
                        end = pointOnCircle(centre, outer, angle),
                        strokeWidth = if (isHourTick) hourTickWidth else minuteTickWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }

            // Hour hand — advances with the minutes.
            val hourAngle = ((hour % 12) + minute / 60f) * 30f
            drawHand(
                centre = centre,
                angleDeg = hourAngle,
                length = radius * if (compact) 0.50f else 0.46f,
                width = hourWidth,
                color = hourHandColor,
            )

            // Minute hand.
            val minuteAngle = (minute + second / 60f) * 6f
            drawHand(
                centre = centre,
                angleDeg = minuteAngle,
                length = radius * if (compact) 0.74f else 0.66f,
                width = minuteWidth,
                color = minuteHandColor,
            )

            // Second hand — the one that proves it is live.
            if (showSecondHand) {
                val secondAngle = if (smoothSweep) {
                    (second + millis / 1000f) * 6f
                } else {
                    second * 6f
                }
                drawHand(
                    centre = centre,
                    angleDeg = secondAngle,
                    length = radius * if (compact) 0.86f else 0.74f,
                    width = secondWidth,
                    color = secondHandColor,
                )
                drawHand(
                    centre = centre,
                    angleDeg = secondAngle + 180f,
                    length = radius * 0.16f,
                    width = secondWidth,
                    color = secondHandColor,
                )
            }

            // Centre cap
            drawCircle(
                color = secondHandColor,
                radius = max(radius * 0.06f, oneDp * 1.6f),
                center = centre,
            )
            drawCircle(
                color = hourHandColor,
                radius = max(radius * 0.026f, oneDp * 0.8f),
                center = centre,
            )
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

/** Digital alternative for the logo slot. */
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

/**
 * Tap the logo to open this: a large clock face with the same three hands, the
 * digital time ticking beside it, so the movement is impossible to miss.
 */
@Composable
fun LiveClockDialog(onDismiss: () -> Unit) {
    var time by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = LocalTime.now()
            delay(200L)
        }
    }
    val use24 = Settings.use24Hour.value
    val timeText = if (use24) {
        time.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    } else {
        time.format(DateTimeFormatter.ofPattern("h:mm:ss a", Locale.US))
    }
    val dateText = time.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.US))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AnalogClock(
                    modifier = Modifier.size(260.dp),
                    showSecondHand = Settings.clockSecondHand.value,
                    smoothSweep = Settings.clockSmooth.value,
                    rimColor = MaterialTheme.colorScheme.primary,
                    tickColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    hourHandColor = MaterialTheme.colorScheme.onSurface,
                    minuteHandColor = MaterialTheme.colorScheme.onSurface,
                    secondHandColor = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(18.dp))
                TickerText(
                    text = timeText,
                    style = ClockTextStyle.copy(fontSize = 30.sp, fontWeight = FontWeight.Light),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = dateText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Hour, minute and second hands, synced to the device clock.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}
