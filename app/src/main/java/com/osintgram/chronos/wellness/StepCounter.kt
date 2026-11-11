package com.osintgram.chronos.wellness

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.resume

/** What the Today card needs to show about today's steps. */
data class StepState(
    val steps: Int = 0,
    val available: Boolean = false,
    /** True when the count had to start when the app was opened, not at midnight. */
    val countedFromNow: Boolean = false,
    /** "counter", "detector" or "none" — shown on the card and written to the log. */
    val sensor: String = "none",
    /** False when ACTIVITY_RECOGNITION has not been granted yet. */
    val permissionGranted: Boolean = false,
)

/**
 * Today's step count from the phone's own sensor.
 *
 * Two sensors can do this job and phones differ in which one they expose:
 *  - **TYPE_STEP_COUNTER** reports steps since boot, so "today" needs a baseline
 *    (stored per day, or zero when the phone rebooted after midnight);
 *  - **TYPE_STEP_DETECTOR** reports one event per step and no absolute number,
 *    so the day's count is what was already stored plus the events seen.
 *
 * Whichever is present, the card fills in immediately on open — a still phone
 * sends no events at all, which is why the count used to sit at zero until the
 * user walked.
 */
object StepCounter {

    const val SENSOR_COUNTER = "counter"
    const val SENSOR_DETECTOR = "detector"
    const val SENSOR_NONE = "none"

    /** Which step sensor this phone actually has, or [SENSOR_NONE]. */
    fun sensorKind(context: Context): String {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return SENSOR_NONE
        return when {
            manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null -> SENSOR_COUNTER
            manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null -> SENSOR_DETECTOR
            else -> SENSOR_NONE
        }
    }

    fun hasSensor(context: Context): Boolean = sensorKind(context) != SENSOR_NONE

    /** ACTIVITY_RECOGNITION is only needed from Android 10 onwards. */
    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isAvailable(context: Context): Boolean = hasSensor(context) && hasPermission(context)

    /** True when the phone booted after local midnight, so the counter started today. */
    fun bootedSinceMidnight(): Boolean {
        val zone = ZoneId.systemDefault()
        val midnight = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val sinceMidnight = System.currentTimeMillis() - midnight
        return SystemClock.elapsedRealtime() < sinceMidnight
    }

    /**
     * One cumulative reading, for code that cannot keep a listener registered
     * (the background worker, the card opening). Null when the sensor says
     * nothing in [timeoutMs], or when this phone only has the detector — that
     * one has no absolute number to read.
     */
    suspend fun readCumulativeOnce(context: Context, timeoutMs: Long = 5_000): Long? =
        withContext(Dispatchers.Default) {
            if (!hasPermission(context)) return@withContext null
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                ?: return@withContext null
            val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
                ?: return@withContext null

            // Suspend until the sensor answers, instead of polling it every
            // 50 ms: one registration, one resume, no wake-ups in between.
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    val listener = object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent) {
                            val value = event.values.firstOrNull()?.toLong() ?: return
                            if (continuation.isActive) {
                                runCatching { manager.unregisterListener(this) }
                                continuation.resume(value)
                            }
                        }

                        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                    }
                    manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                    continuation.invokeOnCancellation {
                        runCatching { manager.unregisterListener(listener) }
                    }
                }
            }
        }

    /**
     * Makes sure today has a baseline, so tomorrow does not have to start
     * counting from the moment the app is opened. Called by the worker.
     */
    suspend fun ensureDailyBaseline(context: Context) {
        if (sensorKind(context) != SENSOR_COUNTER) return
        val today = WellnessLogic.todayKey()
        if (WellnessStore.stepBaseline(today) != null) return
        val cumulative = readCumulativeOnce(context) ?: return
        val bootedToday = bootedSinceMidnight()
        val baseline = WellnessLogic.resolveStepBaseline(cumulative, null, bootedToday)
        WellnessStore.setStepBaseline(baseline, today)
        DiagnosticsLogger.log(
            "StepCounter",
            "baseline for $today = $baseline (cumulative=$cumulative, bootedToday=$bootedToday)",
        )
    }

    /**
     * Registers the sensor while the composable is on screen and writes today's
     * total into the store.
     */
    @Composable
    fun rememberTodaySteps(enabled: Boolean = true): StepState {
        val context = LocalContext.current
        val kind = remember(context, enabled) { if (enabled) sensorKind(context) else SENSOR_NONE }
        val permission = remember(context, enabled) { hasPermission(context) }
        val usable = kind != SENSOR_NONE && permission

        LaunchedEffect(usable, kind) {
            DiagnosticsLogger.log(
                "StepCounter",
                "card opened: sensor=$kind permission=$permission usable=$usable " +
                    "storedToday=${WellnessStore.day().steps} trackerRunning=${StepTracker.running}",
            )
            if (!usable) return@LaunchedEffect
            // Fill the number in before the first step arrives — a still phone
            // sends no events at all. The tracker keeps it live after that.
            if (kind == SENSOR_COUNTER && !StepTracker.running) {
                val cumulative = readCumulativeOnce(context, timeoutMs = 5_000)
                if (cumulative == null) {
                    DiagnosticsLogger.log("StepCounter", "counter gave no reading within 5 s")
                } else {
                    val today = WellnessLogic.todayKey()
                    val bootedToday = bootedSinceMidnight()
                    val stored = WellnessStore.stepBaseline(today)
                    val baseline = WellnessLogic.resolveStepBaseline(cumulative, stored, bootedToday)
                    if (stored == null || cumulative < stored) {
                        WellnessStore.setStepBaseline(baseline, today)
                    }
                    val delta = WellnessLogic.stepDelta(cumulative, baseline)
                    if (WellnessStore.day(today).steps != delta) WellnessStore.setSteps(delta, today)
                    DiagnosticsLogger.log(
                        "StepCounter",
                        "one-shot: cumulative=$cumulative baseline=$baseline today=$delta",
                    )
                }
            }
            StepTracker.start(context)
        }

        DisposableEffect(usable) {
            onDispose {
                // The background service owns the sensor when it is running.
                if (!com.osintgram.chronos.data.Settings.keepAlive.value) StepTracker.stop()
            }
        }

        return StepState(
            steps = if (StepTracker.running) StepTracker.steps else WellnessStore.day().steps,
            available = usable,
            countedFromNow = StepTracker.countedFromNow,
            sensor = kind,
            permissionGranted = permission,
        )
    }
}
