package lk.salli.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import lk.salli.design.logo.BankLogos
import lk.salli.design.theme.BankBrand

/**
 * A bank's mark, at whatever size the surface needs.
 *
 * Real bundled logo when one is mapped for the sender; otherwise a circle in the bank's brand
 * colour carrying its initial. The fallback matters more than it sounds — it keeps every
 * account visually distinct even for the banks we haven't licensed a mark for, so the accounts
 * row never degrades into identical grey discs.
 *
 * @param sender the raw SMS sender ID. Normalisation (trailing newlines, case) happens inside.
 * @param displayName optional human name; its first letter is the fallback initial.
 */
@Composable
fun BankAvatar(
    sender: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    displayName: String? = null,
    contentDescription: String? = null,
) {
    val logoPath = BankLogos.resolve(sender)
    if (logoPath != null) {
        AsyncImage(
            model = BankLogos.asAssetUri(logoPath),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
        )
        return
    }

    val brand = BankBrand.forSender(sender)
    val initial = (displayName ?: sender)
        ?.trim()
        ?.firstOrNull { it.isLetterOrDigit() }
        ?.uppercaseChar()
        ?.toString()
        .orEmpty()

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(brand.primary),
    ) {
        Text(
            text = initial,
            color = brand.onBrand,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                // Scale the initial with the circle so a 24 dp chip avatar and a 56 dp detail
                // header both look deliberate.
                fontSize = (size.value * 0.42f).sp,
            ),
        )
    }
}

@SalliPreview
@Composable
private fun BankAvatarPreview() {
    PreviewFrame {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BankAvatar(sender = "COMBANK")
            BankAvatar(sender = "BOC")
            BankAvatar(sender = "HNB")
            // Dirty sender — trailing newline, as the provider really hands it over.
            BankAvatar(sender = "SEYLAN\n")
            // No bundled logo: brand-coloured initial.
            BankAvatar(sender = "PeoplesBank", displayName = "People's Bank")
            BankAvatar(sender = "SOMETHINGNEW", displayName = "New Bank")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BankAvatar(sender = "PeoplesBank", displayName = "People's Bank", size = 24.dp)
            BankAvatar(sender = "PeoplesBank", displayName = "People's Bank", size = 56.dp)
        }
    }
}
