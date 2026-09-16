package lk.salli.app.features.split

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.SplitGroupEntity
import lk.salli.data.split.SplitService
import lk.salli.design.format.MoneyFormat
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow
import lk.salli.domain.split.SplitError
import lk.salli.domain.split.SplitMath
import lk.salli.domain.split.SplitMethod
import lk.salli.domain.split.SplitParticipant

data class MemberRow(val id: Long, val name: String, val isMe: Boolean, val net: Money)

data class ExpenseRow(
    val id: Long,
    val title: String,
    val amount: Money,
    val paidBy: String,
    val at: Long,
    val shares: List<Pair<String, Money>>,
    val linked: Boolean,
)

data class SettlementRow(val id: Long, val from: String, val to: String, val amount: Money, val at: Long, val linked: Boolean)

data class SuggestionRow(val fromId: Long, val from: String, val toId: Long, val to: String, val amount: Money)

/** An expense prefilled from a real transaction ("Split this"). */
data class ExpensePrefill(val transactionId: Long, val title: String, val amountMinor: Long, val currency: String, val at: Long)

/** A recent bank transaction a settlement can optionally point at. */
data class LinkCandidate(val id: Long, val title: String, val amount: Money, val at: Long, val incoming: Boolean)

data class SplitGroupUiState(
    val group: SplitGroupEntity? = null,
    val members: List<MemberRow> = emptyList(),
    val expenses: List<ExpenseRow> = emptyList(),
    val settlements: List<SettlementRow> = emptyList(),
    val suggestions: List<SuggestionRow> = emptyList(),
    val prefill: ExpensePrefill? = null,
    val loading: Boolean = true,
)

