package lk.salli.app.features.split

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.SplitExpenseEntity
import lk.salli.data.db.entities.SplitSettlementEntity
import lk.salli.data.db.entities.SplitShareEntity
import lk.salli.data.prefs.SalliPreferences
import lk.salli.data.split.SplitService
import lk.salli.domain.Money
import lk.salli.domain.split.ExpenseInput
import lk.salli.domain.split.SettlementInput
import lk.salli.domain.split.SplitMath

data class GroupRow(
    val id: Long,
    val name: String,
    val currency: String,
    val memberCount: Int,
    /** The user's net in this group; positive means they are owed. */
    val myNet: Money?,
    val archived: Boolean,
)

/** A transaction handed over by "Split this", waiting for the user to pick a group. */
data class PendingTransaction(val id: Long, val title: String, val amount: Money)

data class SplitGroupsUiState(
    val groups: List<GroupRow> = emptyList(),
    val pending: PendingTransaction? = null,
    val loading: Boolean = true,
)

@HiltViewModel
class SplitGroupsViewModel @Inject constructor(
    private val db: SalliDatabase,
    private val split: SplitService,
    private val prefs: SalliPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val pending = MutableStateFlow<PendingTransaction?>(null)

    init {
        savedStateHandle.get<Long>("tx")?.takeIf { it > 0L }?.let { txId ->
            viewModelScope.launch {
                val tx = db.transactions().byId(txId) ?: return@launch
                pending.value = PendingTransaction(
                    id = tx.id,
                    title = tx.note?.takeIf { it.isNotBlank() } ?: tx.merchantRaw?.takeIf { it.isNotBlank() } ?: "Transaction",
                    amount = Money(tx.amountMinor, tx.amountCurrency),
                )
            }
        }
    }

    val state: StateFlow<SplitGroupsUiState> = combine(
        db.split().observeGroups(),
        db.split().observeMembers(),
        db.split().observeExpenses(),
        db.split().observeShares(),
        combine(db.split().observeSettlements(), pending) { settlements, pend -> settlements to pend },
    ) { groups, members, expenses, shares, (settlements, pend) ->
        SplitGroupsUiState(
            groups = groups.map { g ->
                val groupMembers = members.filter { it.groupId == g.id }
                val me = groupMembers.firstOrNull { it.isMe }
                val net = groupBalances(g.id, groupMembers.map { it.id }, expenses, shares, settlements)
                GroupRow(
                    id = g.id,
                    name = g.name,
                    currency = g.currency,
                    memberCount = groupMembers.size,
                    myNet = me?.let { Money(net[it.id] ?: 0L, g.currency) },
                    archived = g.archived,
                )
            },
            pending = pend,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SplitGroupsUiState())

    /** Creates a group with the user in it, then hands its id to [onCreated] on the main thread. */
    fun createGroup(name: String, currency: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val meName = prefs.userName.first().ifBlank { "Me" }
            onCreated(split.createGroup(name, currency, meName))
        }
    }
}

internal fun groupBalances(
    groupId: Long,
    memberIds: List<Long>,
    expenses: List<SplitExpenseEntity>,
    shares: List<SplitShareEntity>,
    settlements: List<SplitSettlementEntity>,
): Map<Long, Long> {
    val sharesByExpense = shares.groupBy { it.expenseId }
    return SplitMath.balances(
        memberIds,
        expenses.filter { it.groupId == groupId }.map { e ->
            ExpenseInput(e.paidByMemberId, e.amountMinor, sharesByExpense[e.id].orEmpty().associate { it.memberId to it.shareMinor })
        },
        settlements.filter { it.groupId == groupId }.map { SettlementInput(it.fromMemberId, it.toMemberId, it.amountMinor) },
    )
}
