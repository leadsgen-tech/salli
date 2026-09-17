package lk.salli.app.features.onboarding

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The onboarding pile as real objects. Each SMS card is a rigid capsule made of three circles
 * held together by distance constraints (position-based dynamics, Verlet integration). Cards
 * collide with each other, the walls and the floor, tumble, slide and come to rest on their
 * own; nothing is snapped to a lane, a stack or a slot.
 *
 * The phone's gravity sensor sets which way "down" is, so tilting slides the pile, and the
 * linear-acceleration sensor injects shakes, so shaking the phone shakes the pile. Sorting is
 * a pure function of [sortProgress]: the simulation freezes and every card lerps from where it
 * lies to its row slot. Per-frame values go into state read by `graphicsLayer` lambdas, so the
 * scene never recomposes at 60 fps.
 */
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
    val progress = sortProgress.coerceIn(0f, 1f)
    val progressState = rememberUpdatedState(progress)
    val input = remember { SensorInput() }
    val manager = remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    // Sensors only run while the pile is live; they're unregistered once sorting starts.
    DisposableEffect(manager, progress > 0f, reducedMotion) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_GRAVITY -> {
                        // Screen x runs opposite to the sensor's x for a visual tilt; screen y is down.
                        input.gx = -(event.values.getOrNull(0) ?: 0f)
                        input.gy = event.values.getOrNull(1) ?: 9.8f
                    }
                    Sensor.TYPE_LINEAR_ACCELERATION -> {
                        // The phone jerking right leaves the cards behind, i.e. they move left.
                        input.ax = -(event.values.getOrNull(0) ?: 0f)
                        input.ay = event.values.getOrNull(1) ?: 0f
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (manager != null && progress == 0f && !reducedMotion) {
            manager.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
            manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        }
        onDispose { manager?.unregisterListener(listener) }
    }

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val stageWidth = maxWidth
        val bottomInset = with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() + 22.dp.toPx() }
        val world = remember(widthPx, heightPx, density) { World(widthPx, heightPx, bottomInset, density) }
        val cards = remember(bodySizes, world) {
            val random = Random(System.nanoTime())
            val lanes = listOf(.24f, .70f, .42f, .59f, .20f, .77f, .35f, .65f, .51f).shuffled(random)
            bodySizes.mapIndexed { index, size ->
                val w = with(density) { size.width.toPx() }
                val h = with(density) { size.height.toPx() }
                val laneX = (widthPx * (lanes[index % lanes.size] + (random.nextFloat() - .5f) * .05f)).coerceIn(w / 2f, widthPx - w / 2f)
                Card(
                    index = index, w = w, h = h,
                    cx = laneX, cy = -h - random.nextFloat() * h * 1.5f,
                    rot = (random.nextFloat() - .5f) * .35f,
                    vx = (random.nextFloat() - .5f) * world.dp(160f),
                    dropAt = index * .14f + random.nextFloat() * .1f,
                )
            }
        }

        LaunchedEffect(cards, reducedMotion) {
            if (reducedMotion) {
                world.settleInstantly(cards)
                cards.forEach { it.publish() }
                return@LaunchedEffect
            }
            var previous = 0L
            var elapsed = 0f
            var accumulator = 0f
            while (isActive) {
                val now = withFrameNanos { it }
                val dt = if (previous == 0L) 0f else ((now - previous) / 1_000_000_000f).coerceIn(0f, 1f / 20f)
                previous = now
                val p = progressState.value
                if (p > 0f) {
                    cards.forEach { world.sortFrame(it, p) }
                } else {
                    elapsed += dt
                    cards.forEach { if (!it.active && elapsed >= it.dropAt) it.active = true }
                    // Fixed sub-steps keep collisions stable no matter the frame rate.
                    accumulator = (accumulator + dt).coerceAtMost(STEP * 4)
                    while (accumulator >= STEP) {
                        world.step(cards, STEP, input)
                        accumulator -= STEP
                    }
                    input.decayShake()
                    cards.forEach { c -> if (world.justSettled(c, elapsed)) latestLanded(c.index) }
                }
                cards.forEach { it.syncPose(); it.publish() }
                if (p >= 1f) break
            }
        }

        cards.forEach { c ->
            Box(
                Modifier
                    .size(with(density) { c.w.toDp() }, with(density) { c.h.toDp() })
                    .graphicsLayer {
                        translationX = c.tx.floatValue
                        translationY = c.ty.floatValue
                        rotationZ = c.trot.floatValue * 57.29578f
                        alpha = c.talpha.floatValue
                    },
            ) { card(c.index) }
        }
        cards.forEach { c ->
            if (c.index !in Discarded) {
                Box(
                    Modifier
                        .offset { IntOffset(with(density) { 24.dp.toPx() }.roundToInt(), world.slotTop(c.index).roundToInt()) }
                        .width(stageWidth - 48.dp).height(SlotHeightDp.dp)
                        .graphicsLayer {
                            val p = progressState.value
                            alpha = smoothstep(0.5f, 0.85f, p)
                            translationY = (1f - easeOutCubic(p)) * with(density) { 14.dp.toPx() }
                        },
                ) { row(c.index) }
            }
        }
    }
}

