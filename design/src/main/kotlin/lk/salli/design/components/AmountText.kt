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
import lk.salli.design.format.MoneyFormat
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow

/**
 * Row-scale amount. Flow-coloured: income lights up in `tertiary` (Revolut teal); expenses
 * stay on the default `onSurface` because a wall of red would be deafening in a spending app;
 * transfers go to `onSurfaceVariant` (quiet); declined reads struck-through in the muted tone.
 */
@Composable
fun AmountText(
    money: Money,
    flow: TransactionFlow,
    isDeclined: Boolean = false,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    modifier: Modifier = Modifier,
) {
    val color = when {
        isDeclined -> MaterialTheme.colorScheme.onSurfaceVariant
        flow == TransactionFlow.INCOME -> MaterialTheme.colorScheme.tertiary
        flow == TransactionFlow.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val sign = when {
        isDeclined -> ""
        flow == TransactionFlow.INCOME -> "+"
        flow == TransactionFlow.TRANSFER -> ""
        else -> "−"
    }
    val body = MoneyFormat.format(money, signed = false)
    val text = if (sign.isEmpty()) body else "$sign$body"

    Text(
        text = text,
        color = color,
        style = style.copy(fontWeight = FontWeight.Medium),
        textDecoration = if (isDeclined) TextDecoration.LineThrough else TextDecoration.None,
        modifier = modifier,
    )
}

/**
 * Billboard-scale amount for account cards and the home hero. Currency sits as a tiny
 * superscript-like prefix in `onSurfaceVariant`; the number itself is Space Grotesk display
 * weight 500 with negative tracking — Revolut's billboard recipe.
 */
@Composable
fun HeroAmountText(
    money: Money,
    modifier: Modifier = Modifier,
    negative: Boolean = money.minorUnits < 0,
    style: TextStyle = MaterialTheme.typography.displaySmall,
) {
    val color = if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        Text(
            text = money.currency,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = MoneyFormat.formatBare(money),
            color = color,
            style = style.copy(fontWeight = FontWeight.Medium),
        )
    }
}
