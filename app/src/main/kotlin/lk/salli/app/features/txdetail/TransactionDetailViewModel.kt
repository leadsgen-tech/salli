package lk.salli.app.features.txdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.CategoryEntity
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.data.split.SplitService
import lk.salli.domain.Money

data class TxDetailState(
    val loading: Boolean = true,
    val transaction: TransactionEntity? = null,
    val accountName: String? = null,
    val accountSender: String? = null,
    /** Set when this row is one leg of an internal transfer: the other account's name. */
    val counterpartAccountName: String? = null,
    val counterpartSender: String? = null,
    val counterpartAmountMinor: Long? = null,
    /** Set when the transaction was split with other people. */
    val linkedSplit: SplitService.LinkedSplit? = null,
    val categoryId: Long? = null,
    val categories: List<CategoryEntity> = emptyList(),
)

/**
 * Backs the transaction sheet, which is hoisted above the NavHost so the screen underneath stays
 * put while it opens and closes. One instance lives for the activity and is re-pointed with [open]
 * each time the sheet appears: reopening always reads fresh data (a split just made, a note saved
 * elsewhere), and view models no longer pile up one per transaction ever opened.
 */
@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val db: SalliDatabase,
    private val split: SplitService,
) : ViewModel() {

    private val _state = MutableStateFlow(TxDetailState())
    val state: StateFlow<TxDetailState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun open(txId: Long) {
        loadJob?.cancel()
        _state.value = TxDetailState()
        loadJob = viewModelScope.launch {
            val tx = db.transactions().byId(txId)
            val account = tx?.accountId?.let { db.accounts().byId(it) }
            val otherLeg = tx?.transferGroupId?.let { gid ->
                db.transferGroups().byId(gid)?.let { g ->
                    val otherId = if (g.debitTxId == tx.id) g.creditTxId else g.debitTxId
                    db.transactions().byId(otherId)
                }
            }
            val counterpart = otherLeg?.accountId?.let { db.accounts().byId(it) }
            val cats = db.categories().all()
            _state.value = TxDetailState(
                loading = false,
                transaction = tx,
                accountName = account?.displayName,
                accountSender = account?.senderAddress,
                counterpartAccountName = counterpart?.displayName,
                counterpartSender = counterpart?.senderAddress,
                counterpartAmountMinor = otherLeg?.amountMinor,
                linkedSplit = tx?.let { split.linkedSplit(it.id) },
                categoryId = tx?.categoryId,
                categories = cats,
            )
        }
    }

    fun changeCategory(newId: Long) {
        val txId = _state.value.transaction?.id ?: return
        viewModelScope.launch {
            // Targeted update: writing the cached row back would undo edits made since it loaded.
            db.transactions().setUserCategory(txId, newId, System.currentTimeMillis())
            refreshRow(txId) { it.copy(categoryId = newId) }
        }
    }

    fun setNote(note: String) {
        val txId = _state.value.transaction?.id ?: return
        viewModelScope.launch {
            db.transactions().setNote(txId, note.takeIf { it.isNotBlank() }, System.currentTimeMillis())
            refreshRow(txId) { it }
        }
    }

    fun setExcluded(excluded: Boolean) {
        val txId = _state.value.transaction?.id ?: return
        viewModelScope.launch {
            db.transactions().setHidden(txId, excluded, System.currentTimeMillis())
            refreshRow(txId) { it }
        }
    }

    /** Re-reads the row after a write, unless the sheet has moved on to another transaction. */
    private suspend fun refreshRow(txId: Long, also: (TxDetailState) -> TxDetailState) {
        val fresh = db.transactions().byId(txId) ?: return
        val current = _state.value
        if (current.transaction?.id == txId) _state.value = also(current.copy(transaction = fresh))
    }

    fun displayAmount(): Money? = state.value.transaction?.let {
        Money(it.amountMinor, it.amountCurrency)
    }
}