/** Sample indices that are OTPs/promos: flung off the stage instead of becoming rows. */
private val Discarded = setOf(2, 5)
private const val SlotHeightDp = 56f
private const val STEP = 1f / 120f

/** Latest sensor readings, written on the sensor thread, read by the frame loop. */
private class SensorInput {
    @Volatile var gx = 0f
    @Volatile var gy = 9.8f
    @Volatile var ax = 0f
    @Volatile var ay = 0f

    /** A shake sample must not keep pushing after the event has passed. */
    fun decayShake() { ax *= 0.75f; ay *= 0.75f }
}

private class Card(
    val index: Int, val w: Float, val h: Float,
    var cx: Float, var cy: Float, var rot: Float,
    vx: Float, val dropAt: Float,
) {
    val r = h / 2f
    private val d = (w - h) / 2f
    val px = FloatArray(3)
    val py = FloatArray(3)
    val ppx = FloatArray(3)
    val ppy = FloatArray(3)
    var active = false
    var landed = false
    var touching = false
    var alpha = 1f
    var fromX = 0f; var fromY = 0f; var fromRot = 0f; var captured = false
    val tx = mutableFloatStateOf(cx - w / 2f)
    val ty = mutableFloatStateOf(cy - h / 2f)
    val trot = mutableFloatStateOf(rot)
    val talpha = mutableFloatStateOf(1f)

    init {
        place(cx, cy, rot)
        for (i in 0..2) { ppx[i] = px[i] - vx * STEP; ppy[i] = py[i] }
    }

    fun place(cx: Float, cy: Float, rot: Float) {
        val c = cos(rot); val s = sin(rot)
        for (i in 0..2) { val o = (i - 1) * d; px[i] = cx + o * c; py[i] = cy + o * s }
    }

    fun restLength(i: Int, j: Int): Float = d * (j - i)

    fun speed(): Float {
        var sum = 0f
        for (i in 0..2) sum += hypot(px[i] - ppx[i], py[i] - ppy[i])
        return sum / 3f
    }

    /** Rendered pose is derived from the particles; nothing else is stored. */
    fun syncPose() {
        cx = (px[0] + px[1] + px[2]) / 3f
        cy = (py[0] + py[1] + py[2]) / 3f
        rot = atan2(py[2] - py[0], px[2] - px[0])
    }

    fun publish() {
        tx.floatValue = cx - w / 2f
        ty.floatValue = cy - h / 2f
        trot.floatValue = rot
        talpha.floatValue = alpha
    }
}

private class World(val widthPx: Float, val heightPx: Float, bottomInset: Float, val density: Density) {
    fun dp(value: Float): Float = value * density.density
    private val gPx = dp(1600f)                       // 9.8 m/s² on screen
    private val floor = heightPx - bottomInset
    private val rowSpacing = ((heightPx - dp(390f)) / 7f).coerceIn(dp(48f), dp(64f))

    fun slotTop(index: Int): Float = dp(84f) + rowIndex(index) * rowSpacing

    fun step(cards: List<Card>, dt: Float, input: SensorInput) {
        // Tilt only changes the direction of gravity; a flat phone still drops cards down-screen.
        var gx = input.gx; var gy = input.gy
        val mag = hypot(gx, gy)
        if (mag < 4f) { gx = 0f; gy = 9.8f } else { gx = gx / mag * 9.8f; gy = gy / mag * 9.8f }
        val scale = gPx / 9.8f
        val axPx = (gx + input.ax) * scale
        val ayPx = (gy + input.ay) * scale
        val dt2 = dt * dt

        for (c in cards) {
            if (!c.active) continue
            c.touching = false
            for (i in 0..2) {
                val vx = (c.px[i] - c.ppx[i]) * 0.996f
                val vy = (c.py[i] - c.ppy[i]) * 0.996f
                c.ppx[i] = c.px[i]; c.ppy[i] = c.py[i]
                c.px[i] += vx + axPx * dt2
                c.py[i] += vy + ayPx * dt2
            }
        }
        repeat(5) {
            for (c in cards) if (c.active) rigid(c)
            for (c in cards) if (c.active) bounds(c)
            for (a in cards.indices) {
                val ca = cards[a]; if (!ca.active) continue
                for (b in a + 1 until cards.size) {
                    val cb = cards[b]; if (!cb.active) continue
                    collide(ca, cb)
                }
            }
        }
        for (c in cards) if (c.active) friction(c, dt)
    }

