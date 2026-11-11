package com.osintgram.chronos.wellness

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.osintgram.chronos.diagnostics.DiagnosticsLogger

/**
 * The one owner of the step sensor.
 *
 * Before this, the sensor was registered by the Today card only, so steps were
 * counted **only while that card was on screen** — walk with the app closed and
 * nothing was recorded. The background service also wants the sensor, and two
 * listeners on a `TYPE_STEP_DETECTOR` would double-count every step, so the
 * listener lives here once and both callers share it:
 *
 * - [start] is idempotent; the card and the service both call it.
 * - [stop] is only called by the card, and only when the background service is
 *   not running (that is, when the keep-alive setting is off).
 *
 * Both sensor kinds are handled: a counter reports steps since boot (so the
 * day's baseline is subtracted) and a detector reports one event per step (so
 * the events are added to what the day already holds).
 */
object StepTracker {

    /** Today's count, observable by Compose. */
    var steps by mutableIntStateOf(0)
        private set

    /** "counter", "detector" or "none". */
    var sensor by mutableStateOf(StepCounter.SENSOR_NONE)
        private set

    var running by mutableStateOf(false)
        private set

    /** True when the count had to start at "now" instead of midnight. */
    var countedFromNow by mutableStateOf(false)
        private set

    private var manager: SensorManager? = null
    private var listener: SensorEventListener? = null
    private var lastPersisted = -1
    private var lastLogged = -1
    private var sessionSteps = 0

    /** Registers the sensor if it is not already registered. Safe to call often. */
    fun start(context: Context): Boolean {
        if (running) return true
        if (!StepCounter.hasPermission(context)) {
            DiagnosticsLogger.log("StepCounter", "not started: ACTIVITY_RECOGNITION not granted")
            return false
        }
        val kind = StepCounter.sensorKind(context)
        sensor = kind
        if (kind == StepCounter.SENSOR_NONE) {
            DiagnosticsLogger.log("StepCounter", "not started: no step sensor on this phone")
            return false
        }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return false
        val sensorType =
            if (kind == StepCounter.SENSOR_COUNTER) Sensor.TYPE_STEP_COUNTER else Sensor.TYPE_STEP_DETECTOR
        val hardware = sensorManager.getDefaultSensor(sensorType) ?: return false

        steps = WellnessStore.day().steps
        lastPersisted = -1
        lastLogged = -1
        sessionSteps = 0

        val newListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val today = WellnessLogic.todayKey()
                val delta: Int
                if (kind == StepCounter.SENSOR_COUNTER) {
                    val cumulative = event.values.firstOrNull()?.toLong() ?: return
                    val bootedToday = StepCounter.bootedSinceMidnight()
                    val stored = WellnessStore.stepBaseline(today)
                    val baseline = WellnessLogic.resolveStepBaseline(cumulative, stored, bootedToday)
                    if (stored == null || cumulative < stored) {
                        WellnessStore.setStepBaseline(baseline, today)
                    }
                    delta = WellnessLogic.stepDelta(cumulative, baseline)
                    countedFromNow = WellnessLogic.countedFromNow(stored, bootedToday)
                    if (lastLogged < 0 || delta - lastLogged >= 250) {
                        lastLogged = delta
                        DiagnosticsLogger.log(
                            "StepCounter",
                            "counter cumulative=$cumulative baseline=$baseline today=$delta",
                        )
                    }
                } else {
                    sessionSteps += event.values.firstOrNull()?.toInt()?.coerceAtLeast(1) ?: 1
                    delta = WellnessStore.day(today).steps + 1
                    if (sessionSteps == 1 || sessionSteps % 100 == 0) {
                        DiagnosticsLogger.log(
                            "StepCounter",
                            "detector session=$sessionSteps today=$delta",
                        )
                    }
                }
                steps = delta
                if (lastPersisted < 0 || delta - lastPersisted >= 1) {
                    lastPersisted = delta
                    if (WellnessStore.day(today).steps != delta) WellnessStore.setSteps(delta, today)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val registered = runCatching {
            sensorManager.registerListener(newListener, hardware, SensorManager.SENSOR_DELAY_UI)
        }.getOrDefault(false)
        DiagnosticsLogger.log(
            "StepCounter",
            if (registered) "listening on $kind (shared tracker)" else "registration failed on $kind",
        )
        if (!registered) return false

        manager = sensorManager
        listener = newListener
        running = true
        return true
    }

    /** Unregisters and flushes the last value. Called when the card leaves. */
    fun stop() {
        val sensorManager = manager
        val current = listener
        if (sensorManager != null && current != null) {
            runCatching { sensorManager.unregisterListener(current) }
        }
        if (steps > 0) WellnessStore.setSteps(steps)
        DiagnosticsLogger.log("StepCounter", "stopped (shared tracker), today=$steps")
        manager = null
        listener = null
        running = false
    }
}
