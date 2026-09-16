package lk.salli.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import lk.salli.design.theme.LocalSalliColors
import lk.salli.design.theme.SalliShapeTokens
import lk.salli.design.theme.SalliSpacing

/**
 * A category as a tappable pill: its icon, its name, its hue.
 *
 * Unselected reads as a tint of the category's own colour so a grid of chips is scannable by
 * colour alone; selected fills with that colour. Used by the detail sheet's category picker
 * and the Activity filter sheet.
 */
@Composable
fun CategoryChip(
    name: String,
    iconName: String?,
    colorSeed: Int,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val hue = LocalSalliColors.current.categoryHue(colorSeed)
    val background by animateColorAsState(
        targetValue = if (selected) hue.accent else hue.container,
        label = "category-chip-bg",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.surfaceContainerLowest else hue.onContainer,
        label = "category-chip-fg",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .background(background, SalliShapeTokens.pill)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .defaultMinSize(minHeight = 36.dp)
            .padding(horizontal = SalliSpacing.sm, vertical = SalliSpacing.xxs),
    ) {
        CategoryGlyph(iconName = iconName, tint = content, size = 16.dp)
        Spacer(Modifier.width(SalliSpacing.xs))
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = content,
        )
    }
}

@SalliPreview
@Composable
private fun CategoryChipPreview() {
    PreviewFrame {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryChip(name = "Groceries", iconName = "shopping_cart", colorSeed = 0, selected = true)
            CategoryChip(name = "Food", iconName = "restaurant", colorSeed = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryChip(name = "Transport", iconName = "directions_car", colorSeed = 2)
            CategoryChip(name = "Utilities", iconName = "bolt", colorSeed = 4)
        }
    }
}
