package lk.salli.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import lk.salli.design.motion.LocalReducedMotion
import lk.salli.design.motion.LocalSalliMotion
import lk.salli.design.motion.SalliMotion
import lk.salli.design.motion.rememberReducedMotion

/**
 * Root theme. Cobalt carries primary actions and financial focus; acid lime is reserved for
 * high-value status surfaces such as safe-to-spend.
 *
 * Three things are provided here and nowhere else:
 *
 *  1. **Semantic colours.** [LocalSalliColors] carries income / expense / positive / warning /
 *     negative / transfer / hero and the 12-hue category ramp. Screens read those rather than
 *     re-deciding what green means.
 *  2. **Expressive motion.** [LocalSalliMotion] carries the M3 Expressive spring set. The spec
 *     asked for `MaterialExpressiveTheme(motionScheme = MotionScheme.expressive())`; both are
 *     `internal` in material3 1.4.0 stable (public only on the 1.5.0-alpha line), so
 *     [SalliMotion] reproduces the exact token values — see its KDoc for the table and for how
 *     to switch over once the API opens up.
 *  3. **Reduced motion.** [LocalReducedMotion] mirrors the system animator scale, re-read on
 *     every resume, so hand-rolled animations can fall back to instant.
 *
 * [darkTheme] is a resolved boolean, not a preference: the caller decides how System / Light /
 * Dark collapses into it (`ThemeMode.resolve` in `:data`).
 */
@Composable
fun SalliTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val reducedMotion by rememberReducedMotion()
    val salliColors = if (darkTheme) DarkSalliColors else LightSalliColors

    CompositionLocalProvider(
        LocalSalliColors provides salliColors,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = fallbackScheme(isDark = darkTheme),
            typography = SalliTypography,
            shapes = SalliShapes,
            content = content,
        )
    }
}
