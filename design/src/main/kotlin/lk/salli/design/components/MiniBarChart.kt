package lk.salli.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lk.salli.design.haptics.rememberSalliHaptics
import lk.salli.design.motion.LocalReducedMotion

/**
 * The house bar chart: one bar per bucket (a day on Activity, a month on Insights), springing
 * up on first composition and scrubbable with a finger.
 *
 * Deliberately a `Canvas` rather than a charting library — it draws six primitives, it has to
 * stay at 60 fps under a scrubbing finger, and a dependency that renders axes and legends we
 * don't want is a dependency that renders axes and legends we have to hide.
 *
 * Scrub semantics: dragging selects the bar under the finger and fires [onSelect] once per
 * change with a tick haptic, so the caller's list can follow without the chart knowing what a
 * list is.
 *
 * @param values    raw magnitudes; scaled against the largest. Empty renders nothing.
 * @param highlight index drawn in the accent colour (today, or the selected period).
 * @param selected  index currently scrubbed, if any.
 */
@Composable
fun MiniBarChart(
    values: List<Long>,
    modifier: Modifier = Modifier,
    highlight: Int? = null,
    selected: Int? = null,
    height: Dp = 56.dp,
    barColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onSelect: ((Int) -> Unit)? = null,
) {
    if (values.isEmpty()) return

    val reducedMotion = LocalReducedMotion.current
    val haptics = rememberSalliHaptics()
    val grow = remember(values.size) { Animatable(if (reducedMotion) 1f else 0f) }

    LaunchedEffect(values, reducedMotion) {
        if (reducedMotion) {
            grow.snapTo(1f)
        } else {
            grow.snapTo(0f)
            grow.animateTo(1f, animationSpec = spring(
                dampingRatio = 0.75f,
                stiffness = 220f,
            ))
        }
    }

    val max = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    // Deliberately a plain array rather than a MutableState: this only dedupes repeat
    // callbacks while a finger drags across one bar, and making it snapshot state would
    // recompose the chart on every pointer event it is trying to filter out.
    val lastSelected = remember { intArrayOf(-1) }

    fun pick(x: Float, width: Float) {
        if (onSelect == null || width <= 0f) return
        val index = ((x / width) * values.size).toInt().coerceIn(0, values.size - 1)
        if (index != lastSelected[0]) {
            lastSelected[0] = index
            haptics.tick()
            onSelect(index)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (onSelect == null) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(values.size) {
                            detectTapGestures { offset ->
                                pick(offset.x, size.width.toFloat())
                                // The last-picked guard exists to dedupe a drag, not a
                                // tap: without this reset, tapping the same bar twice is
                                // silently swallowed the second time.
                                lastSelected[0] = -1
                            }
                        }
                        .pointerInput(values.size) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset -> pick(offset.x, size.width.toFloat()) },
                                onDragEnd = { lastSelected[0] = -1 },
                                onDragCancel = { lastSelected[0] = -1 },
                            ) { change, _ -> pick(change.position.x, size.width.toFloat()) }
                        }
                },
            ),
    ) {
        val slot = size.width / values.size
        val gap = (slot * 0.22f).coerceAtMost(3.dp.toPx())
        val barWidth = (slot - gap).coerceAtLeast(1f)
        val radius = CornerRadius(barWidth.coerceAtMost(3.dp.toPx()), barWidth.coerceAtMost(3.dp.toPx()))

        values.forEachIndexed { index, value ->
            val fraction = (value.toFloat() / max.toFloat()).coerceIn(0f, 1f) * grow.value
            // A zero-height bar looks like missing data rather than a quiet day, so every
            // bucket keeps a 2 dp stub.
            val barHeight = (size.height * fraction).coerceAtLeast(2.dp.toPx())
            val color = if (index == selected || index == highlight) accentColor else barColor
            drawRoundRect(
                color = color,
                topLeft = Offset(x = index * slot, y = size.height - barHeight),
                size = Size(width = barWidth, height = barHeight),
                cornerRadius = radius,
            )
        }
    }
}

@SalliPreview
@Composable
private fun MiniBarChartPreview() {
    PreviewFrame {
        MiniBarChart(
            values = listOf(
                1200, 400, 8600, 2300, 0, 5400, 900,
                3100, 7800, 1200, 400, 2600, 9900, 1800,
            ),
            highlight = 13,
        )
        MiniBarChart(
            values = listOf(42_000, 51_000, 38_000, 84_200, 61_000, 47_500),
            highlight = 3,
            height = 72.dp,
        )
    }
}
