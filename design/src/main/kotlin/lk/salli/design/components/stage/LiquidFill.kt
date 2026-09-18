package lk.salli.design.components.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import lk.salli.design.theme.LocalSalliColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Liquid rising inside whatever box this is placed in. The box is the vessel: a goal card on
 * Plan, or the glass in the cap and jar sheets. [fraction] is the level, [lineFraction] an
 * optional dashed mark ("where you should be"), [tilt] leans the surface, and the surface only
 * moves while [stirring], tilting, or for a moment after either stops.
 *
 * The level springs to [fraction] on its own; a caller that already animates the level (the
 * [Glass]) uses [LiquidSurface] directly.
 */
@Composable
fun LiquidFill(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = LocalSalliColors.current.hero,
    alpha: Float = 1f,
    tilt: Float = 0f,
    stirring: Boolean = false,
    /** Follow [fraction] exactly instead of springing to it; used while a pour is running. */
    snap: Boolean = false,
    lineFraction: Float? = null,
    lineColor: Color = LocalSalliColors.current.transfer,
    reducedMotion: Boolean = false,
) {
    val level = remember { Animatable(fraction.coerceIn(0f, 1f)) }
    LaunchedEffect(fraction, snap) {
        val target = fraction.coerceIn(0f, 1f)
        if (snap || reducedMotion) level.snapTo(target) else level.animateTo(target, spring(dampingRatio = 0.7f, stiffness = 180f))
    }
    LiquidSurface(
        level = { level.value },
        modifier = modifier,
        color = color,
        alpha = alpha,
        tilt = tilt,
        stirring = stirring,
        lineFraction = lineFraction,
        lineColor = lineColor,
        reducedMotion = reducedMotion,
    )
}

/**
 * The drawing under [LiquidFill]: a wave-topped fill at [level] (0..1, read per frame so the
 * caller can drive it from its own animation). With [alpha] under 1 the liquid is a tint you
 * can read text through, and a solid crest marks the surface so the level still reads.
 */
@Composable
fun LiquidSurface(
    level: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = LocalSalliColors.current.hero,
    alpha: Float = 1f,
    tilt: Float = 0f,
    stirring: Boolean = false,
    lineFraction: Float? = null,
    lineColor: Color = LocalSalliColors.current.transfer,
    reducedMotion: Boolean = false,
) {
    var phase by remember { mutableFloatStateOf(0f) }
    var settle by remember { mutableFloatStateOf(0f) }
    val tilting = abs(tilt) > 0.03f
    LaunchedEffect(stirring, tilting, settle > 0f, reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        if (stirring || tilting) settle = 1.2f
        if (!stirring && !tilting && settle <= 0f) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (stirring || tilting || settle > 0f) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            phase += dt * (if (stirring) 8f else 4.5f)
            if (!stirring && !tilting) settle = (settle - dt).coerceAtLeast(0f)
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val lv = level().coerceIn(0f, 1f)
        val surface = h - h * lv
        val amp = if (stirring) 3.dp.toPx() else 1.4.dp.toPx()
        val slope = tilt * 16.dp.toPx()
        fun surfaceAt(x: Float): Float {
            val t = x / w
            return surface - slope * (0.5f - t) * 2f + amp * sin(phase + t * 2f * PI.toFloat() * 1.2f)
        }
        val wave = Path().apply {
            moveTo(0f, h)
            var x = 0f
            while (x <= w + 2f) {
                lineTo(x, surfaceAt(x))
                x += 4f
            }
            lineTo(w, h)
            close()
        }
        drawPath(wave, color.copy(alpha = alpha))
        if (alpha < 1f && lv > 0.001f) {
            // A readable tint needs a crest, or the level disappears behind the text.
            val crest = Path().apply {
                var x = 0f
                var first = true
                while (x <= w + 2f) {
                    val y = surfaceAt(x)
                    if (first) { moveTo(x, y); first = false } else lineTo(x, y)
                    x += 4f
                }
            }
            drawPath(crest, color, style = Stroke(width = 2.dp.toPx()))
        }
        lineFraction?.let { lf ->
            val y = h - h * lf.coerceIn(0f, 1f)
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)),
            )
        }
    }
}
