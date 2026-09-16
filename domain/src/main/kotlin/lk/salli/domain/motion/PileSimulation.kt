package lk.salli.domain.motion

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Width and height of one body, in whatever unit the caller works in. The onboarding stage
 * passes pixels (it has measured the SMS bubbles), the tests pass round numbers.
 */
data class BodySize(val width: Float, val height: Float) {
    val halfWidth: Float get() = width / 2f
    val halfHeight: Float get() = height / 2f
}

/**
 * The box the pile lives in. `y` grows downward, the way Compose lays things out: [floor] is
 * the line bodies land on (in Act 1 that is the top edge of the headline), [top] is only used
 * to decide how far above the stage bodies start their fall.
 */
data class PileBounds(
    val left: Float,
    val right: Float,
    val top: Float,
    val floor: Float,
) {
    val width: Float get() = right - left
}

/**
 * Tuning for [PileSimulation]. Defaults match section 11 Act 1 of the design spec: gravity
 * 2400 units/s², restitution 0.35, a mild shove between neighbours so nothing fully overlaps.
 */
data class PileConfig(
    /** Downward acceleration, units per second squared. */
    val gravity: Float = 2400f,
    /** How much downward speed survives a floor bounce. */
    val restitution: Float = 0.35f,
    /** How much sideways speed survives a wall bounce. */
    val wallRestitution: Float = 0.45f,
    /** Sideways speed kept per floor contact — stops bodies sliding forever. */
    val floorFriction: Float = 0.78f,
    /** Exponential air drag, per second. */
    val linearDrag: Float = 0.55f,
    /** Exponential spin drag, per second. */
    val spinDamping: Float = 2.2f,
    /** How hard overlapping bodies push each other apart, 0..1 per solver pass. */
    val repulsion: Float = 0.55f,
    /** Solver passes per fixed step. Two is enough for nine bodies. */
    val repulsionPasses: Int = 2,
    /** Speed below which a body counts as still. */
    val restSpeed: Float = 9f,
    /**
     * How many consecutive steps a body has to stay under [restSpeed] before it counts as
     * settled. A single frame is not enough: every bounce passes through zero vertical speed at
     * its apex, so an instantaneous check calls a body "landed" while it is still in the air.
     * Twelve steps is a tenth of a second at the default tick.
     */
    val restSteps: Int = 12,
    /** Speed cap, so a furious fling can't tunnel through a wall. */
    val maxSpeed: Float = 6000f,
    /** The physics tick. Frames are chopped into steps of exactly this length. */
    val fixedStep: Float = 1f / 120f,
    /** Longest frame the simulation will believe; anything slower is treated as this. */
    val maxFrame: Float = 1f / 15f,
)

/**
 * One body, frozen at a point in time. Immutable, so a Compose frame can hold on to the list
 * while the simulation keeps stepping.
 */
data class Body(
    val index: Int,
    val x: Float,
    val y: Float,
    val rotationRadians: Float,
    val size: BodySize,
    val velocityX: Float,
    val velocityY: Float,
    val spin: Float,
    /** False while the body is still waiting for its staggered drop. */
    val visible: Boolean,
    /** True once it has stopped moving enough to be considered part of the pile. */
    val resting: Boolean,
    /** True while a finger is holding it. */
    val held: Boolean,
) {
    /** Half-width of the body's axis-aligned bounding box, accounting for its rotation. */
    val halfExtentX: Float
        get() = abs(size.halfWidth * cos(rotationRadians)) + abs(size.halfHeight * sin(rotationRadians))

    /** Half-height of the body's axis-aligned bounding box, accounting for its rotation. */
    val halfExtentY: Float
        get() = abs(size.halfWidth * sin(rotationRadians)) + abs(size.halfHeight * cos(rotationRadians))

    val left: Float get() = x - halfExtentX
    val right: Float get() = x + halfExtentX
    val topEdge: Float get() = y - halfExtentY
    val bottom: Float get() = y + halfExtentY

    /** Cheap "is it moving" measure — Manhattan speed, no square root. */
    val speed: Float get() = abs(velocityX) + abs(velocityY)
}

