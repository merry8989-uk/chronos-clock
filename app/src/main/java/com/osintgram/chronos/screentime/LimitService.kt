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
    private var lastStateLog = 0L

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
        val limits = ScreenTimeStore.allLimits()
        DiagnosticsLogger.log(
            "LimitService",
            "Started, ${limits.size} limit(s); usageAccess=${UsageSource.hasAccess(this)} " +
                "overlay=${android.provider.Settings.canDrawOverlays(this)}",
        )
        if (!UsageSource.hasAccess(this)) {
            DiagnosticsLogger.log(
                "LimitService",
                "Usage access missing: the watcher cannot see the foreground app, so limits " +
                    "will not be enforced",
            )
        }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            DiagnosticsLogger.log(
                "LimitService",
                "Draw-over-other-apps missing: the blocker cannot be shown",
            )
        }
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
        // A heartbeat, so the Limits screen can tell "the watcher is alive" from
        // "the switch is on but nothing is running" — the difference between a
        // limit that works and one that is only a number.
        runCatching {
            getSharedPreferences("chronos_screentime", Context.MODE_PRIVATE)
                .edit()
                .putLong(ScreenTimeStore.KEY_WATCHER_TICK, now)
                .apply()
        }
        if (now - lastRefresh > REFRESH_MS) {
            lastRefresh = now
            val day = UsageSource.today(this)
            // Never move a number backwards: a refresh that reports less than we
            // have already counted (a stale usage bucket, or no usage access at
            // all) used to reset the day to zero every minute, so a 15-minute
            // limit could never be reached.
            day.apps.forEach { app ->
                val known = usedMillis[app.packageName] ?: 0L
                usedMillis[app.packageName] = maxOf(known, app.foregroundMillis)
            }
        }

        val foreground = UsageSource.foregroundPackage(this)
        // Keep the cached number honest between refreshes. The phone's own bucket
        // is only refreshed once a minute, so the time in between is added here —
        // but only while the screen is actually on, or a locked phone would keep
        // counting the app that happened to be in front.
        val screenOn = isScreenOn()
        if (screenOn && foreground != null && foreground != packageName) {
            usedMillis[foreground] = (usedMillis[foreground] ?: 0L) + TICK_MS
        }
        lastPackage = foreground

        if (foreground == null || foreground == packageName) {
            hide()
            return
        }
        // "Off for today" is a deliberate choice, so it wins over the limit.
        if (LimitEnforcement.pausedForToday(this, foreground)) {
            hide()
            return
        }
        // A limit plus today's bonus is the number that actually applies.
        val limit = LimitEnforcement.effectiveLimitMinutes(this, foreground)
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
        if (now - lastStateLog > STATE_LOG_MS) {
            lastStateLog = now
            DiagnosticsLogger.log(
                "LimitService",
                "state fg=$foreground used=${used / 1000}s limit=${limit}m screenOn=$screenOn " +
                    "usageAccess=${UsageSource.hasAccess(this)} " +
                    "overlay=${android.provider.Settings.canDrawOverlays(this)} " +
                    "blocking=${shownFor != null}",
            )
        }
        if (ScreenTimeLogic.limitReached(used, limit)) {
            if (shownFor != foreground) {
                shownFor = foreground
                DiagnosticsLogger.log(
                    "LimitService",
                    "Limit reached for $foreground: used=$used limit=${limit}m",
                )
                // Stop the app's background work too: an app that keeps syncing
                // and notifying has not really been stopped.
                stopBackgroundWork(foreground)
                LimitOverlay.show(
                    context = this,
                    appName = UsageSource.labelFor(this, foreground),
                    usedLabel = ScreenTimeLogic.formatDuration(used),
                    limitLabel = "$limit min",
                    onGoHome = { LimitOverlay.hide(); goHome() },
                    onSnooze = {
                        // Ten more minutes is the smallest escape hatch, and it
                        // is recorded against the day so the next tick knows.
                        LimitEnforcement.addBonus(this, foreground, 10)
                        snoozedUntil[foreground] = System.currentTimeMillis() + SNOOZE_MS
                        hide()
                    },
                    onOffForToday = {
                        LimitEnforcement.pauseForToday(this, foreground)
                        hide()
                    },
                    onRaiseLimit = {
                        // Raising a limit is a decision, so it opens the screen
                        // where limits live rather than guessing a number.
                        hide()
                        runCatching {
                            startActivity(
                                Intent(this, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    .putExtra("open_screen_time_limits", true),
                            )
                        }.onFailure {
                            DiagnosticsLogger.log("LimitService", "Could not open limits: ${it.message}")
                        }
                    },
                )
            }
        } else {
            hide()
        }
    }

    /** True when the screen is on, so a locked phone does not accrue time. */
    private fun isScreenOn(): Boolean = runCatching {
        val power = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        power.isInteractive
    }.getOrDefault(true)

    private fun hide() {
        if (shownFor != null) {
            shownFor = null
            LimitOverlay.hide()
        }
    }

    /**
     * Kills the app's background processes.
     *
     * `killBackgroundProcesses` is the only tool a normal app has for this: it
     * does not stop the app from being launched again, which is why the blocker
     * and the accessibility guard exist, but it does stop the work it left
     * running — the sync, the listener, the notification that would otherwise
     * keep arriving after the limit is up.
     */
    private fun stopBackgroundWork(packageName: String) {
        runCatching {
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            manager.killBackgroundProcesses(packageName)
            DiagnosticsLogger.log("LimitService", "Stopped background work of $packageName")
        }.onFailure {
            DiagnosticsLogger.log(
                "LimitService",
                "Could not stop $packageName: ${it.javaClass.simpleName}: ${it.message}",
            )
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
        private const val SNOOZE_MS = 10 * 60_000L
        private const val STATE_LOG_MS = 60_000L

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
        onOffForToday: () -> Unit,
        onRaiseLimit: () -> Unit,
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
                    text = "10 more minutes"
                    setOnClickListener { onSnooze() }
                },
            )
            layout.addView(
                Button(context).apply {
                    text = "Off for today"
                    setOnClickListener { onOffForToday() }
                },
            )
            layout.addView(
                Button(context).apply {
                    text = "Raise the limit"
                    setOnClickListener { onRaiseLimit() }
                },
            )
            layout.addView(
                TextView(context).apply {
                    text = "The limit comes back at the start of your next day."
                    textSize = 12f
                    setTextColor(Color.parseColor("#9AA3B2"))
                    gravity = Gravity.CENTER
                    setPadding(0, 24, 0, 0)
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
