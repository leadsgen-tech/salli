package lk.salli.design.components.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.launch
import lk.salli.design.theme.SalliTheme
import lk.salli.domain.motion.Body
import lk.salli.domain.motion.BodySize
import lk.salli.domain.motion.PileBounds
import lk.salli.domain.motion.PileSimulation
import lk.salli.domain.motion.SortChoreography
import lk.salli.domain.motion.SortColumn
import lk.salli.domain.motion.SortFate
import lk.salli.domain.motion.SortPlacement
import lk.salli.domain.motion.SortSource

/**
 * The onboarding stage for Acts 1 and 2: a pile of SMS bubbles that falls in, can be poked at,
 * and then sorts itself into a timeline while you drag.
 *
 * It owns the *movement* only. What a bubble looks like, and what the row it turns into looks
 * like, are slots — so this file knows nothing about transactions, banks or the design kit, and
 * the two waves can be built in parallel.
 *
 * - **Act 1** ([sortProgress] at 0): [PileSimulation] runs on `withFrameNanos`; bodies fall,
 *   bounce, shove each other and settle above [floorFraction] of the stage height. Dragging a
 *   bubble picks it up, letting go throws it. [onBodyLanded] fires once per bubble as it stops,
 *   which is where the light tap belongs.
 * - **Act 2** ([sortProgress] moving 0 to 1): the physics freezes and [SortChoreography] takes
 *   over. Bodies whose index is in [discarded] are flung off the sides; the rest fly into a
 *   column and crossfade from [bubble] to [row]. [onDiscarded] and [onRowLanded] fire once each
 *   as those moves finish.
 *
 * The caller owns [sortProgress]. Drive it from [scrubToSort] for the drag path, or from an
 * `Animatable` for the "Sort them" button — both land in the same place, which is the whole
 * point of the gesture.
 *
 * With [reducedMotion] the pile skips straight to where it would have settled, drag is off, and
 * the sort still works — it just follows whatever the caller does with [sortProgress], so snap
 * it rather than animating it.
 *
 * Positions are recomputed in the layer phase rather than by recomposing, so the slot content
 * composes once and then only its transform changes as the pile moves.
 *
 * @param bodySizes one entry per bubble, in the order they drop. Keep the total area well under
 *   the stage or the pile will stack up past the top edge.
 * @param discarded indices of the bubbles that are noise — the OTP and the promo in section 11.
 * @param floorFraction where the pile lands, as a fraction of the stage height. In Act 1 that is
 *   the top of the headline.
 * @param seed fixes the drop. The same seed always produces the same pile.
 */