/**
 * The physics behind Act 1 of onboarding: a handful of SMS bubbles fall from the top of the
 * stage, bounce off the floor and the gutters, shove each other aside and settle into a pile
 * you can poke at.
 *
 * It is deliberately small and dumb — nine rounded rectangles, no rotation dynamics beyond a
 * decaying spin, no proper contact manifolds. What it does have to be is *deterministic*: the
 * same seed and the same sequence of [step] calls always produce the same pile, so the
 * reduced-motion layout, the previews and the tests all agree with what the phone draws.
 *
 * Pure Kotlin on purpose — no Android types — so it can move to Kotlin Multiplatform with the
 * rest of `:domain`.
 *
 * Coordinates: `x` grows right, `y` grows *down*, matching Compose.
 */
class PileSimulation private constructor(
    val bounds: PileBounds,
    val config: PileConfig,
    private val state: Array<MutableBody>,
) {

    private var accumulator = 0f
    private var snapshot: List<Body>? = null

    /**
     * Below this downward speed a contact stops bouncing and simply stops. It has to clear the
     * speed one fixed step of gravity adds, otherwise a body sitting on the floor bounces
     * against its own weight forever and the pile never goes quiet.
     */
    private val sleepSpeed: Float =
        maxOf(config.restSpeed * 2f, config.gravity * config.fixedStep * 1.5f)

    /** Seconds of simulated time since the pile was spawned. */
    var elapsedSeconds: Float = 0f
        private set

    /** Current state of every body. Cached, so reading it repeatedly in one frame is free. */
    val bodies: List<Body>
        get() = snapshot ?: state.map { it.toBody() }.also { snapshot = it }

    /** How many bodies are in the pile. */
    val size: Int get() = state.size

    /** True once every body has appeared and has stayed still long enough to count as settled. */
    val isAtRest: Boolean
        get() = state.all { it.delay <= elapsedSeconds && !it.held && it.restSteps >= config.restSteps }

    /**
     * Advances the pile by [dtSeconds] of wall-clock time, in fixed sub-steps so the result
     * does not depend on the frame rate. Leftover time is carried to the next call. A frame
     * longer than [PileConfig.maxFrame] is clamped, so a stalled app doesn't launch the pile
     * into orbit when it resumes.
     */
    fun step(dtSeconds: Float) {
        if (dtSeconds <= 0f) return
        accumulator += min(dtSeconds, config.maxFrame)
        while (accumulator >= config.fixedStep) {
            integrate(config.fixedStep)
            accumulator -= config.fixedStep
        }
    }

    /**
     * Runs the pile forward until everything has settled, or [maxSeconds] of simulated time
     * has passed — whichever comes first. This is how the reduced-motion stage gets its static
     * layout: the same pile everyone else watches fall, just without the falling.
     */
    fun settle(maxSeconds: Float = 8f): PileSimulation {
        val deadline = elapsedSeconds + maxSeconds
        val settleAfter = longestDelay() + 0.25f
        while (elapsedSeconds < deadline) {
            integrate(config.fixedStep)
            if (elapsedSeconds > settleAfter && isAtRest) break
        }
        accumulator = 0f
        return this
    }

    /**
     * Index of the topmost body under ([x], [y]), or null if the point misses them all.
     * "Topmost" means last in the list, which is the one drawn on top.
     */
    fun bodyAt(x: Float, y: Float): Int? {
        for (i in state.indices.reversed()) {
            val b = state[i]
            if (b.delay > elapsedSeconds) continue
            if (b.contains(x, y)) return i
        }
        return null
    }

    /** Picks a body up. While held it ignores gravity and follows [dragTo]. */
    fun grab(index: Int) {
        val b = state[index]
        b.held = true
        b.vx = 0f
        b.vy = 0f
        b.delay = 0f
        b.restSteps = 0
        snapshot = null
    }

    /** Moves a held body. No-op for a body nobody is holding. */
    fun dragTo(index: Int, x: Float, y: Float) {
        val b = state[index]
        if (!b.held) return
        b.x = x
        b.y = y
        snapshot = null
    }

    /** Lets go of a held body, handing it the finger's parting velocity. */
    fun release(index: Int, velocityX: Float, velocityY: Float) {
        state[index].held = false
        fling(index, velocityX, velocityY)
    }

    /** Throws a body. Used by the drag gesture on release, and by tests. */
    fun fling(index: Int, velocityX: Float, velocityY: Float) {
        val b = state[index]
        b.delay = 0f
        b.restSteps = 0
        b.vx = clampSpeed(velocityX)
        b.vy = clampSpeed(velocityY)
        b.spin += velocityX * SPIN_PER_FLING
        snapshot = null
    }

    private fun longestDelay(): Float = state.maxOfOrNull { it.delay } ?: 0f

    private fun integrate(dt: Float) {
        elapsedSeconds += dt
        val drag = exp(-config.linearDrag * dt)
        val spinDrag = exp(-config.spinDamping * dt)

        for (b in state) {
            if (b.held || b.delay > elapsedSeconds) continue
            b.vy += config.gravity * dt
            b.vx = clampSpeed(b.vx * drag)
            b.vy = clampSpeed(b.vy * drag)
            b.x += b.vx * dt
            b.y += b.vy * dt
            b.rotation += b.spin * dt
            b.spin *= spinDrag
        }

        repeat(config.repulsionPasses) { separate() }

        for (b in state) {
            if (b.delay > elapsedSeconds) {
                b.restSteps = 0
                continue
            }
            clampToBounds(b)
            // Sustained stillness, not a single quiet frame — see PileConfig.restSteps.
            if (!b.held && b.speed() < config.restSpeed) b.restSteps++ else b.restSteps = 0
        }
        snapshot = null
    }

    /**
     * Mild position-based separation: for every overlapping pair, push both bodies apart along
     * whichever axis they overlap least, and kill the closing velocity on that axis. Iterating
     * in index order keeps it deterministic.
     */
    private fun separate() {
        for (i in state.indices) {
            val a = state[i]
            if (a.delay > elapsedSeconds) continue
            for (j in i + 1 until state.size) {
                val b = state[j]
                if (b.delay > elapsedSeconds) continue

                val dx = b.x - a.x
                val dy = b.y - a.y
                val overlapX = (a.extentX() + b.extentX()) - abs(dx)
                if (overlapX <= 0f) continue
                val overlapY = (a.extentY() + b.extentY()) - abs(dy)
                if (overlapY <= 0f) continue

                // A held body is immovable; its partner takes the whole correction.
                val aShare = if (a.held) 0f else if (b.held) 1f else 0.5f
                val bShare = if (b.held) 0f else if (a.held) 1f else 0.5f

                if (overlapX < overlapY) {
                    val push = overlapX * config.repulsion
                    val sign = if (dx < 0f) -1f else 1f
                    a.x -= push * aShare * sign
                    b.x += push * bShare * sign
                    val closing = (b.vx - a.vx) * sign
                    if (closing < 0f) {
                        val exchange = closing * 0.5f * (1f + config.restitution)
                        if (!a.held) a.vx += exchange * sign
                        if (!b.held) b.vx -= exchange * sign
                    }
                } else {
                    val push = overlapY * config.repulsion
                    val sign = if (dy < 0f) -1f else 1f
                    a.y -= push * aShare * sign
                    b.y += push * bShare * sign
                    // Whichever body got pushed upward is resting on the other one: stop it
                    // falling, otherwise gravity keeps it awake forever and nothing settles.
                    val upper = if (sign > 0f) a else b
                    val lower = if (sign > 0f) b else a
                    if (!upper.held && upper.vy > 0f) {
                        upper.vy = if (upper.vy > sleepSpeed) -upper.vy * config.restitution else 0f
                    }
                    if (!lower.held && lower.vy < 0f) lower.vy = 0f
                    upper.spin *= config.floorFriction
                }
            }
        }
    }

    private fun clampToBounds(b: MutableBody) {
        val ex = b.extentX()
        val ey = b.extentY()

        // A body wider than the stage would oscillate between the two side clamps forever.
        if (ex * 2f >= bounds.width) {
            b.x = (bounds.left + bounds.right) / 2f
            if (!b.held) b.vx = 0f
        } else if (b.x - ex < bounds.left) {
            b.x = bounds.left + ex
            if (b.vx < 0f && !b.held) b.vx = -b.vx * config.wallRestitution
        } else if (b.x + ex > bounds.right) {
            b.x = bounds.right - ex
            if (b.vx > 0f && !b.held) b.vx = -b.vx * config.wallRestitution
        }

        if (b.y + ey > bounds.floor) {
            b.y = bounds.floor - ey
            if (!b.held) {
                if (b.vy > 0f) {
                    b.vy = if (b.vy > sleepSpeed) -b.vy * config.restitution else 0f
                }
                b.vx *= config.floorFriction
                b.spin *= config.floorFriction
            }
        }
    }

    private fun clampSpeed(v: Float): Float = v.coerceIn(-config.maxSpeed, config.maxSpeed)

    private fun MutableBody.extentX(): Float =
        abs(size.halfWidth * cos(rotation)) + abs(size.halfHeight * sin(rotation))

    private fun MutableBody.extentY(): Float =
        abs(size.halfWidth * sin(rotation)) + abs(size.halfHeight * cos(rotation))

    private fun MutableBody.contains(px: Float, py: Float): Boolean {
        val c = cos(-rotation)
        val s = sin(-rotation)
        val dx = px - x
        val dy = py - y
        val localX = dx * c - dy * s
        val localY = dx * s + dy * c
        return abs(localX) <= size.halfWidth && abs(localY) <= size.halfHeight
    }

    private fun MutableBody.speed(): Float = abs(vx) + abs(vy)

    private fun MutableBody.toBody(): Body = Body(
        index = index,
        x = x,
        y = y,
        rotationRadians = rotation,
        size = size,
        velocityX = vx,
        velocityY = vy,
        spin = spin,
        visible = delay <= elapsedSeconds,
        resting = !held && restSteps >= config.restSteps,
        held = held,
    )

    internal class MutableBody(
        val index: Int,
        val size: BodySize,
        var x: Float,
        var y: Float,
        var rotation: Float,
        var vx: Float,
        var vy: Float,
        var spin: Float,
        var delay: Float,
        var held: Boolean = false,
        var restSteps: Int = 0,
    )

    companion object {
        /** The seed the onboarding stage uses, so every run drops the same pile. */
        const val DEFAULT_SEED: Long = 0x5A111L

        /** How much of a fling's horizontal speed turns into spin. */
        private const val SPIN_PER_FLING = 0.0012f

        /**
         * Builds a pile of bodies waiting to fall in from above [PileBounds.top], one per entry
         * in [sizes]. Start positions, tilt and spin come from [seed], so the same seed always
         * produces the same drop — which is what lets the reduced-motion stage show the settled
         * version of the animation everyone else watches.
         *
         * @param staggerSeconds gap between each body's drop. Section 11 asks for 180 ms.
         */
        fun spawn(
            sizes: List<BodySize>,
            bounds: PileBounds,
            seed: Long = DEFAULT_SEED,
            config: PileConfig = PileConfig(),
            staggerSeconds: Float = 0.18f,
        ): PileSimulation {
            val random = XorShift(seed)
            val bodies = Array(sizes.size) { i ->
                val size = sizes[i]
                val halfSpan = (size.width / 2f).coerceAtMost(bounds.width / 2f)
                val minX = bounds.left + halfSpan
                val maxX = bounds.right - halfSpan
                val x = if (maxX <= minX) (bounds.left + bounds.right) / 2f else random.between(minX, maxX)
                MutableBody(
                    index = i,
                    size = size,
                    x = x,
                    // Started above the stage so each one enters separately, not in a clump.
                    y = bounds.top - size.height - random.between(0f, size.height),
                    rotation = random.between(-0.18f, 0.18f),
                    vx = random.between(-60f, 60f),
                    vy = random.between(0f, 120f),
                    spin = random.between(-1.1f, 1.1f),
                    delay = i * staggerSeconds,
                )
            }
            return PileSimulation(bounds, config, bodies)
        }
    }
}

/**
 * Tiny xorshift64 PRNG. Hand-rolled rather than `java.util.Random` because `:domain` carries no
 * JVM types — and because the exact stream has to stay stable across platforms for the pile to
 * look the same everywhere.
 */
internal class XorShift(seed: Long) {
    private var state: Long = if (seed == 0L) -0x61c8864680b583ebL else seed

    /** Next value in `[0, 1)`. */
    fun nextFloat(): Float {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        state = x
        return ((x ushr 40).toInt() and 0xFFFFFF) / 16_777_216f
    }

    fun between(min: Float, max: Float): Float = min + (max - min) * nextFloat()
}
