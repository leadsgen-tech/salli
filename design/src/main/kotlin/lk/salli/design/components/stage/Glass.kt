package lk.salli.design.components.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import lk.salli.design.haptics.rememberSalliHaptics
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * A height on the glass wall. Rungs are detents the finger snaps to; tides are past levels
 * drawn for reference. A [strong] rung also draws a faint line across the glass, for the one
 * mark that matters most ("Usual"). An empty [label] draws the mark alone, and [line] false
 * draws the label alone, so a caller can label a cluster of near-identical tides once.
 */
data class GlassMark(val label: String, val minor: Long, val strong: Boolean = false, val line: Boolean = true)

/**
 * A glass you fill with your finger. Touch anywhere inside and the liquid rises to the finger;
 * lift and it settles onto the nearest detent with a rubber-band spring. There is no knob and
 * no track: the level is the number, and [surface] (usually the amount) floats just above it.
 *
 * [rungs] are notches on the right wall the level snaps to, each tappable; [tides] are dashed
 * high-water marks with a label, for what the last periods actually cost. [curve] bends the
 * scale: 1 is linear, 0.5 spreads small values so a goal glass can show 25k and 500k on the
 * same wall.
 */
@Composable
fun Glass(
    valueMinor: Long,
    maxMinor: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    rungs: List<GlassMark> = emptyList(),
    tides: List<GlassMark> = emptyList(),
    stepMinor: Long = 100_000L,
    curve: Float = 1f,
    tilt: Float = 0f,
    reducedMotion: Boolean = false,
    surface: @Composable (Long) -> Unit,
) {
    val haptics = rememberSalliHaptics()
    val currentOnChange by rememberUpdatedState(onChange)
    val scope = rememberCoroutineScope()

    fun toFraction(minor: Long): Float =
        if (maxMinor <= 0L) 0f else (minor.toFloat() / maxMinor).coerceIn(0f, 1f).pow(curve)
    fun fromFraction(f: Float): Long =
        (f.coerceIn(0f, 1f).toDouble().pow(1.0 / curve) * maxMinor).roundToLong()
    fun snap(rawFraction: Float): Long {
        rungs.firstOrNull { abs(toFraction(it.minor) - rawFraction) < 0.035f }?.let { return it.minor }
        val raw = fromFraction(rawFraction)
        return ((raw.toDouble() / stepMinor).roundToLong() * stepMinor).coerceIn(0L, maxMinor)
    }

    val level = remember { Animatable(toFraction(valueMinor)) }
    var dragging by remember { mutableStateOf(false) }
    var lastDetent by remember { mutableLongStateOf(valueMinor) }
    val settleSpring = spring<Float>(dampingRatio = 0.55f, stiffness = 320f)
    LaunchedEffect(valueMinor, maxMinor, curve, dragging) {
        if (!dragging) {
            val target = toFraction(valueMinor)
            if (reducedMotion) level.snapTo(target) else level.animateTo(target, settleSpring)
        }
    }

    val shape = RoundedCornerShape(24.dp)
    val inside = MaterialTheme.colorScheme.surfaceContainerLowest
    val outline = MaterialTheme.colorScheme.outlineVariant
    val notch = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val tide = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .clip(shape)
            .background(inside)
            .border(1.5.dp, outline, shape)
            .semantics { contentDescription = "Glass" }
            .pointerInput(maxMinor, stepMinor, rungs, curve) {
                awaitEachGesture {
                    val height = size.height.toFloat()
                    fun set(y: Float, settle: Boolean) {
                        val rawFraction = (1f - y / height).coerceIn(0f, 1f)
                        val snapped = snap(rawFraction)
                        if (snapped != lastDetent) {
                            lastDetent = snapped
                            if (rungs.any { it.minor == snapped }) haptics.thump() else haptics.tick()
                        }
                        currentOnChange(snapped)
                        scope.launch {
                            if (settle || reducedMotion) level.animateTo(toFraction(snapped), settleSpring)
                            else level.snapTo(rawFraction)
                        }
                    }
                    val down = awaitFirstDown()
                    dragging = true
                    set(down.position.y, settle = false)
                    drag(down.id) { change ->
                        set(change.position.y, settle = false)
                        change.consume()
                    }
                    dragging = false
                    set(height * (1f - level.value), settle = true)
                }
            },
    ) {
        LiquidSurface(
            level = { level.value },
            alpha = 0.22f,
            tilt = tilt,
            stirring = dragging,
            reducedMotion = reducedMotion,
            modifier = Modifier.matchParentSize(),
        )
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            tides.filter { it.line }.forEach { t ->
                val y = h - h * toFraction(t.minor)
                drawLine(tide, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 6f)))
            }
            rungs.filter { it.line }.forEach { r ->
                val y = h - h * toFraction(r.minor)
                if (r.strong) drawLine(notch.copy(alpha = 0.18f), Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
                drawLine(notch, Offset(w - 14.dp.toPx(), y), Offset(w, y), strokeWidth = 2.dp.toPx())
            }
        }
        // Labels on the right wall, nudged apart when two heights collide.
        val marks = (rungs.map { it to true } + tides.map { it to false }).filter { it.first.label.isNotBlank() }
        Layout(
            content = {
                marks.forEach { (m, isRung) ->
                    Text(
                        text = m.label,
                        style = if (isRung) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                        color = if (isRung) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        // A faint pill keeps the label legible where the crest passes through it.
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(inside.copy(alpha = 0.75f))
                            .then(if (isRung) Modifier.clickable { haptics.thump(); onChange(m.minor) } else Modifier)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            },
            modifier = Modifier.matchParentSize(),
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
            val h = constraints.maxHeight
            val w = constraints.maxWidth
            val wall = 14.dp.roundToPx()
            val tops = placeables.mapIndexed { i, p ->
                val y = h - h * toFraction(marks[i].first.minor)
                (y - p.height / 2f).roundToInt().coerceIn(0, (h - p.height).coerceAtLeast(0))
            }.toMutableList()
            val order = tops.indices.sortedBy { tops[it] }
            for (k in 1 until order.size) {
                val prev = order[k - 1]
                val cur = order[k]
                val minTop = tops[prev] + placeables[prev].height
                if (tops[cur] < minTop) tops[cur] = minTop
            }
            order.lastOrNull()?.let { last ->
                val overflow = tops[last] + placeables[last].height - h
                if (overflow > 0) order.forEach { tops[it] -= overflow }
            }
            layout(w, h) {
                placeables.forEachIndexed { i, p -> p.placeRelative(w - wall - 2.dp.roundToPx() - p.width, tops[i]) }
            }
        }
        // The buoy: whatever the caller floats on the surface, riding just above the liquid.
        Layout(
            content = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(inside.copy(alpha = 0.82f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) { surface(valueMinor) }
            },
            modifier = Modifier.matchParentSize(),
        ) { measurables, constraints ->
            val p = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
            val h = constraints.maxHeight
            val pad = 8.dp.roundToPx()
            layout(constraints.maxWidth, h) {
                val surfaceY = h - h * level.value
                val top = (surfaceY - p.height - 6.dp.toPx()).roundToInt().coerceIn(pad, (h - p.height - pad).coerceAtLeast(pad))
                p.placeRelative(12.dp.roundToPx(), top)
            }
        }
    }
}
