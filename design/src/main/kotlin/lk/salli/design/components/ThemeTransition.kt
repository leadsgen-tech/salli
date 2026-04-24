package lk.salli.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Telegram-style circular theme transition.
 *
 * The wrapper continuously records the child content into a `GraphicsLayer`. When the user
 * requests a transition (usually from a theme toggle), we:
 *   1. Snapshot the current layer into an `ImageBitmap` — this freezes the "before" frame.
 *   2. Invoke the caller's `flip` callback to actually switch the theme (next recomposition
 *      renders the new palette behind the overlay).
 *   3. Draw the snapshot over the top and animate a transparent circle growing from the
 *      user's tap point. `BlendMode.Clear` on an offscreen layer punches the circle out of
 *      the snapshot, revealing the new theme through it.
 *   4. Once the circle covers the viewport diagonal, drop the overlay.
 *
 * Same idea as the Web's View Transitions API with a circular clip-path reveal.
 */
class ThemeTransitionController internal constructor(
    private val scope: CoroutineScope,
    private val capture: suspend () -> ImageBitmap,
    private val size: () -> IntSize,
) {
    internal var overlay: OverlayState? by mutableStateOf(null)
        private set

    fun request(center: Offset, flip: () -> Unit) {
        if (overlay != null) return  // ignore re-entrancy mid-animation
        scope.launch {
            val bitmap = capture()
            flip()
            val sz = size()
            // Target radius = diagonal, with a small buffer so the circle fully passes the
            // furthest corner instead of stopping exactly on it.
            val targetR = hypot(sz.width.toFloat(), sz.height.toFloat()) * 1.05f
            val anim = Animatable(0f)
            overlay = OverlayState(bitmap, center, 0f)
            anim.animateTo(
                targetValue = targetR,
                animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            ) {
                overlay = OverlayState(bitmap, center, value)
            }
            overlay = null
        }
    }
}

internal data class OverlayState(
    val bitmap: ImageBitmap,
    val center: Offset,
    val radius: Float,
)

/**
 * Exposed to descendants so a theme toggle (anywhere in the tree) can kick off a transition.
 * Null when the tree isn't wrapped in [ThemeTransitionLayer] — callers should fall back to
 * invoking their flip action directly in that case.
 */
val LocalThemeTransition = compositionLocalOf<ThemeTransitionController?> { null }

@Composable
fun ThemeTransitionLayer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    val controller = remember(graphicsLayer, scope) {
        ThemeTransitionController(
            scope = scope,
            capture = { graphicsLayer.toImageBitmap() },
            size = { boxSize },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    graphicsLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(graphicsLayer)
                },
        ) {
            CompositionLocalProvider(LocalThemeTransition provides controller) {
                content()
            }
        }

        val overlay = controller.overlay
        if (overlay != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            ) {
                // Paint the snapshot first — it covers whatever the new-themed content is
                // drawing below. Then punch a transparent hole centred on the tap with
                // BlendMode.Clear. The offscreen compositing strategy keeps the clear op
                // scoped to this canvas only.
                drawImage(overlay.bitmap)
                drawCircle(
                    color = Color.Transparent,
                    radius = overlay.radius,
                    center = overlay.center,
                    blendMode = BlendMode.Clear,
                )
            }
        }
    }
}
