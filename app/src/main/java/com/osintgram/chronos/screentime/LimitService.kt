package com.osintgram.chronos.screentime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.osintgram.chronos.MainActivity
import com.osintgram.chronos.R
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Enforces the daily app limits.
 *
 * It runs as a foreground service while the user has the limiter switched on,
 * asks UsageStatsManager which app is in front every few seconds, and draws a
 * full-screen overlay when that app has passed its limit. The overlay offers
 * "Go home" and "5 more minutes".
 *
 * Limits are only as strict as Android allows: detection is a poll, so an app
 * can be used for a few seconds before the blocker appears, and an OEM that
 * kills background services will stop it. Both are stated on the screen rather
 * than hidden.
 */
class LimitService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null

    /** Today's usage per package, refreshed every minute and topped up per tick. */
    private val usedMillis = mutableMapOf<String, Long>()
    private var lastRefresh = 0L
    private var lastPackage: String? = null

    /** Packages the user allowed five more minutes, with the expiry instant. */
    private val snoozedUntil = mutableMapOf<String, Long>()

    private var shownFor: String? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForegroundCompat()
        pollJob = scope.launch {
            while (isActive) {
                runCatching { tick() }.onFailure {
                    DiagnosticsLogger.log("LimitService", "tick failed: ${it.javaClass.simpleName}: ${it.message}")
                }
                delay(TICK_MS)
            }
        }
        DiagnosticsLogger.log("LimitService", "Started, ${ScreenTimeStore.allLimits().size} limit(s)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        LimitOverlay.hide()
        DiagnosticsLogger.log("LimitService", "Stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---- the loop ----------------------------------------------------------

    private fun tick() {
        val now = System.currentTimeMillis()
        if (now - lastRefresh > REFRESH_MS) {
            lastRefresh = now
            val day = UsageSource.today(this)
            usedMillis.clear()
            day.apps.forEach { usedMillis[it.packageName] = it.foregroundMillis }
        }

        val foreground = UsageSource.foregroundPackage(this)
        // Keep the cached number honest between refreshes.
        if (foreground != null && foreground == lastPackage && foreground != packageName) {
            usedMillis[foreground] = (usedMillis[foreground] ?: 0L) + TICK_MS
        }
        lastPackage = foreground

        if (foreground == null || foreground == packageName) {
            hide()
            return
        }
        val limit = ScreenTimeStore.limitFor(foreground)
        if (limit == null) {
            hide()
            return
        }
        val snooze = snoozedUntil[foreground] ?: 0L
        if (now < snooze) {
            hide()
            return
        }
        val used = usedMillis[foreground] ?: 0L
        if (ScreenTimeLogic.limitReached(used, limit)) {
            if (shownFor != foreground) {
                shownFor = foreground
                DiagnosticsLogger.log(
                    "LimitService",
                    "Limit reached for $foreground: used=$used limit=${limit}m",
                )
                LimitOverlay.show(
                    context = this,
                    appName = UsageSource.labelFor(this, foreground),
                    usedLabel = ScreenTimeLogic.formatDuration(used),
                    limitLabel = "$limit min",
                    onGoHome = { LimitOverlay.hide(); goHome() },
                    onSnooze = {
                        snoozedUntil[foreground] = System.currentTimeMillis() + SNOOZE_MS
                        hide()
                    },
                )
            }
        } else {
            hide()
        }
    }

    private fun hide() {
        if (shownFor != null) {
            shownFor = null
            LimitOverlay.hide()
        }
    }

    private fun goHome() {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    // ---- notification ------------------------------------------------------

    private fun startForegroundCompat() {
        val open = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val count = ScreenTimeStore.allLimits().size
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Screen time limits on")
            .setContentText("Watching $count app(s) today")
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Screen time limits", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Shown while the app-time limiter is running" },
        )
    }

    companion object {
        private const val CHANNEL_ID = "screen_time_limits"
        private const val NOTIFICATION_ID = 4242
        private const val TICK_MS = 4_000L
        private const val REFRESH_MS = 60_000L
        private const val SNOOZE_MS = 5 * 60_000L

        /** Starts or stops the watcher to match the setting. */
        fun sync(context: Context) {
            val intent = Intent(context, LimitService::class.java)
            if (Settings.screenTimeLimiter.value && ScreenTimeStore.allLimits().isNotEmpty()) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }.onFailure {
                    DiagnosticsLogger.log("LimitService", "Could not start: ${it.message}")
                }
            } else {
                runCatching { context.stopService(intent) }
            }
        }
    }
}

/**
 * The full-screen blocker. A plain WindowManager overlay, because a background
 * activity start is restricted on modern Android while the overlay permission
 * is not.
 */
object LimitOverlay {

    private var view: View? = null
    private var manager: WindowManager? = null

    fun show(
        context: Context,
        appName: String,
        usedLabel: String,
        limitLabel: String,
        onGoHome: () -> Unit,
        onSnooze: () -> Unit,
    ) {
        if (!android.provider.Settings.canDrawOverlays(context)) {
            DiagnosticsLogger.log("LimitService", "Overlay permission missing; cannot block")
            return
        }
        hide()
        runCatching {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#F20B0E14"))
                setPadding(64, 64, 64, 64)
                isClickable = true
            }
            layout.addView(
                TextView(context).apply {
                    text = "Daily limit reached"
                    textSize = 26f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                },
            )
            layout.addView(
                TextView(context).apply {
                    text = "$appName · $usedLabel of $limitLabel today"
                    textSize = 15f
                    setTextColor(Color.parseColor("#9AA3B2"))
                    gravity = Gravity.CENTER
                    setPadding(0, 16, 0, 32)
                },
            )
            layout.addView(
                Button(context).apply {
                    text = "Go home"
                    setOnClickListener { onGoHome() }
                },
            )
            layout.addView(
                Button(context).apply {
                    text = "5 more minutes"
                    setOnClickListener { onSnooze() }
                },
            )

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                },
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT,
            )
            windowManager.addView(layout, params)
            manager = windowManager
            view = layout
        }.onFailure {
            DiagnosticsLogger.log("LimitService", "Overlay failed: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    fun hide() {
        val current = view ?: return
        runCatching { manager?.removeView(current) }
        view = null
        manager = null
    }
}
