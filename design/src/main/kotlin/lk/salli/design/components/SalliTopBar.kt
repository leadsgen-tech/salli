package lk.salli.design.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Large title that collapses to a compact bar as the screen scrolls.
 *
 * Wraps M3's [LargeTopAppBar] with Salli's colours (transparent over the screen ground, no
 * elevation tint) so every screen's header behaves identically. The caller owns the
 * [TopAppBarScrollBehavior] because it has to be attached to the screen's scroll container
 * too; build it with [rememberSalliTopBarScrollBehavior].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalliTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    LargeTopAppBar(
        title = { Text(text = title) },
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier,
    )
}

/** The collapse behaviour every Salli screen uses: large → compact, never pinned. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSalliTopBarScrollBehavior(): TopAppBarScrollBehavior =
    TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

/** 48 dp touch target with a 24 dp glyph — the only icon button shape in the app. */
@Composable
fun SalliIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SalliPreview
@Composable
private fun SalliTopBarPreview() {
    PreviewFrame {
        SalliTopBar(
            title = "Activity",
            actions = {
                SalliIconButton(
                    icon = Icons.Outlined.Search,
                    contentDescription = "Search",
                    onClick = {},
                )
            },
        )
    }
}
