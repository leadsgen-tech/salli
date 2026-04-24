package lk.salli.app.features.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.salli.app.sms.HistoricalImporter
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.AccountEntity
import lk.salli.data.prefs.SalliPreferences
import lk.salli.domain.ParseMode

/**
 * Drives the onboarding page flow. Three pieces of state the pages need:
 *
 *  - [ImportUiState] for the live historical-import progress bar
 *  - the user's live-discovered accounts (so the import page can animate bank-branded cards
 *    flying in as each new account surfaces)
 *  - the chosen [ParseMode] so the picker page can reflect the saved pref
 *
 * Kept in one ViewModel because the pages share data and navigating between them resets Compose
 * state on every HorizontalPager swipe.
 */
data class ImportUiState(
    val running: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val inserted: Int = 0,
    val merged: Int = 0,
    val paired: Int = 0,
    val duplicates: Int = 0,
    val queued: Int = 0,
    val dropped: Int = 0,
    val finished: Boolean = false,
    val error: String? = null,
)

data class OnboardingState(
    val import: ImportUiState = ImportUiState(),
    val accounts: List<AccountEntity> = emptyList(),
    val parseMode: ParseMode = ParseMode.STANDARD,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val importer: HistoricalImporter,
    private val db: SalliDatabase,
    private val prefs: SalliPreferences,
) : ViewModel() {

    private val importState = MutableStateFlow(ImportUiState())

    val state: StateFlow<OnboardingState> = combine(
        importState,
        db.accounts().observeAll(),
        prefs.parseMode,
    ) { import, accounts, mode ->
        OnboardingState(import = import, accounts = accounts, parseMode = mode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingState())

    // Backwards-compatible alias so the import page can read the old shape directly.
    val importProgress: StateFlow<ImportUiState> = importState.asStateFlow()

    fun setParseMode(mode: ParseMode) {
        viewModelScope.launch { prefs.setParseMode(mode) }
    }

    fun runImport() {
        if (importState.value.running) return
        importState.value = ImportUiState(running = true)
        viewModelScope.launch {
            runCatching {
                importer.import(sinceMillis = null).collect { p ->
                    importState.value = importState.value.copy(
                        processed = p.processed,
                        total = p.total,
                        inserted = p.inserted,
                        merged = p.merged,
                        paired = p.paired,
                        duplicates = p.duplicates,
                        queued = p.queued,
                        dropped = p.dropped,
                    )
                }
            }.onFailure { err ->
                importState.value = importState.value.copy(
                    running = false,
                    finished = true,
                    error = err.message ?: err::class.simpleName,
                )
                return@launch
            }
            // Mark the one-shot historical import as done so SmsRefresher.ensureHistoricalImport
            // skips it when the user visits Settings. If the user skipped onboarding instead,
            // the pref stays false and Settings' SMS-grant path will trigger it.
            prefs.setHistoricalImportCompleted(true)
            importState.value = importState.value.copy(running = false, finished = true)
        }
    }
}
