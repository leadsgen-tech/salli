package lk.salli.design.components.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lk.salli.design.theme.SalliTheme
import lk.salli.domain.motion.OdometerColumn
import lk.salli.domain.motion.OdometerDigits

/**
 * A number that changes by rolling, one digit wheel at a time, the way a meter does.
 *
 * Every hero amount in the app is one of these: Home's "spent this period", Plan's "spoken for",
 * the day nets, and the count in onboarding Act 3. The point is that a changing total should
 * look like the same number moving rather than a new number replacing the old one, so you can
 * see *which* digits moved and roughly how far.
 *
 * Only digits roll. The "Rs", the thousands commas, the decimal point and a minus sign stay
 * exactly where they are, which keeps the whole thing from sliding about. Every wheel turns the
 * same way — up when the amount grew, down when it shrank — because digits counting in opposite
 * directions read as a glitch.
 *
 * Wheels are laid out on the measured width of a single digit, so this wants a tabular-figures
 * style: Space Grotesk with `tnum`, which is what Salli's typography already sets.
 *
 * With [reducedMotion] the number simply changes.
 *
 * @param text the formatted amount, straight from `MoneyFormat`.
 * @param style the text style. Use a display or headline style; the wheel height comes from it.
 */
@Composable
fun SpringOdometer(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displaySmall,
    color: Color = Color.Unspecified,
    reducedMotion: Boolean = false,
    animationSpec: AnimationSpec<Float> = OdometerDefaults.Roll,
) {
    val resolvedColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val metrics = remember(style, measurer, density) {
        val layout = measurer.measure(AnnotatedString("0"), style)
        with(density) { DigitMetrics(layout.size.width.toDp(), layout.size.height.toDp()) }
    }

    // `from` is what the wheels are leaving, `to` is what they are landing on. Both move at the
    // start of a roll, not the end, so a value that changes again mid-roll still rolls from the
    // number that was on screen rather than from two values ago.
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf(text) }
    val columns = remember(from, to) { OdometerDigits.columns(from, to) }
    val roll = remember { Animatable(1f) }

    LaunchedEffect(text) {
        if (to == text) return@LaunchedEffect
        from = to
        to = text
        if (reducedMotion) {
            roll.snapTo(1f)
        } else {
            roll.snapTo(0f)
            roll.animateTo(1f, animationSpec)
        }
    }

    Row(
        // `clearAndSetSemantics`, not `semantics`: each wheel stacks up to five Texts, including
        // the clipped overshoot digit, and TalkBack would otherwise read every one of them.
        modifier = modifier.clearAndSetSemantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        columns.forEachIndexed { index, column ->
            when {
                !column.rolls -> Text(
                    text = column.char.toString(),
                    style = style,
                    color = resolvedColor,
                    maxLines = 1,
                )

                else -> DigitWheel(
                    column = column,
                    progress = {
                        OdometerDigits.staggeredProgress(
                            progress = roll.value,
                            index = index,
                            count = columns.size,
                            stagger = OdometerDefaults.Stagger,
                        )
                    },
                    metrics = metrics,
                    style = style,
                    color = resolvedColor,
                )
            }
        }
    }
}

/** Tuning for [SpringOdometer]. */
object OdometerDefaults {
    /**
     * Stands in for `MotionScheme.expressive().fastSpatialSpec` until material3 1.4 lands.
     * Slightly bouncy: the number lands with a nudge, not a thud.
     */
    val Roll: AnimationSpec<Float> = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessLow)

    /** How much of the roll the leftmost column waits out before it starts. */
    const val Stagger: Float = OdometerDigits.DEFAULT_STAGGER

    /**
     * A wheel that has to travel further than this skips the middle of its roll; nine stacked
     * texts to watch a 3 become a 2 is not worth the layers.
     */
    const val MaxVisibleSteps: Int = OdometerDigits.DEFAULT_MAX_VISIBLE_STEPS
}

private data class DigitMetrics(val width: Dp, val height: Dp)

/**
 * One digit's worth of wheel: a strip of digits in the roll's direction, clipped to a single
 * digit's box and slid along.
 */
@Composable
private fun DigitWheel(
    column: OdometerColumn,
    progress: () -> Float,
    metrics: DigitMetrics,
    style: TextStyle,
    color: Color,
) {
    val steps = OdometerDigits.visibleSteps(column, OdometerDefaults.MaxVisibleSteps)
    val digits = remember(column.from, column.to, column.direction, steps) {
        OdometerDigits.wheelDigits(column, OdometerDefaults.MaxVisibleSteps)
    }
    val heightPx = with(LocalDensity.current) { metrics.height.toPx() }

    Box(
        modifier = Modifier
            .width(metrics.width)
            .height(metrics.height)
            .clipToBounds(),
    ) {
        Column(
            modifier = Modifier.graphicsLayer {
                // The strip runs from `from` down to `to`, so sliding it up by `steps` cells
                // brings the destination digit into the window. No upper clamp: the spring
                // overshoots into the spare digit past the target and settles back, which is
                // exactly what a real wheel does.
                translationY = -progress().coerceAtLeast(0f) * steps * heightPx
            },
        ) {
            digits.forEach { digit ->
                Text(
                    text = digit.toString(),
                    style = style,
                    color = color,
                    maxLines = 1,
                    modifier = Modifier.height(metrics.height),
                )
            }
        }
    }
}

// --- previews -------------------------------------------------------------------------------

@Composable
private fun SpringOdometerPreview(dark: Boolean) {
    SalliTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Spent this period", style = MaterialTheme.typography.labelMedium)
                SpringOdometer(text = "Rs 84,200.00")
                Text("Transactions found", style = MaterialTheme.typography.labelMedium)
                SpringOdometer(
                    text = "890",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("Safe today", style = MaterialTheme.typography.labelMedium)
                SpringOdometer(text = "Rs 4,120.00", style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

@Preview(name = "Spring odometer · light", showBackground = true, widthDp = 380)
@Composable
private fun SpringOdometerLightPreview() = SpringOdometerPreview(dark = false)

@Preview(name = "Spring odometer · dark", showBackground = true, widthDp = 380)
@Composable
private fun SpringOdometerDarkPreview() = SpringOdometerPreview(dark = true)
