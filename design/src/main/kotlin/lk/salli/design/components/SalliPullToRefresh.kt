package lk.salli.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import kotlinx.coroutines.launch

/**
 * Pull-to-refresh: a chewy pill that squashes and stretches with the drag. At rest it's a
 * short capsule pinned at the top. Pull down and it elongates, its corners stay fully rounded.
 * Cross the trigger distance and it snaps into a circle (haptic tick). Release past threshold
 * and it spins while the refresh runs. Release short and it compresses back up with an
 * overshoot worth flicking again.
 *
 * Also paints a soft vertical wash behind the pill so the gesture reads as fullscreen rather
 * than a confined spinner at the top.
 */
@Composable
fun SalliPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val thresholdPx = with(density) { TRIGGER_DISTANCE_DP.dp.toPx() }
    val maxPullPx = with(density) { MAX_PULL_DP.dp.toPx() }

    var rawPull by remember { mutableFloatStateOf(0f) }
    var thresholdArmed by remember { mutableStateOf(false) }
    val indicatorPx = remember { Animatable(0f) }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && indicatorPx.value > 0f) {
            indicatorPx.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
            rawPull = 0f
            thresholdArmed = false
        }
    }

    val nestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (isRefreshing) return Offset.Zero
                if (rawPull > 0f && available.y < 0f && source == NestedScrollSource.UserInput) {
                    val consumed = minOf(-available.y, rawPull)
                    rawPull = (rawPull - consumed).coerceAtLeast(0f)
                    scope.launch { indicatorPx.snapTo(damped(rawPull, maxPullPx)) }
                    updateArmed(rawPull, thresholdPx, thresholdArmed, haptic) { thresholdArmed = it }
                    return Offset(0f, -consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (isRefreshing) return Offset.Zero
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y <= 0f) return Offset.Zero
                rawPull = (rawPull + available.y).coerceAtMost(maxPullPx * 2f)
                scope.launch { indicatorPx.snapTo(damped(rawPull, maxPullPx)) }
                updateArmed(rawPull, thresholdPx, thresholdArmed, haptic) { thresholdArmed = it }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (isRefreshing) return Velocity.Zero
                if (rawPull <= 0f) return Velocity.Zero
                val triggered = rawPull >= thresholdPx
                rawPull = 0f
                thresholdArmed = false
                if (triggered) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRefresh()
                    indicatorPx.animateTo(
                        targetValue = thresholdPx,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    )
                } else {
                    indicatorPx.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioHighBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                }
                return available
            }
        }
    }

    Box(modifier = modifier.nestedScroll(nestedScroll)) {
        Box(
            modifier = Modifier.graphicsLayer {
                translationY = indicatorPx.value
            },
        ) {
            content()
        }

        ElasticPill(
            offsetPx = indicatorPx.value,
            thresholdPx = thresholdPx,
            armed = thresholdArmed,
            isRefreshing = isRefreshing,
        )
    }
}

private fun damped(raw: Float, max: Float): Float {
    val ratio = (raw / max).coerceAtLeast(0f)
    val dampened = max * (1f - (1f / (1f + ratio.pow(1.1f))))
    return dampened.coerceAtLeast(0f)
}

