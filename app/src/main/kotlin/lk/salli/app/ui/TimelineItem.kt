package lk.salli.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CreditCardOff
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.LocalAtm
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector
import lk.salli.data.db.entities.CategoryEntity
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/** UI-ready view model for a single row in Home / Timeline. */
data class TimelineItem(
    val id: Long,
    val title: String,
    val subtitle: String,
    val amount: Money,
    val flow: TransactionFlow,
    val type: TransactionType,
    val icon: ImageVector,
    val emoji: String,
    val merchantRaw: String?,
    val isDeclined: Boolean,
    val timestamp: Long,
)

fun TransactionEntity.toTimelineItem(
    category: CategoryEntity?,
    accountDisplayName: String?,
    counterpartAccountName: String? = null,
): TimelineItem {
    val type = TransactionType.fromId(typeId)
    val flow = TransactionFlow.fromId(flowId)
    // A user-written note wins over everything — if they took the time to type a name, use
    // it as the row title. Falls through to merchantRaw, then the type's generic label.
    val title = note?.takeIf { it.isNotBlank() }
        ?: deriveTitle(type = type, merchantRaw = merchantRaw, isDeclined = isDeclined)

    // For a paired transfer the subtitle becomes "Source → Destination" instead of the
    // generic category/account join — clearer about what moved where.
    val directionLine = if (transferGroupId != null && counterpartAccountName != null &&
        accountDisplayName != null
    ) {
        if (flow == TransactionFlow.EXPENSE) {
            "$accountDisplayName → $counterpartAccountName"
        } else {
            "$counterpartAccountName → $accountDisplayName"
        }
    } else null

    val subtitleParts = buildList {
        if (directionLine != null) {
            add(directionLine)
        } else {
            category?.name?.let { add(it) }
            accountDisplayName?.let { add(it) }
        }
        feeMinor?.takeIf { it > 0 }?.let { fee ->
            add("Fee " + formatRupees(fee, amountCurrency))
        }
    }
    return TimelineItem(
        id = id,
        title = title,
        subtitle = subtitleParts.joinToString(separator = " · "),
        amount = Money(amountMinor, amountCurrency),
        flow = flow,
        type = type,
        icon = iconFor(type),
        emoji = emojiFor(type),
        merchantRaw = merchantRaw,
        isDeclined = isDeclined,
        timestamp = timestamp,
    )
}

/** Single source of truth for the emoji used in transaction avatars. Both Home and
 *  Timeline read from here so the two screens never drift apart. */
fun emojiFor(type: TransactionType): String = when (type) {
    TransactionType.POS -> "🛍️"
    TransactionType.ATM -> "💵"
    TransactionType.CDM -> "🏦"
    TransactionType.CHEQUE -> "📄"
    // All electronic-funds transfer channels share one glyph.
    TransactionType.ONLINE_TRANSFER,
    TransactionType.CEFT,
    TransactionType.SLIPS -> "💸"
    TransactionType.MOBILE_PAYMENT -> "📱"
    TransactionType.FEE -> "🧾"
    TransactionType.DECLINED -> "🚫"
    TransactionType.BALANCE_CORRECTION -> "🔧"
    TransactionType.OTHER -> "🧾"
}

/** Compact rupee formatter used for secondary subtitle fragments (like the fee line). */
private fun formatRupees(minor: Long, currency: String): String {
    val symbol = if (currency == "LKR") "Rs " else "$currency "
    val abs = kotlin.math.abs(minor)
    val major = abs / 100
    val cents = abs % 100
    val formatter = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US)
    return "$symbol${formatter.format(major)}.${"%02d".format(cents)}"
}

private fun deriveTitle(
    type: TransactionType,
    merchantRaw: String?,
    isDeclined: Boolean,
): String {
    val prefix = if (isDeclined) "Declined · " else ""
    val base = merchantRaw?.takeIf { it.isNotBlank() } ?: when (type) {
        TransactionType.ATM -> "ATM"
        TransactionType.CDM -> "Cash deposit"
        TransactionType.CHEQUE -> "Cheque"
        // Every interbank/intrabank electronic funds transfer reads as "Transfer" in the UI.
        // The underlying channel (CEFT / SLIPS / plain online) is an implementation detail
        // the user shouldn't need to parse — BOC emits "CEFT Transfer Debit" when the
        // destination is another bank and "Online Transfer Debit" when it's another BOC
        // account; same thing to the user. Collapse.
        TransactionType.ONLINE_TRANSFER,
        TransactionType.CEFT,
        TransactionType.SLIPS -> "Transfer"
        TransactionType.POS -> "Purchase"
        TransactionType.MOBILE_PAYMENT -> "Mobile Payment"
        TransactionType.FEE -> "Fee"
        TransactionType.DECLINED -> "Declined"
        TransactionType.BALANCE_CORRECTION -> "Balance correction"
        TransactionType.OTHER -> "Transaction"
    }
    return prefix + base
}

private fun iconFor(type: TransactionType): ImageVector = when (type) {
    TransactionType.POS -> Icons.Outlined.CreditCard
    TransactionType.ATM -> Icons.Outlined.LocalAtm
    TransactionType.CDM -> Icons.Outlined.Savings
    TransactionType.CHEQUE -> Icons.Outlined.Receipt
    // Same reasoning as deriveTitle — transfers share one glyph regardless of the SMS
    // channel wording. SwapHoriz reads as "money moved between accounts" cleanly.
    TransactionType.ONLINE_TRANSFER,
    TransactionType.CEFT,
    TransactionType.SLIPS -> Icons.Outlined.SwapHoriz
    TransactionType.MOBILE_PAYMENT -> Icons.Outlined.PhoneAndroid
    TransactionType.FEE -> Icons.Outlined.CurrencyExchange
    TransactionType.DECLINED -> Icons.Outlined.CreditCardOff
    TransactionType.BALANCE_CORRECTION -> Icons.Outlined.AutoFixHigh
    TransactionType.OTHER -> Icons.Outlined.AttachMoney
}
