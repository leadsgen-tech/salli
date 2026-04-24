package lk.salli.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Revolut-style shape language: pill buttons everywhere, 20dp on cards, 28dp on sheets. Pull
 * from the [Shapes] slots for component overrides or from [SalliShapeTokens] for one-offs.
 */
internal val SalliShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    // 20dp feature cards — Revolut's product-grid radius.
    large = RoundedCornerShape(20.dp),
    // 28dp modal sheets.
    extraLarge = RoundedCornerShape(28.dp),
)

object SalliShapeTokens {
    /** Revolut: 9999px radius on every button. */
    val pill = RoundedCornerShape(percent = 50)

    /** Oversized hero surfaces — top-app-bar-sized, or full-width banners. */
    val bannerLarge = RoundedCornerShape(32.dp)

    /** Modal bottom sheets. */
    val bottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}
