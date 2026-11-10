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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId

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

            var value: Long? = null
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (value == null) value = event.values.firstOrNull()?.toLong()
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            try {
                withTimeoutOrNull(timeoutMs) {
                    while (value == null) delay(50)
                }
            } finally {
                runCatching { manager.unregisterListener(listener) }
            }
            value
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
        val usable = remember(kind, permission) { kind != SENSOR_NONE && permission }

        var state by remember {
            mutableStateOf(
                StepState(
                    steps = WellnessStore.day().steps,
                    available = usable,
                    sensor = kind,
                    permissionGranted = permission,
                ),
            )
        }

        LaunchedEffect(usable, kind) {
            DiagnosticsLogger.log(
                "StepCounter",
                "card opened: sensor=$kind permission=$permission usable=$usable " +
                    "storedToday=${WellnessStore.day().steps}",
            )
            if (!usable) return@LaunchedEffect
            if (kind != SENSOR_COUNTER) {
                // The detector has no absolute value; the stored count is the day.
                state = state.copy(
                    steps = WellnessStore.day().steps,
                    available = true,
                    sensor = kind,
                    permissionGranted = true,
                )
                return@LaunchedEffect
            }
            val cumulative = readCumulativeOnce(context, timeoutMs = 5_000)
            if (cumulative == null) {
                DiagnosticsLogger.log("StepCounter", "counter gave no reading within 5 s")
                return@LaunchedEffect
            }
            val today = WellnessLogic.todayKey()
            val bootedToday = bootedSinceMidnight()
            val stored = WellnessStore.stepBaseline(today)
            val baseline = WellnessLogic.resolveStepBaseline(cumulative, stored, bootedToday)
            if (stored == null || cumulative < stored) {
                WellnessStore.setStepBaseline(baseline, today)
            }
            val delta = WellnessLogic.stepDelta(cumulative, baseline)
            state = StepState(
                steps = delta,
                available = true,
                countedFromNow = WellnessLogic.countedFromNow(stored, bootedToday),
                sensor = kind,
                permissionGranted = true,
            )
            if (WellnessStore.day(today).steps != delta) WellnessStore.setSteps(delta, today)
            DiagnosticsLogger.log(
                "StepCounter",
                "one-shot: cumulative=$cumulative baseline=$baseline today=$delta",
            )
        }

        DisposableEffect(usable, kind) {
            if (!usable) return@DisposableEffect onDispose { }
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                ?: return@DisposableEffect onDispose { }
            val sensorType = if (kind == SENSOR_COUNTER) Sensor.TYPE_STEP_COUNTER else Sensor.TYPE_STEP_DETECTOR
            val sensor = manager.getDefaultSensor(sensorType)
                ?: return@DisposableEffect onDispose { }

            var lastPersisted = -1
            var lastLogged = -1
            var sessionSteps = 0

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val today = WellnessLogic.todayKey()
                    val delta: Int
                    if (kind == SENSOR_COUNTER) {
                        val cumulative = event.values.firstOrNull()?.toLong() ?: return
                        val bootedToday = bootedSinceMidnight()
                        val stored = WellnessStore.stepBaseline(today)
                        val baseline = WellnessLogic.resolveStepBaseline(cumulative, stored, bootedToday)
                        if (stored == null || cumulative < stored) {
                            WellnessStore.setStepBaseline(baseline, today)
                        }
                        delta = WellnessLogic.stepDelta(cumulative, baseline)
                        if (lastLogged < 0 || delta - lastLogged >= 250) {
                            lastLogged = delta
                            DiagnosticsLogger.log(
                                "StepCounter",
                                "counter cumulative=$cumulative baseline=$baseline today=$delta",
                            )
                        }
                    } else {
                        // Detector: one event per step, added to what the day holds.
                        sessionSteps += event.values.firstOrNull()?.toInt()?.coerceAtLeast(1) ?: 1
                        delta = WellnessStore.day(today).steps + 1
                        if (sessionSteps % 100 == 0) {
                            DiagnosticsLogger.log(
                                "StepCounter",
                                "detector session=$sessionSteps today=$delta",
                            )
                        }
                    }
                    state = state.copy(steps = delta, available = true, sensor = kind, permissionGranted = true)
                    if (delta != lastPersisted && (lastPersisted < 0 || delta - lastPersisted >= 5)) {
                        lastPersisted = delta
                        if (WellnessStore.day(today).steps != delta) WellnessStore.setSteps(delta, today)
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }

            val registered = runCatching {
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            }.getOrDefault(false)
            DiagnosticsLogger.log(
                "StepCounter",
                if (registered) "listening on $kind" else "listener registration failed on $kind",
            )

            onDispose {
                runCatching { manager.unregisterListener(listener) }
                if (state.steps > 0) WellnessStore.setSteps(state.steps)
                DiagnosticsLogger.log("StepCounter", "stopped listening, today=${state.steps}")
            }
        }
        return state
    }
}
