package lk.salli.app.features.bills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import lk.salli.design.components.EmptyState
import lk.salli.design.format.MoneyFormat

@Composable
fun BillsScreen(
    onBack: () -> Unit,
    viewModel: BillsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val dateFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().padding(top = statusBar)) {
        TopBar(onBack = onBack, openCount = state.open.size)

        if (!state.loading && state.open.isEmpty() && state.paid.isEmpty()) {
            EmptyState(
                title = "No bills yet",
                message = "SLT-MOBITEL, Dialog, CEB and Water Board bill SMS show up here with their due dates.",
                icon = Icons.Outlined.ReceiptLong,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ReminderStepper(
                    days = state.reminderDays,
                    onChange = { viewModel.setReminderDays(it) },
                )
            }

            item { SectionLabel(if (state.open.isEmpty()) "NOTHING TO PAY" else "TO PAY") }
            items(state.open, key = { "open-${it.id}" }) { bill ->
                BillCard(bill = bill, dateFmt = dateFmt, onMarkPaid = { viewModel.markPaid(bill.id) })
            }

            if (state.paid.isNotEmpty()) {
                item { Spacer(Modifier.size(8.dp)) }
                item { SectionLabel("PAID") }
                items(state.paid, key = { "paid-${it.id}" }) { bill ->
                    BillCard(bill = bill, dateFmt = dateFmt, onMarkPaid = null)
                }
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, openCount: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Bills",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when (openCount) {
                    0 -> "Nothing outstanding"
                    1 -> "1 bill to pay"
                    else -> "$openCount bills to pay"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
    )
}

@Composable
private fun ReminderStepper(days: Int, onChange: (Int) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Remind me before due",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when (days) {
                        0 -> "Only on the due day"
                        1 -> "1 day before, and on the day"
                        else -> "$days days before, and on the day"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { onChange((days - 1).coerceAtLeast(0)) }, enabled = days > 0) { Text("−") }
            Text(
                text = "$days",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = { onChange((days + 1).coerceAtMost(14)) }, enabled = days < 14) { Text("+") }
        }
    }
}

@Composable
private fun BillCard(bill: BillRow, dateFmt: SimpleDateFormat, onMarkPaid: (() -> Unit)?) {
    val dueLine = when {
        bill.inCredit -> if (bill.amount.minorUnits == 0L) "Nothing to pay"
        else "Nothing to pay · in credit ${MoneyFormat.format(bill.amount)}"
        bill.carriedForward -> "Part-paid" + (bill.paidAmount?.let { " ${MoneyFormat.format(it)}" } ?: "") +
            " · the rest moved to the next bill"
        bill.paidAt != null -> "Paid ${dateFmt.format(Date(bill.paidAt))}" +
            (bill.paidAmount?.let { " · ${MoneyFormat.format(it)}" } ?: "")
        else -> {
            val due = when {
                bill.dueDate == null -> "No due date given"
                bill.daysLeft == null -> "Due ${dateFmt.format(Date(bill.dueDate))}"
                bill.daysLeft < 0 -> "Overdue by ${-bill.daysLeft} day${if (bill.daysLeft == -1) "" else "s"} · was due ${dateFmt.format(Date(bill.dueDate))}"
                bill.daysLeft == 0 -> "Due today"
                bill.daysLeft == 1 -> "Due tomorrow · ${dateFmt.format(Date(bill.dueDate))}"
                else -> "Due in ${bill.daysLeft} days · ${dateFmt.format(Date(bill.dueDate))}"
            }
            bill.paidSoFar?.let { "$due · ${MoneyFormat.format(it)} paid so far" } ?: due
        }
    }
    val accent = bill.isOverdue && bill.paidAt == null
    Surface(
        color = if (accent) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bill.biller,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = listOfNotNull(bill.accountRef, bill.periodLabel).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = MoneyFormat.format(bill.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (accent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.size(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dueLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (accent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (onMarkPaid != null) {
                    TextButton(onClick = onMarkPaid) { Text("Mark paid") }
                }
            }
        }
    }
}
