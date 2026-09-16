package lk.salli.domain.motion

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PileSimulationTest {

    /** A phone-shaped stage: 1080 px wide, floor where the Act 1 headline starts. */
    private val bounds = PileBounds(left = 0f, right = 1080f, top = 0f, floor = 1500f)

    /** Nine bubbles, roughly the shape of the onboarding SMS cards at 3x density. */
    private val nineBubbles = List(9) { i ->
        BodySize(width = 300f + (i % 3) * 60f, height = 120f + (i % 2) * 24f)
    }

    private fun pile(seed: Long = PileSimulation.DEFAULT_SEED) =
        PileSimulation.spawn(nineBubbles, bounds, seed = seed)

    @Test
    fun `bodies settle above the floor`() {
        val sim = pile().settle()

        assertThat(sim.isAtRest).isTrue()
        sim.bodies.forEach { body ->
            assertThat(body.bottom).isAtMost(bounds.floor + TOLERANCE)
            // They actually fell, and a pile this size fits on the stage rather than
            // towering off the top of it.
            assertThat(body.topEdge).isGreaterThan(bounds.top)
            assertThat(body.resting).isTrue()
        }
    }

    @Test
    fun `settling lands on the same pile as watching it fall`() {
        val watched = pile()
        repeat(60 * 6) { watched.step(1f / 60f) }
        val settled = pile().settle(maxSeconds = 6f)

        assertThat(settled.isAtRest).isTrue()
        settled.bodies.forEachIndexed { i, body ->
            // settle() stops the moment the pile is quiet, so it can be a few pixels of creep
            // shy of where six full seconds would leave it. Same arrangement either way — which
            // is the point, because that is the layout reduced motion shows.
            assertThat(body.x).isWithin(SETTLE_CREEP).of(watched.bodies[i].x)
            assertThat(body.y).isWithin(SETTLE_CREEP).of(watched.bodies[i].y)
        }
    }

    @Test
    fun `settled bodies stack instead of sitting on top of each other`() {
        val sim = pile().settle()
        val bodies = sim.bodies

        for (i in bodies.indices) {
            for (j in i + 1 until bodies.size) {
                val a = bodies[i]
                val b = bodies[j]
                val overlapX = (a.halfExtentX + b.halfExtentX) - kotlin.math.abs(b.x - a.x)
                val overlapY = (a.halfExtentY + b.halfExtentY) - kotlin.math.abs(b.y - a.y)
                val fullyOverlapping = overlapX > 0f && overlapY > 0f &&
                    overlapX > minOf(a.size.width, b.size.width) * 0.9f &&
                    overlapY > minOf(a.size.height, b.size.height) * 0.9f
                assertThat(fullyOverlapping).isFalse()
            }
        }
    }

    @Test
    fun `bodies never leave the bounds while falling`() {
        val sim = pile()
        repeat(360) {
            sim.step(1f / 60f)
            sim.bodies.filter { it.visible }.forEach { body ->
                assertThat(body.left).isAtLeast(bounds.left - TOLERANCE)
                assertThat(body.right).isAtMost(bounds.right + TOLERANCE)
                assertThat(body.bottom).isAtMost(bounds.floor + TOLERANCE)
            }
        }
    }

    @Test
    fun `a furious fling still cannot leave the bounds`() {
        val sim = pile().settle()
        sim.fling(0, velocityX = 99_000f, velocityY = -99_000f)
        repeat(240) {
            sim.step(1f / 60f)
            sim.bodies.forEach { body ->
                assertThat(body.left).isAtLeast(bounds.left - TOLERANCE)
                assertThat(body.right).isAtMost(bounds.right + TOLERANCE)
                assertThat(body.bottom).isAtMost(bounds.floor + TOLERANCE)
            }
        }
    }

    @Test
    fun `a fling moves the body`() {
        val sim = pile().settle()
        val before = sim.bodies[0]

        sim.fling(0, velocityX = 1800f, velocityY = -2200f)
        // One frame is enough to see it go; the arc is the physics' problem, not this test's.
        sim.step(1f / 60f)
        val after = sim.bodies[0]

        assertThat(after.x).isGreaterThan(before.x)
        assertThat(after.y).isLessThan(before.y)
    }

    @Test
    fun `a held body follows the finger and ignores gravity`() {
        val sim = pile().settle()
        sim.grab(2)
        sim.dragTo(2, x = 540f, y = 300f)
        repeat(30) { sim.step(1f / 60f) }

        val held = sim.bodies[2]
        assertThat(held.held).isTrue()
        assertThat(held.x).isWithin(TOLERANCE).of(540f)
        assertThat(held.y).isWithin(TOLERANCE).of(300f)
    }

    @Test
    fun `hit testing finds the body under a point and nothing under empty space`() {
        val sim = pile().settle()
        val target = sim.bodies[4]

        assertThat(sim.bodyAt(target.x, target.y)).isNotNull()
        assertThat(sim.bodyAt(bounds.right + 500f, bounds.floor + 500f)).isNull()
    }

    @Test
    fun `the same seed produces the same pile`() {
        val a = pile(seed = 1234L)
        val b = pile(seed = 1234L)

        repeat(200) {
            a.step(1f / 60f)
            b.step(1f / 60f)
        }

        assertThat(b.bodies).isEqualTo(a.bodies)
    }

    @Test
    fun `different seeds produce different piles`() {
        val a = pile(seed = 1234L).settle()
        val b = pile(seed = 4321L).settle()

        assertThat(b.bodies).isNotEqualTo(a.bodies)
    }

    @Test
    fun `stepping in small slices matches stepping in one frame`() {
        val whole = pile()
        val sliced = pile()

        repeat(120) {
            whole.step(1f / 60f)
            sliced.step(1f / 120f)
            sliced.step(1f / 120f)
        }

        assertThat(sliced.bodies).isEqualTo(whole.bodies)
    }

    @Test
    fun `bodies drop in a stagger rather than all at once`() {
        val sim = pile()
        sim.step(0.1f)

        val visible = sim.bodies.count { it.visible }
        assertThat(visible).isEqualTo(1)
    }

    @Test
    fun `a body wider than the stage is centred rather than rattling between the walls`() {
        val narrow = PileBounds(left = 0f, right = 200f, top = 0f, floor = 600f)
        val sim = PileSimulation.spawn(listOf(BodySize(400f, 100f)), narrow).settle()

        assertThat(sim.bodies[0].x).isWithin(TOLERANCE).of(100f)
        // And it is on the floor, not hanging at the top of a bounce.
        assertThat(sim.bodies[0].bottom).isWithin(TOLERANCE).of(narrow.floor)
    }

    @Test
    fun `a body at the top of a bounce is not mistaken for a landed one`() {
        val sim = PileSimulation.spawn(
            sizes = listOf(BodySize(200f, 100f)),
            bounds = bounds,
            // No sideways drift, so the only thing that could look like rest is the apex of
            // each bounce — where vertical speed genuinely passes through zero.
            config = PileConfig(linearDrag = 0f),
        )
        repeat(90) { sim.step(1f / 60f) }
        sim.fling(0, velocityX = 0f, velocityY = -1600f)

        var restedInTheAir = false
        repeat(120) {
            sim.step(1f / 60f)
            val body = sim.bodies[0]
            if (body.resting && body.bottom < bounds.floor - 1f) restedInTheAir = true
        }
        assertThat(restedInTheAir).isFalse()
    }

    @Test
    fun `a settled body is genuinely still, not caught at an apex`() {
        val sim = pile().settle()
        sim.bodies.forEach { assertThat(it.speed).isLessThan(sim.config.restSpeed) }

        // Stepping on from a settled pile does not move it.
        val before = sim.bodies
        repeat(30) { sim.step(1f / 60f) }
        sim.bodies.forEachIndexed { i, body ->
            assertThat(body.y).isWithin(1f).of(before[i].y)
        }
    }

    private companion object {
        const val TOLERANCE = 0.5f

        /** A twentieth of a bubble — invisible, but float creep is real. */
        const val SETTLE_CREEP = 15f
    }
}
