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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
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
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

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
    var tiltY by remember { mutableStateOf(0f) }
    val manager = remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    DisposableEffect(manager) {
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Android's gravity X runs opposite to the visual direction of a left tilt here.
                tiltX = -(event.values.getOrNull(0)?.coerceIn(-9f, 9f) ?: 0f)
                tiltY = -(event.values.getOrNull(1)?.coerceIn(-9f, 9f) ?: 0f)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager?.unregisterListener(listener) }
    }

    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val stageWidth = maxWidth
        val density = androidx.compose.ui.platform.LocalDensity.current
        val bottomInset = with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() + 22.dp.toPx() }
        val sizes = remember(bodySizes, density) {
            bodySizes.map { with(density) { PhysicsSize(it.width.toPx(), it.height.toPx()) } }
        }
        val progress = sortProgress.coerceIn(0f, 1f)
        val latestProgress by rememberUpdatedState(progress)
        val bodies = remember(sizes, widthPx, heightPx) {
            val random = Random(System.nanoTime())
            // Spread each new replay across the width, then vary the entry point and momentum.
            val lanes = listOf(.24f, .70f, .42f, .59f, .20f, .77f, .35f, .65f, .51f).shuffled(random)
            sizes.mapIndexed { index, size ->
                val half = size.width / 2f
                val laneX = (widthPx * (lanes[index % lanes.size] + (random.nextFloat() - .5f) * .045f))
                    .coerceIn(half, widthPx - half)
                val entryX = (laneX + (random.nextFloat() - .5f) * widthPx * .18f)
                    .coerceIn(half, widthPx - half)
                PhysicsBody(
                    index = index, size = size, x = entryX,
                    y = -size.height - random.nextFloat() * size.height,
                    vx = (random.nextFloat() - .5f) * 300f, vy = 0f,
                    rotation = (random.nextFloat() - .5f) * .14f,
                    spin = (random.nextFloat() - .5f) * .60f,
                    dropAt = index * .13f + random.nextFloat() * .11f,
                )
            }.toMutableList()
        }
        val frame = remember(bodies) { mutableStateOf(bodies.map { it.snapshot() }) }
        LaunchedEffect(bodies, reducedMotion) {
            var previous = 0L
            var elapsed = 0f
            while (isActive) {
                val now = withFrameNanos { it }
                val dt = if (previous == 0L) 0f else ((now - previous) / 1_000_000_000f).coerceIn(0f, 1f / 20f)
                previous = now
                elapsed += dt
                bodies.forEachIndexed { index, body ->
                    val baseFloor = heightPx - bottomInset - body.size.height
                    val supportFloor = bodies.asSequence()
                        .filter { it.index < index && it.landed && abs(it.x - body.x) < (it.size.width + body.size.width) * .24f }
                        .map { it.y - body.size.height + min(it.size.height, body.size.height) * .73f }
                        .minOrNull() ?: baseFloor
                    val support = min(baseFloor, supportFloor)
                    // A sideways slide can remove support, but gaining support must not pop a
                    // settled card upward through another card.
                    val floor = if (body.landed && support < body.y - with(density) { 4.dp.toPx() }) body.y else support
                    if (reducedMotion) {
                        body.y = floor
                        body.vy = 0f
                        body.rotation = 0f
                        body.landed = true
                    } else if (latestProgress > 0f) {
                        val targetY = with(density) { 84.dp.toPx() } + rowIndex(index) * rowSpacing(heightPx, density)
                        val targetX = with(density) { 12.dp.toPx() } + body.size.width / 2f
                        body.x += (targetX - body.x) * (0.16f * latestProgress).coerceAtMost(0.16f)
                        body.y += (targetY - body.y) * (0.16f * latestProgress).coerceAtMost(0.16f)
                        body.rotation *= 0.84f
                    } else if (elapsed >= body.dropAt) {
                        val half = body.size.width / 2f
                        val tiltShift = with(density) { 5.dp.toPx() } * tiltX
                        // Capture the actual landing spot. Never pull a fallen card back to a
                        // prearranged lane; tilt only shifts the pile from where it landed.
                        body.vx = if (body.landed) {
                            val desiredX = (body.restX + tiltShift).coerceIn(half, widthPx - half)
                            (body.vx + (desiredX - body.x) * 18f * dt) * .88f
                        } else {
                            (body.vx + tiltX * 90f * dt) * .992f
                        }
                        if (body.landed && body.y < floor - 3f) body.landed = false
                        val gravity = (1800f + tiltY * 140f).coerceIn(400f, 3400f)
                        body.vy = if (body.landed) 0f else (body.vy + gravity * dt) * .998f
                        body.x += body.vx * dt
                        body.y += body.vy * dt
                        body.rotation = (body.rotation + body.spin * dt).coerceIn(-.17f, .17f)
                        body.spin *= if (body.landed) .90f else .97f
                        val left = body.size.width / 2f
                        val right = widthPx - left
                        if (body.x < left || body.x > right) {
                            body.x = body.x.coerceIn(left, right)
                            body.vx *= -0.32f
                        }
                        if (body.y >= floor) {
                            body.y = floor
                            if (body.vy > 80f) body.vy *= -0.22f else {
                                body.vy = 0f
                                if (!body.hapticPlayed) {
                                    latestLanded(index)
                                    body.hapticPlayed = true
                                }
                                if (!body.landed) body.restX = body.x - tiltShift
                                body.landed = true
                            }
                        }
                    }
                }
                frame.value = bodies.map { it.snapshot() }
            }
        }
        Box(Modifier.fillMaxSize()) {
            frame.value.forEach { body ->
                Box(
                        Modifier.offset { IntOffset((body.x - body.size.width / 2f).roundToInt(), body.y.roundToInt()) }
                        .size(with(density) { body.size.width.toDp() }, with(density) { body.size.height.toDp() })
                        .graphicsLayer { rotationZ = body.rotation * 57.29578f; alpha = 1f - progress },
                ) { card(body.index) }
                if (body.index !in setOf(2, 5)) {
                    Box(
                        Modifier.offset { IntOffset(with(density) { 24.dp.toPx() }.roundToInt(), (with(density) { 84.dp.toPx() } + rowIndex(body.index) * rowSpacing(heightPx, density)).roundToInt()) }
                            .width(stageWidth - 48.dp).height(56.dp)
                            .graphicsLayer { alpha = progress },
                    ) { row(body.index) }
                }
            }
        }
    }
}

private data class PhysicsSize(val width: Float, val height: Float)
private data class PhysicsBody(
    val index: Int, val size: PhysicsSize, var x: Float, var y: Float,
    var vx: Float, var vy: Float, var rotation: Float, var spin: Float,
    val dropAt: Float,
    var landed: Boolean = false, var hapticPlayed: Boolean = false, var restX: Float = 0f,
)
private data class PhysicsSnapshot(val index: Int, val size: PhysicsSize, val x: Float, val y: Float, val rotation: Float)
private fun PhysicsBody.snapshot() = PhysicsSnapshot(index, size, x, y, rotation)
private fun rowIndex(index: Int) = index - if (index > 5) 2 else if (index > 2) 1 else 0
private fun rowSpacing(heightPx: Float, density: androidx.compose.ui.unit.Density): Float = with(density) {
    ((heightPx - 390.dp.toPx()) / 7f).coerceIn(48.dp.toPx(), 64.dp.toPx())
}
