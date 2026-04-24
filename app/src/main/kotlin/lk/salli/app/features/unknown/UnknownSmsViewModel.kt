package lk.salli.app.features.unknown

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.UnknownSmsEntity

data class UnknownSmsUiState(
    val pending: List<UnknownSmsEntity> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class UnknownSmsViewModel @Inject constructor(
    private val db: SalliDatabase,
) : ViewModel() {

    val state: StateFlow<UnknownSmsUiState> = db.unknownSms().observePending()
        .map { UnknownSmsUiState(pending = it, isLoading = false) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            UnknownSmsUiState(),
        )

    /** User confirms this SMS is noise (promo, OTP, etc.) and doesn't want to see it again. */
    fun ignore(entry: UnknownSmsEntity) {
        viewModelScope.launch { db.unknownSms().resolve(entry.id, "ignored") }
    }

    /**
     * User confirms this looks like a real transaction that our parser missed. Flags it for
     * template-authoring triage. Eventually this would open a form letting them pick amount
     * and account — for v1 we just mark it.
     */
    fun reportAsTransaction(entry: UnknownSmsEntity) {
        viewModelScope.launch { db.unknownSms().resolve(entry.id, "template_added") }
    }

    fun delete(entry: UnknownSmsEntity) {
        viewModelScope.launch { db.unknownSms().deleteById(entry.id) }
    }
}
