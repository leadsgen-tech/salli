package lk.salli.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Root theme. Defaults to light; a user-controlled toggle (Home top-right) flips into the
 * Burnt Peach / Deep Steel Blue dark palette. System dark-mode is deliberately not followed —
 * the two palettes are brand choices, not accessibility adaptations.
 */
@Composable
fun SalliTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = fallbackScheme(isDark = darkTheme),
        typography = SalliTypography,
        shapes = SalliShapes,
        content = content,
    )
}
