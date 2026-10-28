package com.osintgram.chronos.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.osintgram.chronos.MainActivity
import com.osintgram.chronos.R
import com.osintgram.chronos.data.Alarm
import com.osintgram.chronos.data.AlarmStore
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
 * Owns the ringing state: plays the alarm tone (with optional volume ramp),
 * vibrates, shows the full-screen notification and launches [RingActivity].
 */
class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var rampJob: Job? = null
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    private var alarmId: Long = -1L
    private var snoozeCount: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                val alarm = AlarmStore.byId(alarmId)
                stopRinging()
                alarm?.let { finishAlarm(it) }
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                snooze()
                return START_NOT_STICKY
            }
            else -> {
                alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
                snoozeCount = intent?.getIntExtra(EXTRA_SNOOZE_COUNT, 0) ?: 0
                val alarm = AlarmStore.byId(alarmId)
                if (alarm == null) {
                    DiagnosticsLogger.log("AlarmService", "No alarm $alarmId to ring")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, buildNotification(alarm, snoozeCount))
                startRinging(alarm)
            }
        }
        return START_STICKY
    }

    private fun startRinging(alarm: Alarm) {
        DiagnosticsLogger.log("AlarmService", "Ringing alarm ${alarm.id} '${alarm.label}'")

        val uri: Uri = alarm.soundUri?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                val target = AlarmScheduler.targetVolume()
                val start = if (alarm.volumeRamp) 0.08f else target
                setVolume(start, start)
                start()
            }
            if (alarm.volumeRamp) startVolumeRamp(target = AlarmScheduler.targetVolume())
        }.onFailure {
            DiagnosticsLogger.log("AlarmService", "Could not play alarm sound: ${it.message}")
        }

        if (alarm.vibrate) startVibration()

        if (Settings.fullScreenAlarm.value) {
            runCatching {
                startActivity(
                    Intent(this, RingActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra(RingActivity.EXTRA_ALARM_ID, alarm.id)
                        putExtra(RingActivity.EXTRA_SNOOZE_COUNT, snoozeCount)
                    },
                )
            }.onFailure {
                DiagnosticsLogger.log("AlarmService", "Full-screen launch failed: ${it.message}")
            }
        }
    }

    private fun startVolumeRamp(target: Float) {
        rampJob?.cancel()
        rampJob = scope.launch {
            var volume = 0.08f
            while (volume < target) {
                delay(700)
                volume = (volume + 0.06f).coerceAtMost(target)
                runCatching { player?.setVolume(volume, volume) }
            }
        }
    }

    private fun startVibration() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val pattern = longArrayOf(0, 700, 500, 700, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, 1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(pattern, 1)
        }
    }

    private fun snooze() {
        val alarm = AlarmStore.byId(alarmId)
        stopRinging()
        if (alarm == null) return
        val limit = Settings.snoozeLimit.value
        if (snoozeCount >= limit) {
            DiagnosticsLogger.log("AlarmService", "Snooze limit ($limit) reached — dismissing")
            finishAlarm(alarm)
            return
        }
        AlarmScheduler.snooze(this, alarm, alarm.snoozeMinutes, snoozeCount + 1)
    }

    /** Called when the user dismisses, or when the snooze limit runs out. */
    private fun finishAlarm(alarm: Alarm) {
        if (alarm.repeatDays.isEmpty() && alarm.deleteAfterRing) {
            AlarmStore.delete(alarm.id)
        } else if (alarm.enabled) {
            AlarmScheduler.schedule(this, alarm)
        }
    }

    private fun stopRinging() {
        rampJob?.cancel()
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        runCatching { vibrator?.cancel() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(alarm: Alarm, snoozeCount: Int): Notification {
        val use24 = Settings.use24Hour.value
        val title = if (alarm.label.isBlank()) "Alarm" else alarm.label
        val time = "${alarm.timeText(use24)} ${alarm.meridiem(use24)}".trim()

        val fullScreen = PendingIntent.getActivity(
            this,
            alarm.id.toInt(),
            Intent(this, RingActivity::class.java).apply {
                putExtra(RingActivity.EXTRA_ALARM_ID, alarm.id)
                putExtra(RingActivity.EXTRA_SNOOZE_COUNT, snoozeCount)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val snoozeIntent = PendingIntent.getService(
            this,
            2000 + alarm.id.toInt(),
            Intent(this, AlarmService::class.java).apply { action = ACTION_SNOOZE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismissIntent = PendingIntent.getService(
            this,
            3000 + alarm.id.toInt(),
            Intent(this, AlarmService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("$time — alarm ringing")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setSilent(true)
            .addAction(0, "Snooze ${alarm.snoozeMinutes} min", snoozeIntent)
            .addAction(0, "Dismiss", dismissIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alarm ringing",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Shown while an alarm is ringing"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        rampJob?.cancel()
        scope.cancel()
        runCatching {
            player?.release()
        }
        player = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "chronos_alarm_ringing"
        const val NOTIFICATION_ID = 4201
        const val ACTION_START = "com.osintgram.chronos.alarm.START"
        const val ACTION_STOP = "com.osintgram.chronos.alarm.STOP"
        const val ACTION_SNOOZE = "com.osintgram.chronos.alarm.SNOOZE"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SNOOZE_COUNT = "snooze_count"

        /** Used by the ring screen to silence the alarm and close everything. */
        fun dismiss(context: Context) {
            context.startService(
                Intent(context, AlarmService::class.java).apply { action = ACTION_STOP },
            )
        }

        fun requestSnooze(context: Context) {
            context.startService(
                Intent(context, AlarmService::class.java).apply { action = ACTION_SNOOZE },
            )
        }

        fun ringtoneTitle(context: Context, uriString: String?): String {
            val uri = uriString?.let(Uri::parse)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: return "Default alarm sound"
            val ringtone = RingtoneManager.getRingtone(context, uri)
            return ringtone?.getTitle(context) ?: "Default alarm sound"
        }

        fun systemAlarmRingtones(context: Context): List<Pair<String, String>> {
            val out = mutableListOf<Pair<String, String>>()
            val manager = RingtoneManager(context).apply {
                setType(RingtoneManager.TYPE_ALARM)
            }
            val total = runCatching { manager.cursor.count }.getOrDefault(0)
            for (i in 0 until total) {
                val uri = manager.getRingtoneUri(i) ?: continue
                val title = manager.getRingtone(i)?.getTitle(context) ?: continue
                out.add(title to uri.toString())
            }
            return out
        }

        fun alarmStreamVolume(context: Context): Int {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            return audio.getStreamVolume(AudioManager.STREAM_ALARM)
        }
    }
}
