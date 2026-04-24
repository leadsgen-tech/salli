package lk.salli.app.features.txdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.CategoryEntity
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.domain.Money

data class TxDetailState(
    val loading: Boolean = true,
    val transaction: TransactionEntity? = null,
    val accountName: String? = null,
    val categoryId: Long? = null,
    val categories: List<CategoryEntity> = emptyList(),
)

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val db: SalliDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val txId: Long = savedStateHandle.get<Long>("id") ?: 0L

    private val _state = MutableStateFlow(TxDetailState())
    val state: StateFlow<TxDetailState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val tx = db.transactions().byId(txId)
            val account = tx?.accountId?.let { db.accounts().byId(it) }
            val cats = db.categories().all()
            _state.value = TxDetailState(
                loading = false,
                transaction = tx,
                accountName = account?.displayName,
                categoryId = tx?.categoryId,
                categories = cats,
            )
        }
    }

    fun changeCategory(newId: Long) {
        val tx = _state.value.transaction ?: return
        viewModelScope.launch {
            // userTagged=true locks this choice against the startup recategorise pass.
            db.transactions().update(
                tx.copy(
                    categoryId = newId,
                    userTagged = true,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            _state.value = _state.value.copy(categoryId = newId)
        }
    }

    fun setNote(note: String) {
        val tx = _state.value.transaction ?: return
        viewModelScope.launch {
            val updated = tx.copy(
                note = note.takeIf { it.isNotBlank() },
                updatedAt = System.currentTimeMillis(),
            )
            db.transactions().update(updated)
            _state.value = _state.value.copy(transaction = updated)
        }
    }

    fun displayAmount(): Money? = state.value.transaction?.let {
        Money(it.amountMinor, it.amountCurrency)
    }
}
