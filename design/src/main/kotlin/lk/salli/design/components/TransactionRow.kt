package lk.salli.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.LocalAtm
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import lk.salli.design.format.TimeFormat
import lk.salli.design.logo.MerchantLogos
import lk.salli.design.theme.LocalSalliColors
import lk.salli.design.theme.SalliShapeTokens
import lk.salli.design.theme.SalliSpacing
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow

/**
 * The transaction row, used everywhere transactions are listed.
 *
 * 44 dp leading slot — merchant logo when we have one, otherwise the category icon on its
 * tinted container, so a glance down the list reads as colour-coded categories rather than a
 * column of identical grey circles. Title, subtitle, then the amount and a relative time.
 *
 * Variants, all driven by data rather than by the caller picking colours:
 *  - **own transfer** ([isOwnTransfer]) — muted throughout with a swap glyph; money that moved
 *    between your own accounts isn't spending and shouldn't look like it.
 *  - **declined** ([isDeclined]) — struck through, `negative` label.
 *
 * Minimum height 64 dp so long lists scroll predictably.
 *
 * [standalone] controls the container: `true` (the default, and what the screens that haven't
 * been rebuilt yet still use) draws the row on its own rounded surface; `false` draws a bare
 * row for stacking inside a [GroupedList], which owns the radius and the hairlines.
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
    categoryIconName: String? = null,
    categoryColorSeed: Int? = null,
    isOwnTransfer: Boolean = flow == TransactionFlow.TRANSFER,
    statusLabel: String? = null,
    standalone: Boolean = true,
    /** Non-null when the user excluded this row: drawn faded, with this label before the subtitle. */
    excludedLabel: String? = null,
    /** From-bank and to-bank sender ids of an own transfer; draws both logos in the leading slot. */
    pairSenders: Pair<String?, String?>? = null,
    /** Two letters for the leading tile when there is no logo; null falls back to the category glyph. */
    monogram: String? = null,
    /** The row's bank and what happened there; used when there is no merchant to show. */
    accountSender: String? = null,
    badge: TileBadge? = null,
) {
    if (standalone) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = SalliShapeTokens.row,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = SalliSpacing.md, vertical = SalliSpacing.xxs),
        ) {
            TransactionRowContent(
                title = title,
                subtitle = subtitle,
                amount = amount,
                flow = flow,
                leadingIcon = leadingIcon,
                merchantRaw = merchantRaw,
                timestamp = timestamp,
                isDeclined = isDeclined,
                categoryIconName = categoryIconName,
                categoryColorSeed = categoryColorSeed,
                isOwnTransfer = isOwnTransfer,
                statusLabel = statusLabel,
                excludedLabel = excludedLabel,
                pairSenders = pairSenders,
                monogram = monogram,
                accountSender = accountSender,
                badge = badge,
            )
        }
    } else {
        TransactionRowContent(
            title = title,
            subtitle = subtitle,
            amount = amount,
            flow = flow,
            leadingIcon = leadingIcon,
            merchantRaw = merchantRaw,
            timestamp = timestamp,
            isDeclined = isDeclined,
            categoryIconName = categoryIconName,
            categoryColorSeed = categoryColorSeed,
            isOwnTransfer = isOwnTransfer,
            statusLabel = statusLabel,
            excludedLabel = excludedLabel,
            pairSenders = pairSenders,
            monogram = monogram,
            accountSender = accountSender,
            badge = badge,
            modifier = modifier,
        )
    }
}

