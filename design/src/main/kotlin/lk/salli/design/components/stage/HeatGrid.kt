package lk.salli.design.components.stage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import lk.salli.design.theme.SalliTheme
import lk.salli.domain.motion.GridBucketing

/**
 * A grid of dots whose brightness is the data: onboarding Act 3's "your year lighting up", and
 * Insights' "When you spend". The one piece of expressive drawing in the app that earns its
 * place, because it *is* the user's history rather than a picture of one.
 *
 * Cell size comes from the width it is given — hand it 52 columns and it works out how big each
 * dot can be and how tall it needs to be — so the same composable draws a 52x7 year and a 24x7
 * week without any layout maths at the call site.
 *
 * When [animateLightUp] is on, a cell that goes from nothing to something springs up to its new
 * brightness rather than appearing, which is what makes a running import look like a thing
 * filling in. All the cells share one frame loop that stops as soon as they have all arrived, so
 * a still grid costs nothing.
 *
 * @param values one entry per cell, row-major: `index = row * columns + column`. The helpers in
 *   [GridBucketing] build these.
 * @param color the fully-lit colour. Cells scale their alpha between [minAlpha] and [maxAlpha]
 *   by value against the grid's own busiest cell, so a quiet week still reads.
 * @param reducedMotion snaps every cell to its value instead of springing.
 * @param contentDescription one sentence for TalkBack; 364 dots are not worth reading out.
 */
@Composable
fun HeatGrid(
    columns: Int,
    rows: Int,
    values: FloatArray,
    color: Color,
    modifier: Modifier = Modifier,
    gap: Dp = 2.dp,
    maxCellSize: Dp = 12.dp,
    minAlpha: Float = 0.06f,
    maxAlpha: Float = 1f,
    cornerFraction: Float = 0.3f,
    animateLightUp: Boolean = true,
    reducedMotion: Boolean = false,
    contentDescription: String? = null,
) {
    require(columns > 0 && rows > 0) { "A heat grid needs at least one cell" }

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val gapPx = with(density) { gap.toPx() }
        val widthPx = constraints.maxWidth.toFloat()
        val cellPx = min(
            (widthPx - gapPx * (columns - 1)) / columns,
            with(density) { maxCellSize.toPx() },
        ).coerceAtLeast(1f)
        val gridHeight = with(density) { (cellPx * rows + gapPx * (rows - 1)).toDp() }

        val cellCount = columns * rows
        // Where each cell is drawn right now, and how fast it is getting there. Plain arrays,
        // not 364 Animatables. Only the published copy is snapshot state, so a moving grid
        // repaints without recomposing anything.
        val working = remember(columns, rows) { FloatArray(cellCount) }
        val velocity = remember(columns, rows) { FloatArray(cellCount) }
        var shown by remember(columns, rows) { mutableStateOf(FloatArray(cellCount)) }
        val target by rememberUpdatedState(values)

        LaunchedEffect(columns, rows, values.contentHashCode(), reducedMotion, animateLightUp) {
            val peak = target.maxOrNull() ?: 0f
            if (reducedMotion || !animateLightUp) {
                for (i in 0 until cellCount) {
                    working[i] = normalised(target, i, peak)
                    velocity[i] = 0f
                }
                shown = working.copyOf()
                return@LaunchedEffect
            }

            var last = 0L
            while (!settled(working, target, peak, cellCount)) {
                val now = withFrameNanos { it }
                val dt = if (last == 0L) 0f else ((now - last).coerceAtLeast(0L) / NANOS_PER_SECOND)
                last = now
                if (dt > 0f) {
                    val step = min(dt, MAX_FRAME_SECONDS)
                    for (i in 0 until cellCount) {
                        val displacement = working[i] - normalised(target, i, peak)
                        // Slightly under-damped spring: arrives briskly with one small overshoot.
                        velocity[i] += (
                            -2f * SPRING_DAMPING * SPRING_OMEGA * velocity[i] -
                                SPRING_OMEGA * SPRING_OMEGA * displacement
                            ) * step
                        working[i] += velocity[i] * step
                    }
                    shown = working.copyOf()
                }
            }
            for (i in 0 until cellCount) {
                working[i] = normalised(target, i, peak)
                velocity[i] = 0f
            }
            shown = working.copyOf()
        }

        val description = contentDescription
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight)
                .then(
                    if (description == null) {
                        Modifier
                    } else {
                        Modifier.semantics { this.contentDescription = description }
                    },
                ),
        ) {
            // `shown` is read here, in the draw phase, so the spring repaints without
            // recomposing.
            val lit = shown
            val radius = CornerRadius(cellPx * cornerFraction)
            for (row in 0 until rows) {
                for (col in 0 until columns) {
                    val value = lit.getOrElse(row * columns + col) { 0f }.coerceIn(0f, 1f)
                    drawRoundRect(
                        color = color.copy(alpha = minAlpha + (maxAlpha - minAlpha) * value),
                        topLeft = Offset(col * (cellPx + gapPx), row * (cellPx + gapPx)),
                        size = Size(cellPx, cellPx),
                        cornerRadius = radius,
                    )
                }
            }
        }
    }
}

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val MAX_FRAME_SECONDS = 1f / 20f

