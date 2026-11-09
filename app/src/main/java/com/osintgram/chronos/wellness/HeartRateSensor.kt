package com.osintgram.chronos.wellness

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.osintgram.chronos.diagnostics.DiagnosticsLogger

/** One heart-rate reading from the device sensor. */
data class HeartRateReading(val bpm: Int, val measuredAt: Long)

/**
 * The phone's own heart-rate sensor, used only while the user has the Today
 * card open and has tapped Measure.
 *
 * The sensor is read in the foreground on purpose: this app is not a medical
 * monitor, so it does not ask for the background body-sensor permission that
 * continuous monitoring needs. BODY_SENSORS is a runtime permission, requested
 * when the user taps Allow on the card and never at launch.
 */
object HeartRateSensor {

    fun hasSensor(context: Context): Boolean {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
        return manager.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BODY_SENSORS,
        ) == PackageManager.PERMISSION_GRANTED

    fun isAvailable(context: Context): Boolean = hasSensor(context) && hasPermission(context)
}

/**
 * Listens while [measuring] is true and reports the last valid reading. A
 * reading of zero is the sensor's "no contact" value and is ignored.
 */
@Composable
fun rememberHeartRate(
    measuring: Boolean,
    onReading: (HeartRateReading) -> Unit,
): HeartRateReading? {
    val context = LocalContext.current
    var reading by remember { mutableStateOf<HeartRateReading?>(null) }
    val latestCallback by rememberUpdatedState(onReading)
    val available = remember(context, measuring) {
        measuring && HeartRateSensor.isAvailable(context)
    }

    DisposableEffect(available) {
        if (!available) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (manager == null || sensor == null) return@DisposableEffect onDispose { }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val bpm = event.values.firstOrNull()?.toInt() ?: return
                if (bpm <= 0) return
                val next = HeartRateReading(bpm, System.currentTimeMillis())
                reading = next
                latestCallback(next)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val registered = runCatching {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }.getOrDefault(false)
        if (!registered) {
            DiagnosticsLogger.log("HeartRate", "Listener could not be registered")
        }
        onDispose { runCatching { manager.unregisterListener(listener) } }
    }
    return reading
}
