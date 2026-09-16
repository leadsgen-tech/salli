package lk.salli.app.features.goals

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import lk.salli.design.components.EmptyState
import lk.salli.design.format.MoneyFormat

@Composable
fun GoalsScreen(
    onBack: () -> Unit,
    viewModel: GoalsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<GoalRow?>(null) }
    var contributingTo by remember { mutableStateOf<GoalRow?>(null) }
    var deleting by remember { mutableStateOf<GoalRow?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(top = statusBar)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Goals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                val active = state.goals.count { !it.isArchived }
                Text(text = if (active == 1) "1 goal" else "$active goals", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { creating = true }) { Text("New goal") }
        }

        if (!state.loading && state.goals.isEmpty()) {
            EmptyState(
                title = "No goals yet",
                message = "Set a target, record money as you put it aside, and Salli shows what to save each period to get there on time.",
                icon = Icons.Outlined.Flag,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.goals, key = { it.id }) { goal ->
                    GoalCard(
                        goal = goal,
                        onAdd = { contributingTo = goal },
                        onEdit = { editing = goal },
                        onArchive = { viewModel.setArchived(goal.id, !goal.isArchived) },
                        onDelete = { deleting = goal },
                    )
                }
            }
        }
    }

    if (creating || editing != null) {
        GoalEditorDialog(
            initial = editing,
            accounts = state.accounts,
            onDismiss = { creating = false; editing = null },
            onSave = { name, target, date, account ->
                viewModel.saveGoal(editing?.id, name, target, date, account)
                creating = false
                editing = null
            },
        )
    }
    contributingTo?.let { goal ->
        ContributionDialog(
            goalName = goal.name,
            onDismiss = { contributingTo = null },
            onSave = { amount, note ->
                viewModel.addContribution(goal.id, amount, note)
                contributingTo = null
            },
        )
    }
    deleting?.let { goal ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${goal.name}?") },
            text = { Text("The goal and the money recorded toward it are removed. Your transactions are not touched.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteGoal(goal.id); deleting = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun GoalCard(goal: GoalRow, onAdd: () -> Unit, onEdit: () -> Unit, onArchive: () -> Unit, onDelete: () -> Unit) {
    val dateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    fun date(millis: Long) = dateFmt.format(Date(millis))

    val plan = when {
        goal.isComplete -> "Reached"
        goal.targetDate == null -> "No target date"
        goal.isOverdue -> "Was due ${date(goal.targetDate)} · ${MoneyFormat.format(goal.remaining)} still to save"
        else -> "By ${date(goal.targetDate)} · " +
            (goal.perCycle?.let { "${MoneyFormat.format(it)} per period" } ?: "") +
            (goal.toSaveThisCycle?.let { " · ${MoneyFormat.format(it)} left this period" } ?: "")
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                Text(
                    text = goal.name + if (goal.isArchived) " · archived" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(text = "${goal.percent}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.size(8.dp))
            LinearProgressIndicator(
                progress = { goal.percent / 100f },
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "${MoneyFormat.format(goal.saved)} of ${MoneyFormat.format(goal.target)}" +
                    if (!goal.isComplete) " · ${MoneyFormat.format(goal.remaining)} to go" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(text = plan, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            goal.linkedAccountName?.let {
                Text(text = "Tracks the $it balance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            goal.contributions.take(3).forEach { c ->
                Text(
                    text = "${date(c.at)} · ${MoneyFormat.format(c.amount)}" + (c.note?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (goal.linkedAccountId == null && !goal.isArchived && !goal.isComplete) {
                    TextButton(onClick = onAdd) { Text("Add money") }
                }
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onArchive) { Text(if (goal.isArchived) "Unarchive" else "Archive") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun GoalEditorDialog(
    initial: GoalRow?,
    accounts: List<AccountOption>,
    onDismiss: () -> Unit,
    onSave: (name: String, targetMinor: Long, targetDate: Long?, linkedAccountId: Long?) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var target by remember { mutableStateOf(initial?.target?.let { plainAmount(it.minorUnits) }.orEmpty()) }
    var date by remember { mutableStateOf(initial?.targetDate?.let { isoDate(it) }.orEmpty()) }
    var account by remember { mutableStateOf(initial?.linkedAccountId) }

    val targetMinor = parseMinor(target)
    val dateMillis = parseDate(date)
    val dateOk = date.isBlank() || dateMillis != null
    val valid = name.isNotBlank() && targetMinor != null && dateOk

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New goal" else "Edit goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Target (Rs)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Target date, optional (YYYY-MM-DD)") },
                    singleLine = true,
                    isError = !dateOk,
                )
                if (accounts.isNotEmpty()) {
                    Text(text = "Count progress from an account balance (optional)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(selected = account == null, onClick = { account = null }, label = { Text("None") })
                        accounts.forEach { a ->
                            FilterChip(selected = account == a.id, onClick = { account = a.id }, label = { Text(a.name) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (targetMinor != null) onSave(name, targetMinor, dateMillis, account) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ContributionDialog(goalName: String, onDismiss: () -> Unit, onSave: (amountMinor: Long, note: String?) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val amountMinor = parseMinor(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to $goalName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (Rs)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note, optional") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (amountMinor != null) onSave(amountMinor, note) }, enabled = amountMinor != null) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "12,500.50" → 1250050. Null for blank, zero, negative or unparseable input. */
internal fun parseMinor(text: String): Long? = runCatching {
    val cleaned = text.replace(",", "").trim()
    if (cleaned.isEmpty()) return@runCatching null
    val value = BigDecimal(cleaned)
    if (value.signum() <= 0) null else value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
}.getOrNull()

private fun plainAmount(minor: Long): String =
    if (minor % 100 == 0L) (minor / 100).toString() else "%d.%02d".format(Locale.US, minor / 100, minor % 100)

private fun isoDate(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

/** "2026-12-20" → noon that day in local time, so the date never slips across a boundary. */
private fun parseDate(text: String): Long? = runCatching {
    LocalDate.parse(text.trim()).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()
