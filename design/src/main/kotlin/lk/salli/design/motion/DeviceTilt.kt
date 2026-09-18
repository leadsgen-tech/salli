package lk.salli.design.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * How far the phone leans left or right, −1..1, from the gravity sensor. Zero when flat or when
 * the device has no such sensor. Smoothed so a jar of liquid follows the hand rather than every
 * jitter. Only listens while [enabled] and the caller is in composition.
 */
@Composable
fun rememberDeviceTilt(enabled: Boolean = true): State<Float> {
    val context = LocalContext.current
    val tilt = remember { mutableFloatStateOf(0f) }
    DisposableEffect(enabled, context) {
        if (!enabled) {
            tilt.floatValue = 0f
            return@DisposableEffect onDispose { }
        }
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Portrait: gravity along x grows as the phone rolls sideways.
                val target = (-event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                tilt.floatValue += (target - tilt.floatValue) * 0.25f
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (manager != null && sensor != null) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose {
            manager?.unregisterListener(listener)
            tilt.floatValue = 0f
        }
    }
    return tilt
}