@HiltViewModel
class SplitGroupViewModel @Inject constructor(
    private val db: SalliDatabase,
    private val split: SplitService,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val groupId: Long = savedStateHandle.get<Long>("groupId") ?: 0L
    private val prefill = MutableStateFlow<ExpensePrefill?>(null)
    private val _linkCandidates = MutableStateFlow<List<LinkCandidate>>(emptyList())
    val linkCandidates: StateFlow<List<LinkCandidate>> = _linkCandidates.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        savedStateHandle.get<Long>("tx")?.takeIf { it > 0L }?.let { txId ->
            viewModelScope.launch {
                val tx = db.transactions().byId(txId) ?: return@launch
                prefill.value = ExpensePrefill(
                    transactionId = tx.id,
                    title = tx.note?.takeIf { it.isNotBlank() } ?: tx.merchantRaw?.takeIf { it.isNotBlank() } ?: "Transaction",
                    amountMinor = tx.amountMinor,
                    currency = tx.amountCurrency,
                    at = tx.timestamp,
                )
            }
        }
    }

    val state: StateFlow<SplitGroupUiState> = combine(
        db.split().observeGroup(groupId),
        db.split().observeMembers(),
        db.split().observeExpenses(),
        db.split().observeShares(),
        combine(db.split().observeSettlements(), prefill) { settlements, pre -> settlements to pre },
    ) { group, allMembers, allExpenses, allShares, (allSettlements, pre) ->
        if (group == null) return@combine SplitGroupUiState(loading = false)
        val members = allMembers.filter { it.groupId == groupId }
        val names = members.associate { it.id to it.name }
        val net = groupBalances(groupId, members.map { it.id }, allExpenses, allShares, allSettlements)
        val sharesByExpense = allShares.groupBy { it.expenseId }
        fun money(minor: Long) = Money(minor, group.currency)
        SplitGroupUiState(
            group = group,
            members = members.map { MemberRow(it.id, it.name, it.isMe, money(net[it.id] ?: 0L)) },
            expenses = allExpenses.filter { it.groupId == groupId }.map { e ->
                ExpenseRow(
                    id = e.id,
                    title = e.title,
                    amount = money(e.amountMinor),
                    paidBy = names[e.paidByMemberId] ?: "Someone",
                    at = e.at,
                    shares = sharesByExpense[e.id].orEmpty().map { (names[it.memberId] ?: "Someone") to money(it.shareMinor) },
                    linked = e.linkedTransactionId != null,
                )
            },
            settlements = allSettlements.filter { it.groupId == groupId }.map { s ->
                SettlementRow(s.id, names[s.fromMemberId] ?: "Someone", names[s.toMemberId] ?: "Someone", money(s.amountMinor), s.at, s.linkedTransactionId != null)
            },
            suggestions = SplitMath.simplify(net).map { t ->
                SuggestionRow(t.from, names[t.from] ?: "Someone", t.to, names[t.to] ?: "Someone", money(t.amountMinor))
            },
            prefill = pre?.takeIf { it.currency.equals(group.currency, ignoreCase = true) },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SplitGroupUiState())

    /** The "Split this" hand-off is used once; reopening the group later starts clean. */
    fun consumePrefill() {
        prefill.value = null
        savedStateHandle["tx"] = -1L
    }

    fun addMember(name: String) {
        viewModelScope.launch {
            (split.addMember(groupId, name) as? SplitService.Outcome.Rejected)?.let { _messages.send(message(it.error)) }
        }
    }

    fun addExpense(
        title: String,
        amountMinor: Long,
        paidBy: Long,
        method: SplitMethod,
        participants: List<SplitParticipant>,
        prefilled: ExpensePrefill?,
    ) {
        val group = state.value.group ?: return
        viewModelScope.launch {
            val outcome = split.addExpense(
                groupId = groupId,
                title = title,
                amountMinor = amountMinor,
                currency = group.currency,
                paidByMemberId = paidBy,
                method = method,
                participants = participants,
                at = prefilled?.at ?: System.currentTimeMillis(),
                linkedTransactionId = prefilled?.transactionId,
            )
            when (outcome) {
                is SplitService.Outcome.Saved -> if (prefilled != null) consumePrefill()
                is SplitService.Outcome.Rejected -> _messages.send(message(outcome.error))
            }
        }
    }

    fun loadLinkCandidates() {
        val group = state.value.group ?: return
        viewModelScope.launch {
            val since = System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000
            _linkCandidates.value = db.transactions().recentAll(since)
                .filter {
                    !it.isDeclined && it.amountCurrency.equals(group.currency, ignoreCase = true) &&
                        (it.flowId == TransactionFlow.EXPENSE.id || it.flowId == TransactionFlow.INCOME.id)
                }
                .take(12)
                .map {
                    LinkCandidate(
                        id = it.id,
                        title = it.note?.takeIf { n -> n.isNotBlank() } ?: it.merchantRaw?.takeIf { m -> m.isNotBlank() } ?: it.senderAddress.orEmpty(),
                        amount = Money(it.amountMinor, it.amountCurrency),
                        at = it.timestamp,
                        incoming = it.flowId == TransactionFlow.INCOME.id,
                    )
                }
        }
    }

    fun recordSettlement(fromId: Long, toId: Long, amountMinor: Long, linkedTransactionId: Long?) {
        viewModelScope.launch {
            (split.recordSettlement(groupId, fromId, toId, amountMinor, linkedTransactionId = linkedTransactionId) as? SplitService.Outcome.Rejected)
                ?.let { _messages.send(message(it.error)) }
        }
    }

    fun deleteExpense(id: Long) = viewModelScope.launch { split.deleteExpense(id) }

    fun deleteSettlement(id: Long) = viewModelScope.launch { split.deleteSettlement(id) }

    fun setArchived(archived: Boolean) = viewModelScope.launch { split.setArchived(groupId, archived) }

    fun deleteGroup(onDone: () -> Unit) {
        viewModelScope.launch {
            split.deleteGroup(groupId)
            onDone()
        }
    }

    /** Plain-text summary for the share sheet. Built only when the user taps Share. */
    fun shareText(): String {
        val s = state.value
        val group = s.group ?: return ""
        val fmt = MoneyFormat::format
        return buildString {
            appendLine("${group.name} (${group.currency})")
            if (s.expenses.isNotEmpty()) {
                appendLine()
                appendLine("Expenses")
                s.expenses.asReversed().forEach { appendLine("• ${it.title}: ${it.paidBy} paid ${fmt(it.amount, false)}") }
            }
            appendLine()
            appendLine("Balances")
            s.members.forEach { m ->
                appendLine(
                    "• " + when {
                        m.net.minorUnits > 0L -> "${m.name} is owed ${fmt(m.net, false)}"
                        m.net.minorUnits < 0L -> "${m.name} owes ${fmt(Money(-m.net.minorUnits, m.net.currency), false)}"
                        else -> "${m.name} is settled"
                    },
                )
            }
            if (s.suggestions.isNotEmpty()) {
                appendLine()
                appendLine("To settle up")
                s.suggestions.forEach { appendLine("• ${it.from} owes ${it.to} ${fmt(it.amount, false)}") }
            }
            appendLine()
            append("Shared from Salli")
        }
    }

    private fun message(error: SplitError): String = when (error) {
        SplitError.EXACT_MISMATCH -> "The amounts don't add up to the total"
        SplitError.CURRENCY_MISMATCH -> "This group uses a different currency"
        SplitError.NO_PARTICIPANTS -> "Pick at least one person"
        SplitError.NON_POSITIVE_TOTAL -> "Enter an amount above zero"
        SplitError.INVALID_WEIGHTS -> "Give at least one person a weight"
        SplitError.SAME_MEMBER -> "Someone can't pay themselves"
        else -> "Couldn't save that"
    }
}