/** ~2.2 Hz and slightly under-damped: brisk, with just enough bounce to read as alive. */
private const val SPRING_OMEGA = 14f
private const val SPRING_DAMPING = 0.75f

private const val SETTLE_EPSILON = 0.004f

/** A cell's share of the grid's busiest cell, which is what its alpha is scaled by. */
private fun normalised(values: FloatArray, index: Int, peak: Float): Float {
    if (peak <= 0f) return 0f
    val v = values.getOrNull(index) ?: return 0f
    return (v / peak).coerceIn(0f, 1f)
}

private fun settled(shown: FloatArray, target: FloatArray, peak: Float, count: Int): Boolean {
    for (i in 0 until count) {
        if (abs(shown[i] - normalised(target, i, peak)) > SETTLE_EPSILON) return false
    }
    return true
}

// --- previews -------------------------------------------------------------------------------

private fun previewYearValues(): FloatArray {
    val grid = FloatArray(GridBucketing.YEAR_COLUMNS * GridBucketing.YEAR_ROWS)
    var seed = 20260916L
    for (i in grid.indices) {
        seed = seed * 6364136223846793005L + 1442695040888963407L
        val roll = ((seed ushr 33).toInt() and 0xFF) / 255f
        val weekday = i % GridBucketing.YEAR_COLUMNS
        // Busier as the year goes on, quieter at weekends: something with a shape to it.
        grid[i] = max(0f, roll * (0.25f + 0.75f * weekday / GridBucketing.YEAR_COLUMNS) - 0.2f)
    }
    return grid
}

private fun previewWeekHourValues(): FloatArray {
    val grid = FloatArray(GridBucketing.HOUR_COLUMNS * GridBucketing.WEEK_ROWS)
    for (day in 0 until GridBucketing.WEEK_ROWS) {
        for (hour in 0 until GridBucketing.HOUR_COLUMNS) {
            val evening = if (hour in 17..21) 1f else if (hour in 12..16) 0.5f else 0.12f
            val weekday = if (day < 5) 1f else 0.45f
            grid[day * GridBucketing.HOUR_COLUMNS + hour] = evening * weekday
        }
    }
    return grid
}

@Composable
private fun HeatGridPreview(dark: Boolean) {
    SalliTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text("Your year", style = MaterialTheme.typography.titleMedium)
                HeatGrid(
                    columns = GridBucketing.YEAR_COLUMNS,
                    rows = GridBucketing.YEAR_ROWS,
                    values = remember { previewYearValues() },
                    color = MaterialTheme.colorScheme.primary,
                    reducedMotion = true,
                    contentDescription = "Spending across the last year",
                )
                Text("When you spend", style = MaterialTheme.typography.titleMedium)
                HeatGrid(
                    columns = GridBucketing.HOUR_COLUMNS,
                    rows = GridBucketing.WEEK_ROWS,
                    values = remember { previewWeekHourValues() },
                    color = MaterialTheme.colorScheme.primary,
                    gap = 3.dp,
                    reducedMotion = true,
                    contentDescription = "Mostly weekday evenings",
                )
            }
        }
    }
}

@Preview(name = "Heat grid · light", showBackground = true, widthDp = 400)
@Composable
private fun HeatGridLightPreview() = HeatGridPreview(dark = false)

@Preview(name = "Heat grid · dark", showBackground = true, widthDp = 400)
@Composable
private fun HeatGridDarkPreview() = HeatGridPreview(dark = true)
