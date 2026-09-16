package lk.salli.design.components.stage

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lk.salli.design.theme.SalliTheme

/** Where a pull-to-refresh gesture has got to. */
enum class CapsulePhase {
    /** Finger is down and the capsule is growing out of the top edge. */
    Pulling,

    /** Far enough: let go and it will run. */
    Armed,

    /** Reading messages. */
    Refreshing,

    /** Finished, with something to say about it. */
    Done,

    /** Could not read the messages. */
    Failed,
}

/**
 * The pull-to-refresh indicator: a capsule that grows out of the top of the screen with a
 * message-bubble glyph inside it, and tells you what actually happened when it lets go.
 *
 * The old indicator was a wordless pill that spun and then vanished, so a refresh that found
 * nothing looked exactly like one that found three transactions. This one always ends on a
 * sentence — "3 new transactions", "Up to date", "Couldn't read messages".
 *
 * This is the **visual only**: no gesture, no nested scroll, no refresh call. Give it a phase, a
 * pull distance and a message and it draws them. Wiring it to `PullToRefreshBox` and to
 * `SmsRefresher.status` is the screen's job.
 *
 * @param distanceFraction how far through the pull the finger is, 0 to 1. Fills the glyph and
 *   grows the capsule while [phase] is [CapsulePhase.Pulling]; ignored afterwards.
 * @param message the line beside the glyph. Comes from `strings.xml`, never from here.
 * @param loadingIndicator the spinner shown while refreshing, in a [CapsuleDefaults.GlyphSize]
 *   box. It is a slot because material3 is pinned at 1.3.1 in this module; once the toolchain
 *   moves to 1.4, pass the Expressive `LoadingIndicator` here and the capsule needs no change.
 * @param action optional trailing control — the "Retry" button on [CapsulePhase.Failed].
 * @param reducedMotion drops the springs; the capsule simply fades.
 */
