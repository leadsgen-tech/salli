package lk.salli.domain.motion

import kotlin.math.abs

/** Where a body ends up once the sort has run. */
enum class SortFate {
    /** An OTP or a promo: flung off the side of the stage and forgotten. */
    DISCARDED,

    /** A real transaction: flies into the timeline column and becomes a row. */
    KEPT,
}

/**
 * One body as the choreography finds it: wherever the physics pile left it, plus the single
 * decision the caller has to make — is this message a transaction, or noise?
 */
data class SortSource(
    val index: Int,
    val x: Float,
    val y: Float,
    val rotationRadians: Float,
    val keep: Boolean,
)

/**
 * The timeline the kept bodies fly into. All in the caller's units (pixels on the stage),
 * `y` growing downward.
 *
 * @param exitLeftX x a discarded body has reached once it is safely off the left edge.
 * @param exitRightX the same on the right.
 */
data class SortColumn(
    val centerX: Float,
    val firstRowY: Float,
    val rowSpacing: Float,
    val exitLeftX: Float,
    val exitRightX: Float,
)

/**
 * Where one body should be drawn at a given sort progress, and how far through its
 * SMS-bubble-to-transaction-row crossfade it is.
 */
data class SortPlacement(
    val index: Int,
    val fate: SortFate,
    /** Position in the timeline column, or -1 for a discarded body. */
    val slot: Int,
    val x: Float,
    val y: Float,
    val rotationRadians: Float,
    val scale: Float,
    /** Whole-body opacity. Only discarded bodies fade. */
    val alpha: Float,
    /** 0 = still an SMS bubble, 1 = fully a transaction row. */
    val rowCrossfade: Float,
    /** True once this body has finished its move — the cue for a haptic. */
    val landed: Boolean,
)

/**
 * The Act 2 "scrub to sort" move, as pure arithmetic: given a progress from 0 to 1, say where
 * every body in the pile belongs.
 *
 * Progress bands come straight from section 11 of the design spec, and they overlap on purpose
 * so the whole thing reads as one gesture rather than three:
 *
 * ```
 * 0.0 -> 0.3   OTPs and promos are flung off the sides
 * 0.2 -> 0.8   the rest fly into a column, one after another, crossfading into rows
 * 0.5 -> 1.0   the stage colour turns from cobalt into the app background
 * ```
 *
 * Inside the 0.2–0.8 window each kept body gets its own slice, staggered by slot, so rows land
 * top to bottom instead of all at once. The first slot's slice starts at exactly 0.2 and the
 * last one's ends at exactly 0.8.
 *
 * Nothing here animates: the caller owns the progress value (a finger, or a spring after the
 * "Sort them" button) and asks this class what to draw. That also means the whole choreography
 * scrubs backwards, which is what makes the gesture feel like a real mechanism rather than a
 * video.
 */
