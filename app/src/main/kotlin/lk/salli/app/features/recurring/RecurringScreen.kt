package lk.salli.app.features.recurring

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import lk.salli.data.db.entities.RecurringSeriesEntity
import lk.salli.design.components.EmptyState
import lk.salli.design.format.MoneyFormat
import lk.salli.domain.Money
import lk.salli.domain.recurring.RecurringStatus

@Composable
fun RecurringScreen(
    onBack: () -> Unit,
    viewModel: RecurringViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LaunchedEffect(Unit) { viewModel.refresh() }

    val found = state.failing.size + state.dueSoon.size + state.active.size + state.missed.size
    Column(modifier = Modifier.fillMaxSize().padding(top = statusBar)) {
        TopBar(onBack = onBack, subtitle = if (found == 1) "1 found" else "$found found")

        if (!state.loading && state.isEmpty) {
            EmptyState(
                title = "Nothing repeating yet",
                message = "Payments that repeat on a schedule, like a lease, a gym or a subscription, appear here once Salli has seen them three times.",
                icon = Icons.Outlined.Autorenew,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                state.next30Days?.let { total -> item(key = "total") { UpcomingTotal(total) } }
                section("KEEPS GETTING DECLINED", state.failing, viewModel)
                section("DUE THIS WEEK", state.dueSoon, viewModel)
                section("REPEATING", state.active, viewModel)
                section("MISSED OR STOPPED", state.missed, viewModel)
                section("MARKED NOT RECURRING", state.dismissed, viewModel)
            }
        }
    }
}

private fun LazyListScope.section(title: String, rows: List<RecurringRow>, viewModel: RecurringViewModel) {
    if (rows.isEmpty()) return
    item(key = "h-$title") { SectionLabel(title) }
    items(rows, key = { "r-${it.id}" }) { row ->
        SeriesCard(
            row = row,
            onConfirm = { viewModel.confirm(row.id) },
            onDismiss = { viewModel.dismiss(row.id) },
            onRestore = { viewModel.restore(row.id) },
        )
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, subtitle: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Recurring & subscriptions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun UpcomingTotal(total: Money) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(text = "Expected in the next 30 days", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = MoneyFormat.format(total), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = "Repeating payments that are still on schedule", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SeriesCard(row: RecurringRow, onConfirm: () -> Unit, onDismiss: () -> Unit, onRestore: () -> Unit) {
    val dateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    fun date(millis: Long?) = millis?.let { dateFmt.format(Date(it)) } ?: "unknown"
    val failing = row.status == RecurringStatus.FAILING
    val times = if (row.declinedAttempts == 1) "once" else "${row.declinedAttempts} times"
    val detail = when {
        failing && row.nextAt == null -> "Declined $times · latest ${date(row.lastAt)}"
        failing -> "${row.cadenceLabel} · declined $times since ${date(row.lastAt)}"
        !row.isDetected -> "${row.cadenceLabel} · no longer seen · last ${date(row.lastAt)}"
        row.status == RecurringStatus.MISSED -> "${row.cadenceLabel} · was expected ${date(row.nextAt)} · last ${date(row.lastAt)}"
        else -> "${row.cadenceLabel} · ${if (row.isFixed) "fixed" else "amount varies"} · next ${date(row.nextAt)}"
    }

    Surface(
        color = if (failing) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Autorenew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = (if (row.isIncome) "+" else "") + MoneyFormat.format(row.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (failing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                when (row.userState) {
                    RecurringSeriesEntity.DISMISSED -> TextButton(onClick = onRestore) { Text("Restore") }
                    RecurringSeriesEntity.CONFIRMED -> {
                        Text(
                            text = "Confirmed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        TextButton(onClick = onDismiss) { Text("Not recurring") }
                    }
                    else -> {
                        TextButton(onClick = onDismiss) { Text("Not recurring") }
                        TextButton(onClick = onConfirm) { Text("Confirm") }
                    }
                }
            }
        }
    }
}
