package lk.salli.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Six radii, no others. Anything that needs a corner picks one of these; a screen that invents
 * a seventh is a screen that drifted.
 *
 *   hero 28 · card 20 · tile 16 · row 14 · pill 50 % · sheet top 28
 *
 * The M3 [Shapes] slots are wired so stock components land on the same ladder:
 * `extraSmall` → row, `small` → tile, `medium` → tile, `large` → card, `extraLarge` → hero.
 */
object SalliShapeTokens {
    /** Home hero card and the onboarding stage. */
    val hero = RoundedCornerShape(28.dp)

    /** Standard card / grouped-list container. */
    val card = RoundedCornerShape(20.dp)

    /** Stat tiles, leading avatars' rounded-square variant, inline banners. */
    val tile = RoundedCornerShape(16.dp)

    /** Individual rows and small inline surfaces. */
    val row = RoundedCornerShape(14.dp)

    /** Buttons, chips, the nav lozenge. */
    val pill = RoundedCornerShape(percent = 50)

    /** Modal bottom sheets — top corners only. */
    val bottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

internal val SalliShapes = Shapes(
    extraSmall = SalliShapeTokens.row,
    small = SalliShapeTokens.tile,
    medium = SalliShapeTokens.tile,
    large = SalliShapeTokens.card,
    extraLarge = SalliShapeTokens.hero,
)