class SortChoreography(
    bodies: List<SortSource>,
    private val column: SortColumn,
) {
    private val order: List<Int> = bodies.map { it.index }
    private val byIndex: Map<Int, SortSource> = bodies.associateBy { it.index }

    /** Kept bodies in slot order — the first of these gets slot 0, the top row. */
    val keptIndices: List<Int> = bodies.filter { it.keep }.map { it.index }

    /** Discarded bodies in the order they are thrown out. */
    val discardedIndices: List<Int> = bodies.filterNot { it.keep }.map { it.index }

    private val slotOf: Map<Int, Int> = keptIndices.withIndex().associate { (slot, i) -> i to slot }
    private val discardOrder: Map<Int, Int> = discardedIndices.withIndex().associate { (n, i) -> i to n }

    private val flySpan: Float =
        if (keptIndices.size <= 1) FLY_WINDOW else FLY_WINDOW * FLY_SPAN_SHARE

    private val flyStagger: Float =
        if (keptIndices.size <= 1) 0f else (FLY_WINDOW - flySpan) / (keptIndices.size - 1)

    /**
     * The progress window during which the body at [index] flies into its slot. Discarded
     * bodies report the discard window instead.
     */
    fun flyBand(index: Int): ClosedFloatingPointRange<Float> {
        val slot = slotOf[index] ?: return DISCARD_START..DISCARD_END
        val start = FLY_START + slot * flyStagger
        return start..(start + flySpan)
    }

    /** Where to draw [index] at this [progress], and whether it has landed. */
    fun placement(index: Int, progress: Float): SortPlacement {
        val source = requireNotNull(byIndex[index]) { "No body with index $index in this sort" }
        val p = progress.coerceIn(0f, 1f)
        return if (source.keep) keptPlacement(source, p) else discardedPlacement(source, p)
    }

    /** Every placement at once, in the order the caller supplied the bodies. */
    fun placements(progress: Float): List<SortPlacement> = order.map { placement(it, progress) }

    /**
     * How far the stage has turned from cobalt into the app background: 0 until halfway, 1 at
     * the end. Drives the background colour, the text colours and the status bar icons.
     */
    fun stageFraction(progress: Float): Float = band(progress, STAGE_START, STAGE_END)

    /**
     * Opacity of the "OTPs and promos: ignored." caption that appears under the pile while the
     * noise is being thrown out.
     */
    fun captionAlpha(progress: Float): Float = band(progress, CAPTION_START, CAPTION_END)

    private fun keptPlacement(source: SortSource, p: Float): SortPlacement {
        val slot = slotOf.getValue(source.index)
        val window = flyBand(source.index)
        val t = band(p, window.start, window.endInclusive)
        val eased = easeInOutCubic(t)

        val targetY = column.firstRowY + slot * column.rowSpacing
        // The crossfade happens in the back half of each body's own flight, so the row arrives
        // as the bubble arrives rather than mid-air.
        val crossfadeStart = window.start + (window.endInclusive - window.start) * CROSSFADE_AT

        return SortPlacement(
            index = source.index,
            fate = SortFate.KEPT,
            slot = slot,
            x = lerp(source.x, column.centerX, eased),
            y = lerp(source.y, targetY, eased),
            rotationRadians = lerp(source.rotationRadians, 0f, eased),
            scale = 1f,
            alpha = 1f,
            rowCrossfade = band(p, crossfadeStart, window.endInclusive),
            landed = t >= 1f,
        )
    }

    private fun discardedPlacement(source: SortSource, p: Float): SortPlacement {
        val nth = discardOrder.getValue(source.index)
        val t = band(p, DISCARD_START, DISCARD_END)
        // Alternate sides so the two bits of noise don't chase each other out the same edge.
        val toLeft = nth % 2 == 0
        val targetX = if (toLeft) column.exitLeftX else column.exitRightX
        // Fast off the mark, slowing as it leaves — a thrown thing, not a tweened one.
        val travel = targetX - source.x
        // A shallow arc: up a little on the way out, then away.
        val arc = -DISCARD_RISE * t + DISCARD_FALL * t * t

        return SortPlacement(
            index = source.index,
            fate = SortFate.DISCARDED,
            slot = -1,
            x = source.x + travel * easeOutQuad(t),
            y = source.y + arc * abs(travel),
            rotationRadians = source.rotationRadians + DISCARD_SPIN * t * (if (toLeft) -1f else 1f),
            scale = 1f - DISCARD_SHRINK * t,
            alpha = 1f - band(t, DISCARD_FADE_AT, 1f),
            rowCrossfade = 0f,
            landed = t >= 1f,
        )
    }

    companion object {
        /** Noise starts being thrown out the instant the gesture does. */
        const val DISCARD_START = 0f
        const val DISCARD_END = 0.3f

        /** The whole "fly into the column" window. Individual bodies get slices of it. */
        const val FLY_START = 0.2f
        const val FLY_END = 0.8f

        /** Cobalt stage turns into the app background over the back half. */
        const val STAGE_START = 0.5f
        const val STAGE_END = 1f

        /** The "OTPs and promos: ignored." caption fades in behind the discard. */
        const val CAPTION_START = 0.12f
        const val CAPTION_END = 0.3f

        private const val FLY_WINDOW = FLY_END - FLY_START

        /** How much of the fly window one body's flight occupies; the rest becomes stagger. */
        private const val FLY_SPAN_SHARE = 0.6f

        /** Point within a body's own flight where the SMS-to-row crossfade begins. */
        private const val CROSSFADE_AT = 0.5f

        private const val DISCARD_RISE = 0.22f
        private const val DISCARD_FALL = 0.55f
        private const val DISCARD_SPIN = 3.2f
        private const val DISCARD_SHRINK = 0.25f
        private const val DISCARD_FADE_AT = 0.75f

        /**
         * Normalises [progress] inside `[start, end]` to 0..1, clamped at both ends. The one
         * primitive everything above is built from, exposed because the stage composable wants
         * the same bands for its own colour work.
         */
        fun band(progress: Float, start: Float, end: Float): Float {
            if (end <= start) return if (progress >= end) 1f else 0f
            return ((progress - start) / (end - start)).coerceIn(0f, 1f)
        }

        fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

        /** Settles into place: slow, quick, slow. Used for the flight into a slot. */
        fun easeInOutCubic(t: Float): Float {
            val x = t.coerceIn(0f, 1f)
            if (x < 0.5f) return 4f * x * x * x
            val f = -2f * x + 2f
            return 1f - (f * f * f) / 2f
        }

        /** Fast off the mark then coasting — a thrown object with air drag. */
        fun easeOutQuad(t: Float): Float {
            val x = t.coerceIn(0f, 1f)
            return x * (2f - x)
        }
    }
}
