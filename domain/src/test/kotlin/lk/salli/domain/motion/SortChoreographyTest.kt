package lk.salli.domain.motion

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SortChoreographyTest {

    private val column = SortColumn(
        centerX = 540f,
        firstRowY = 400f,
        rowSpacing = 200f,
        exitLeftX = -600f,
        exitRightX = 1680f,
    )

    /** Nine bodies as Act 1 leaves them: seven transactions plus an OTP and a promo. */
    private fun ninePile(): List<SortSource> = List(9) { i ->
        SortSource(
            index = i,
            x = 200f + i * 60f,
            y = 900f + (i % 3) * 40f,
            rotationRadians = 0.1f * (if (i % 2 == 0) 1f else -1f),
            keep = i != 2 && i != 5,
        )
    }

    private fun choreography(sources: List<SortSource> = ninePile()) = SortChoreography(sources, column)

    // --- band edges -------------------------------------------------------------------

    @Test
    fun `band clamps at both ends and is linear between them`() {
        assertThat(SortChoreography.band(0.1f, 0.2f, 0.8f)).isEqualTo(0f)
        assertThat(SortChoreography.band(0.2f, 0.2f, 0.8f)).isEqualTo(0f)
        assertThat(SortChoreography.band(0.5f, 0.2f, 0.8f)).isWithin(EPS).of(0.5f)
        assertThat(SortChoreography.band(0.8f, 0.2f, 0.8f)).isEqualTo(1f)
        assertThat(SortChoreography.band(0.9f, 0.2f, 0.8f)).isEqualTo(1f)
    }

    @Test
    fun `stage colour holds until halfway then finishes at the end`() {
        val c = choreography()
        assertThat(c.stageFraction(0f)).isEqualTo(0f)
        assertThat(c.stageFraction(0.5f)).isEqualTo(0f)
        assertThat(c.stageFraction(0.75f)).isWithin(EPS).of(0.5f)
        assertThat(c.stageFraction(1f)).isEqualTo(1f)
    }

    @Test
    fun `the noise caption fades in behind the discard and is up by the time it ends`() {
        val c = choreography()
        assertThat(c.captionAlpha(0f)).isEqualTo(0f)
        assertThat(c.captionAlpha(SortChoreography.CAPTION_START)).isEqualTo(0f)
        assertThat(c.captionAlpha(SortChoreography.DISCARD_END)).isEqualTo(1f)
    }

    @Test
    fun `the first slot starts exactly at the fly window and the last ends exactly at its close`() {
        val c = choreography()
        val first = c.flyBand(c.keptIndices.first())
        val last = c.flyBand(c.keptIndices.last())

        assertThat(first.start).isWithin(EPS).of(SortChoreography.FLY_START)
        assertThat(last.endInclusive).isWithin(EPS).of(SortChoreography.FLY_END)
    }

    @Test
    fun `fly bands overlap so rows land one after another rather than in a batch`() {
        val c = choreography()
        val bands = c.keptIndices.map { c.flyBand(it) }

        bands.zipWithNext { a, b ->
            assertThat(b.start).isGreaterThan(a.start)
            assertThat(b.start).isLessThan(a.endInclusive)
        }
    }

    @Test
    fun `a lone kept body gets the whole fly window`() {
        val c = choreography(listOf(SortSource(0, 100f, 100f, 0f, keep = true)))
        val band = c.flyBand(0)

        assertThat(band.start).isWithin(EPS).of(SortChoreography.FLY_START)
        assertThat(band.endInclusive).isWithin(EPS).of(SortChoreography.FLY_END)
    }

    // --- kept bodies ------------------------------------------------------------------

    @Test
    fun `kept bodies split into slots in the order they were supplied`() {
        val c = choreography()
        assertThat(c.keptIndices).containsExactly(0, 1, 3, 4, 6, 7, 8).inOrder()
        assertThat(c.discardedIndices).containsExactly(2, 5).inOrder()
    }

    @Test
    fun `at zero progress a kept body is still where the pile left it`() {
        val c = choreography()
        val source = ninePile().first { it.keep }
        val p = c.placement(source.index, 0f)

        assertThat(p.fate).isEqualTo(SortFate.KEPT)
        assertThat(p.x).isWithin(EPS).of(source.x)
        assertThat(p.y).isWithin(EPS).of(source.y)
        assertThat(p.rowCrossfade).isEqualTo(0f)
        assertThat(p.landed).isFalse()
    }

    @Test
    fun `at full progress every kept body sits square in its slot as a row`() {
        val c = choreography()
        c.keptIndices.forEachIndexed { slot, index ->
            val p = c.placement(index, 1f)
            assertThat(p.slot).isEqualTo(slot)
            assertThat(p.x).isWithin(EPS).of(column.centerX)
            assertThat(p.y).isWithin(EPS).of(column.firstRowY + slot * column.rowSpacing)
            assertThat(p.rotationRadians).isWithin(EPS).of(0f)
            assertThat(p.rowCrossfade).isEqualTo(1f)
            assertThat(p.alpha).isEqualTo(1f)
            assertThat(p.landed).isTrue()
        }
    }

    @Test
    fun `the top row lands before the bottom one`() {
        val c = choreography()
        val topBand = c.flyBand(c.keptIndices.first())
        val bottom = c.keptIndices.last()

        // The moment the top row is home, the bottom one is still in the air.
        assertThat(c.placement(c.keptIndices.first(), topBand.endInclusive).landed).isTrue()
        assertThat(c.placement(bottom, topBand.endInclusive).landed).isFalse()
    }

    @Test
    fun `the crossfade to a row only starts halfway through that body's own flight`() {
        val c = choreography()
        val index = c.keptIndices.first()
        val band = c.flyBand(index)
        val midpoint = band.start + (band.endInclusive - band.start) / 2f

        assertThat(c.placement(index, midpoint).rowCrossfade).isWithin(EPS).of(0f)
        assertThat(c.placement(index, band.endInclusive).rowCrossfade).isEqualTo(1f)
    }

    @Test
    fun `a kept body does not move before its slice of the window opens`() {
        val c = choreography()
        val last = c.keptIndices.last()
        val source = ninePile().first { it.index == last }
        val band = c.flyBand(last)

        val p = c.placement(last, band.start)
        assertThat(p.x).isWithin(EPS).of(source.x)
        assertThat(p.y).isWithin(EPS).of(source.y)
    }

    // --- discarded bodies -------------------------------------------------------------

    @Test
    fun `discarded bodies are gone by the end of the discard window`() {
        val c = choreography()
        c.discardedIndices.forEach { index ->
            val p = c.placement(index, SortChoreography.DISCARD_END)
            assertThat(p.fate).isEqualTo(SortFate.DISCARDED)
            assertThat(p.slot).isEqualTo(-1)
            assertThat(p.alpha).isEqualTo(0f)
            assertThat(p.landed).isTrue()
            val offStage = p.x <= column.exitLeftX + EPS || p.x >= column.exitRightX - EPS
            assertThat(offStage).isTrue()
        }
    }

    @Test
    fun `discarded bodies leave by opposite sides`() {
        val c = choreography()
        val (first, second) = c.discardedIndices
        val a = c.placement(first, SortChoreography.DISCARD_END)
        val b = c.placement(second, SortChoreography.DISCARD_END)

        assertThat(a.x).isLessThan(column.exitRightX)
        assertThat(b.x).isGreaterThan(column.exitLeftX)
        assertThat(a.x < b.x).isTrue()
    }

    @Test
    fun `discarded bodies stay put at zero and never become rows`() {
        val c = choreography()
        val index = c.discardedIndices.first()
        val source = ninePile().first { it.index == index }
        val p = c.placement(index, 0f)

        assertThat(p.x).isWithin(EPS).of(source.x)
        assertThat(p.alpha).isEqualTo(1f)
        assertThat(c.placement(index, 1f).rowCrossfade).isEqualTo(0f)
    }

    // --- misc -------------------------------------------------------------------------

    @Test
    fun `placements come back in the order the bodies were supplied`() {
        val c = choreography()
        assertThat(c.placements(0.5f).map { it.index })
            .containsExactlyElementsIn(ninePile().map { it.index })
            .inOrder()
    }

    @Test
    fun `progress outside zero to one is clamped rather than extrapolated`() {
        val c = choreography()
        val index = c.keptIndices.first()
        assertThat(c.placement(index, -3f)).isEqualTo(c.placement(index, 0f))
        assertThat(c.placement(index, 4f)).isEqualTo(c.placement(index, 1f))
    }

    @Test
    fun `a pile with nothing worth keeping still works`() {
        val c = choreography(
            listOf(
                SortSource(0, 100f, 100f, 0f, keep = false),
                SortSource(1, 200f, 100f, 0f, keep = false),
            ),
        )
        assertThat(c.keptIndices).isEmpty()
        assertThat(c.placements(1f).map { it.fate }).containsExactly(SortFate.DISCARDED, SortFate.DISCARDED)
    }

    @Test
    fun `easings hit their endpoints`() {
        assertThat(SortChoreography.easeInOutCubic(0f)).isWithin(EPS).of(0f)
        assertThat(SortChoreography.easeInOutCubic(1f)).isWithin(EPS).of(1f)
        assertThat(SortChoreography.easeOutQuad(0f)).isWithin(EPS).of(0f)
        assertThat(SortChoreography.easeOutQuad(1f)).isWithin(EPS).of(1f)
        // A throw covers ground early; a settle does not.
        assertThat(SortChoreography.easeOutQuad(0.25f)).isGreaterThan(0.25f)
        assertThat(SortChoreography.easeInOutCubic(0.25f)).isLessThan(0.25f)
    }

    private companion object {
        const val EPS = 1e-4f
    }
}
