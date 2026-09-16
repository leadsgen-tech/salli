package lk.salli.app.features.split

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lk.salli.design.components.EmptyState
import lk.salli.design.format.MoneyFormat
import lk.salli.domain.Money

@Composable
fun SplitGroupsScreen(
    onBack: () -> Unit,
    onOpenGroup: (groupId: Long, pendingTransactionId: Long?) -> Unit,
    viewModel: SplitGroupsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var creating by remember { mutableStateOf(false) }
    val pending = state.pending

    Column(modifier = Modifier.fillMaxSize().padding(top = statusBar)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Shared expenses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                val active = state.groups.count { !it.archived }
                Text(text = if (active == 1) "1 group" else "$active groups", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { creating = true }) { Text("New group") }
        }

        if (pending != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = "Split ${pending.title} · ${MoneyFormat.format(pending.amount)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = "Pick a group below, or create one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        if (!state.loading && state.groups.isEmpty()) {
            EmptyState(
                title = "No groups yet",
                message = "Make a group for a trip, a flat or a dinner, add the people, and Salli keeps track of who owes whom.",
                icon = Icons.Outlined.Groups,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.groups, key = { it.id }) { group ->
                    GroupCard(group) {
                        if (pending != null && !pending.amount.currency.equals(group.currency, ignoreCase = true)) {
                            Toast.makeText(context, "${group.name} is kept in ${group.currency}; this transaction is ${pending.amount.currency}", Toast.LENGTH_SHORT).show()
                        } else {
                            onOpenGroup(group.id, pending?.id)
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        CreateGroupDialog(
            defaultCurrency = pending?.amount?.currency ?: "LKR",
            onDismiss = { creating = false },
            onCreate = { name, currency ->
                creating = false
                // A transaction can only go into a group of its own currency. Otherwise open the new
                // group without it and say why, rather than dropping it silently.
                val fits = pending == null || pending.amount.currency.equals(currency, ignoreCase = true)
                if (pending != null && !fits) {
                    Toast.makeText(
                        context,
                        "$name is kept in $currency; this transaction is ${pending.amount.currency}, so it wasn't added",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                val pendingId = if (fits) pending?.id else null
                viewModel.createGroup(name, currency) { id -> onOpenGroup(id, pendingId) }
            },
        )
    }
}

@Composable
private fun GroupCard(group: GroupRow, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = group.name + if (group.archived) " · archived" else "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${group.memberCount} ${if (group.memberCount == 1) "person" else "people"} · ${group.currency}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = balanceWords(group.myNet), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun CreateGroupDialog(defaultCurrency: String, onDismiss: () -> Unit, onCreate: (name: String, currency: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(defaultCurrency.uppercase()) }
    val options = listOf("LKR", "USD").let { if (currency in it) it else it + currency }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Text(text = "Currency. A group keeps one currency.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { c -> FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(c) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, currency) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "You're owed Rs 4,000.00", "You owe Rs 1,200.00" or "You're all settled". */
internal fun balanceWords(net: Money?): String = when {
    net == null -> ""
    net.minorUnits > 0L -> "You're owed ${MoneyFormat.format(net)}"
    net.minorUnits < 0L -> "You owe ${MoneyFormat.format(Money(-net.minorUnits, net.currency))}"
    else -> "You're all settled"
}
