package com.osintgram.chronos.wellness

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.osintgram.chronos.diagnostics.DiagnosticsLogger

/**
 * Today's step count from the device's own step counter.
 *
 * The platform sensor reports steps since boot, so the first reading of a day
 * becomes that day's baseline and the difference is what the user sees. A
 * reboot restarts the counter, which shows up as a reading below the baseline —
 * the baseline moves instead of the number going negative.
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

    /**
     * Registers the counter while the composable is on screen and writes
     * today's total into the store. Returns today's steps, which is zero until
     * the sensor produces a reading.
     */
    @Composable
    fun rememberTodaySteps(enabled: Boolean = true): Int {
        val context = androidx.compose.ui.platform.LocalContext.current
        val available = remember(context) { enabled && isAvailable(context) }
        var steps by remember { mutableIntStateOf(WellnessStore.day().steps) }

        DisposableEffect(available) {
            if (!available) return@DisposableEffect onDispose { }
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val sensor = manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            if (manager == null || sensor == null) return@DisposableEffect onDispose { }

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val cumulative = event.values.firstOrNull()?.toLong() ?: return
                    val today = WellnessLogic.todayKey()
                    val baseline = WellnessStore.stepBaseline(today)
                    if (WellnessLogic.needsRebaseline(cumulative, baseline)) {
                        WellnessStore.setStepBaseline(cumulative, today)
                        return
                    }
                    val delta = WellnessLogic.stepDelta(cumulative, baseline)
                    steps = delta
                    if (WellnessStore.day(today).steps != delta) {
                        WellnessStore.setSteps(delta, today)
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            val registered = runCatching {
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }.getOrDefault(false)
            if (!registered) {
                DiagnosticsLogger.log("StepCounter", "Step counter listener could not be registered")
            }
            onDispose { runCatching { manager.unregisterListener(listener) } }
        }
        return steps
    }
}