@Composable
private fun TransactionRowContent(
    title: String,
    subtitle: String,
    amount: Money,
    flow: TransactionFlow,
    leadingIcon: ImageVector,
    merchantRaw: String?,
    timestamp: Long?,
    isDeclined: Boolean,
    categoryIconName: String?,
    categoryColorSeed: Int?,
    isOwnTransfer: Boolean,
    statusLabel: String?,
    modifier: Modifier = Modifier,
    excludedLabel: String? = null,
    pairSenders: Pair<String?, String?>? = null,
    monogram: String? = null,
    accountSender: String? = null,
    badge: TileBadge? = null,
) {
    val salli = LocalSalliColors.current
    val logoPath = MerchantLogos.resolve(merchantRaw)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = SalliSpacing.md, vertical = SalliSpacing.sm)
            // Excluded rows fade as a whole rather than recolouring each part: they are still
            // real transactions, just ones the totals ignore.
            .alpha(if (excludedLabel != null) 0.5f else 1f),
    ) {
        // The tile answers "who or what": a merchant we know, the two banks of a move, the
        // merchant's initials on its category colour, or your own bank with a mark for what
        // the account did. One rule, every row.
        when {
            logoPath != null -> MerchantLogo(path = logoPath, size = LeadingSize)
            isOwnTransfer && pairSenders != null -> PairAvatar(from = pairSenders.first, to = pairSenders.second)
            monogram != null -> MonogramTile(text = monogram, colorSeed = categoryColorSeed ?: NeutralSeed, size = LeadingSize)
            accountSender != null && badge != null -> BankTile(sender = accountSender, badge = badge)
            categoryColorSeed != null -> CategoryIcon(
                iconName = categoryIconName,
                colorSeed = categoryColorSeed,
                size = LeadingSize,
            )
            else -> MutedAvatar(icon = leadingIcon)
        }

        Spacer(Modifier.width(SalliSpacing.sm))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isOwnTransfer) salli.transfer else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (isDeclined) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val secondary = statusLabel
                ?: excludedLabel?.let { label -> listOf(label, subtitle).filter { it.isNotBlank() }.joinToString(" · ") }
                ?: subtitle
            if (secondary.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (statusLabel != null) salli.negative else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(SalliSpacing.sm))

        Column(horizontalAlignment = Alignment.End) {
            AmountText(money = amount, flow = flow, isDeclined = isDeclined)
            if (timestamp != null) {
                Spacer(Modifier.height(2.dp))
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

private val LeadingSize = 44.dp

@Composable
fun MerchantAvatar(
    merchantRaw: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = LeadingSize,
) {
    val path = MerchantLogos.resolve(merchantRaw)
    val monogram = lk.salli.design.format.Monogram.of(merchantRaw)
    when {
        path != null -> MerchantLogo(path = path, size = size, modifier = modifier)
        monogram != null -> Box(modifier = modifier) { MonogramTile(text = monogram, colorSeed = NeutralSeed, size = size) }
        else -> MutedAvatar(icon = Icons.Outlined.Receipt, size = size, modifier = modifier)
    }
}

@Composable
private fun MerchantLogo(
    path: String,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = MerchantLogos.asAssetUri(path),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape),
    )
}

private const val NeutralSeed = 11

/** What an account did, for the mark on a [BankTile]. */
enum class TileBadge { SENT, RECEIVED, ATM, DEPOSIT, CHEQUE, FEE, CARD }

/**
 * Two letters on the category's tinted tile. Recognition (KE is Keells) and category colour at
 * once, with no artwork to draw for the long tail of merchants we have no logo for.
 */
@Composable
private fun MonogramTile(text: String, colorSeed: Int, size: androidx.compose.ui.unit.Dp) {
    val hue = LocalSalliColors.current.categoryHue(colorSeed)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(SalliShapeTokens.row)
            .background(hue.container),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
            color = hue.onContainer,
            maxLines = 1,
        )
    }
}

/**
 * Your own bank's logo with a small mark for what the account did: an arrow out for money
 * sent, an arrow in for money received, a note for cash, a cheque, a percent for a fee. Used
 * when the SMS named no merchant, which is every transfer, withdrawal and deposit.
 */
