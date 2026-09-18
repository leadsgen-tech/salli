package lk.salli.design.components.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import lk.salli.design.haptics.rememberSalliHaptics
import lk.salli.design.theme.LocalSalliColors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** A labelled position on the dial: a past period, the usual amount, or a named stop. */
data class DialMark(val label: String, val minor: Long)

/**
 * A cap you pull rather than type. The track runs from zero to [maxMinor]; drag anywhere on it
 * and the fill follows the finger, snapping to [stepMinor] and to the named [stops] with a tick
 * haptic at every detent. Ghost ticks mark what the last periods actually cost, and the darker
 * [usualMinor] tick is their median, so the cap is set against your own history rather than a
 * guess. Release and the fill springs onto its detent: a rubber band, not a slider.
 *
 * The number itself is drawn by the caller (usually a [SpringOdometer]) so it can use the
 * screen's own type scale.
 */
@Composable
fun CapDial(
    valueMinor: Long,
    maxMinor: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    ghosts: List<DialMark> = emptyList(),
    usualMinor: Long? = null,
    stops: List<DialMark> = emptyList(),
    stepMinor: Long = 100_000L,
    reducedMotion: Boolean = false,
) {
    val haptics = rememberSalliHaptics()
    val currentOnChange by rememberUpdatedState(onChange)
    val scope = rememberCoroutineScope()
    val display = remember { Animatable(fraction(valueMinor, maxMinor)) }
    var dragging by remember { mutableStateOf(false) }
    var lastDetent by remember { mutableLongStateOf(valueMinor) }

    LaunchedEffect(valueMinor, maxMinor, dragging) {
        if (!dragging) display.animateTo(fraction(valueMinor, maxMinor), spring(dampingRatio = 0.55f, stiffness = 320f))
    }

    fun snap(raw: Long): Long {
        val clamped = raw.coerceIn(0L, maxMinor)
        val magnet = maxMinor * 0.025
        stops.firstOrNull { abs(it.minor - clamped) < magnet }?.let { return it.minor }
        return ((clamped.toDouble() / stepMinor).roundToLong() * stepMinor).coerceIn(0L, maxMinor)
    }

    val salli = LocalSalliColors.current
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val fillColor = salli.hero
    val ghostColor = MaterialTheme.colorScheme.outlineVariant
    val usualColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val knobFill = MaterialTheme.colorScheme.surfaceContainerLowest
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.semantics { contentDescription = "Cap dial" }) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .pointerInput(maxMinor, stepMinor, stops) {
                    awaitEachGesture {
                        val width = size.width.toFloat()
                        fun set(x: Float, settle: Boolean) {
                            val rawFraction = (x / width).coerceIn(0f, 1f)
                            val snapped = snap((rawFraction * maxMinor).roundToLong())
                            if (snapped != lastDetent) {
                                lastDetent = snapped
                                haptics.tick()
                            }
                            currentOnChange(snapped)
                            scope.launch {
                                if (settle || reducedMotion) {
                                    display.animateTo(fraction(snapped, maxMinor), spring(dampingRatio = 0.55f, stiffness = 320f))
                                } else {
                                    display.snapTo(rawFraction)
                                }
                            }
                        }
                        val down = awaitFirstDown()
                        dragging = true
                        set(down.position.x, settle = false)
                        drag(down.id) { change ->
                            set(change.position.x, settle = false)
                            change.consume()
                        }
                        dragging = false
                        set((display.value * width), settle = true)
                    }
                },
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val knob = 40.dp
            val trackTop = 60.dp
            val trackHeight = 14.dp

            Canvas(modifier = Modifier.fillMaxWidth().height(112.dp)) {
                val trackY = trackTop.toPx()
                val th = trackHeight.toPx()
                // Ghost ticks: the last periods, rising out of the track.
                ghosts.forEach { g ->
                    val x = fraction(g.minor, maxMinor) * size.width
                    drawLine(ghostColor, Offset(x, trackY - 34.dp.toPx()), Offset(x, trackY + th), strokeWidth = 2.dp.toPx())
                }
                usualMinor?.let { u ->
                    val x = fraction(u, maxMinor) * size.width
                    drawLine(usualColor, Offset(x, trackY - 44.dp.toPx()), Offset(x, trackY + th), strokeWidth = 2.dp.toPx())
                }
                drawRoundRect(trackColor, Offset(0f, trackY), Size(size.width, th), CornerRadius(th / 2f))
                val fillW = (display.value * size.width).coerceIn(0f, size.width)
                drawRoundRect(fillColor, Offset(0f, trackY), Size(fillW, th), CornerRadius(th / 2f))
                // Stop ticks sit inside the track so they read as detents, not as data.
                stops.forEach { s ->
                    val x = fraction(s.minor, maxMinor) * size.width
                    drawLine(Color.White.copy(alpha = 0.7f), Offset(x, trackY + 3.dp.toPx()), Offset(x, trackY + th - 3.dp.toPx()), strokeWidth = 2.dp.toPx())
                }
                // Knob.
                val kx = fillW
                val ky = trackY + th / 2f
                drawCircle(fillColor.copy(alpha = 0.22f), radius = knob.toPx() / 2f + 4.dp.toPx(), center = Offset(kx, ky))
                drawCircle(knobFill, radius = knob.toPx() / 2f, center = Offset(kx, ky))
                drawCircle(fillColor, radius = knob.toPx() / 2f, center = Offset(kx, ky), style = Stroke(width = 3.dp.toPx()))
            }
            // Ghost labels, alternating heights so neighbours never collide.
            ghosts.forEachIndexed { index, g ->
                val x = fraction(g.minor, maxMinor) * widthPx
                Text(
                    text = g.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    maxLines = 1,
                    modifier = Modifier.offset {
                        // Centred on the tick, but never pushed off either edge of the dial.
                        val labelWidth = 72.dp.toPx()
                        val left = (x - labelWidth / 2f).coerceIn(0f, (widthPx - labelWidth).coerceAtLeast(0f))
                        IntOffset(left.roundToInt(), (if (index % 2 == 0) 0.dp else 14.dp).roundToPx())
                    },
                )
            }
        }
        if (stops.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stops.forEach { s ->
                    val hit = s.minor == valueMinor
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (hit) fillColor else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { onChange(s.minor) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = s.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hit) salli.onHero else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

private fun fraction(minor: Long, max: Long): Float =
    if (max <= 0L) 0f else (minor.toFloat() / max).coerceIn(0f, 1f)
