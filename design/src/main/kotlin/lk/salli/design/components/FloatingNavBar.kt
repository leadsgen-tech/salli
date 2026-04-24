package lk.salli.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/**
 * A distinctive floating navigation bar — bottom-anchored pill container with each destination
 * as an icon; the selected one expands into a labeled pill in the primary colour. Unselected
 * tabs are icon-only on the container surface.
 *
 * When [hazeState] is provided the pill's background is a live blur of whatever content is
 * drawn behind it (content below the nav should call `Modifier.haze(hazeState)`). When null it
 * falls back to an opaque `surfaceContainerHighest` fill.
 *
 * Intended to be drawn as an overlay inside a `Box` — NOT inside `Scaffold.bottomBar` — so
 * content can scroll underneath and get blurred through the pill.
 */
@Composable
fun <T> FloatingNavBar(
    items: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String,
    icon: (T) -> ImageVector,
    key: (T) -> String,
    hazeState: HazeState? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val pillBg: Modifier = if (hazeState != null) {
        Modifier.hazeChild(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = scheme.surface,
                tint = HazeTint(scheme.surfaceContainerHighest.copy(alpha = 0.55f)),
                blurRadius = 24.dp,
            ),
        )
    } else {
        Modifier.background(scheme.surfaceContainerHighest)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = navBarInset + 8.dp,
                top = 8.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(CircleShape)
                .then(pillBg)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            items.forEach { item ->
                val isSelected = selected != null && key(item) == key(selected)
                NavItem(
                    label = label(item),
                    icon = icon(item),
                    selected = isSelected,
                    onClick = { onSelect(item) },
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        targetValue = if (selected) scheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 260),
        label = "nav-item-bg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) scheme.onPrimary else scheme.onSurface,
        animationSpec = tween(durationMillis = 260),
        label = "nav-item-fg",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (selected) null else label,
            tint = fg,
            modifier = Modifier.size(20.dp),
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(200)) + expandHorizontally(tween(260)),
            exit = fadeOut(tween(140)) + shrinkHorizontally(tween(200)),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
