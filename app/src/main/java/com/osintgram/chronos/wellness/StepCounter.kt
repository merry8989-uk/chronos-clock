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
import java.time.LocalTime
import java.time.ZoneId

/** What the Today card needs to show about today's steps. */
data class StepState(
    val steps: Int = 0,
    val available: Boolean = false,
    /** True when the count had to start when the app was opened, not at midnight. */
    val countedFromNow: Boolean = false,
)

/**
 * Today's step count from the device's own step counter.
 *
 * The platform sensor reports steps since boot, so "today" needs a baseline:
 *  - a stored baseline for the day is used as-is;
 *  - with no stored baseline, a phone that rebooted after midnight counts from
 *    zero, so every step since boot is today's;
 *  - otherwise the counter has been running since before midnight and the only
 *    honest starting point is now — the card says so.
 *
 * The listener runs while the card is on screen at the UI rate, so the number
 * moves as the user walks. A six-hourly worker records the day's baseline even
 * when the app is never opened, so the next day starts from midnight rather
 * than from the first launch.
 */
object StepCounter {

    fun hasSensor(context: Context): Boolean {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
        return manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }

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
     * (the background worker). Null when the sensor says nothing in [timeoutMs].
     */
    suspend fun readCumulativeOnce(context: Context, timeoutMs: Long = 3_000): Long? =
        withContext(Dispatchers.Default) {
            if (!isAvailable(context)) return@withContext null
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
        val today = WellnessLogic.todayKey()
        if (WellnessStore.stepBaseline(today) != null) return
        val cumulative = readCumulativeOnce(context) ?: return
        val bootedToday = bootedSinceMidnight()
        val baseline = WellnessLogic.resolveStepBaseline(cumulative, null, bootedToday)
        WellnessStore.setStepBaseline(baseline, today)
        DiagnosticsLogger.log(
            "StepCounter",
            "Baseline for $today = $baseline (cumulative=$cumulative, bootedToday=$bootedToday)",
        )
    }

    /**
     * Registers the counter while the composable is on screen and writes today's
     * total into the store. Returns the count plus where it came from.
     */
    @Composable
    fun rememberTodaySteps(enabled: Boolean = true): StepState {
        val context = LocalContext.current
        val available = remember(context, enabled) { enabled && isAvailable(context) }
        var state by remember {
            mutableStateOf(
                StepState(
                    steps = WellnessStore.day().steps,
                    available = available,
                ),
            )
        }

        // A phone lying still sends no step events at all, so the card used to
        // sit at zero until the user walked. One read on open fills it in now.
        var liveSeen by remember { mutableStateOf(false) }
        LaunchedEffect(available) {
            if (!available) return@LaunchedEffect
            val cumulative = readCumulativeOnce(context, timeoutMs = 2_000)
            if (cumulative == null) {
                DiagnosticsLogger.log("StepCounter", "no reading within 2 s of opening the card")
                return@LaunchedEffect
            }
            if (liveSeen) return@LaunchedEffect
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
            )
            if (WellnessStore.day(today).steps != delta) WellnessStore.setSteps(delta, today)
            DiagnosticsLogger.log(
                "StepCounter",
                "opened: cumulative=$cumulative baseline=$baseline today=$delta",
            )
        }

        DisposableEffect(available) {
            if (!available) {
                state = state.copy(available = false)
                return@DisposableEffect onDispose { }
            }
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val sensor = manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            if (manager == null || sensor == null) return@DisposableEffect onDispose { }

            var lastPersisted = -1
            var lastLogged = -1
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val cumulative = event.values.firstOrNull()?.toLong() ?: return
                    liveSeen = true
                    val today = WellnessLogic.todayKey()
                    val bootedToday = bootedSinceMidnight()
                    val stored = WellnessStore.stepBaseline(today)
                    val baseline = WellnessLogic.resolveStepBaseline(cumulative, stored, bootedToday)
                    if (stored == null || cumulative < stored) {
                        WellnessStore.setStepBaseline(baseline, today)
                    }
                    val delta = WellnessLogic.stepDelta(cumulative, baseline)
                    val fromNow = WellnessLogic.countedFromNow(stored, bootedToday)
                    // The log had no step values at all, so a "steps are wrong"
                    // report could not be checked. First reading and every 250
                    // steps after that now say exactly what the sensor sent.
                    if (lastLogged < 0 || delta - lastLogged >= 250) {
                        lastLogged = delta
                        DiagnosticsLogger.log(
                            "StepCounter",
                            "cumulative=$cumulative baseline=$baseline today=$delta " +
                                "fromNow=$fromNow bootedToday=$bootedToday",
                        )
                    }
                    if (delta != state.steps || fromNow != state.countedFromNow) {
                        state = StepState(steps = delta, available = true, countedFromNow = fromNow)
                    }
                    // Persist at most every few steps: one prefs write per step
                    // would be wasteful, and the store is read on launch anyway.
                    if (delta - lastPersisted >= 5 || (delta == 0 && lastPersisted != 0)) {
                        lastPersisted = delta
                        if (WellnessStore.day(today).steps != delta) {
                            WellnessStore.setSteps(delta, today)
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            val registered = runCatching {
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            }.getOrDefault(false)
            if (!registered) {
                DiagnosticsLogger.log("StepCounter", "Step counter listener could not be registered")
            } else {
                DiagnosticsLogger.log("StepCounter", "Step counter listening")
            }
            onDispose {
                runCatching { manager.unregisterListener(listener) }
                // Flush the last value so closing the card never loses steps.
                if (state.steps > 0) WellnessStore.setSteps(state.steps)
            }
        }
        return state
    }
}
