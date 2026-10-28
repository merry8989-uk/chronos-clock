package com.osintgram.chronos.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the analog widget face ourselves instead of using the platform's
 * AnalogClock, so it matches the app icon: brand rim, minute ticks, and hour
 * and minute hands in the chosen colours.
 */
object ClockFaceRenderer {

    fun face(
        context: Context,
        sizePx: Int,
        background: Int,
        rimColor: Int,
        tickColor: Int,
        hourHandColor: Int,
        minuteHandColor: Int,
        secondHandColor: Int,
        showSecondHand: Boolean,
    ): Bitmap {
        val size = sizePx.coerceAtLeast(64)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centre = size / 2f
        val radius = centre - size * 0.02f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Rounded background
        if (Color.alpha(background) > 0) {
            paint.style = Paint.Style.FILL
            paint.color = background
            val corner = size * 0.22f
            canvas.drawRoundRect(
                RectF(0f, 0f, size.toFloat(), size.toFloat()),
                corner,
                corner,
                paint,
            )
        }

        // Rim
        val rimWidth = radius * 0.055f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = rimWidth
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = rimColor
        canvas.drawCircle(centre, centre, radius - rimWidth, paint)

        // Minute ticks, longer every five minutes
        for (i in 0 until 60) {
            val angle = i * 6.0
            val isHour = i % 5 == 0
            paint.strokeWidth = if (isHour) radius * 0.028f else radius * 0.011f
            paint.color = if (isHour) tickColor else withAlpha(tickColor, 0.45f)
            val inner = radius * if (isHour) 0.70f else 0.80f
            val outer = radius * 0.87f
            val from = point(centre, inner, angle)
            val to = point(centre, outer, angle)
            canvas.drawLine(from.first, from.second, to.first, to.second, paint)
        }

        val time = LocalTime.now()
        val hourAngle = ((time.hour % 12) + time.minute / 60f) * 30f
        val minuteAngle = (time.minute + time.second / 60f) * 6f

        paint.strokeCap = Paint.Cap.ROUND
        paint.color = hourHandColor
        paint.strokeWidth = radius * 0.085f
        canvas.drawLine(centre, centre, pointX(centre, radius * 0.46f, hourAngle),
            pointY(centre, radius * 0.46f, hourAngle), paint)

        paint.color = minuteHandColor
        paint.strokeWidth = radius * 0.055f
        canvas.drawLine(centre, centre, pointX(centre, radius * 0.66f, minuteAngle),
            pointY(centre, radius * 0.66f, minuteAngle), paint)

        if (showSecondHand) {
            val secondAngle = time.second * 6f
            paint.color = secondHandColor
            paint.strokeWidth = radius * 0.022f
            canvas.drawLine(centre, centre, pointX(centre, radius * 0.74f, secondAngle),
                pointY(centre, radius * 0.74f, secondAngle), paint)
        }

        paint.style = Paint.Style.FILL
        paint.color = secondHandColor
        canvas.drawCircle(centre, centre, radius * 0.045f, paint)
        paint.color = hourHandColor
        canvas.drawCircle(centre, centre, radius * 0.018f, paint)

        return bitmap
    }

    private fun point(centre: Float, distance: Float, angleDeg: Double): Pair<Float, Float> =
        pointX(centre, distance, angleDeg) to pointY(centre, distance, angleDeg)

    private fun pointX(centre: Float, distance: Float, angleDeg: Double): Float {
        val rad = Math.toRadians(angleDeg - 90.0)
        return centre + (cos(rad) * distance).toFloat()
    }

    private fun pointY(centre: Float, distance: Float, angleDeg: Double): Float {
        val rad = Math.toRadians(angleDeg - 90.0)
        return centre + (sin(rad) * distance).toFloat()
    }

    private fun withAlpha(color: Int, factor: Float): Int =
        Color.argb(
            (Color.alpha(color) * factor).toInt().coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )
}
