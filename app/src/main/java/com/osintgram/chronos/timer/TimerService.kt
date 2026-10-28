package com.osintgram.chronos.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import kotlinx.coroutines.launch

/**
 * Keeps a running timer alive in the background: a live countdown notification
 * with pause / add-time / stop / new-timer actions, and the optional floating
 * bubble when the user has switched it on.
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickJob: Job? = null
    private var notifiedFinish = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                TimerEngine.reset(this)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                TimerEngine.pause(this)
                return START_NOT_STICKY
            }
            ACTION_EXTEND -> {
                val step = Settings.timerStepMinutes.value.coerceIn(1, 30)
                TimerEngine.extend(step * 60_000L)
                refreshNotification()
                return START_STICKY
            }
            else -> {
                notifiedFinish = false
                startForeground(NOTIFICATION_ID, buildNotification().build())
                if (Settings.timerBubble.value) TimerBubble.show(this)
                startTicking()
            }
        }
        return START_STICKY
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {
                TimerEngine.tick()
                val state = TimerEngine.state.value
                if (Settings.timerBubble.value) TimerBubble.update(state.remainingMs)
                refreshNotification()
                if (!state.running) {
                    if (state.finished) onTimerFinished()
                    break
                }
                delay(500)
            }
        }
    }

    private fun refreshNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification().build())
    }

    private fun onTimerFinished() {
        if (notifiedFinish) return
        notifiedFinish = true
        val state = TimerEngine.state.value
        DiagnosticsLogger.log("TimerService", "Timer finished: ${state.label}")
        TimerBubble.hide(this)

        if (Settings.timerDoneSound.value) {
            runCatching {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                RingtoneManager.getRingtone(this, uri)?.play()
            }
        }
        if (Settings.timerVibrate.value) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (vibrator.hasVibrator()) {
                val pattern = longArrayOf(0, 500, 300, 500, 300, 500)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            }
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            FINISHED_ID,
            NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Timer finished")
                .setContentText(state.label)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent())
                .build(),
        )
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun buildNotification(): NotificationCompat.Builder {
        val state = TimerEngine.state.value
        val step = Settings.timerStepMinutes.value.coerceIn(1, 30)
        val running = state.running
        val remaining = state.remainingMs

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (running) "Timer running" else "Timer paused")
            .setContentText("${TimerEngine.format(remaining)} · ${state.label}")
            // The system renders the countdown itself, so it ticks every second
            // without us waking up.
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(System.currentTimeMillis() + remaining)
            .setOngoing(running)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .addAction(0, if (running) "Pause" else "Resume", serviceIntent(ACTION_PAUSE, 10))
            .addAction(0, "+$step min", serviceIntent(ACTION_EXTEND, 11))
            .addAction(0, "Stop", serviceIntent(ACTION_STOP, 12))
            .addAction(0, "New timer", openAppIntent())
    }

    private fun serviceIntent(actionName: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, TimerService::class.java).apply { action = actionName },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Timer progress",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "The running countdown in the shade" },
        )
        // The completion alert needs its own loud channel: the progress one is
        // silent by design, so reusing it made finished timers go unnoticed.
        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                "Timer finished",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alert when a countdown reaches zero"
                enableVibration(true)
            },
        )
    }

    override fun onDestroy() {
        tickJob?.cancel()
        scope.cancel()
        TimerBubble.hide(this)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "chronos_timer"
        const val ALERT_CHANNEL_ID = "chronos_timer_alerts"
        const val NOTIFICATION_ID = 4301
        const val FINISHED_ID = 4302
        const val ACTION_START = "com.osintgram.chronos.timer.START"
        const val ACTION_STOP = "com.osintgram.chronos.timer.STOP"
        const val ACTION_PAUSE = "com.osintgram.chronos.timer.PAUSE"
        const val ACTION_EXTEND = "com.osintgram.chronos.timer.EXTEND"
    }
}
