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
import androidx.compose.ui.geometry.Offset
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
 * A savings goal drawn as a jar of liquid. The level is progress; the dashed line is where the
 * level should be by the end of this period; tilt the phone and the liquid follows.
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
            val lip = 8.dp.toPx()
            val bodyLeft = w * 0.18f
            val bodyRight = w * 0.82f
            val radius = 12.dp.toPx()
            val body = Path().apply {
                moveTo(bodyLeft, lip)
                lineTo(bodyRight, lip)
                lineTo(bodyRight, h - radius)
                quadraticTo(bodyRight, h, bodyRight - radius, h)
                lineTo(bodyLeft + radius, h)
                quadraticTo(bodyLeft, h, bodyLeft, h - radius)
                close()
            }
            // Glass, then liquid clipped to it, then the line and the rim on top.
            drawPath(body, inside)
            clipPath(body) {
                val level = h - (h - lip) * fill.value
                val amp = if (pouring) 2.5.dp.toPx() else 1.2.dp.toPx()
                val slope = tilt * 14.dp.toPx()
                val wave = Path().apply {
                    moveTo(bodyLeft, h)
                    var x = bodyLeft
                    while (x <= bodyRight + 2f) {
                        val t = (x - bodyLeft) / (bodyRight - bodyLeft)
                        val y = level - slope * (0.5f - t) * 2f + amp * sin(phase + t * 2f * PI.toFloat() * 1.3f)
                        lineTo(x, y)
                        x += 4f
                    }
                    lineTo(bodyRight, h)
                    close()
                }
                drawPath(wave, liquid)
                lineFraction?.let { lf ->
                    val y = h - (h - lip) * lf
                    drawLine(
                        color = lineColor,
                        start = Offset(bodyLeft, y),
                        end = Offset(bodyRight, y),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)),
                    )
                }
            }
            drawPath(body, outline, style = Stroke(width = 2.dp.toPx()))
            // The rim: a slightly wider band at the mouth, so it reads as a jar and not a bar.
            drawLine(
                color = outline,
                start = Offset(bodyLeft - 4.dp.toPx(), lip),
                end = Offset(bodyRight + 4.dp.toPx(), lip),
                strokeWidth = 3.dp.toPx(),
            )
            if (burst.value > 0f) {
                drawCircle(
                    color = burstColor.copy(alpha = (1f - burst.value) * 0.9f),
                    radius = burst.value * w * 0.6f,
                    center = Offset(w / 2f, lip),
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
