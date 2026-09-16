package lk.salli.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lk.salli.design.theme.LocalSalliColors
import lk.salli.design.theme.SalliShapeTokens
import lk.salli.design.theme.SalliSpacing

/**
 * Inset grouped container — one rounded `surfaceContainerLowest` block, hairlines between
 * rows, nothing between the block and the screen gutter. The iOS grouped-table feel, in M3
 * colours, because it reads faster than a stack of individually floating cards when the rows
 * are a list of facts rather than a list of objects.
 *
 * **Never put a scrollable inside this.** It is a plain `Column`; the caller supplies the
 * scroll (usually one `LazyColumn` for the whole screen). Nesting a second scroller here is
 * what makes long lists drop frames.
 */
@Composable
fun GroupedList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = SalliShapeTokens.card,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

/** 1 dp hairline between two [ListRow]s. Call it between rows, not after the last one. */
@Composable
fun ListDivider(startInset: Dp = SalliSpacing.md) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startInset)
            .height(1.dp)
            .background(LocalSalliColors.current.hairline),
    )
}

/**
 * One row of a [GroupedList]: 56 dp minimum height, 16 dp padding, optional leading slot,
 * title + optional subtitle, optional trailing slot. Supply [onClick] and the row gets a
 * chevron unless the caller already filled the trailing slot.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null && trailing == null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = SalliSpacing.md, vertical = SalliSpacing.sm),
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(SalliSpacing.sm))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(SalliSpacing.sm))
            trailing()
        }
        if (showChevron) {
            Spacer(Modifier.width(SalliSpacing.xxs))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Section heading above a group. Sentence case, never caps — SCREAMING LABELS are a 2014
 * Android tic and they cost a line of visual weight for no information.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 32.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = SalliSpacing.xs)) {
                Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@SalliPreview
@Composable
private fun GroupedListPreview() {
    PreviewFrame {
        SectionHeader(title = "Up next", actionLabel = "See all", onAction = {})
        GroupedList {
            ListRow(
                title = "SLT bill",
                subtitle = "Due in 3 days",
                leading = { CategoryIcon(iconName = "bolt", colorSeed = 4) },
                trailing = { StatusPill(text = "Rs 11,953", tone = SalliTone.WARNING) },
                onClick = {},
            )
            ListDivider()
            ListRow(
                title = "Netflix",
                subtitle = "Recurring · Friday",
                leading = { CategoryIcon(iconName = "subscriptions", colorSeed = 5) },
                onClick = {},
            )
            ListDivider()
            ListRow(
                title = "Fuel Pass",
                subtitle = "1 vehicle",
                leading = { CategoryIcon(iconName = "local_gas_station", colorSeed = 3) },
                trailing = { StatusPill(text = "Eligible today", tone = SalliTone.POSITIVE) },
                onClick = {},
            )
        }
    }
}
