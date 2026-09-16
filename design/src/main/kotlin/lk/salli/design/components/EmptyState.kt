package lk.salli.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import lk.salli.design.theme.SalliSpacing

/**
 * The only empty states Salli ships are the ones that tell you how to get data — "connect SMS",
 * "scan history", "waiting for your first alert". A section with nothing to show should
 * disappear instead of explaining itself, so if you're reaching for this to say "no results",
 * hide the section.
 *
 * 40 dp icon, titleMedium, bodyMedium, optional [PrimaryButton]. Deliberately restrained: an
 * illustration here would be the one piece of pure decoration in the everyday app.
 */
@Composable
fun EmptyState(
    title: String,
    message: String,
    icon: ImageVector = Icons.Outlined.Inbox,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(SalliSpacing.xxl),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(SalliSpacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(SalliSpacing.xxs))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(SalliSpacing.lg))
            PrimaryButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
            )
        }
    }
}

@SalliPreview
@Composable
private fun EmptyStatePreview() {
    PreviewFrame {
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            EmptyState(
                title = "Waiting for your first bank alert",
                message = "Salli reads bank SMS as it arrives. Nothing to show until one lands.",
            )
        }
        Box(Modifier.fillMaxWidth().height(280.dp)) {
            EmptyState(
                title = "Connect your bank messages",
                message = "Salli reads them on this phone. They never leave it.",
                actionLabel = "Allow SMS access",
                onAction = {},
            )
        }
    }
}