@Composable
fun BubbleStage(
    bodySizes: List<DpSize>,
    sortProgress: Float,
    modifier: Modifier = Modifier,
    discarded: Set<Int> = emptySet(),
    reducedMotion: Boolean = false,
    seed: Long = PileSimulation.DEFAULT_SEED,
    floorFraction: Float = 0.74f,
    rowHeight: Dp = 64.dp,
    rowGap: Dp = 8.dp,
    rowGutter: Dp = 20.dp,
    columnTop: Dp = 24.dp,
    onBodyLanded: (Int) -> Unit = {},
    onDiscarded: (Int) -> Unit = {},
    onRowLanded: (Int) -> Unit = {},
    bubble: @Composable (index: Int) -> Unit,
    row: @Composable (index: Int) -> Unit,
) {
    val progress = sortProgress.coerceIn(0f, 1f)
    val sorting = progress > 0f

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        // An unbounded constraint is Int.MAX_VALUE, and a two-billion-pixel stage would scatter
        // the pile somewhere off in the next county. Fall back to a phone-shaped stage instead.
        val widthPx = if (constraints.hasBoundedWidth) {
            constraints.maxWidth.toFloat()
        } else {
            with(density) { FALLBACK_STAGE_WIDTH.toPx() }
        }
        val heightPx = if (constraints.hasBoundedHeight) {
            constraints.maxHeight.toFloat()
        } else {
            with(density) { FALLBACK_STAGE_HEIGHT.toPx() }
        }

        val sizesPx = remember(bodySizes, density) {
            bodySizes.map { with(density) { BodySize(it.width.toPx(), it.height.toPx()) } }
        }
        val bounds = remember(widthPx, heightPx, floorFraction) {
            PileBounds(left = 0f, right = widthPx, top = 0f, floor = heightPx * floorFraction)
        }
        val column = remember(widthPx, density, rowHeight, rowGap, columnTop, sizesPx) {
            with(density) {
                val widest = sizesPx.maxOfOrNull { it.width } ?: 0f
                SortColumn(
                    centerX = widthPx / 2f,
                    firstRowY = columnTop.toPx() + rowHeight.toPx() / 2f,
                    rowSpacing = rowHeight.toPx() + rowGap.toPx(),
                    exitLeftX = -widest,
                    exitRightX = widthPx + widest,
                )
            }
        }

        val simulation = remember(sizesPx, bounds, seed, reducedMotion) {
            PileSimulation.spawn(sizesPx, bounds, seed = seed)
                .also { if (reducedMotion) it.settle() }
        }

        // Written by the frame loop, read only inside graphicsLayer blocks, so a moving pile
        // never invalidates the composition of the bubbles and rows sitting on it.
        val pile = remember(simulation) { mutableStateOf(simulation.bodies) }
        val choreography = remember(simulation) {
            mutableStateOf(choreographyOf(simulation.bodies, discarded, column))
        }
        val progressHolder = remember { mutableFloatStateOf(progress) }
        SideEffect { progressHolder.floatValue = progress }

        // Bumped when a finger picks a bubble up, which restarts the frame loop after it has
        // parked itself on a settled pile.
        val wake = remember(simulation) { mutableIntStateOf(0) }

        val landedNotifier = rememberLandedNotifier(
            bodyCount = bodySizes.size,
            onBodyLanded = onBodyLanded,
            onDiscarded = onDiscarded,
            onRowLanded = onRowLanded,
        )

        LaunchedEffect(simulation, reducedMotion, sorting, discarded, column, wake.intValue) {
            if (sorting) {
                // The physics stops where the finger picked it up; that pile is the sort's
                // starting position, and it stays frozen for the whole of Act 2.
                choreography.value = choreographyOf(pile.value, discarded, column)
                return@LaunchedEffect
            }
            if (reducedMotion) {
                pile.value = simulation.bodies
                choreography.value = choreographyOf(simulation.bodies, discarded, column)
                // Nothing fell, so nothing landed — no nine taps in a row.
                landedNotifier.suppressSettled()
                return@LaunchedEffect
            }
            var last = 0L
            while (true) {
                val now = withFrameNanos { it }
                if (last != 0L) {
                    simulation.step((now - last).coerceAtLeast(0L) / NANOS_PER_SECOND)
                }
                last = now
                val bodies = simulation.bodies
                pile.value = bodies
                // Kept in step with the pile so the first frame of Act 2 starts from where the
                // bubbles actually are, rather than from where they were spawned.
                choreography.value = choreographyOf(bodies, discarded, column)
                bodies.forEach { if (it.visible && it.resting) landedNotifier.onSettled(it.index) }
                // A settled pile does not need a frame callback. A drag bumps `wake`, which
                // restarts this effect.
                if (simulation.isAtRest) break
            }
        }

        // Fires the discard and row-landed taps as the sort passes each body's band end.
        SideEffect {
            if (!sorting) {
                landedNotifier.resetSorted()
                return@SideEffect
            }
            val sort = choreography.value
            for (index in bodySizes.indices) {
                val placement = sort.placement(index, progress)
                landedNotifier.onSorted(index, placement.fate, placement.landed)
            }
        }

        // Captured out here because the inner Box's scope hides BoxWithConstraints'.
        val rowWidth = (maxWidth - rowGutter * 2).coerceAtLeast(0.dp)

        val stage = Modifier
            .fillMaxSize()
            .then(
                if (reducedMotion || sorting) {
                    Modifier
                } else {
                    Modifier.pileDragging(simulation) { wake.intValue++ }
                },
            )

        Box(stage) {
            bodySizes.forEachIndexed { index, bodySize ->
                key(index) {
                    val placement = {
                        placementFor(
                            index = index,
                            pile = pile.value,
                            sort = choreography.value,
                            progress = progressHolder.floatValue,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(bodySize)
                            .graphicsLayer {
                                val p = placement() ?: run {
                                    alpha = 0f
                                    return@graphicsLayer
                                }
                                translationX = p.x - size.width / 2f
                                translationY = p.y - size.height / 2f
                                rotationZ = p.rotationRadians * DEGREES_PER_RADIAN
                                scaleX = p.scale
                                scaleY = p.scale
                                alpha = p.alpha * (1f - p.rowCrossfade)
                            },
                    ) {
                        bubble(index)
                    }
                }
            }

            // Rows only exist once the sort is under way, and never for the noise.
            if (sorting) {
                bodySizes.indices.forEach { index ->
                    if (index in discarded) return@forEach
                    key(index) {
                        val placement = {
                            placementFor(
                                index = index,
                                pile = pile.value,
                                sort = choreography.value,
                                progress = progressHolder.floatValue,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    val p = placement() ?: run {
                                        alpha = 0f
                                        return@graphicsLayer
                                    }
                                    translationX = p.x - size.width / 2f
                                    translationY = p.y - size.height / 2f
                                    alpha = p.rowCrossfade
                                }
                                .width(rowWidth)
                                .height(rowHeight),
                        ) {
                            row(index)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Drag up anywhere to run the sort, let go to see whether it takes.
 *
 * Progress follows the finger over [travel] of upward movement. Release past [completeAt] and it
 * springs the rest of the way; release short and it springs back to the pile, so a half-hearted
 * swipe costs nothing. [onProgress] is called with every value in between, which is what
 * [BubbleStage] wants for `sortProgress`.
 *
 * The modifier keeps its own progress. If the "Sort them" button drives the same stage, let the
 * button write into the same state this feeds, so both paths agree on where the sort is.
 *
 * Touches that land on a bubble are the pile's, not the scrub's: [BubbleStage] only consumes a
 * drag that starts on a body, so put this on a parent of the stage and both gestures coexist.
 */
@Composable
fun Modifier.scrubToSort(
    onProgress: (Float) -> Unit,
    enabled: Boolean = true,
    reducedMotion: Boolean = false,
    travel: Dp = ScrubDefaults.Travel,
    completeAt: Float = ScrubDefaults.CompleteAt,
    onCompleted: () -> Unit = {},
): Modifier {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val travelPx = with(LocalDensity.current) { travel.toPx() }.coerceAtLeast(1f)
    val latestProgress by rememberUpdatedState(onProgress)
    val latestCompleted by rememberUpdatedState(onCompleted)
    // The finger's own running total. `snapTo` is dispatched to the next frame, so several
    // touch samples can land inside one frame; reading `progress.value` back each time would
    // compute every one of them from the same stale number and throw all but the last away.
    val scrubbed = remember { FloatHolder(0f) }

    LaunchedEffect(progress) {
        snapshotFlow { progress.value }.collect { latestProgress(it) }
    }

    if (!enabled) return this

    return this.pointerInput(travelPx, completeAt, reducedMotion) {
        detectVerticalDragGestures(
            onDragStart = { scrubbed.value = progress.value },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                // Upward is negative in Compose, and upward is what sorts the pile.
                val next = (scrubbed.value - dragAmount / travelPx).coerceIn(0f, 1f)
                scrubbed.value = next
                scope.launch { progress.snapTo(next) }
            },
            onDragEnd = {
                scope.launch {
                    if (scrubbed.value >= completeAt) {
                        if (reducedMotion) progress.snapTo(1f) else progress.animateTo(1f, ScrubDefaults.Settle)
                        latestCompleted()
                    } else {
                        if (reducedMotion) progress.snapTo(0f) else progress.animateTo(0f, ScrubDefaults.SpringBack)
                    }
                }
            },
            onDragCancel = {
                scrubbed.value = 0f
                scope.launch {
                    if (reducedMotion) progress.snapTo(0f) else progress.animateTo(0f, ScrubDefaults.SpringBack)
                }
            },
        )
    }
}

/** Tuning for [scrubToSort], exposed so a screen can match its own affordances to the gesture. */
object ScrubDefaults {
    /** Upward distance that takes the sort from nothing to done. */
    val Travel: Dp = 220.dp

    /** Release above this and the sort completes itself. */
    const val CompleteAt: Float = 0.4f

    /**
     * Stand-ins for `MotionScheme.expressive()`'s spatial specs, which need material3 1.4.
     * Swap them for the real scheme once the toolchain bump lands.
     */
    val Settle: AnimationSpec<Float> =
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

    val SpringBack: AnimationSpec<Float> =
        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
}

// --- internals ------------------------------------------------------------------------------

private const val DEGREES_PER_RADIAN = 57.29578f
private const val NANOS_PER_SECOND = 1_000_000_000f
private val FALLBACK_STAGE_HEIGHT = 640.dp
private val FALLBACK_STAGE_WIDTH = 360.dp

/** A plain mutable float that survives recomposition without being snapshot state. */
private class FloatHolder(var value: Float)

private fun choreographyOf(
    bodies: List<Body>,
    discarded: Set<Int>,
    column: SortColumn,
): SortChoreography = SortChoreography(
    bodies.map {
        SortSource(
            index = it.index,
            x = it.x,
            y = it.y,
            rotationRadians = it.rotationRadians,
            keep = it.index !in discarded,
        )
    },
    column,
)

/** Physics position below a progress of zero, choreographed position above it. */
private fun placementFor(
    index: Int,
    pile: List<Body>,
    sort: SortChoreography,
    progress: Float,
): SortPlacement? {
    if (progress > 0f) return sort.placement(index, progress)
    val body = pile.getOrNull(index) ?: return null
    if (!body.visible) return null
    return SortPlacement(
        index = index,
        fate = SortFate.KEPT,
        slot = -1,
        x = body.x,
        y = body.y,
        rotationRadians = body.rotationRadians,
        scale = 1f,
        alpha = 1f,
        rowCrossfade = 0f,
        landed = body.resting,
    )
}

/**
 * Picks a bubble up on touch-down, follows the finger, and throws it on release. A touch that
 * misses every bubble is left unconsumed so a parent's [scrubToSort] still sees it.
 *
 * [onWake] is called as soon as a bubble is picked up, so the stage can restart a frame loop
 * that had parked itself on a settled pile.
 */
private fun Modifier.pileDragging(simulation: PileSimulation, onWake: () -> Unit): Modifier =
    pointerInput(simulation) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val index = simulation.bodyAt(down.position.x, down.position.y)
                ?: return@awaitEachGesture

            val started = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                ?: return@awaitEachGesture

            val tracker = VelocityTracker()
            simulation.grab(index)
            onWake()
            tracker.addPosition(started.uptimeMillis, started.position)
            simulation.dragTo(index, started.position.x, started.position.y)

            val finished = drag(started.id) { change ->
                tracker.addPosition(change.uptimeMillis, change.position)
                simulation.dragTo(index, change.position.x, change.position.y)
                change.consume()
            }

            val velocity = if (finished) tracker.calculateVelocity() else Velocity.Zero
            simulation.release(index, velocity.x, velocity.y)
        }
    }

/** One-shot bookkeeping so each haptic fires once, and re-arms if the sort is scrubbed back. */
private class LandedNotifier(
    bodyCount: Int,
    private val onBodyLanded: () -> (Int) -> Unit,
    private val onDiscarded: () -> (Int) -> Unit,
    private val onRowLanded: () -> (Int) -> Unit,
) {
    private val settled = BooleanArray(bodyCount)
    private val sorted = BooleanArray(bodyCount)

    fun onSettled(index: Int) {
        if (index !in settled.indices || settled[index]) return
        settled[index] = true
        onBodyLanded()(index)
    }

    fun onSorted(index: Int, fate: SortFate, landed: Boolean) {
        if (index !in sorted.indices) return
        if (!landed) {
            sorted[index] = false
            return
        }
        if (sorted[index]) return
        sorted[index] = true
        when (fate) {
            SortFate.DISCARDED -> onDiscarded()(index)
            SortFate.KEPT -> onRowLanded()(index)
        }
    }

    fun resetSorted() = sorted.fill(false)

    /** Reduced motion: the pile is already where it lands, so nothing should tap. */
    fun suppressSettled() = settled.fill(true)
}

@Composable
private fun rememberLandedNotifier(
    bodyCount: Int,
    onBodyLanded: (Int) -> Unit,
    onDiscarded: (Int) -> Unit,
    onRowLanded: (Int) -> Unit,
): LandedNotifier {
    val landed by rememberUpdatedState(onBodyLanded)
    val dropped by rememberUpdatedState(onDiscarded)
    val rowed by rememberUpdatedState(onRowLanded)
    return remember(bodyCount) {
        LandedNotifier(bodyCount, { landed }, { dropped }, { rowed })
    }
}

// --- previews -------------------------------------------------------------------------------

private val PreviewBubbleSizes = listOf(
    DpSize(178.dp, 68.dp),
    DpSize(150.dp, 60.dp),
    DpSize(196.dp, 72.dp),
    DpSize(164.dp, 64.dp),
    DpSize(140.dp, 56.dp),
    DpSize(186.dp, 68.dp),
    DpSize(158.dp, 60.dp),
    DpSize(172.dp, 64.dp),
    DpSize(148.dp, 60.dp),
)

private val PreviewSenders =
    listOf("COMBANK", "BOC", "OTP 482913", "PeoplesBank", "SLTBILL", "DIALOG", "HNB", "1919", "CEB")

@Composable
private fun PreviewBubble(index: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.padding(12.dp)) {
            Text(
                text = PreviewSenders[index % PreviewSenders.size],
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PreviewRow(index: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "${PreviewSenders[index % PreviewSenders.size]} · Rs 4,280",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun BubbleStagePreview(dark: Boolean, progress: Float) {
    SalliTheme(darkTheme = dark) {
        Box(
            Modifier
                .size(360.dp, 640.dp)
                .background(MaterialTheme.colorScheme.primary),
        ) {
            BubbleStage(
                bodySizes = PreviewBubbleSizes,
                sortProgress = progress,
                discarded = setOf(2, 5),
                // Previews have no frame clock worth waiting for, so show the settled pile.
                reducedMotion = true,
                modifier = Modifier.fillMaxSize(),
                bubble = { PreviewBubble(it) },
                row = { PreviewRow(it) },
            )
        }
    }
}

@Preview(name = "Bubble stage · Act 1 · light", showBackground = true)
@Composable
private fun BubbleStageAct1LightPreview() = BubbleStagePreview(dark = false, progress = 0f)

@Preview(name = "Bubble stage · Act 1 · dark", showBackground = true)
@Composable
private fun BubbleStageAct1DarkPreview() = BubbleStagePreview(dark = true, progress = 0f)

@Preview(name = "Bubble stage · mid-sort · light", showBackground = true)
@Composable
private fun BubbleStageMidSortLightPreview() = BubbleStagePreview(dark = false, progress = 0.55f)

@Preview(name = "Bubble stage · sorted · light", showBackground = true)
@Composable
private fun BubbleStageSortedLightPreview() = BubbleStagePreview(dark = false, progress = 1f)

@Preview(name = "Bubble stage · sorted · dark", showBackground = true)
@Composable
private fun BubbleStageSortedDarkPreview() = BubbleStagePreview(dark = true, progress = 1f)
