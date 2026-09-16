package lk.salli.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lk.salli.design.motion.SalliMotionSpecs

/**
 * Circular progress for goals: 6 dp stroke, rounded cap, sweeping clockwise from twelve.
 *
 * A ring rather than a bar because a savings goal is a whole thing you're filling, not a
 * budget you're burning — and because two of them sit side by side on Plan where two bars
 * would read as one chart.
 */
@Composable
fun RingProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    stroke: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: (@Composable () -> Unit)? = null,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = SalliMotionSpecs.slowSpatial(),
        label = "ring-progress",
    )
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(
                width = this.size.width - strokePx,
                height = this.size.height - strokePx,
            )
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            if (animated > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        content?.invoke()
    }
}

@SalliPreview
@Composable
private fun RingProgressPreview() {
    PreviewFrame {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RingProgress(progress = 0.4f)
            RingProgress(progress = 0.85f, size = 56.dp) {
                Text(text = "85%", style = MaterialTheme.typography.labelSmall)
            }
            RingProgress(progress = 1f, size = 36.dp, stroke = 5.dp)
        }
    }
}
