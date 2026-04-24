package lk.salli.design.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Tiny bar chart for compact metric tiles. Each float in [values] becomes one vertical bar,
 * height proportional to its value vs the max in the series. Used for week/month trend tiles
 * where the info value is the *shape* of recent spending, not the individual numbers.
 *
 * The whole series animates 0 → full on first composition (and whenever the series changes)
 * via a low-stiffness spring so it feels organic.
 *
 * @param values ordered list of per-bucket values. Zero-length or all-zero renders a flat
 *               baseline line so the tile doesn't collapse visually.
 * @param color bar colour. Pass a muted tint if the tile's background is already busy.
 * @param barWidthFraction what portion of each bar's slot is the bar itself. 0.5 leaves
 *                         ~equal whitespace between bars.
 */
@Composable
fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    barWidthFraction: Float = 0.55f,
) {
    val key = values.hashCode()
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "sparkline-$key",
    )
    val max = values.maxOrNull()?.takeIf { it > 0f } ?: 0f

    Canvas(modifier = modifier) {
        if (values.isEmpty() || max <= 0f) {
            // Empty or all-zero series → a flat dotted baseline so the tile keeps its
            // rhythm instead of disappearing.
            drawLine(
                color = color.copy(alpha = 0.35f),
                start = Offset(0f, size.height - 1f),
                end = Offset(size.width, size.height - 1f),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)),
                cap = StrokeCap.Round,
            )
            return@Canvas
        }

        val slot = size.width / values.size
        val barWidth = (slot * barWidthFraction).coerceAtLeast(2f)
        val xOffsetPerBar = (slot - barWidth) / 2f

        values.forEachIndexed { i, v ->
            val heightFraction = (v / max).coerceIn(0f, 1f) * progress
            val h = size.height * heightFraction
            val x = slot * i + xOffsetPerBar
            val y = size.height - h

            // Subtle vertical gradient so the bar tops read slightly dimmer than their base.
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.75f), color),
                    startY = y,
                    endY = size.height,
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
            )
        }
    }
}

/**
 * Line-style variant of the sparkline. Slightly different visual affordance: continuous line
 * suggests a balance/metric over time better than discrete bars. Same animation semantics.
 */
@Composable
fun SparklineLine(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidthPx: Float = 3f,
) {
    val key = values.hashCode()
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessVeryLow),
        label = "sparkline-line-$key",
    )
    val max = values.maxOrNull() ?: 0f
    val min = values.minOrNull() ?: 0f
    val range = (max - min).takeIf { it > 0f } ?: 1f

    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas

        val path = androidx.compose.ui.graphics.Path()
        val stepX = size.width / (values.size - 1).toFloat()
        values.forEachIndexed { i, v ->
            val normalised = (v - min) / range
            val x = stepX * i
            val y = size.height - size.height * normalised * progress
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
        )
    }
}
