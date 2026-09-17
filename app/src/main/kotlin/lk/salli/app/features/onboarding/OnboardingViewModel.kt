package lk.salli.app.features.onboarding

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.salli.app.sms.HistoricalImporter
import lk.salli.app.sms.InboxSummary
import lk.salli.app.sms.TransactionPreview
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.AccountEntity
import lk.salli.data.prefs.SalliPreferences

enum class OnboardingStage { WELCOME, DEMO, PRIVACY, SMS_ACCESS, IMPORT }
enum class OnboardingCompletionTarget { HOME, REVIEW_UNKNOWN }

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
    val interrupted: Boolean = false,
    /** Deliberately user-safe copy. The underlying exception is only written to Logcat. */
    val error: String? = null,
    val previews: List<TransactionPreview> = emptyList(),
)

data class OnboardingState(
    val stage: OnboardingStage = OnboardingStage.WELCOME,
    val import: ImportUiState = ImportUiState(),
    val accounts: List<AccountEntity> = emptyList(),
    val completing: Boolean = false,
    val completionError: String? = null,
    val completionTarget: OnboardingCompletionTarget? = null,
    val inboxSummary: InboxSummary? = null,
    val savedTransactionCount: Int = 0,
    val historyDays: List<Long> = emptyList(),
)

private data class SavedHistory(val count: Int = 0, val days: List<Long> = emptyList())

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val importer: HistoricalImporter,
    private val db: SalliDatabase,
    private val prefs: SalliPreferences,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val savedStage = savedStateHandle.getStateFlow(STAGE_KEY, OnboardingStage.WELCOME.name)
    private val initialStage = savedStateHandle.get<String>(STAGE_KEY)
        ?.let { name -> OnboardingStage.entries.firstOrNull { it.name == name } }
        ?: OnboardingStage.WELCOME
    private val initialImport = if (savedStateHandle.get<Boolean>(IMPORT_FINISHED_KEY) == true) {
        ImportUiState(
            processed = savedStateHandle.get<Int>(PROCESSED_KEY) ?: 0,
            total = savedStateHandle.get<Int>(TOTAL_KEY) ?: 0,
            inserted = savedStateHandle.get<Int>(INSERTED_KEY) ?: 0,
            merged = savedStateHandle.get<Int>(MERGED_KEY) ?: 0,
            paired = savedStateHandle.get<Int>(PAIRED_KEY) ?: 0,
            duplicates = savedStateHandle.get<Int>(DUPLICATES_KEY) ?: 0,
            queued = savedStateHandle.get<Int>(QUEUED_KEY) ?: 0,
            dropped = savedStateHandle.get<Int>(DROPPED_KEY) ?: 0,
            finished = true,
        )
    } else {
        ImportUiState(interrupted = savedStateHandle.get<Boolean>(IMPORT_STARTED_KEY) == true)
    }
    private val importState = MutableStateFlow(initialImport)
    private val completing = MutableStateFlow(false)
    private val completionError = MutableStateFlow<String?>(null)
    private val completionTarget = MutableStateFlow<OnboardingCompletionTarget?>(null)
    private val completionStarted = AtomicBoolean(false)
    private val inboxSummary = MutableStateFlow<InboxSummary?>(null)
    private val savedHistory = MutableStateFlow(SavedHistory())

    init {
        viewModelScope.launch { runCatching { refreshHistory() }.onFailure { Log.w("SalliOnboarding", "History summary unavailable", it) } }
    }

    val state: StateFlow<OnboardingState> = combine(
        combine(savedStage, importState) { stageName, import -> stageName to import },
        combine(db.accounts().observeAll().onStart { emit(emptyList()) }, completing) { accounts, isCompleting -> accounts to isCompleting },
        combine(completionError, completionTarget) { error, target -> error to target },
        inboxSummary,
        savedHistory,
    ) { stageAndImport, accountsAndCompleting, completion, summary, history ->
        val (stageName, import) = stageAndImport
        val (accounts, isCompleting) = accountsAndCompleting
        val (saveError, savedTarget) = completion
        OnboardingState(
            stage = OnboardingStage.entries.firstOrNull { it.name == stageName }
                ?: OnboardingStage.WELCOME,
            import = import,
            accounts = accounts,
            completing = isCompleting,
            completionError = saveError,
            completionTarget = savedTarget,
            inboxSummary = summary,
            savedTransactionCount = history.count,
            historyDays = history.days,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        OnboardingState(stage = initialStage, import = initialImport),
    )

    val importProgress: StateFlow<ImportUiState> = importState.asStateFlow()

    fun showStage(stage: OnboardingStage) {
        savedStateHandle[STAGE_KEY] = stage.name
    }

    fun goBack() {
        val currentName = savedStage.value
        val current = OnboardingStage.entries.firstOrNull { it.name == currentName }
            ?: OnboardingStage.WELCOME
        if (current != OnboardingStage.WELCOME) showStage(OnboardingStage.entries[current.ordinal - 1])
    }

    fun runImport() {
        if (importState.value.running) return
        savedStateHandle[IMPORT_STARTED_KEY] = true
        savedStateHandle[IMPORT_FINISHED_KEY] = false
        importState.value = ImportUiState(running = true)
        viewModelScope.launch {
            try {
                inboxSummary.value = importer.summarize()
                prefs.setHistoricalImportDeferred(false)
                importer.import(sinceMillis = null, emitPreviews = true).collect { progress ->
                    importState.value = ImportUiState(
                        running = true,
                        processed = progress.processed,
                        total = progress.total,
                        inserted = progress.inserted,
                        merged = progress.merged,
                        paired = progress.paired,
                        duplicates = progress.duplicates,
                        queued = progress.queued,
                        dropped = progress.dropped,
                        previews = if (progress.preview == null) importState.value.previews
                            else (importState.value.previews + progress.preview).takeLast(4),
                    )
                }
                prefs.setHistoricalImportCompleted(true)
                savedStateHandle[IMPORT_STARTED_KEY] = false
                importState.value = importState.value.copy(running = false, finished = true)
                saveFinishedImport(importState.value)
                runCatching { refreshHistory() }.onFailure { Log.w("SalliOnboarding", "History summary unavailable", it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("SalliOnboarding", "Historical SMS import failed", error)
                importState.value = importState.value.copy(
                    running = false,
                    finished = false,
                    interrupted = false,
                    error = "Salli couldn’t finish reading the inbox. Your existing data is safe.",
                )
            }
        }
    }

    private suspend fun refreshHistory() {
        val dao = db.transactions()
        val days = dao.recentVisibleTimestamps().map { timestamp ->
            Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        }
        savedHistory.value = SavedHistory(dao.visibleCount(), days)
    }

    /** Persist first-run completion before navigation. Rapid taps remain a single operation. */
    fun complete(
        deferHistory: Boolean = false,
        target: OnboardingCompletionTarget = OnboardingCompletionTarget.HOME,
    ) {
        if (importState.value.running) return
        if (!completionStarted.compareAndSet(false, true)) return
        completing.value = true
        completionError.value = null
        viewModelScope.launch {
            try {
                if (deferHistory) prefs.setHistoricalImportDeferred(true)
                prefs.setOnboardingCompleted(true)
                completing.value = false
                completionTarget.value = target
            } catch (cancelled: CancellationException) {
                completionStarted.set(false)
                completing.value = false
                throw cancelled
            } catch (error: Exception) {
                Log.e("SalliOnboarding", "Could not save onboarding completion", error)
                completionStarted.set(false)
                completing.value = false
                completionError.value = "Couldn’t save your choice. Please try again."
            }
        }
    }

    /** Called by the active composition after it dispatches the saved navigation target. */
    fun consumeCompletion() {
        completionTarget.value = null
    }

    private fun saveFinishedImport(import: ImportUiState) {
        savedStateHandle[IMPORT_FINISHED_KEY] = true
        savedStateHandle[PROCESSED_KEY] = import.processed
        savedStateHandle[TOTAL_KEY] = import.total
        savedStateHandle[INSERTED_KEY] = import.inserted
        savedStateHandle[MERGED_KEY] = import.merged
        savedStateHandle[PAIRED_KEY] = import.paired
        savedStateHandle[DUPLICATES_KEY] = import.duplicates
        savedStateHandle[QUEUED_KEY] = import.queued
        savedStateHandle[DROPPED_KEY] = import.dropped
    }

    companion object {
        private const val STAGE_KEY = "onboarding_stage"
        private const val IMPORT_STARTED_KEY = "onboarding_import_started"
        private const val IMPORT_FINISHED_KEY = "onboarding_import_finished"
        private const val PROCESSED_KEY = "onboarding_import_processed"
        private const val TOTAL_KEY = "onboarding_import_total"
        private const val INSERTED_KEY = "onboarding_import_inserted"
        private const val MERGED_KEY = "onboarding_import_merged"
        private const val PAIRED_KEY = "onboarding_import_paired"
        private const val DUPLICATES_KEY = "onboarding_import_duplicates"
        private const val QUEUED_KEY = "onboarding_import_queued"
        private const val DROPPED_KEY = "onboarding_import_dropped"
    }
}
