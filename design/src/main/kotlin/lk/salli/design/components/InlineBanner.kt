package lk.salli.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import lk.salli.design.theme.SalliShapeTokens
import lk.salli.design.theme.SalliSpacing

/**
 * One line of toned attention: "4 messages need a look", "Turn on alerts".
 *
 * Not a card and not a dialog — it sits inline in the scroll and disappears the moment its
 * condition clears. Anything that needs a paragraph is not a banner.
 *
 * @param onDismiss when non-null, renders a close affordance. Callers persist the dismissal.
 */
@Composable
fun InlineBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: SalliTone = SalliTone.WARNING,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    dismissContentDescription: String = "Dismiss",
) {
    val colors = tone.colors()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SalliSpacing.xs),
        modifier = modifier
            .fillMaxWidth()
            .background(colors.container, SalliShapeTokens.row)
            .then(if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = SalliSpacing.sm, vertical = SalliSpacing.xs),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.content,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.content,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(SalliSpacing.xxs))
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = colors.content,
                modifier = Modifier.clickable(onClick = onAction),
            )
        }
        if (onDismiss != null) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = dismissContentDescription,
                    tint = colors.content,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@SalliPreview
@Composable
private fun InlineBannerPreview() {
    PreviewFrame {
        InlineBanner(
            text = "4 messages need a look",
            icon = Icons.Outlined.Inbox,
            actionLabel = "Review",
            onAction = {},
        )
        InlineBanner(
            text = "Turn on alerts for new transactions and bills",
            tone = SalliTone.NEUTRAL,
            icon = Icons.Outlined.NotificationsNone,
            actionLabel = "Allow",
            onAction = {},
            onDismiss = {},
        )
        InlineBanner(text = "Couldn't read messages", tone = SalliTone.NEGATIVE)
    }
}