@Composable
fun StatusCapsule(
    phase: CapsulePhase,
    distanceFraction: Float,
    message: String,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    accent: Color = MaterialTheme.colorScheme.primary,
    positive: Color = CapsuleDefaults.positive(),
    onPositive: Color = CapsuleDefaults.onPositive(),
    negative: Color = MaterialTheme.colorScheme.error,
    action: (@Composable () -> Unit)? = null,
    loadingIndicator: @Composable () -> Unit = { CapsuleDefaults.LoadingIndicator(accent) },
) {
    val distance = distanceFraction.coerceIn(0f, 1f)

    // Pulling: the capsule grows with the finger. Everything after that is full size.
    val presence = if (phase == CapsulePhase.Pulling) distance else 1f

    // Armed gives one bounce, which is the moment the tick haptic belongs to.
    val armScale = remember { Animatable(1f) }
    LaunchedEffect(phase, reducedMotion) {
        if (phase == CapsulePhase.Armed && !reducedMotion) {
            armScale.animateTo(ARMED_OVERSHOOT, tween(durationMillis = 110))
            armScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
        } else {
            armScale.snapTo(1f)
        }
    }

    val glyphTone by animateColorAsState(
        targetValue = when (phase) {
            CapsulePhase.Done -> positive
            CapsulePhase.Failed -> negative
            else -> accent
        },
        animationSpec = if (reducedMotion) tween(0) else spring(),
        label = "capsule-tone",
    )
    val capsuleAlpha by animateFloatAsState(
        targetValue = if (phase == CapsulePhase.Pulling && distance <= 0f) 0f else 1f,
        animationSpec = if (reducedMotion) tween(0) else tween(durationMillis = 140),
        label = "capsule-alpha",
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(percent = 50),
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
        modifier = modifier
            .height(CapsuleDefaults.Height)
            .defaultMinSize(minWidth = CapsuleDefaults.Height)
            .graphicsLayer {
                val scale = if (reducedMotion) presence else presence * armScale.value
                scaleX = scale
                scaleY = scale
                alpha = capsuleAlpha
                // It comes out of the top edge, so it grows downward from its own top.
                transformOrigin = TransformOrigin(0.5f, 0f)
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(start = 12.dp, end = 16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(CapsuleDefaults.GlyphSize),
            ) {
                // Crossfading on the glyph rather than on the phase: arming does not swap the
                // bubble for another bubble, it just finishes filling the one already there.
                Crossfade(
                    targetState = phase.glyph(),
                    animationSpec = if (reducedMotion) tween(0) else tween(durationMillis = 180),
                    label = "capsule-glyph",
                ) { glyph ->
                    when (glyph) {
                        CapsuleGlyph.Bubble -> MessageBubbleGlyph(
                            fill = if (phase == CapsulePhase.Pulling) distance else 1f,
                            color = glyphTone,
                            modifier = Modifier.size(CapsuleDefaults.GlyphSize),
                        )

                        CapsuleGlyph.Spinner -> loadingIndicator()

                        // Acid Lime is a container colour, not an ink one: a lime tick on the
                        // near-white light surface would be all but invisible, so the disc is
                        // lime and the tick sits on it.
                        CapsuleGlyph.Check -> Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(CapsuleDefaults.GlyphSize)
                                .background(positive, CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = onPositive,
                                modifier = Modifier.size(CapsuleDefaults.GlyphSize * 0.72f),
                            )
                        }

                        CapsuleGlyph.Bang -> Icon(
                            imageVector = Icons.Rounded.PriorityHigh,
                            contentDescription = null,
                            tint = negative,
                            modifier = Modifier.size(CapsuleDefaults.GlyphSize),
                        )
                    }
                }
            }

            Text(
                text = message,
                style = MaterialTheme.typography.labelLarge,
                color = when (phase) {
                    CapsulePhase.Failed -> negative
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            action?.invoke()
        }
    }
}

/** Sizes and colours [StatusCapsule] falls back to. */
object CapsuleDefaults {
    /** Section 10 asks for a 36 dp capsule. */
    val Height: Dp = 36.dp
    val GlyphSize: Dp = 18.dp

    /**
     * Acid Lime, the brand's "this went well" colour, and the near-black that reads on top of
     * it. Literals for now because the semantic token layer is being built in parallel; pass
     * `LocalSalliColors.positive` / `onPositive` once they exist.
     */
    @Composable
    fun positive(): Color = SalliLime

    @Composable
    fun onPositive(): Color = SalliOnLime

    @Composable
    fun LoadingIndicator(color: Color) {
        CircularProgressIndicator(
            color = color,
            strokeWidth = 2.dp,
            modifier = Modifier.size(GlyphSize),
        )
    }

    private val SalliLime = Color(0xFFDFFF32)
    private val SalliOnLime = Color(0xFF111407)
}

private const val ARMED_OVERSHOOT = 1.06f

/** The four things the capsule can show. Fewer than there are phases, on purpose. */
private enum class CapsuleGlyph { Bubble, Spinner, Check, Bang }

private fun CapsulePhase.glyph(): CapsuleGlyph = when (this) {
    CapsulePhase.Pulling, CapsulePhase.Armed -> CapsuleGlyph.Bubble
    CapsulePhase.Refreshing -> CapsuleGlyph.Spinner
    CapsulePhase.Done -> CapsuleGlyph.Check
    CapsulePhase.Failed -> CapsuleGlyph.Bang
}

/**
 * The little speech bubble that fills up as you pull: outline always, solid from the bottom up
 * by [fill]. It is a message bubble because what is being fetched is messages.
 */
@Composable
private fun MessageBubbleGlyph(
    fill: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val body = h * 0.78f
        val corner = body * 0.3f
        val tailWidth = w * 0.22f

        val bubble = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = w,
                    bottom = body,
                    radiusX = corner,
                    radiusY = corner,
                ),
            )
            // The tail, bottom-left, the way a received message points.
            moveTo(w * 0.22f, body - 1f)
            lineTo(w * 0.22f, h)
            lineTo(w * 0.22f + tailWidth, body - 1f)
            close()
        }

        clipPath(bubble) {
            drawRect(
                color = color,
                topLeft = Offset(0f, h * (1f - fill.coerceIn(0f, 1f))),
                size = Size(w, h),
            )
        }
        drawPath(
            path = bubble,
            color = color,
            style = Stroke(width = size.minDimension * 0.1f, cap = StrokeCap.Round),
        )
    }
}

// --- previews -------------------------------------------------------------------------------

@Composable
private fun StatusCapsulePreview(dark: Boolean) {
    SalliTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StatusCapsule(
                    phase = CapsulePhase.Pulling,
                    distanceFraction = 0.55f,
                    message = "Pull to check messages",
                )
                StatusCapsule(
                    phase = CapsulePhase.Armed,
                    distanceFraction = 1f,
                    message = "Release to check",
                )
                StatusCapsule(
                    phase = CapsulePhase.Refreshing,
                    distanceFraction = 1f,
                    message = "Checking messages…",
                )
                StatusCapsule(
                    phase = CapsulePhase.Done,
                    distanceFraction = 1f,
                    message = "3 new transactions",
                )
                StatusCapsule(
                    phase = CapsulePhase.Done,
                    distanceFraction = 1f,
                    message = "Up to date",
                )
                StatusCapsule(
                    phase = CapsulePhase.Failed,
                    distanceFraction = 1f,
                    message = "Couldn't read messages",
                    action = {
                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
        }
    }
}

@Preview(name = "Status capsule · light", showBackground = true, widthDp = 380)
@Composable
private fun StatusCapsuleLightPreview() = StatusCapsulePreview(dark = false)

@Preview(name = "Status capsule · dark", showBackground = true, widthDp = 380)
@Composable
private fun StatusCapsuleDarkPreview() = StatusCapsulePreview(dark = true)
