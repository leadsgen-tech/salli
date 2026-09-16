package lk.salli.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalAtm
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lk.salli.design.theme.LocalSalliColors
import lk.salli.design.theme.SalliShapeTokens

/**
 * The single category-icon mapper.
 *
 * Keyed on the **Material icon names the seed data actually stores** (`shopping_cart`,
 * `directions_car`, `bolt`, `payments`…). The previous mapper lived privately in
 * `BudgetsScreen` and keyed on a different, invented vocabulary (`grocery`, `car`, `bill`), so
 * almost every seeded category fell through to a generic receipt. Those legacy keys are kept
 * as aliases below — user-created categories may still carry them — but the seed names are the
 * contract.
 *
 * If you add a seed category, add its icon name here in the same commit.
 */
fun categoryIconFor(iconName: String?): ImageVector = when (iconName?.lowercase()?.trim()) {
    // --- names used by SeedCategories -------------------------------------------------
    "shopping_cart" -> Icons.Outlined.ShoppingCart
    "restaurant" -> Icons.Outlined.Restaurant
    "directions_car" -> Icons.Outlined.DirectionsCar
    "local_gas_station" -> Icons.Outlined.LocalGasStation
    "bolt" -> Icons.Outlined.Bolt
    "subscriptions" -> Icons.Outlined.Subscriptions
    "shopping_bag" -> Icons.Outlined.ShoppingBag
    "medical_services" -> Icons.Outlined.MedicalServices
    "school" -> Icons.Outlined.School
    "movie" -> Icons.Outlined.Movie
    "home" -> Icons.Outlined.Home
    "payments" -> Icons.Outlined.Payments
    "swap_horiz" -> Icons.Outlined.SwapHoriz
    "local_atm" -> Icons.Outlined.LocalAtm
    "receipt_long" -> Icons.AutoMirrored.Outlined.ReceiptLong
    "category" -> Icons.Outlined.Category

    // --- extras a user might pick when creating their own category --------------------
    "local_cafe", "cafe", "coffee" -> Icons.Outlined.LocalCafe
    "local_taxi", "taxi", "ride" -> Icons.Outlined.LocalTaxi
    "flight", "travel" -> Icons.Outlined.Flight
    "pets", "pet" -> Icons.Outlined.Pets

    // --- legacy keys from the old BudgetsScreen mapper --------------------------------
    "grocery", "groceries" -> Icons.Outlined.ShoppingCart
    "fastfood", "food" -> Icons.Outlined.Restaurant
    "transport", "car" -> Icons.Outlined.DirectionsCar
    "fuel" -> Icons.Outlined.LocalGasStation
    "utilities", "bill" -> Icons.Outlined.Bolt
    "shopping" -> Icons.Outlined.ShoppingBag
    "healthcare", "medical" -> Icons.Outlined.MedicalServices
    "education" -> Icons.Outlined.School
    "entertainment", "movies" -> Icons.Outlined.Movie
    "rent" -> Icons.Outlined.Home
    "salary", "income" -> Icons.Outlined.Payments
    "cash", "atm" -> Icons.Outlined.LocalAtm
    "fees" -> Icons.AutoMirrored.Outlined.ReceiptLong

    else -> Icons.Outlined.Category
}

/**
 * A category's icon on its tinted container — the 40 dp leading tile used by transaction rows,
 * category lists and chips. Pass the category's stored `colorSeed`; the hue comes from the
 * palette in [lk.salli.design.theme.SalliColors].
 */
@Composable
fun CategoryIcon(
    iconName: String?,
    colorSeed: Int,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String? = null,
) {
    val hue = LocalSalliColors.current.categoryHue(colorSeed)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(SalliShapeTokens.row)
            .background(hue.container),
    ) {
        Icon(
            imageVector = categoryIconFor(iconName),
            contentDescription = contentDescription,
            tint = hue.onContainer,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** Untinted variant for places that supply their own background (chips, filter grids). */
@Composable
fun CategoryGlyph(
    iconName: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalSalliColors.current.expense,
    size: Dp = 18.dp,
    contentDescription: String? = null,
) {
    Icon(
        imageVector = categoryIconFor(iconName),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}

@SalliPreview
@Composable
private fun CategoryIconPreview() {
    PreviewFrame {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryIcon(iconName = "shopping_cart", colorSeed = 0)
            CategoryIcon(iconName = "restaurant", colorSeed = 1)
            CategoryIcon(iconName = "directions_car", colorSeed = 2)
            CategoryIcon(iconName = "bolt", colorSeed = 4)
            CategoryIcon(iconName = "payments", colorSeed = 9)
            CategoryIcon(iconName = "not_a_real_icon", colorSeed = 11)
        }
    }
}
