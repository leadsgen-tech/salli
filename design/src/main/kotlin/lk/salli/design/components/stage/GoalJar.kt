package lk.salli.design.components.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lk.salli.design.haptics.rememberSalliHaptics
import lk.salli.design.theme.LocalSalliColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * A savings goal drawn as a see-through piggy bank. The level is progress; the dashed line is
 * where the level should be by the end of this period; tilt the phone and the liquid follows.
 *
 * **Hold the jar to pour.** The amount climbs faster the longer the press is held, with a tick
 * haptic at each detent, and lands on release through [onPour]. A goal that is already full
 * cannot be poured into. Nothing here is decoration: the level, the line and the hold are the
 * three things a goal needs — how far, how far it should be, and putting money in.
 *
 * @param lineMinor where the level ought to be by now, or null to draw no line.
 * @param tilt −1..1 from `rememberDeviceTilt`; 0 keeps the surface level.
 */
@Composable
fun GoalJarTile(
    name: String,
    savedMinor: Long,
    targetMinor: Long,
    lineMinor: Long?,
    formatAmount: (Long) -> String,
    onPour: (Long) -> Unit,
    modifier: Modifier = Modifier,
    tilt: Float = 0f,
    canPour: Boolean = true,
    reducedMotion: Boolean = false,
    jarHeight: Dp = 120.dp,
) {
    val haptics = rememberSalliHaptics()
    val currentOnPour by rememberUpdatedState(onPour)
    var pouring by remember { mutableStateOf(false) }
    var pourMinor by remember { mutableLongStateOf(0L) }
    var phase by remember { mutableStateOf(0f) }
    var settle by remember { mutableStateOf(0f) }
    val burst = remember { Animatable(0f) }
    val fill = remember { Animatable(fraction(savedMinor, targetMinor)) }

    // The level follows the money: a jump on release, a spring when a saved amount arrives.
    LaunchedEffect(savedMinor, targetMinor) {
        if (!pouring) fill.animateTo(fraction(savedMinor, targetMinor), spring(dampingRatio = 0.7f, stiffness = 200f))
    }

    // Pouring: the amount grows with the square of the hold, snapped to Rs 100, capped at the
    // room left in the jar. Detents at 1k, 5k, 10k and every 10k after that.
    LaunchedEffect(pouring) {
        if (!pouring) return@LaunchedEffect
        val start = withFrameNanos { it }
        var lastDetent = 0L
        val room = (targetMinor - savedMinor).coerceAtLeast(0L)
        while (pouring) {
            val held = (withFrameNanos { it } - start) / 1_000_000_000f
            val major = ((held * held * 4000f + held * 1500f) / 100f).toLong() * 100L
            val next = (major * 100L).coerceAtMost(room)
            if (next != pourMinor) {
                pourMinor = next
                fill.snapTo(fraction(savedMinor + next, targetMinor))
                val detent = detentFor(next)
                if (detent != lastDetent) {
                    lastDetent = detent
                    if (detent > 0L) haptics.tick()
                }
                if (next == room && room > 0L && burst.value == 0f) {
                    haptics.thump()
                    launchBurst(burst)
                }
            }
        }
    }

    // The surface only animates while there is a reason to: a pour, a lean, or the settle after.
    val tilting = abs(tilt) > 0.03f
    LaunchedEffect(pouring, tilting, settle > 0f, reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        if (!pouring && !tilting && settle <= 0f) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (pouring || tilting || settle > 0f) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            phase += dt * (if (pouring) 9f else 5f)
            if (!pouring && !tilting) settle = (settle - dt).coerceAtLeast(0f)
        }
    }

    val salli = LocalSalliColors.current
    val liquid = salli.hero
    val outline = MaterialTheme.colorScheme.outlineVariant
    val inside = MaterialTheme.colorScheme.surfaceContainerLowest
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val burstColor = salli.positive
    val live = savedMinor + pourMinor
    val lineFraction = lineMinor?.let { fraction(it, targetMinor) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.semantics {
            contentDescription = "$name, ${formatAmount(savedMinor)} of ${formatAmount(targetMinor)}"
        },
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(jarHeight)
                .pointerInput(canPour, savedMinor, targetMinor) {
                    if (!canPour) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            pourMinor = 0L
                            pouring = true
                            tryAwaitRelease()
                            pouring = false
                            settle = 1.2f
                            val poured = pourMinor
                            pourMinor = 0L
                            if (poured > 0L) currentOnPour(poured)
                        },
                    )
                },
        ) {
            val w = size.width
            val h = size.height
            // A piggy you can see into. Body, snout, ear, legs and a coin slot are drawn as
            // outlines; the liquid is clipped to the body and snout so the level stays the
            // whole point. The pig faces right.
            val bodyRect = Rect(left = w * 0.10f, top = h * 0.24f, right = w * 0.84f, bottom = h * 0.86f)
            val snoutRect = Rect(left = w * 0.80f, top = h * 0.46f, right = w * 0.97f, bottom = h * 0.66f)
            val snoutRadius = CornerRadius(w * 0.06f, w * 0.06f)
            val vessel = Path().apply {
                addOval(bodyRect)
                addRoundRect(RoundRect(snoutRect, snoutRadius))
            }
            val liquidTop = bodyRect.top
            val liquidBottom = bodyRect.bottom
            drawPath(vessel, inside)
            clipPath(vessel) {
                val level = liquidBottom - (liquidBottom - liquidTop) * fill.value
                val amp = if (pouring) 2.5.dp.toPx() else 1.2.dp.toPx()
                val slope = tilt * 14.dp.toPx()
                val wave = Path().apply {
                    moveTo(0f, h)
                    var x = 0f
                    while (x <= w + 2f) {
                        val t = x / w
                        val y = level - slope * (0.5f - t) * 2f + amp * sin(phase + t * 2f * PI.toFloat() * 1.3f)
                        lineTo(x, y)
                        x += 4f
                    }
                    lineTo(w, h)
                    close()
                }
                drawPath(wave, liquid)
                lineFraction?.let { lf ->
                    val y = liquidBottom - (liquidBottom - liquidTop) * lf
                    drawLine(
                        color = lineColor,
                        start = Offset(bodyRect.left, y),
                        end = Offset(bodyRect.right, y),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)),
                    )
                }
            }
            val stroke = Stroke(width = 2.dp.toPx())
            // Legs first so the body outline sits over them.
            val legW = w * 0.11f
            val legH = h * 0.12f
            listOf(w * 0.26f, w * 0.58f).forEach { lx ->
                drawRoundRect(inside, Offset(lx, bodyRect.bottom - legH * 0.4f), Size(legW, legH), CornerRadius(legW * 0.3f))
                drawRoundRect(outline, Offset(lx, bodyRect.bottom - legH * 0.4f), Size(legW, legH), CornerRadius(legW * 0.3f), style = stroke)
            }
            drawOval(outline, topLeft = Offset(bodyRect.left, bodyRect.top), size = Size(bodyRect.width, bodyRect.height), style = stroke)
            drawRoundRect(outline, Offset(snoutRect.left, snoutRect.top), Size(snoutRect.width, snoutRect.height), snoutRadius, style = stroke)
            // Nostrils, ear, eye, tail: the few marks that make it a pig and not a bean.
            val nostril = 1.6.dp.toPx()
            drawCircle(outline, nostril, Offset(snoutRect.left + snoutRect.width * 0.38f, snoutRect.top + snoutRect.height * 0.5f))
            drawCircle(outline, nostril, Offset(snoutRect.left + snoutRect.width * 0.68f, snoutRect.top + snoutRect.height * 0.5f))
            val ear = Path().apply {
                moveTo(w * 0.62f, bodyRect.top + h * 0.04f)
                lineTo(w * 0.70f, bodyRect.top - h * 0.06f)
                lineTo(w * 0.76f, bodyRect.top + h * 0.08f)
                close()
            }
            drawPath(ear, inside); drawPath(ear, outline, style = stroke)
            drawCircle(outline, 2.dp.toPx(), Offset(w * 0.66f, bodyRect.top + h * 0.20f))
            val tail = Path().apply {
                moveTo(bodyRect.left + 2f, bodyRect.top + bodyRect.height * 0.45f)
                cubicTo(w * 0.02f, bodyRect.top + bodyRect.height * 0.30f, w * 0.06f, bodyRect.top + bodyRect.height * 0.62f, w * 0.01f, bodyRect.top + bodyRect.height * 0.52f)
            }
            drawPath(tail, outline, style = Stroke(width = 1.5.dp.toPx()))
            // The coin slot on the back.
            drawLine(outline, Offset(w * 0.36f, bodyRect.top + 1.dp.toPx()), Offset(w * 0.50f, bodyRect.top + 1.dp.toPx()), strokeWidth = 3.dp.toPx())
            if (burst.value > 0f) {
                drawCircle(
                    color = burstColor.copy(alpha = (1f - burst.value) * 0.9f),
                    radius = burst.value * w * 0.6f,
                    center = Offset(w * 0.43f, bodyRect.top),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        SpringOdometer(
            text = formatAmount(live),
            style = MaterialTheme.typography.titleSmall,
            reducedMotion = reducedMotion,
        )
    }
}

private fun fraction(minor: Long, target: Long): Float =
    if (target <= 0L) 0f else (minor.toFloat() / target).coerceIn(0f, 1f)

/** Detents: 1k, 5k, 10k, then every 10k. Amounts are minor units (cents). */
private fun detentFor(minor: Long): Long = when {
    minor >= 1_000_000L -> (minor / 1_000_000L) * 1_000_000L
    minor >= 500_000L -> 500_000L
    minor >= 100_000L -> 100_000L
    else -> 0L
}

private suspend fun launchBurst(burst: Animatable<Float, *>) {
    burst.snapTo(0.01f)
    burst.animateTo(1f, spring(dampingRatio = 1f, stiffness = 120f))
    burst.snapTo(0f)
}

/** Width of one jar tile in a row of three. */
val GoalJarTileWidth: Dp = 108.dp