private fun updateArmed(
    raw: Float,
    threshold: Float,
    currentlyArmed: Boolean,
    haptic: HapticFeedback,
    set: (Boolean) -> Unit,
) {
    val nowArmed = raw >= threshold
    if (nowArmed != currentlyArmed) {
        if (nowArmed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        set(nowArmed)
    }
}

/* --------------------------------- Pill ------------------------------------------- */

private const val TRIGGER_DISTANCE_DP = 88f
private const val MAX_PULL_DP = 170f

/** Indicator zone height — gives the wash somewhere to live. */
private const val INDICATOR_HEIGHT_DP = 200f

/** Pill dimensions at rest (no pull). */
private const val PILL_WIDTH_DP = 44f
private const val PILL_HEIGHT_DP = 6f

/** Pill dimensions at the trigger threshold — a clean circle. */
private const val PILL_CIRCLE_DP = 32f

@Composable
private fun ElasticPill(
    offsetPx: Float,
    thresholdPx: Float,
    armed: Boolean,
    isRefreshing: Boolean,
) {
    if (offsetPx <= 0.5f && !isRefreshing) return

    val density = LocalDensity.current
    val progress = (offsetPx / thresholdPx).coerceIn(0f, 1.5f)
    val eased = progress.coerceAtMost(1f).pow(0.75f) // quicker growth early, settles near threshold

    val pillWidthPx = with(density) { lerpf(PILL_WIDTH_DP, PILL_CIRCLE_DP, eased).dp.toPx() }
    val pillHeightPx = with(density) { lerpf(PILL_HEIGHT_DP, PILL_CIRCLE_DP, eased).dp.toPx() }

    val infinite = rememberInfiniteTransition(label = "pill-fx")
    // Gentle breathing scale while refreshing so the pill never looks frozen.
    val breathe by infinite.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
        ),
        label = "rotation",
    )

    val restingColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val activeColor = MaterialTheme.colorScheme.primary
    val pillColor = lerp(restingColor, activeColor, eased.coerceAtMost(1f))

    // Where the pill sits vertically. Grows downward with pull; during refresh it hovers a
    // hair below the threshold position so it reads as "detached".
    val baseY = with(density) { 36.dp.toPx() }
    val yOffset = when {
        isRefreshing -> baseY + thresholdPx * 0.25f
        else -> baseY + offsetPx * 0.35f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(INDICATOR_HEIGHT_DP.dp),
    ) {
        // Soft vertical wash — carries the gesture across the screen without being loud.
        val washAlpha = if (isRefreshing) 1f else progress.coerceAtMost(1f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = washAlpha * 0.55f }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            activeColor.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        // Crossfade between two states:
        //  - pulling: a stretched capsule growing toward a circle
        //  - end state (armed / refreshing): a thin ring with a rotating arc — gives the
        //    final form visual texture so it doesn't land as a plain orange dot
        val endStateAlpha = ((progress - 0.6f) / 0.4f).coerceIn(0f, 1f).let { base ->
            if (isRefreshing) 1f else base
        }
        val pillAlpha = if (isRefreshing) 0f else 1f - endStateAlpha * 0.9f

        val density2 = LocalDensity.current
        val pillWidthDp = with(density2) { pillWidthPx.toDp() }
        val pillHeightDp = with(density2) { pillHeightPx.toDp() }
        val circleSize = PILL_CIRCLE_DP.dp

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = yOffset
                    val s = if (isRefreshing) breathe else 1f
                    scaleX = s
                    scaleY = s
                    if (armed && !isRefreshing) {
                        scaleX *= 1.1f
                        scaleY *= 1.1f
                    }
                },
        ) {
            // Pulling pill — fades out as the end-state ring fades in.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = pillAlpha }
                    .height(pillHeightDp)
                    .width(pillWidthDp)
                    .clip(RoundedCornerShape(50))
                    .background(pillColor),
            )

            // End state — ring + rotating arc. The ring is the muted track, the arc is the
            // primary accent sweeping around it.
            val ringStrokePx = with(density) { 3.dp.toPx() }
            Canvas(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(circleSize)
                    .graphicsLayer { alpha = endStateAlpha },
            ) {
                val inset = ringStrokePx / 2f
                val diameter = size.minDimension - ringStrokePx
                val topLeft = Offset(inset, inset)
                val arcSize = Size(diameter, diameter)

                // Muted full-circle track.
                drawCircle(
                    color = restingColor.copy(alpha = 0.35f),
                    radius = diameter / 2f,
                    style = Stroke(width = ringStrokePx),
                )
                // Primary arc sweeping around the track. Stroke is rounded so the ends feel
                // organic, not clipped. 120° sweep reads as a clear segment without looking
                // like a full C.
                drawArc(
                    color = activeColor,
                    startAngle = rotation,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = ringStrokePx, cap = StrokeCap.Round),
                )
            }
        }
    }
}

private fun lerpf(a: Float, b: Float, t: Float): Float = a + (b - a) * t
