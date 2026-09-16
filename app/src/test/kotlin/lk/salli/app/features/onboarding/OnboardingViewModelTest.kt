package lk.salli.app.features.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import lk.salli.app.sms.HistoricalImporter
import lk.salli.app.sms.SmsInboxReader
import lk.salli.data.db.SalliDatabase
import lk.salli.data.ingest.IngestResult
import lk.salli.data.prefs.SalliPreferences
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class OnboardingViewModelTest {
    private lateinit var db: SalliDatabase
    private lateinit var prefs: SalliPreferences
    private val models = ViewModelStore()
    private var modelId = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SalliDatabase::class.java)
            .allowMainThreadQueries().build()
        prefs = SalliPreferences(context)
        runBlocking {
            prefs.setOnboardingCompleted(false)
            prefs.setHistoricalImportCompleted(false)
        }
    }

    @After
    fun tearDown() {
        models.clear()
        db.close()
        Dispatchers.resetMain()
    }

    private fun model(
        importer: HistoricalImporter = HistoricalImporter({ emptyList() }) { _, _, _ ->
            error("An empty inbox must never call ingest")
        },
        saved: SavedStateHandle = SavedStateHandle(),
    ) = OnboardingViewModel(importer, db, prefs, saved).also { models.put("vm-${modelId++}", it) }

    @Test
    fun `skip persists before emitting navigation and duplicate taps keep first destination`() = runBlocking {
        val vm = model()
        vm.complete(target = OnboardingCompletionTarget.HOME)
        vm.complete(target = OnboardingCompletionTarget.REVIEW_UNKNOWN)

        val result = withTimeout(10_000) { vm.state.first { it.completionTarget != null } }
        assertThat(result.completionTarget).isEqualTo(OnboardingCompletionTarget.HOME)
        assertThat(prefs.onboardingCompleted.first()).isTrue()
        assertThat(prefs.historicalImportCompleted.first()).isFalse()
    }

    @Test
    fun `provider failure does not mark historical import complete or leak diagnostics`() = runBlocking {
        val vm = model(HistoricalImporter({ error("Internal provider diagnostic") }) { _, _, _ ->
            IngestResult.Inserted(1)
        })
        vm.runImport()
        val result = withTimeout(10_000) { vm.importProgress.first { it.error != null } }

        assertThat(result.running).isFalse()
        assertThat(result.finished).isFalse()
        assertThat(result.error).doesNotContain("Internal provider diagnostic")
        assertThat(prefs.historicalImportCompleted.first()).isFalse()
        assertThat(prefs.onboardingCompleted.first()).isFalse()
    }

    @Test
    fun `empty inbox finishes honestly and waits for explicit completion`() = runBlocking {
        val vm = model()
        vm.runImport()
        val result = withTimeout(10_000) { vm.importProgress.first { it.finished } }

        assertThat(result.total).isEqualTo(0)
        assertThat(result.inserted).isEqualTo(0)
        assertThat(prefs.historicalImportCompleted.first()).isTrue()
        assertThat(prefs.onboardingCompleted.first()).isFalse()
    }

    @Test
    fun `cannot finish introduction while import is still writing`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val vm = model(HistoricalImporter({ listOf(sample) }) { _, _, _ ->
            gate.await()
            IngestResult.Inserted(1)
        })
        vm.runImport()
        vm.complete(target = OnboardingCompletionTarget.HOME)
        assertThat(vm.state.value.completionTarget).isNull()
        assertThat(prefs.onboardingCompleted.first()).isFalse()

        gate.complete(Unit)
        withTimeout(10_000) { vm.importProgress.first { it.finished } }
        Unit
    }

    @Test
    fun `duplicate-only rerun retains completed results across saved-state recreation`() = runBlocking {
        val saved = SavedStateHandle()
        val importer = HistoricalImporter({ listOf(sample) }) { _, _, _ -> IngestResult.Duplicate(7) }
        val vm = model(importer, saved)
        vm.showStage(OnboardingStage.IMPORT)
        vm.runImport()
        val result = withTimeout(10_000) { vm.importProgress.first { it.finished } }
        assertThat(result.duplicates).isEqualTo(1)
        assertThat(result.inserted).isEqualTo(0)

        val restoredHandle = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val restored = model(importer, restoredHandle).importProgress.value
        assertThat(restored.finished).isTrue()
        assertThat(restored.running).isFalse()
        assertThat(restored.duplicates).isEqualTo(1)
    }

    private val sample = SmsInboxReader.RawSms("COMBANK", "Synthetic test message", 1L)
}
