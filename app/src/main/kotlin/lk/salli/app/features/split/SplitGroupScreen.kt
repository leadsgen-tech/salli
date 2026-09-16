package lk.salli.app.features.split

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import lk.salli.design.components.EmptyState
import lk.salli.design.format.MoneyFormat
import lk.salli.domain.Money
import lk.salli.domain.split.SplitError
import lk.salli.domain.split.SplitMath
import lk.salli.domain.split.SplitMethod
import lk.salli.domain.split.SplitParticipant
import lk.salli.domain.split.SplitResult

/**
 * One shared-expense group: who owes whom, the expenses behind it, and the payments that settled
 * it. "Split this" on a transaction lands here with the add-expense form already open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitGroupScreen(
    onBack: () -> Unit,
    viewModel: SplitGroupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val candidates by viewModel.linkCandidates.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val dateFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    var addingExpense by remember { mutableStateOf(false) }
    var addingMember by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf<SuggestionRow?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDeleteGroup by remember { mutableStateOf(false) }
    var confirmDeleteExpense by remember { mutableStateOf<ExpenseRow?>(null) }
    var confirmDeleteSettlement by remember { mutableStateOf<SettlementRow?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    val prefill = state.prefill
    // A transaction handed over by "Split this" opens the form once; consumePrefill clears it.
    LaunchedEffect(prefill?.transactionId) { if (prefill != null) addingExpense = true }

    val group = state.group
    Column(modifier = Modifier.fillMaxSize().padding(top = statusBar)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group?.name ?: "Group",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (group != null) {
                    val people = state.members.size
                    Text(
                        text = "$people ${if (people == 1) "person" else "people"} · ${group.currency}" +
                            if (group.archived) " · archived" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (group != null) {
                IconButton(onClick = {
                    val text = viewModel.shareText()
                    if (text.isNotBlank()) {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, "Share summary"))
                    }
                }) {
                    Icon(imageVector = Icons.Outlined.Share, contentDescription = "Share summary")
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Add person") },
                            onClick = { menuOpen = false; addingMember = true },
                        )
                        DropdownMenuItem(
                            text = { Text(if (group.archived) "Unarchive" else "Archive") },
                            onClick = { menuOpen = false; viewModel.setArchived(!group.archived) },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete group") },
                            onClick = { menuOpen = false; confirmDeleteGroup = true },
                        )
                    }
                }
            }
        }

        if (group == null) {
            if (!state.loading) {
                EmptyState(
                    title = "Group not found",
                    message = "It may have been deleted.",
                    icon = Icons.Outlined.Groups,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Button(onClick = { addingExpense = true }, enabled = state.members.isNotEmpty()) { Text("Add expense") }
                    OutlinedButton(onClick = { addingMember = true }) { Text("Add person") }
                }
            }

            item { GroupSectionLabel("BALANCES") }
            items(state.members, key = { "m${it.id}" }) { member ->
                GroupCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = member.name + if (member.isMe) " (you)" else "",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = memberWords(member),
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                member.net.minorUnits < 0L -> MaterialTheme.colorScheme.error
                                member.net.minorUnits > 0L -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            if (state.suggestions.isNotEmpty()) {
                item { GroupSectionLabel("TO SETTLE UP") }
                items(state.suggestions, key = { "t${it.fromId}-${it.toId}" }) { suggestion ->
                    GroupCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${suggestion.from} pays ${suggestion.to} ${MoneyFormat.format(suggestion.amount)}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                viewModel.loadLinkCandidates()
                                settling = suggestion
                            }) { Text("Record") }
                        }
                    }
                }
            }

            item { GroupSectionLabel("EXPENSES") }
            if (state.expenses.isEmpty()) {
                item {
                    Text(
                        text = "No expenses yet. Add one, or open a transaction and tap \"Split this\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }
            }
            items(state.expenses, key = { "e${it.id}" }) { expense ->
                GroupCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = expense.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Paid by ${expense.paidBy} · ${dateFmt.format(Date(expense.at))}" +
                                    if (expense.linked) " · from a bank transaction" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = MoneyFormat.format(expense.amount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = expense.shares.joinToString(" · ") { (name, share) -> "$name ${MoneyFormat.format(share)}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { confirmDeleteExpense = expense }) { Text("Delete") }
                    }
                }
            }

            if (state.settlements.isNotEmpty()) {
                item { GroupSectionLabel("PAYMENTS") }
                items(state.settlements, key = { "s${it.id}" }) { payment ->
                    GroupCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${payment.from} paid ${payment.to} ${MoneyFormat.format(payment.amount)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = dateFmt.format(Date(payment.at)) + if (payment.linked) " · linked to a bank transaction" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { confirmDeleteSettlement = payment }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }

    if (addingExpense && group != null) {
        ModalBottomSheet(
            onDismissRequest = {
                addingExpense = false
                if (prefill != null) viewModel.consumePrefill()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            AddExpenseForm(
                members = state.members,
                currency = group.currency,
                prefill = prefill,
                onCancel = {
                    addingExpense = false
                    if (prefill != null) viewModel.consumePrefill()
                },
                onSave = { title, amountMinor, paidBy, method, participants ->
                    viewModel.addExpense(title, amountMinor, paidBy, method, participants, prefill)
                    addingExpense = false
                },
            )
        }
    }

    if (addingMember) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingMember = false },
            title = { Text("Add person") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { viewModel.addMember(name); addingMember = false }, enabled = name.isNotBlank()) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addingMember = false }) { Text("Cancel") } },
        )
    }

    settling?.let { suggestion ->
        SettleDialog(
            suggestion = suggestion,
            candidates = candidates,
            dateFmt = dateFmt,
            onDismiss = { settling = null },
            onSave = { amountMinor, linkedTransactionId ->
                viewModel.recordSettlement(suggestion.fromId, suggestion.toId, amountMinor, linkedTransactionId)
                settling = null
            },
        )
    }

    confirmDeleteExpense?.let { expense ->
        ConfirmDialog(
            title = "Delete \"${expense.title}\"?",
            message = "Balances are recalculated without it.",
            onConfirm = { viewModel.deleteExpense(expense.id); confirmDeleteExpense = null },
            onDismiss = { confirmDeleteExpense = null },
        )
    }
    confirmDeleteSettlement?.let { payment ->
        ConfirmDialog(
            title = "Delete this payment?",
            message = "${payment.from} will owe ${payment.to} ${MoneyFormat.format(payment.amount)} again.",
            onConfirm = { viewModel.deleteSettlement(payment.id); confirmDeleteSettlement = null },
            onDismiss = { confirmDeleteSettlement = null },
        )
    }
    if (confirmDeleteGroup && group != null) {
        ConfirmDialog(
            title = "Delete ${group.name}?",
            message = "Its people, expenses and payments are removed. Your bank transactions are not touched.",
            onConfirm = { confirmDeleteGroup = false; viewModel.deleteGroup(onBack) },
            onDismiss = { confirmDeleteGroup = false },
        )
    }
}

@Composable
private fun AddExpenseForm(
    members: List<MemberRow>,
    currency: String,
    prefill: ExpensePrefill?,
    onCancel: () -> Unit,
    onSave: (title: String, amountMinor: Long, paidBy: Long, method: SplitMethod, participants: List<SplitParticipant>) -> Unit,
) {
    var title by remember(prefill) { mutableStateOf(prefill?.title.orEmpty()) }
    var amountText by remember(prefill) { mutableStateOf(prefill?.let { minorToText(it.amountMinor) }.orEmpty()) }
    var paidBy by remember(members) { mutableStateOf((members.firstOrNull { it.isMe } ?: members.firstOrNull())?.id ?: 0L) }
    var method by remember { mutableStateOf(SplitMethod.EQUAL) }
    val included = remember(members) { mutableStateMapOf<Long, Boolean>().apply { members.forEach { put(it.id, true) } } }
    val values = remember(members) { mutableStateMapOf<Long, String>() }

    val amountMinor = parseMinor(amountText)
    val participants = members.filter { included[it.id] == true }.map { m ->
        val raw = values[m.id].orEmpty()
        SplitParticipant(
            memberId = m.id,
            value = when (method) {
                SplitMethod.EQUAL -> 0L
                // Blank or 0 is a real answer: this person is in the group but pays nothing here.
                SplitMethod.EXACT -> if (raw.isBlank()) 0L else parseMinorAllowingZero(raw) ?: -1L
                SplitMethod.WEIGHTS -> if (raw.isBlank()) 1L else raw.trim().toLongOrNull() ?: -1L
            },
        )
    }
    val result = amountMinor?.let { SplitMath.shares(it, participants, method) }
    val shares = (result as? SplitResult.Ok)?.shares
    val problem: String? = when {
        title.isBlank() -> "Give it a name"
        amountMinor == null -> "Enter the total"
        result is SplitResult.Invalid -> splitErrorWords(result.error, amountMinor, participants, currency)
        else -> null
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
    ) {
        Text(text = "Add expense", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (prefill != null) {
            Text(
                text = "From a bank transaction of ${MoneyFormat.format(Money(prefill.amountMinor, prefill.currency))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("What was it for") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Total ($currency)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(text = "PAID BY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            members.forEach { m ->
                FilterChip(selected = paidBy == m.id, onClick = { paidBy = m.id }, label = { Text(m.name) })
            }
        }

        Text(text = "SPLIT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf(SplitMethod.EQUAL to "Equally", SplitMethod.EXACT to "Exact amounts", SplitMethod.WEIGHTS to "By shares").forEach { (m, label) ->
                FilterChip(selected = method == m, onClick = { method = m }, label = { Text(label) })
            }
        }

        members.forEach { m ->
            val isIn = included[m.id] == true
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = isIn, onCheckedChange = { included[m.id] = it })
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = m.name + if (m.isMe) " (you)" else "", style = MaterialTheme.typography.bodyLarge)
                    val share = shares?.get(m.id)
                    if (isIn && share != null) {
                        Text(
                            text = MoneyFormat.format(Money(share, currency)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (isIn && method != SplitMethod.EQUAL) {
                    OutlinedTextField(
                        value = values[m.id].orEmpty(),
                        onValueChange = { values[m.id] = it },
                        singleLine = true,
                        placeholder = { Text(if (method == SplitMethod.EXACT) "0.00" else "1") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (method == SplitMethod.EXACT) KeyboardType.Decimal else KeyboardType.Number,
                        ),
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
        }

        if (problem != null) {
            Text(text = problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(
                onClick = { amountMinor?.let { onSave(title.trim(), it, paidBy, method, participants) } },
                enabled = problem == null,
            ) { Text("Save") }
        }
    }
}

@Composable
private fun SettleDialog(
    suggestion: SuggestionRow,
    candidates: List<LinkCandidate>,
    dateFmt: SimpleDateFormat,
    onDismiss: () -> Unit,
    onSave: (amountMinor: Long, linkedTransactionId: Long?) -> Unit,
) {
    var amountText by remember(suggestion) { mutableStateOf(minorToText(suggestion.amount.minorUnits)) }
    var linked by remember(suggestion) { mutableStateOf<Long?>(null) }
    val amountMinor = parseMinor(amountText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record a payment") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(text = "${suggestion.from} pays ${suggestion.to}", style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (${suggestion.amount.currency})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (candidates.isNotEmpty()) {
                    Text(
                        text = "Link a bank transaction (optional)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    candidates.forEach { c ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { linked = if (linked == c.id) null else c.id },
                        ) {
                            RadioButton(selected = linked == c.id, onClick = { linked = if (linked == c.id) null else c.id })
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = c.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = "${if (c.incoming) "In" else "Out"} · ${MoneyFormat.format(c.amount)} · ${dateFmt.format(Date(c.at))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { amountMinor?.let { onSave(it, linked) } }, enabled = amountMinor != null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GroupSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
    }
}

private fun memberWords(member: MemberRow): String = when {
    member.net.minorUnits > 0L -> "is owed ${MoneyFormat.format(member.net)}"
    member.net.minorUnits < 0L -> "owes ${MoneyFormat.format(Money(-member.net.minorUnits, member.net.currency))}"
    else -> "settled"
}

private fun splitErrorWords(error: SplitError, totalMinor: Long, participants: List<SplitParticipant>, currency: String): String =
    when (error) {
        SplitError.NO_PARTICIPANTS -> "Pick at least one person"
        SplitError.NON_POSITIVE_TOTAL -> "Enter an amount above zero"
        SplitError.NEGATIVE_VALUE -> "Check the numbers entered"
        SplitError.EXACT_MISMATCH -> {
            val left = totalMinor - participants.sumOf { it.value.coerceAtLeast(0L) }
            if (left > 0L) "${MoneyFormat.format(Money(left, currency))} still to assign"
            else "${MoneyFormat.format(Money(-left, currency))} more than the total"
        }
        SplitError.INVALID_WEIGHTS -> "Give at least one person a share"
        else -> "Check the split"
    }

private val amountPattern = Regex("""^\d{1,12}(\.\d{0,2})?$""")

/** "1,250.5" → 125050. Null for anything that is not a positive amount. */
internal fun parseMinor(text: String): Long? {
    val cleaned = text.replace(",", "").trim()
    if (!amountPattern.matches(cleaned)) return null
    return Money.ofMajor(cleaned, "LKR").minorUnits.takeIf { it > 0L }
}

/** Like [parseMinor], but "0" and "0.00" are valid amounts. */
private fun parseMinorAllowingZero(text: String): Long? {
    val cleaned = text.replace(",", "").trim()
    if (!amountPattern.matches(cleaned)) return null
    return Money.ofMajor(cleaned, "LKR").minorUnits
}

private fun minorToText(minor: Long): String = String.format(Locale.US, "%d.%02d", minor / 100, minor % 100)
