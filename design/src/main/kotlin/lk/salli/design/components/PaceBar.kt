package lk.salli.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import lk.salli.design.motion.SalliMotionSpecs

/**
 * The pace track: how much of the budget (or the period) is gone, against how much *should*
 * be gone by now.
 *
 * An 8 dp rounded track, a fill, and a 2 dp tick at [expected] — the expected-burn marker is
 * the whole point. A progress bar alone says "70 % spent"; the tick turns that into "70 %
 * spent, 62 % of the month elapsed", which is the only version that changes a decision.
 *
 * Lifted out of `BudgetsScreen.ProgressTrack` so Home's hero, Plan's budget cards and the
 * widget all draw the same thing.
 *
 * @param progress fraction spent, 0..1+ (values above 1 clamp; use [SalliTone.NEGATIVE]).
 * @param expected fraction of the period elapsed, 0..1. Pass `null` to hide the tick.
 * @param tone     colour of the fill.
 */
@Composable
fun PaceBar(
    progress: Float,
    modifier: Modifier = Modifier,
    expected: Float? = null,
    tone: SalliTone = SalliTone.NEUTRAL,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tickColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = SalliMotionSpecs.slowSpatial(),
        label = "pace-fill",
    )
    val fill = tone.colors().accent

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp),
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        if (animated > 0f) {
            drawRoundRect(
                color = fill,
                size = Size(width = size.width * animated, height = size.height),
                cornerRadius = radius,
            )
        }
        if (expected != null) {
            val x = size.width * expected.coerceIn(0f, 1f)
            val tickWidth = 2.dp.toPx()
            val overhang = 3.dp.toPx()
            drawRect(
                color = tickColor,
                topLeft = Offset(
                    x = (x - tickWidth / 2f).coerceIn(0f, size.width - tickWidth),
                    y = -overhang,
                ),
                size = Size(width = tickWidth, height = size.height + overhang * 2),
            )
        }
    }
}

@SalliPreview
@Composable
private fun PaceBarPreview() {
    PreviewFrame {
        PaceBar(progress = 0.46f, expected = 0.53f, tone = SalliTone.POSITIVE)
        PaceBar(progress = 0.78f, expected = 0.53f, tone = SalliTone.WARNING)
        PaceBar(progress = 1f, expected = 0.53f, tone = SalliTone.NEGATIVE)
        PaceBar(progress = 0.3f)
    }
}
