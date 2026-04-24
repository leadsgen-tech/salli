package lk.salli.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import lk.salli.design.format.TimeFormat
import lk.salli.design.logo.MerchantLogos
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow

/**
 * Unified transaction row used on Home, Timeline, and anywhere else we list tx activity.
 * White pill card on the paper background, emoji avatar on the left (falls through to a
 * merchant logo when one is registered for the raw merchant name), merchant/title + subtitle
 * in the middle, amount on the right.
 *
 * Both Home and Timeline read their emoji from `TimelineItem.emoji` (single source of truth
 * in [lk.salli.app.ui.emojiFor]) — no visual drift between screens.
 */
@Composable
fun TransactionRow(
    title: String,
    subtitle: String,
    amount: Money,
    flow: TransactionFlow,
    leadingIcon: ImageVector = Icons.Outlined.Receipt,
    merchantRaw: String? = null,
    timestamp: Long? = null,
    isDeclined: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val logoPath = MerchantLogos.resolve(merchantRaw)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            if (logoPath != null) {
                MerchantLogo(path = logoPath)
            } else {
                IconAvatar(icon = leadingIcon)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                AmountText(money = amount, flow = flow, isDeclined = isDeclined)
                if (timestamp != null) {
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = TimeFormat.relative(timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun MerchantLogo(path: String) {
    AsyncImage(
        model = MerchantLogos.asAssetUri(path),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape),
    )
}

@Composable
private fun IconAvatar(icon: ImageVector) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Sticky date header rendered between groups of rows. Optionally shows a trailing amount
 * aligned right — the day's net ("Today · Rs 1,125.00" / "Yesterday · -Rs 8,521.25").
 */
@Composable
fun DateHeader(
    label: String,
    modifier: Modifier = Modifier,
    trailingAmount: String? = null,
    trailingPositive: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (trailingAmount != null) {
            Text(
                text = trailingAmount,
                style = MaterialTheme.typography.labelMedium,
                color = if (trailingPositive) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
