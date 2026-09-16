package lk.salli.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lk.salli.design.motion.LocalReducedMotion
import lk.salli.design.theme.LocalSalliColors
import lk.salli.design.theme.SalliSpacing

/**
 * Salli's bottom navigation: a floating pill with four destinations, drawn as an overlay in a
 * `Box` rather than in `Scaffold.bottomBar` so content scrolls underneath it.
 *
 * The signature is the **lime lozenge** behind the selected item. It is the one place the
 * accent appears unconditionally, it never moves house, and it is why the bar is recognisably
 * this app's and not the framework's. Alongside it: the icon swaps outlined → filled on
 * select, and springs 1 → 1.06 → 1 so the tap has a physical result.
 *
 * Labels stay visible on every item. An icon-only bar saves 10 dp and costs a guess.
 *
 * @param visible drives the hide-on-scroll behaviour. The bar slides off the bottom edge
 *   rather than fading, so it reads as "moved out of the way", not "broken". Wave 2 wires this
 *   to each tab's `nestedScroll`; until then it stays `true` and nothing moves.
 */
@Composable
fun <T> FloatingNavBar(
    items: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    icon: (T) -> ImageVector,
    key: (T) -> String,
    modifier: Modifier = Modifier,
    selectedIcon: ((T) -> ImageVector)? = null,
    visible: Boolean = true,
) {
    val reducedMotion = LocalReducedMotion.current
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) fadeIn(snap()) else slideInVertically { it } + fadeIn(),
        exit = if (reducedMotion) fadeOut(snap()) else slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = SalliSpacing.sm,
                        end = SalliSpacing.sm,
                        bottom = navBarInset + SalliSpacing.xs,
                    top = 0.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 2.dp,
                    modifier = Modifier.widthIn(max = 360.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(horizontal = 4.dp),
                ) {
                    items.forEach { item ->
                        val isSelected = selected != null && key(item) == key(selected)
                        NavItem(
                            label = label(item),
                            icon = if (isSelected) {
                                selectedIcon?.invoke(item) ?: icon(item)
                            } else {
                                icon(item)
                            },
                            selected = isSelected,
                            reducedMotion = reducedMotion,
                            onClick = { onSelect(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit,
) {
    val salli = LocalSalliColors.current
    val haptic = LocalHapticFeedback.current
    val lozenge by animateColorAsState(
        targetValue = if (selected) salli.positive else Color.Transparent,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 220),
        label = "nav-lozenge",
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) salli.onPositive else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 220),
        label = "nav-icon",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 220),
        label = "nav-label",
    )

    // 1 → 1.06 → 1 on the transition into selected. Not a loop and not a pulse: one nudge, so
    // the tap lands physically. Skipped entirely when animations are off.
    val scale = remember { Animatable(1f) }
    LaunchedEffect(selected, reducedMotion) {
        if (selected && !reducedMotion) {
            scale.animateTo(1.06f, spring(dampingRatio = 0.4f, stiffness = 900f))
            scale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 600f))
        } else {
            scale.snapTo(1f)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Tab, onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            })
            .padding(horizontal = 2.dp)
            .width(64.dp)
            .height(48.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .scale(scale.value)
                .size(width = 44.dp, height = 26.dp)
                .clip(CircleShape)
                .background(lozenge),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (selected) null else label,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private enum class PreviewTab(
    val label: String,
    val outlined: ImageVector,
    val filled: ImageVector,
) {
    HOME("Home", Icons.Outlined.Home, Icons.Filled.Home),
    ACTIVITY("Activity", Icons.Outlined.Receipt, Icons.Filled.Receipt),
    PLAN("Plan", Icons.AutoMirrored.Outlined.EventNote, Icons.AutoMirrored.Filled.EventNote),
    INSIGHTS("Insights", Icons.Outlined.Insights, Icons.Filled.Insights),
}

@SalliPreview
@Composable
private fun FloatingNavBarPreview() {
    PreviewFrame {
        var current by remember { mutableStateOf(PreviewTab.HOME) }
        FloatingNavBar(
            items = PreviewTab.entries,
            selected = current,
            onSelect = { current = it },
            label = { it.label },
            icon = { it.outlined },
            selectedIcon = { it.filled },
            key = { it.name },
        )
    }
}
