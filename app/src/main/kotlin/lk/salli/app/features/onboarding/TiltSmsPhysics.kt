package lk.salli.app.features.onboarding

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import kotlin.math.roundToInt

/** A small sensor driven falling-card scene for onboarding previews. */
@Composable
fun TiltSmsPhysics(
    bodySizes: List<DpSize>,
    sortProgress: Float = 0f,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    onBodyLanded: (Int) -> Unit = {},
    card: @Composable (Int) -> Unit,
    row: @Composable (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val latestLanded by rememberUpdatedState(onBodyLanded)
    var tiltX by remember { mutableStateOf(0f) }
    val manager = remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    DisposableEffect(manager) {
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Screen coordinates: positive x tips the pile toward the right wall.
                tiltX = event.values.getOrNull(0)?.coerceIn(-9f, 9f) ?: 0f
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager?.unregisterListener(listener) }
    }

    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val density = androidx.compose.ui.platform.LocalDensity.current
        val sizes = remember(bodySizes, density) {
            bodySizes.map { with(density) { PhysicsSize(it.width.toPx(), it.height.toPx()) } }
        }
        val progress = sortProgress.coerceIn(0f, 1f)
        val bodies = remember(sizes, widthPx, heightPx) {
            sizes.mapIndexed { index, size ->
                PhysicsBody(index, size, widthPx / 2f, -size.height - index * 18f, 0f, 0f, 0f)
            }.toMutableList()
        }
        LaunchedEffect(bodies, reducedMotion) {
            var previous = 0L
            while (isActive) {
                val now = withFrameNanos { it }
                val dt = if (previous == 0L) 0f else ((now - previous) / 1_000_000_000f).coerceIn(0f, 1f / 20f)
                previous = now
                bodies.forEachIndexed { index, body ->
                    val floor = heightPx - with(density) { 12.dp.toPx() } - body.size.height
                    if (reducedMotion) {
                        body.y = floor - (index * 22f).coerceAtMost(heightPx * .7f)
                        body.vy = 0f
                        body.rotation = 0f
                    } else if (progress > 0f) {
                        val targetY = with(density) { (8 + index * 48).dp.toPx() }
                        val targetX = with(density) { 12.dp.toPx() } + body.size.width / 2f
                        body.x += (targetX - body.x) * (0.16f * progress).coerceAtMost(0.16f)
                        body.y += (targetY - body.y) * (0.16f * progress).coerceAtMost(0.16f)
                        body.rotation *= 0.84f
                    } else if (!body.landed) {
                        body.vx = (body.vx + tiltX * 35f * dt) * 0.992f
                        body.vy = (body.vy + 2100f * dt) * 0.998f
                        body.x += body.vx * dt
                        body.y += body.vy * dt
                        body.rotation += (body.vx / 900f) * dt
                        val left = body.size.width / 2f
                        val right = widthPx - left
                        if (body.x < left || body.x > right) {
                            body.x = body.x.coerceIn(left, right)
                            body.vx *= -0.32f
                        }
                        if (body.y >= floor - index * 22f) {
                            body.y = floor - index * 22f
                            if (body.vy > 80f) body.vy *= -0.22f else {
                                body.vy = 0f; body.vx *= 0.55f; body.landed = true
                                latestLanded(index)
                            }
                        }
                    }
                }
                yield()
            }
        }
        Box(Modifier.fillMaxSize()) {
            bodies.forEach { body ->
                Box(
                    Modifier.offset { IntOffset((body.x - body.size.width / 2f).roundToInt(), body.y.roundToInt()) }
                        .graphicsLayer { rotationZ = body.rotation * 57.29578f; alpha = 1f - progress },
                ) { card(body.index) }
                if (body.index !in setOf(2, 5)) {
                    Box(
                        Modifier.offset { IntOffset(with(density) { 12.dp.toPx() }.roundToInt(), with(density) { (8 + body.index * 48).dp.toPx() }.roundToInt()) }
                            .fillMaxWidth().height(44.dp)
                            .graphicsLayer { alpha = progress },
                    ) { row(body.index) }
                }
            }
        }
    }
}

private data class PhysicsSize(val width: Float, val height: Float)
private data class PhysicsBody(val index: Int, val size: PhysicsSize, var x: Float, var y: Float, var vx: Float, var vy: Float, var rotation: Float, var landed: Boolean = false)