    /**
     * Coulomb friction against the floor and walls, once per sub-step: a fixed speed loss, so a
     * gentle tilt holds the pile and a steeper one lets it slide, like paper on a table.
     */
    private fun friction(c: Card, dt: Float) {
        val loss = 0.35f * gPx * dt * dt   // μ·g·dt expressed as a per-step position delta
        for (i in 0..2) {
            val r = c.r
            val onFloor = c.py[i] >= floor - r - 0.5f
            val onWall = c.px[i] <= r + 0.5f || c.px[i] >= widthPx - r - 0.5f
            val onTop = c.py[i] <= r + 0.5f
            if (onFloor || onTop) {
                val vx = c.px[i] - c.ppx[i]
                c.ppx[i] = if (kotlin.math.abs(vx) <= loss) c.px[i] else c.px[i] - (vx - kotlin.math.sign(vx) * loss)
            }
            if (onWall) {
                val vy = c.py[i] - c.ppy[i]
                c.ppy[i] = if (kotlin.math.abs(vy) <= loss) c.py[i] else c.py[i] - (vy - kotlin.math.sign(vy) * loss)
            }
        }
    }

    /** Distance constraints keep the three circles a rigid capsule. */
    private fun rigid(c: Card) {
        for (i in 0..2) for (j in i + 1..2) {
            val dx = c.px[j] - c.px[i]; val dy = c.py[j] - c.py[i]
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.0001f)
            val diff = (dist - c.restLength(i, j)) / dist * 0.5f
            c.px[i] += dx * diff; c.py[i] += dy * diff
            c.px[j] -= dx * diff; c.py[j] -= dy * diff
        }
    }

    private fun bounds(c: Card) {
        for (i in 0..2) {
            val r = c.r
            if (c.px[i] < r) { c.px[i] = r; c.touching = true }
            if (c.px[i] > widthPx - r) { c.px[i] = widthPx - r; c.touching = true }
            if (c.py[i] > floor - r) {
                val vy = c.py[i] - c.ppy[i]
                c.py[i] = floor - r
                if (vy > 0f) c.ppy[i] = c.py[i] + vy * 0.12f           // faint bounce
                c.touching = true
            }
            // Top edge is a wall only for cards already on stage; new cards fall in from above it.
            if (c.py[i] < r && c.ppy[i] >= r) { c.py[i] = r; c.ppy[i] = c.py[i]; c.touching = true }
        }
    }

    private fun collide(a: Card, b: Card) {
        val minD = a.r + b.r
        for (i in 0..2) for (j in 0..2) {
            val dx = b.px[j] - a.px[i]; val dy = b.py[j] - a.py[i]
            val d2 = dx * dx + dy * dy
            if (d2 >= minD * minD || d2 < 0.0001f) continue
            val dist = sqrt(d2)
            val push = (minD - dist) / dist * 0.5f
            a.px[i] -= dx * push; a.py[i] -= dy * push
            b.px[j] += dx * push; b.py[j] += dy * push
            // Contact friction: bleed a little relative motion so heaps settle instead of jittering.
            a.ppx[i] += (a.px[i] - a.ppx[i]) * 0.08f; a.ppy[i] += (a.py[i] - a.ppy[i]) * 0.08f
            b.ppx[j] += (b.px[j] - b.ppx[j]) * 0.08f; b.ppy[j] += (b.py[j] - b.ppy[j]) * 0.08f
            a.touching = true; b.touching = true
        }
    }

    /** One haptic tick the first time a card comes to rest in contact with something. */
    fun justSettled(c: Card, elapsed: Float): Boolean {
        if (!c.active || c.landed || elapsed < c.dropAt + 0.25f) return false
        if (!c.touching || c.speed() > dp(0.6f)) return false
        c.landed = true
        return true
    }

    /** Sorting: a pure function of progress, so it can't drift or re-settle. */
    fun sortFrame(c: Card, p: Float) {
        if (!c.captured) {
            c.syncPose()
            c.fromX = c.cx; c.fromY = c.cy; c.fromRot = c.rot; c.captured = true
        }
        if (c.index in Discarded) {
            val dir = if (c.index % 2 == 0) -1f else 1f
            val t = easeInCubic((p / 0.45f).coerceIn(0f, 1f))
            c.cx = c.fromX + dir * (widthPx * 0.9f + c.w) * t
            c.cy = c.fromY - dp(60f) * t
            c.rot = c.fromRot + dir * 0.45f * t
            c.alpha = 1f - smoothstep(0.2f, 0.45f, p)
        } else {
            val t = easeInOutCubic(p)
            val targetY = slotTop(c.index) + dp(SlotHeightDp) / 2f
            c.cx = c.fromX + (widthPx / 2f - c.fromX) * t
            c.cy = c.fromY + (targetY - c.fromY) * t
            c.rot = c.fromRot * (1f - t)
            c.alpha = 1f - smoothstep(0.55f, 0.9f, p)
        }
        c.place(c.cx, c.cy, c.rot)
    }

    /** Reduced motion: run the drop to rest off-screen, then show the result. */
    fun settleInstantly(cards: List<Card>) {
        val still = SensorInput()
        cards.forEach { it.active = true }
        repeat(360) { step(cards, STEP, still) }
        cards.forEach { it.syncPose(); it.landed = true }
    }
}

private fun rowIndex(index: Int) = index - Discarded.count { it < index }
private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
private fun easeInCubic(t: Float) = t * t * t
private fun easeOutCubic(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)
private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f
