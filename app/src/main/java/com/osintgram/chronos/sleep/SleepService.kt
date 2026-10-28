package com.osintgram.chronos.sleep

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.osintgram.chronos.MainActivity
import com.osintgram.chronos.R
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.data.SleepSession
import com.osintgram.chronos.data.SleepStore
import com.osintgram.chronos.data.SnoreEvent
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Live state of the night being recorded, for the Sleep screen. */
object SleepTracker {

    data class State(
        val recording: Boolean = false,
        val startedAtMillis: Long = 0L,
        val elapsedMs: Long = 0L,
        val snoreCount: Int = 0,
        val level: Int = 0,
        val screenOffMs: Long = 0L,
        val wakeUps: Int = 0,
        val screenIsOff: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun update(transform: (State) -> State) {
        _state.value = transform(_state.value)
    }

    fun reset() {
        _state.value = State()
    }

    fun start(context: Context) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, SleepService::class.java).apply { action = SleepService.ACTION_START },
        )
    }

    fun stop(context: Context) {
        context.startService(
            Intent(context, SleepService::class.java).apply { action = SleepService.ACTION_STOP },
        )
    }

    fun canRecord(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}

/**
 * Records a night: microphone level for snore detection and accelerometer
 * movement. Writes a WAV clip for every snore it finds so the sound can be
 * played back in the morning.
 */
