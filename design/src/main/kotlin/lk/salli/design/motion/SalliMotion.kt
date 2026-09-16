package lk.salli.design.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The M3 Expressive motion scheme, as springs Salli can actually call.
 *
 * **Why this exists instead of `MotionScheme.expressive()`.** The design spec asks for
 * `MaterialExpressiveTheme(motionScheme = MotionScheme.expressive())`. Those APIs ship inside
 * `compose-material3` **1.4.0** — the latest stable 1.4.x — but they are marked `internal`
 * there; only the 1.5.0-alpha line exposes them publicly. Salli is the owner's daily money app
 * and goes to F-Droid, so pinning the whole UI to a Compose alpha to reach six spring
 * constants is a bad trade. Instead the constants are reproduced here, taken verbatim from
 * `androidx.compose.material3.tokens.ExpressiveMotionTokens` in the 1.4.0 artifact:
 *
 * | spec            | damping | stiffness |
 * |-----------------|---------|-----------|
 * | defaultSpatial  | 0.8     | 380       |
 * | fastSpatial     | 0.6     | 800       |
 * | slowSpatial     | 0.8     | 200       |
 * | defaultEffects  | 1.0     | 1600      |
 * | fastEffects     | 1.0     | 3800      |
 * | slowEffects     | 1.0     | 800       |
 *
 * **Spatial** specs move things (position, size, corner radius) and are allowed to overshoot;
 * **effects** specs change things that shouldn't wobble (colour, alpha) and are critically
 * damped. Picking the wrong one is what makes a UI feel cheap: a colour that bounces reads as
 * a bug.
 *
 * When material3 makes the real API public, delete this file, swap `SalliTheme` to
 * `MaterialExpressiveTheme`, and point [LocalSalliMotion] at `MaterialTheme.motionScheme`.
 */
@Immutable
class SalliMotion {
    fun <T> defaultSpatial(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = 380f)

    fun <T> fastSpatial(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.6f, stiffness = 800f)

    fun <T> slowSpatial(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = 200f)

    fun <T> defaultEffects(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 1600f)

    fun <T> fastEffects(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 3800f)

    fun <T> slowEffects(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 800f)
}

val LocalSalliMotion = staticCompositionLocalOf { SalliMotion() }

/**
 * Motion accessor that already honours reduced motion: with the system animator scale at zero
 * every spec collapses to [snap], so a caller writes `SalliMotionSpecs.spatial()` once instead
 * of branching on [LocalReducedMotion] at every animation.
 */
object SalliMotionSpecs {

    @Composable
    @ReadOnlyComposable
    fun <T> defaultSpatial(): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) snap() else LocalSalliMotion.current.defaultSpatial()

    @Composable
    @ReadOnlyComposable
    fun <T> fastSpatial(): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) snap() else LocalSalliMotion.current.fastSpatial()

    @Composable
    @ReadOnlyComposable
    fun <T> slowSpatial(): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) snap() else LocalSalliMotion.current.slowSpatial()

    @Composable
    @ReadOnlyComposable
    fun <T> defaultEffects(): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) snap() else LocalSalliMotion.current.defaultEffects()

    @Composable
    @ReadOnlyComposable
    fun <T> fastEffects(): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) snap() else LocalSalliMotion.current.fastEffects()

    @Composable
    @ReadOnlyComposable
    fun <T> slowEffects(): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) snap() else LocalSalliMotion.current.slowEffects()
}
