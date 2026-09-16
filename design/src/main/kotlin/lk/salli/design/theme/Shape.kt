package lk.salli.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Crisp rounded geometry: tighter controls, relaxed cards, and roomier modal sheets.
 */
internal val SalliShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object SalliShapeTokens {
    /** Fully rounded controls and filter chips. */
    val pill = RoundedCornerShape(percent = 50)

    /** High-emphasis financial hero surfaces. */
    val bannerLarge = RoundedCornerShape(24.dp)

    /** Modal bottom sheets. */
    val bottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}
