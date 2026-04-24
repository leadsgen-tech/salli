package lk.salli.app.features.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.salli.app.ai.LocalLlmRunner
import lk.salli.app.ai.ModelDownloadWorker
import lk.salli.app.sms.SmsRefresher
import lk.salli.data.ai.ImportResult
import lk.salli.data.ai.ModelManager
import lk.salli.data.ai.ModelStatus
import lk.salli.data.db.SalliDatabase
import lk.salli.data.export.DataWiper
import lk.salli.data.export.TransactionExporter
import lk.salli.data.prefs.SalliPreferences
import lk.salli.domain.ParseMode

sealed interface SettingsEvent {
    data class ShareCsv(val intent: Intent) : SettingsEvent
    data class Message(val text: String) : SettingsEvent
}

data class SettingsUiState(
    val exporting: Boolean = false,
    val wiping: Boolean = false,
    val importingModel: Boolean = false,
    val testingAi: Boolean = false,
    val aiTestResult: AiTestResult? = null,
    val parseMode: ParseMode = ParseMode.STANDARD,
    val modelStatus: ModelStatus = ModelStatus.NotDownloaded,
    val userName: String = "",
    val unknownSmsCount: Int = 0,
)

data class AiTestResult(
    val prompt: String,
    val output: String,
    val loadMillis: Long,
    val inferMillis: Long,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val exporter: TransactionExporter,
    private val wiper: DataWiper,
    private val prefs: SalliPreferences,
    private val modelManager: ModelManager,
    private val llmRunner: LocalLlmRunner,
    private val db: SalliDatabase,
    private val app: Application,
    private val refresher: SmsRefresher,
) : ViewModel() {

    /**
     * Kick off the one-shot historical import if it hasn't already run. Called when the user
     * grants SMS access from Settings — catches the onboarding-skipped path where the inbox
     * would otherwise only trickle in via the live receiver.
     */
    fun ensureHistoricalImport() {
        refresher.ensureHistoricalImport()
    }

    /** User-initiated full-inbox rescan — bypasses the already-imported pref. */
    fun resyncMessages() {
        refresher.resyncAll()
    }

    /** Whether a refresh/resync is currently running, for the tile's trailing spinner. */
    val syncing: StateFlow<Boolean> = refresher.refreshing

    private val actionState = MutableStateFlow(SettingsUiState())

    val state: StateFlow<SettingsUiState> = combine(
        actionState,
        prefs.parseMode,
        modelManager.observeStatus(),
        prefs.userName,
        db.unknownSms().observePendingCount(),
    ) { base, mode, model, name, unknownCount ->
        base.copy(
            parseMode = mode,
            modelStatus = model,
            userName = name,
            unknownSmsCount = unknownCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    // Events are one-shot effects (open a share sheet, show a toast) — modelling them as
    // StateFlow would replay the last emission on every re-subscription (e.g. config change),
    // firing a "Deleted" toast a second time. A Channel guarantees exactly-once delivery.
    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    private fun emit(event: SettingsEvent) {
        _events.trySend(event)
    }

    fun exportCsv() {
        if (actionState.value.exporting) return
        actionState.value = actionState.value.copy(exporting = true)
        viewModelScope.launch {
            runCatching { exporter.exportToCsv() }
                .onSuccess { file ->
                    emit(SettingsEvent.ShareCsv(exporter.shareIntent(file)))
                }
                .onFailure {
                    emit(SettingsEvent.Message("Export failed: ${it.message}"))
                }
            actionState.value = actionState.value.copy(exporting = false)
        }
    }

    fun deleteAllData() {
        if (actionState.value.wiping) return
        actionState.value = actionState.value.copy(wiping = true)
        viewModelScope.launch {
            runCatching { wiper.wipe() }
                .onSuccess { emit(SettingsEvent.Message("All data cleared")) }
                .onFailure { emit(SettingsEvent.Message("Delete failed: ${it.message}")) }
            actionState.value = actionState.value.copy(wiping = false)
        }
    }

    fun setParseMode(mode: ParseMode) {
        // Guard: can't switch to AI mode unless the model is installed.
        if (mode == ParseMode.AI && state.value.modelStatus !is ModelStatus.Installed) {
            emit(SettingsEvent.Message("Download the AI model first"))
            return
        }
        viewModelScope.launch { prefs.setParseMode(mode) }
    }

    fun downloadModel() {
        val status = state.value.modelStatus
        if (status is ModelStatus.Installed || status is ModelStatus.Downloading ||
            status is ModelStatus.Queued
        ) return
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(ModelManager.buildInputData())
            .build()
        modelManager.enqueueDownload(request)
    }

    fun cancelDownload() {
        modelManager.cancelDownload()
    }

    fun importModelFromUri(uri: Uri) {
        if (actionState.value.importingModel) return
        actionState.value = actionState.value.copy(importingModel = true)
        viewModelScope.launch {
            when (val result = modelManager.importFromUri(uri)) {
                is ImportResult.Success -> {
                    emit(
                        SettingsEvent.Message(
                            "Model loaded (${result.bytes / (1024 * 1024)} MB)",
                        ),
                    )
                }
                is ImportResult.Failed -> {
                    emit(SettingsEvent.Message("Import failed: ${result.reason}"))
                }
            }
            actionState.value = actionState.value.copy(importingModel = false)
        }
    }

    fun deleteModel() {
        // If AI mode is active, drop back to Standard first so the user doesn't get a broken state.
        viewModelScope.launch {
            if (state.value.parseMode == ParseMode.AI) {
                prefs.setParseMode(ParseMode.STANDARD)
            }
            llmRunner.release()
            modelManager.deleteModel()
            emit(SettingsEvent.Message("AI model deleted"))
        }
    }

    /**
     * Debug action: run one real SMS through the loaded model and report output + timings.
     * Confirms (a) the file loads, (b) MediaPipe is happy with our Qwen variant, (c) the
     * prompt shape actually extracts the fields we need.
     */
    fun testAi() {
        if (actionState.value.testingAi) return
        if (state.value.modelStatus !is ModelStatus.Installed) {
            emit(SettingsEvent.Message("Download the model first"))
            return
        }
        actionState.value = actionState.value.copy(testingAi = true, aiTestResult = null)
        viewModelScope.launch {
            val prompt = AI_TEST_PROMPT
            val outcome = runCatching { llmRunner.run(prompt) }
            outcome.fold(
                onSuccess = { c ->
                    Log.i("SalliAI", "test OK | load=${c.loadMillis}ms infer=${c.inferMillis}ms")
                    Log.i("SalliAI", "output: ${c.text}")
                },
                onFailure = { Log.e("SalliAI", "test failed: ${it.message}", it) },
            )
            actionState.value = actionState.value.copy(
                testingAi = false,
                aiTestResult = outcome.fold(
                    onSuccess = { c ->
                        AiTestResult(
                            prompt = prompt,
                            output = c.text,
                            loadMillis = c.loadMillis,
                            inferMillis = c.inferMillis,
                        )
                    },
                    onFailure = { t ->
                        AiTestResult(
                            prompt = prompt,
                            output = "",
                            loadMillis = 0,
                            inferMillis = 0,
                            error = "${t.javaClass.simpleName}: ${t.message}",
                        )
                    },
                ),
            )
        }
    }

    fun dismissAiTest() {
        actionState.value = actionState.value.copy(aiTestResult = null)
    }

    fun setUserName(name: String) {
        viewModelScope.launch { prefs.setUserName(name) }
    }

    private companion object {
        // A real debit SMS from the user's BOC inbox, redacted. Schema-constrained prompt —
        // the small Qwen 0.5B needs a terse, example-heavy shape to stay on-rails.
        const val AI_TEST_PROMPT = """You extract transaction details from Sri Lankan bank SMS.
Respond with a single JSON object and nothing else. Schema:
{"amount": number, "currency": "LKR"|"USD", "direction": "debit"|"credit", "merchant": string|null, "balance": number|null}

SMS: "Online Transfer Debit Rs 85000.00 From A/C No XXXXXXXXXX870. Balance available Rs 929.10 - Thank you for banking with BOC"

JSON:"""
    }
}
