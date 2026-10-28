package com.osintgram.chronos.timer

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.osintgram.chronos.MainActivity
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The optional floating timer bubble: a small draggable circle that shows the
 * countdown over other apps. Only ever shown when the user turns it on in
 * Settings and grants the "display over other apps" permission.
 */
object TimerBubble {

    private var bubbleView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null

    /** True when the overlay permission is granted (or not needed). */
    fun canShow(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return Settings.canDrawOverlays(context)
    }

    fun isShowing(): Boolean = bubbleView != null

    fun show(context: Context) {
        if (bubbleView != null) return
        if (!canShow(context)) {
            DiagnosticsLogger.log("TimerBubble", "Overlay permission not granted — not showing")
            return
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        val size = dp(context, 76)
        val text = TextView(context).apply {
            setTextColor(AndroidColor.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(AndroidColor.parseColor("#E64C8DFF"))
                setStroke(dp(context, 2), AndroidColor.parseColor("#CCFFFFFF"))
            }
            text = "0:00"
        }

        val layoutParams = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(context, 16)
            y = dp(context, 140)
        }

        attachGestures(context, text, layoutParams)

        runCatching {
            wm.addView(text, layoutParams)
            bubbleView = text
            params = layoutParams
            DiagnosticsLogger.log("TimerBubble", "Bubble shown")
        }.onFailure {
            DiagnosticsLogger.log("TimerBubble", "Could not add bubble: ${it.message}")
        }
    }

    private fun attachGestures(
        context: Context,
        view: View,
        layoutParams: WindowManager.LayoutParams,
    ) {
        val gestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    openApp(context)
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    hide(context)
                }
            },
        )

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layoutParams.x
                    startY = layoutParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).roundToInt()
                    val dy = (event.rawY - touchY).roundToInt()
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    layoutParams.x = startX + dx
                    layoutParams.y = startY + dy
                    runCatching { windowManager?.updateViewLayout(view, layoutParams) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) gestureDetector.onTouchEvent(event)
                    true
                }
                else -> gestureDetector.onTouchEvent(event)
            }
        }
    }

    /** Called by the timer service on every tick. */
    fun update(remainingMs: Long) {
        val view = bubbleView as? TextView ?: return
        val totalSeconds = (remainingMs + 999) / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        view.text = if (minutes >= 60) {
            String.format("%d:%02d", minutes / 60, minutes % 60)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    fun hide(context: Context) {
        val view = bubbleView ?: return
        runCatching { windowManager?.removeView(view) }
        bubbleView = null
        params = null
        DiagnosticsLogger.log("TimerBubble", "Bubble hidden")
    }

    private fun openApp(context: Context) {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
            )
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()
}
