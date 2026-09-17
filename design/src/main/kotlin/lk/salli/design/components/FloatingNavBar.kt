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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import lk.salli.design.motion.LocalReducedMotion

/** Compact floating capsule: selected tab expands to reveal its label; the others stay icons. */
@Composable
fun <T> FloatingNavBar(
    items: List<T>, selected: T?, onSelect: (T) -> Unit, label: (T) -> String,
    icon: (T) -> ImageVector, key: (T) -> String, modifier: Modifier = Modifier,
    selectedIcon: ((T) -> ImageVector)? = null, visible: Boolean = true,
) {
    val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = inset + 8.dp), contentAlignment = Alignment.Center) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = CircleShape, shadowElevation = 2.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    items.forEach { item ->
                        val chosen = selected != null && key(item) == key(selected)
                        NavItem(label(item), if (chosen) selectedIcon?.invoke(item) ?: icon(item) else icon(item), chosen, reduced) { onSelect(item) }
                    }
                }
            }
        }
    }
}

@Composable private fun NavItem(label: String, icon: ImageVector, selected: Boolean, reduced: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, tween(if (reduced) 0 else 260), label = "nav-bg")
    val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, tween(if (reduced) 0 else 260), label = "nav-fg")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(CircleShape).background(bg).semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(icon, if (selected) null else label, tint = fg, modifier = Modifier.size(20.dp))
        AnimatedVisibility(selected, enter = fadeIn(tween(if (reduced) 0 else 200)) + expandHorizontally(tween(if (reduced) 0 else 260)), exit = fadeOut(tween(if (reduced) 0 else 140)) + shrinkHorizontally(tween(if (reduced) 0 else 200))) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