class SleepService : Service(), SensorEventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioJob: Job? = null
    private var record: AudioRecord? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var sensorManager: SensorManager? = null

    @Volatile private var running = false

    private var sessionId: Long = 0L
    private var startedAt: Long = 0L

    private val snoreEvents = mutableListOf<SnoreEvent>()
    private val noiseSamples = mutableListOf<Int>()
    private val movementSamples = mutableListOf<Int>()

    private var intervalMovement = 0.0
    private var lastMagnitude = 0f
    private var clipCounter = 0

    // Screen activity: the honest, measurable half of "sleep activity".
    private var screenReceiver: BroadcastReceiver? = null
    private var lastScreenOffAt = 0L
    private var lastScreenOnAt = 0L
    private var screenOffTotal = 0L
    private var screenOnTotal = 0L
    private val screenOffEvents = mutableListOf<Long>()
    private val screenOnEvents = mutableListOf<Long>()
    private var unlockCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRecording()
                return START_NOT_STICKY
            }
            else -> startRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (running) return
        if (!SleepTracker.canRecord(this)) {
            DiagnosticsLogger.log("SleepService", "Microphone permission missing — not recording")
            SleepTracker.reset()
            stopSelf()
            return
        }

        sessionId = System.currentTimeMillis()
        startedAt = System.currentTimeMillis()
        snoreEvents.clear()
        noiseSamples.clear()
        movementSamples.clear()
        clipCounter = 0
        intervalMovement = 0.0
        screenOffEvents.clear()
        screenOnEvents.clear()
        screenOffTotal = 0L
        screenOnTotal = 0L
        unlockCount = 0
        lastScreenOffAt = 0L
        lastScreenOnAt = 0L
        running = true

        startForeground(NOTIFICATION_ID, buildNotification(0, 0))
        SleepTracker.update {
            it.copy(recording = true, startedAtMillis = startedAt, elapsedMs = 0L, snoreCount = 0)
        }

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "chronos:sleep")
            .apply { acquire(12 * 60 * 60 * 1000L) }

        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { accelerometer ->
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }

        registerScreenTracking()

        audioJob = scope.launch { recordLoop() }
        DiagnosticsLogger.log("SleepService", "Sleep session $sessionId started")
    }

    private suspend fun recordLoop() = withContext(Dispatchers.IO) {
        val sampleRate = 16_000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = max(minBuf * 2, 8192)

        val audio = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        } catch (e: Exception) {
            DiagnosticsLogger.log("SleepService", "AudioRecord failed: ${e.message}")
            null
        }

        if (audio == null || audio.state != AudioRecord.STATE_INITIALIZED) {
            DiagnosticsLogger.log("SleepService", "AudioRecord not initialised")
            runCatching { audio?.release() }
            return@withContext
        }

        record = audio
        val chunk = ShortArray(sampleRate / 10)           // 100 ms
        val rolling = ShortArray(sampleRate * 10)         // last 10 s, for clips
        var rollingPos = 0
        var rollingFilled = 0

        val sensitivityFactor = thresholdFor(Settings.sleepSensitivity.value)
        var baseline = 400.0
        var loudMs = 0
        var quietMs = 0
        var cooldownMs = 0
        var intervalMs = 0L
        var intervalPeak = 0

        try {
            audio.startRecording()
        } catch (e: SecurityException) {
            DiagnosticsLogger.log("SleepService", "startRecording denied: ${e.message}")
            runCatching { audio.release() }
            return@withContext
        }

        while (isActive && running) {
            val read = audio.read(chunk, 0, chunk.size)
            if (read <= 0) {
                delay(80)
                continue
            }

            var sumSquares = 0.0
            for (i in 0 until read) {
                val v = chunk[i].toDouble()
                sumSquares += v * v
            }
            val rms = sqrt(sumSquares / read)
            val level = (rms / 300.0).toInt().coerceIn(0, 100)

            // rolling buffer
            for (i in 0 until read) {
                rolling[rollingPos] = chunk[i]
                rollingPos = (rollingPos + 1) % rolling.size
                if (rollingFilled < rolling.size) rollingFilled++
            }

            baseline = baseline * 0.995 + rms * 0.005
            intervalPeak = max(intervalPeak, level)

            val threshold = max(700.0, baseline * sensitivityFactor)
            if (rms > threshold) {
                loudMs += 100
                quietMs = 0
            } else {
                quietMs += 100
                if (quietMs > 300) loudMs = 0
            }

            if (loudMs >= 700 && cooldownMs <= 0) {
                cooldownMs = 8_000
                loudMs = 0
                val at = System.currentTimeMillis()
                val clip = if (Settings.sleepClips.value) {
                    writeClip(rolling, rollingPos, rollingFilled, at)
                } else {
                    null
                }
                snoreEvents.add(SnoreEvent(atMillis = at, peakLevel = level, clipPath = clip))
                SleepTracker.update { it.copy(snoreCount = snoreEvents.size) }
                DiagnosticsLogger.log("SleepService", "Snore detected (level $level)")
            }
            if (cooldownMs > 0) cooldownMs -= 100

            intervalMs += 100
            if (intervalMs >= SleepSession.SAMPLE_INTERVAL_MS) {
                noiseSamples.add(intervalPeak)
                movementSamples.add(intervalMovement.toInt())
                intervalMovement = 0.0
                intervalPeak = 0
                intervalMs = 0
            }

            SleepTracker.update {
                it.copy(
                    elapsedMs = System.currentTimeMillis() - startedAt,
                    level = level,
                )
            }
            if (noiseSamples.size % 4 == 0) {
                updateNotification(snoreEvents.size, level)
            }
        }

        runCatching {
            audio.stop()
            audio.release()
        }
        record = null
    }

    /** Writes the last 10 seconds of audio to a WAV file. */
    private fun writeClip(rolling: ShortArray, pos: Int, filled: Int, atMillis: Long): String? {
        return runCatching {
            val dir = File(filesDir, "sleep/clips").apply { mkdirs() }
            val file = File(dir, "snore_${sessionId}_${clipCounter++}.wav")

            val total = filled
            val pcm = ByteArray(total * 2)
            var idx = 0
            val start = if (filled < rolling.size) 0 else pos
            for (i in 0 until total) {
                val sample = rolling[(start + i) % rolling.size]
                pcm[idx] = (sample.toInt() and 0xFF).toByte()
                pcm[idx + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                idx += 2
            }
            file.outputStream().use { out ->
                out.write(wavHeader(pcm.size, 16_000))
                out.write(pcm)
            }
            file.absolutePath
        }.onFailure {
            DiagnosticsLogger.log("SleepService", "Could not write clip: ${it.message}")
        }.getOrNull()
    }

    private fun wavHeader(dataSize: Int, sampleRate: Int): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * bitsPerSample / 8).toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray())
        header.putInt(dataSize)
        return header.array()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val e = event ?: return
        val x = e.values.getOrElse(0) { 0f }
        val y = e.values.getOrElse(1) { 0f }
        val z = e.values.getOrElse(2) { 0f }
        val magnitude = sqrt(x * x + y * y + z * z)
        intervalMovement += abs(magnitude - lastMagnitude).toDouble()
        lastMagnitude = magnitude
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun stopRecording() {
        if (!running) {
            stopSelf()
            return
        }
        running = false
        audioJob?.cancel()
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        sensorManager?.unregisterListener(this)

        val end = System.currentTimeMillis()
        unregisterScreenTracking(end)
        val session = SleepSession(
            id = sessionId,
            startMillis = startedAt,
            endMillis = end,
            snores = snoreEvents.toList(),
            noise = noiseSamples.toList(),
            movement = movementSamples.toList(),
            screenOffEvents = screenOffEvents.toList(),
            screenOnEvents = screenOnEvents.toList(),
            unlocks = unlockCount,
            screenOffMillis = screenOffTotal,
            screenOnMillis = screenOnTotal,
        )
        if (end - startedAt > 60_000L) {
            SleepStore.save(session)
            DiagnosticsLogger.log(
                "SleepService",
                "Saved session ${session.id}: ${snoreEvents.size} snore(s), " +
                    "${screenOnEvents.size} screen-on event(s) over ${(end - startedAt) / 60000} min",
            )
        } else {
            DiagnosticsLogger.log("SleepService", "Session too short to keep — discarded")
        }

        SleepTracker.reset()
        runCatching { wakeLock?.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Watches the screen turning on and off. Screen-off is the best sleep proxy a
     * phone has without a wearable, and every screen-on is a plausible wake-up or
     * a phone check. Registered at runtime because the platform does not deliver
     * these broadcasts to manifest-declared receivers.
     */
    private fun registerScreenTracking() {
        val now = System.currentTimeMillis()
        val interactive = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        if (interactive) {
            lastScreenOnAt = now
        } else {
            lastScreenOffAt = now
            screenOffEvents.add(now)
        }
        SleepTracker.update { it.copy(screenIsOff = !interactive) }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        val at = System.currentTimeMillis()
                        if (lastScreenOnAt > 0L) {
                            screenOnTotal += at - lastScreenOnAt
                            lastScreenOnAt = 0L
                        }
                        if (lastScreenOffAt == 0L) {
                            lastScreenOffAt = at
                            screenOffEvents.add(at)
                        }
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        val at = System.currentTimeMillis()
                        if (lastScreenOffAt > 0L) {
                            screenOffTotal += at - lastScreenOffAt
                            lastScreenOffAt = 0L
                        }
                        lastScreenOnAt = at
                        screenOnEvents.add(at)
                    }
                    Intent.ACTION_USER_PRESENT -> unlockCount += 1
                }
                SleepTracker.update {
                    it.copy(
                        screenOffMs = currentScreenOffMillis(),
                        wakeUps = screenOnEvents.size,
                        screenIsOff = lastScreenOffAt > 0L,
                    )
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenReceiver = receiver
    }

    private fun unregisterScreenTracking(end: Long) {
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
        if (lastScreenOffAt > 0L) {
            screenOffTotal += (end - lastScreenOffAt).coerceAtLeast(0L)
            lastScreenOffAt = 0L
        }
        if (lastScreenOnAt > 0L) {
            screenOnTotal += (end - lastScreenOnAt).coerceAtLeast(0L)
            lastScreenOnAt = 0L
        }
    }

    private fun currentScreenOffMillis(): Long {
        val runningSpan = if (lastScreenOffAt > 0L) {
            (System.currentTimeMillis() - lastScreenOffAt).coerceAtLeast(0L)
        } else {
            0L
        }
        return screenOffTotal + runningSpan
    }

    private fun buildNotification(snores: Int, level: Int) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Recording your sleep")
        .setContentText("$snores snore(s) detected · level $level")
        .setOngoing(true)
        .setSilent(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openIntent())
        .addAction(0, "Stop", stopIntent())
        .build()

    private fun updateNotification(snores: Int, level: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(snores, level))
    }

    private fun openIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        this,
        1,
        Intent(this, SleepService::class.java).apply { action = ACTION_STOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sleep recording",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Shown while Chronos records snoring and movement" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        running = false
        audioJob?.cancel()
        scope.cancel()
        runCatching { record?.release() }
        runCatching { wakeLock?.release() }
        sensorManager?.unregisterListener(this)
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "chronos_sleep"
        const val NOTIFICATION_ID = 4401
        const val ACTION_START = "com.osintgram.chronos.sleep.START"
        const val ACTION_STOP = "com.osintgram.chronos.sleep.STOP"

        /** Snore sensitivity thresholds, from Settings. */
        fun thresholdFor(sensitivity: String): Double = when (sensitivity) {
            "high" -> 1.7
            "low" -> 3.4
            else -> 2.4
        }
    }
}
