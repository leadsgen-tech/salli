package lk.salli.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CreditCardOff
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Bolt
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
import lk.salli.domain.money.MoneyFormat
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
    val merchantRaw: String?,
    val isDeclined: Boolean,
    val timestamp: Long,
    /** Both legs of a movement between the user's own accounts, folded into one row. */
    val isOwnTransfer: Boolean = false,
    /** The other leg's transaction id when [isOwnTransfer]; lets detail views show both. */
    val counterpartId: Long? = null,
    /** Excluded by the user: still listed when asked for, muted, and left out of every total. */
    val isExcluded: Boolean = false,
)

/**
 * Maps a list of rows to timeline items, folding each complete internal-transfer pair into a
 * single "Own transfer" row positioned where its first leg was. A leg whose partner is outside
 * the list (other day, other range) stays a single row with the directional subtitle.
 */
fun List<TransactionEntity>.toTimelineItems(
    categoriesById: Map<Long, CategoryEntity>,
    accountsById: Map<Long, lk.salli.data.db.entities.AccountEntity>,
): List<TimelineItem> {
    val legsByGroup = filter { it.transferGroupId != null }.groupBy { it.transferGroupId!! }
    val consumed = HashSet<Long>()
    val out = ArrayList<TimelineItem>(size)
    for (row in this) {
        if (row.id in consumed) continue
        val gid = row.transferGroupId
        val pair = gid?.let { legsByGroup[it] }
        if (pair != null && pair.size == 2) {
            // Both legs carry flow TRANSFER once paired, so direction is recovered from the
            // amounts: the sending side is the larger leg (its bank deducted the fee). Equal
            // amounts fall back to insertion order.
            val sorted = pair.sortedWith(compareByDescending<TransactionEntity> { it.amountMinor }.thenBy { it.id })
            val from = sorted[0]
            val to = sorted[1]
            consumed.add(from.id); consumed.add(to.id)
            out.add(ownTransferItem(from, to, accountsById))
        } else {
            val counterpartName = pair?.firstOrNull { it.id != row.id }?.let { accountsById[it.accountId]?.displayName }
            out.add(
                row.toTimelineItem(
                    category = row.categoryId?.let { categoriesById[it] },
                    accountDisplayName = accountsById[row.accountId]?.displayName,
                    counterpartAccountName = counterpartName,
                ),
            )
        }
    }
    return out
}

private fun ownTransferItem(
    from: TransactionEntity,
    to: TransactionEntity,
    accountsById: Map<Long, lk.salli.data.db.entities.AccountEntity>,
): TimelineItem {
    val fromName = accountsById[from.accountId]?.displayName ?: from.senderAddress ?: "Account"
    val toName = accountsById[to.accountId]?.displayName ?: to.senderAddress ?: "Account"
    val fee = (from.amountMinor - to.amountMinor).takeIf { it > 0 }
    val subtitle = buildList {
        add("$fromName → $toName")
        fee?.let { add("Fee " + MoneyFormat.formatMinor(it, from.amountCurrency)) }
    }.joinToString(" · ")
    return TimelineItem(
        id = from.id,
        title = "Own transfer",
        subtitle = subtitle,
        // What actually moved between the accounts is the credited amount; the fee is
        // surfaced separately so the row never reads as spending.
        amount = Money(to.amountMinor, to.amountCurrency),
        flow = TransactionFlow.TRANSFER,
        type = TransactionType.ONLINE_TRANSFER,
        icon = Icons.Outlined.SwapHoriz,
        merchantRaw = null,
        isDeclined = false,
        timestamp = maxOf(from.timestamp, to.timestamp),
        isOwnTransfer = true,
        counterpartId = to.id,
        isExcluded = from.isHidden && to.isHidden,
    )
}

fun TransactionEntity.toTimelineItem(
    category: CategoryEntity?,
    accountDisplayName: String?,
    counterpartAccountName: String? = null,
): TimelineItem {
    val parsedType = TransactionType.fromId(typeId)
    // Older rows stored People's Bank transfers and bill payments under MOBILE_PAYMENT.
    // Normalise them while presenting so existing installs become consistent immediately;
    // newly parsed rows use the specific types directly.
    val type = canonicalType(parsedType, senderAddress, rawBody)
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
            add("Fee " + MoneyFormat.formatMinor(fee, amountCurrency))
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
        merchantRaw = merchantRaw,
        isDeclined = isDeclined,
        timestamp = timestamp,
        isExcluded = isHidden,
    )
}

private fun deriveTitle(
    type: TransactionType,
    merchantRaw: String?,
    isDeclined: Boolean,
): String {
    val prefix = if (isDeclined) "Declined · " else ""
    val generic = when (type) {
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
        TransactionType.MOBILE_PAYMENT -> "Mobile payment"
        TransactionType.POS -> "Purchase"
        TransactionType.BILL_PAYMENT -> "Bill payment"
        TransactionType.FEE -> "Fee"
        TransactionType.DECLINED -> "Declined"
        TransactionType.BALANCE_CORRECTION -> "Balance correction"
        TransactionType.OTHER -> "Transaction"
    }
    // A transfer is titled by who it went to — the beneficiary bank or the person the parser
    // found — and only falls back to "Transfer" when the counterparty is just digits. The
    // detail screen already showed the counterparty; the row used to throw it away.
    val counterparty = merchantRaw?.trim()?.takeIf { it.isNotBlank() && it.any(Char::isLetter) }
    return prefix + (counterparty ?: generic)
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
    TransactionType.BILL_PAYMENT -> Icons.Outlined.Bolt
    TransactionType.FEE -> Icons.Outlined.CurrencyExchange
    TransactionType.DECLINED -> Icons.Outlined.CreditCardOff
    TransactionType.BALANCE_CORRECTION -> Icons.Outlined.AutoFixHigh
    TransactionType.OTHER -> Icons.Outlined.AttachMoney
}

private fun canonicalType(
    type: TransactionType,
    senderAddress: String?,
    rawBody: String?,
): TransactionType = when {
    type != TransactionType.MOBILE_PAYMENT -> type
    !senderAddress.orEmpty().trim().equals("PeoplesBank", ignoreCase = true) -> type
    rawBody.orEmpty().contains("Mobile Payment Successful", ignoreCase = true) ->
        TransactionType.BILL_PAYMENT
    rawBody.orEmpty().contains("LPAY Tfr", ignoreCase = true) ||
        rawBody.orEmpty().contains("PeoPAY", ignoreCase = true) ||
        rawBody.orEmpty().contains("Just Pay", ignoreCase = true) ||
        rawBody.orEmpty().contains("Fund transfer", ignoreCase = true) ->
        TransactionType.ONLINE_TRANSFER
    else -> type
}
