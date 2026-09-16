package lk.salli.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import lk.salli.design.theme.LocalSalliColors
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow
import lk.salli.domain.money.MoneyFormat

/**
 * Row-scale amount, coloured by what kind of money it is — not by whether it's big.
 *
 * Income takes the `income` token; expenses stay plain ink (a spending app where every row is
 * red is a wall of alarm and nothing stands out); transfers and fees go quiet; declined reads
 * struck through.
 */
@Composable
fun AmountText(
    money: Money,
    flow: TransactionFlow,
    isDeclined: Boolean = false,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    modifier: Modifier = Modifier,
) {
    val salli = LocalSalliColors.current
    val color = when {
        isDeclined -> salli.transfer
        flow == TransactionFlow.INCOME -> salli.income
        flow == TransactionFlow.TRANSFER -> salli.transfer
        else -> salli.expense
    }
    val sign = when {
        isDeclined -> ""
        flow == TransactionFlow.INCOME -> "+"
        flow == TransactionFlow.TRANSFER -> ""
        else -> "−"
    }

    Text(
        text = sign + MoneyFormat.format(money),
        color = color,
        style = style.copy(fontWeight = FontWeight.Medium),
        textDecoration = if (isDeclined) TextDecoration.LineThrough else TextDecoration.None,
        modifier = modifier,
    )
}

/**
 * Billboard-scale amount for the hero. The currency sits as a small prefix in the muted tone
 * so the digits own the line; the digits themselves are Space Grotesk with tabular figures,
 * which is what keeps a number from jittering sideways when it animates.
 */
@Composable
fun HeroAmountText(
    money: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displaySmall,
    color: androidx.compose.ui.graphics.Color = androidx.compose.material3.LocalContentColor.current,
    currencyColor: androidx.compose.ui.graphics.Color = color.copy(alpha = 0.7f),
    isDeclined: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        Text(
            text = MoneyFormat.symbol(money.currency),
            color = currencyColor,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = MoneyFormat.bare(money),
            color = color,
            style = style.copy(fontWeight = FontWeight.Medium),
            textDecoration = if (isDeclined) TextDecoration.LineThrough else TextDecoration.None,
        )
    }
}

@SalliPreview
@Composable
private fun AmountTextPreview() {
    PreviewFrame {
        AmountText(money = Money(428_000, "LKR"), flow = TransactionFlow.EXPENSE)
        AmountText(money = Money(24_500_000, "LKR"), flow = TransactionFlow.INCOME)
        AmountText(money = Money(1_000_000, "LKR"), flow = TransactionFlow.TRANSFER)
        AmountText(
            money = Money(1_240_000, "LKR"),
            flow = TransactionFlow.EXPENSE,
            isDeclined = true,
        )
        HeroAmountText(money = Money(8_420_000, "LKR"))
    }
}
