package lk.salli.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Root theme. Cobalt carries primary actions and financial focus; acid lime is reserved for
 * high-value status surfaces such as safe-to-spend. The user-controlled toggle is retained.
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