@Composable
private fun BankTile(sender: String, badge: TileBadge) {
    val salli = LocalSalliColors.current
    val (icon, tint, container) = when (badge) {
        TileBadge.SENT -> Triple(Icons.AutoMirrored.Outlined.ArrowForward, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.surfaceContainerHighest)
        TileBadge.RECEIVED, TileBadge.DEPOSIT -> Triple(Icons.Outlined.ArrowDownward, salli.onIncome, salli.income)
        TileBadge.ATM -> Triple(Icons.Outlined.LocalAtm, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.surfaceContainerHighest)
        TileBadge.CHEQUE -> Triple(Icons.Outlined.Receipt, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.surfaceContainerHighest)
        TileBadge.FEE -> Triple(Icons.Outlined.Percent, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.surfaceContainerHighest)
        TileBadge.CARD -> Triple(Icons.Outlined.CreditCard, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.surfaceContainerHighest)
    }
    Box(modifier = Modifier.size(LeadingSize)) {
        BankAvatar(sender = sender, size = 40.dp, modifier = Modifier.align(Alignment.TopStart))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(18.dp)
                .background(container, CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

/**
 * Both banks of an own transfer in one slot: the sending bank behind, the receiving bank in
 * front, a small arrow between. Reads "BOC → Peoples" before the title does.
 */
@Composable
private fun PairAvatar(from: String?, to: String?) {
    Box(modifier = Modifier.size(LeadingSize)) {
        BankAvatar(sender = from, size = 30.dp, modifier = Modifier.align(Alignment.TopStart))
        BankAvatar(
            sender = to,
            size = 30.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(16.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

@Composable
private fun MutedAvatar(
    icon: ImageVector,
    size: androidx.compose.ui.unit.Dp = LeadingSize,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(SalliShapeTokens.row)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Sticky day header between groups of rows: the label on the left, the day's net on the right.
 */
@Composable
fun DateHeader(
    label: String,
    modifier: Modifier = Modifier,
    trailingAmount: String? = null,
    trailingPositive: Boolean = true,
) {
    val salli = LocalSalliColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            // Matches the screen gutter so the header's label lines up with the rows below
            // it rather than floating 16 dp to their left.
            .padding(
                start = SalliSpacing.screenGutter,
                end = SalliSpacing.screenGutter,
                top = SalliSpacing.lg,
                bottom = SalliSpacing.xs,
            ),
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
                color = if (trailingPositive) salli.income else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@SalliPreview
@Composable
private fun TransactionRowPreview() {
    PreviewFrame {
        DateHeader(label = "Today", trailingAmount = "−Rs 6,140.00", trailingPositive = false)
        GroupedList {
            TransactionRow(
                standalone = false,
                title = "Keells Super",
                subtitle = "Groceries · ComBank 4273",
                amount = Money(428_000, "LKR"),
                flow = TransactionFlow.EXPENSE,
                merchantRaw = "KEELLS SUPER NAWALA",
                timestamp = System.currentTimeMillis(),
            )
            ListDivider()
            TransactionRow(
                standalone = false,
                title = "PickMe",
                subtitle = "Transport · ComBank 4273",
                amount = Money(186_000, "LKR"),
                flow = TransactionFlow.EXPENSE,
                categoryIconName = "directions_car",
                categoryColorSeed = 2,
            )
            ListDivider()
            TransactionRow(
                standalone = false,
                title = "Own transfer",
                subtitle = "BOC 870 → ComBank 4273",
                amount = Money(1_000_000, "LKR"),
                flow = TransactionFlow.TRANSFER,
            )
            ListDivider()
            TransactionRow(
                standalone = false,
                title = "Daraz",
                subtitle = "Shopping · ComBank 4273",
                amount = Money(1_240_000, "LKR"),
                flow = TransactionFlow.EXPENSE,
                isDeclined = true,
                statusLabel = "Declined by bank",
            )
            ListDivider()
            TransactionRow(
                standalone = false,
                title = "Salary",
                subtitle = "Income · BOC 870",
                amount = Money(24_500_000, "LKR"),
                flow = TransactionFlow.INCOME,
                categoryIconName = "payments",
                categoryColorSeed = 9,
            )
        }
    }
}
