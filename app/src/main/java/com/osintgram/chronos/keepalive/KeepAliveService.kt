package com.osintgram.chronos.keepalive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.osintgram.chronos.MainActivity
import com.osintgram.chronos.R
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.diagnostics.DeviceMetrics
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import com.osintgram.chronos.wellness.StepCounter
import com.osintgram.chronos.wellness.WellnessStore
import com.osintgram.chronos.wellness.WellnessSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps Chronos alive in the background.
 *
 * Android will happily freeze or kill an app that is not on screen, which is
 * why step counting, the Health Connect sync and the app-time limits used to
 * stop the moment the user left the app. With this on, a low-priority
 * foreground service stays running and every fifteen minutes writes a
 * heartbeat into the log — so the log shows what happened while the app was in
 * the background, not just while it was open.
 *
 * It costs a permanent notification. That is the price of not being killed, and
 * the setting says so.
 */
class KeepAliveService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForegroundCompat()
        // The whole point of running in the background: the step sensor keeps
        // reporting while the app is closed. One shared listener, so the Today
        // card and this service can never double-count.
        com.osintgram.chronos.wellness.StepTracker.start(this)
        job = scope.launch {
            DiagnosticsLogger.log(
                "Alive",
                "service started; first heartbeat in ${HEARTBEAT_MS / 60_000} min",
            )
            while (isActive) {
                delay(HEARTBEAT_MS)
                runCatching { heartbeat() }.onFailure {
                    DiagnosticsLogger.log(
                        "Alive",
                        "heartbeat failed: ${it.javaClass.simpleName}: ${it.message}",
                    )
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: if Android kills the service it restarts it for us.
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        com.osintgram.chronos.wellness.StepTracker.stop()
        DiagnosticsLogger.log("Alive", "service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** One line that says the app was alive and what it knew at that moment. */
    private suspend fun heartbeat() {
        StepCounter.ensureDailyBaseline(this)
        WellnessSync.scheduleIfEnabled(this)
        val steps = WellnessStore.day().steps
        val hc = com.osintgram.chronos.wellness.HealthConnectSource
        val limits = com.osintgram.chronos.screentime.ScreenTimeStore.allLimits().size
        val snapshot = DeviceMetrics.snapshot(this, BuildConfigVersion)
        DiagnosticsLogger.log(
            "Alive",
            "heartbeat up=${SystemClock.elapsedRealtime() / 60_000}m steps=$steps " +
                "sensor=${com.osintgram.chronos.wellness.StepTracker.sensor} " +
                "tracking=${com.osintgram.chronos.wellness.StepTracker.running} " +
                "hc=${hc.status(this)} limits=$limits " +
                "battery=${snapshot.batteryPercent}% net=${snapshot.network} " +
                "mem=${snapshot.memoryAvailableMb}MB",
        )
    }

    private fun startForegroundCompat() {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Chronos is running")
            .setContentText("Counting steps and syncing in the background")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Background service", NotificationManager.IMPORTANCE_MIN)
                .apply { description = "Keeps Chronos counting and syncing while closed" },
        )
    }

    companion object {
        private const val CHANNEL_ID = "chronos_alive"
        private const val NOTIFICATION_ID = 4243
        private const val HEARTBEAT_MS = 15 * 60_000L
        private val BuildConfigVersion = com.osintgram.chronos.BuildConfig.VERSION_NAME

        /** Starts or stops the service to match the setting. */
        fun sync(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            if (Settings.keepAlive.value) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }.onFailure {
                    DiagnosticsLogger.log("Alive", "could not start: ${it.message}")
                }
            } else {
                runCatching { context.stopService(intent) }
            }
        }
    }
}
