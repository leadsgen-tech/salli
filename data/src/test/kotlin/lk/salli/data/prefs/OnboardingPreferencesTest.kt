package lk.salli.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingPreferencesTest {
    @get:Rule val folder = TemporaryFolder()
    private val storageJob = SupervisorJob()
    private val store by lazy {
        PreferenceDataStoreFactory.create(scope = CoroutineScope(storageJob + Dispatchers.IO)) {
            folder.root.resolve("onboarding.preferences_pb")
        }
    }
    private fun preferences() = SalliPreferences.fromStore(store)

    @After
    fun stopStorage() = runBlocking { storageJob.cancelAndJoin() }

    @Test
    fun `declining old-message scan remains distinct from completed import across restore`() = runBlocking {
        val prefs = preferences()
        prefs.setHistoricalImportCompleted(false)
        prefs.setHistoricalImportDeferred(true)
        prefs.setOnboardingCompleted(true)
        prefs.restore(PreferencesSnapshot())

        assertThat(prefs.historicalImportDeferred.first()).isTrue()
        assertThat(prefs.historicalImportCompleted.first()).isFalse()
        assertThat(prefs.onboardingCompleted.first()).isTrue()
    }

    @Test
    fun `skipping introduction persists without pretending SMS was imported`() = runBlocking {
        val prefs = preferences()
        prefs.setHistoricalImportCompleted(false)
        prefs.setOnboardingCompleted(true)

        val reopened = preferences()
        assertThat(reopened.onboardingCompleted.first()).isTrue()
        assertThat(reopened.historicalImportCompleted.first()).isFalse()
    }

    @Test
    fun `restoring old backup cannot resurrect introduction after skip`() = runBlocking {
        val prefs = preferences()
        prefs.setOnboardingCompleted(true)
        prefs.setHistoricalImportCompleted(false)
        prefs.restore(PreferencesSnapshot(historicalImportCompleted = false))

        assertThat(prefs.onboardingCompleted.first()).isTrue()
        assertThat(prefs.historicalImportCompleted.first()).isFalse()
    }

    @Test
    fun `restoring imported backup preserves explicit unfinished introduction`() = runBlocking {
        val prefs = preferences()
        prefs.setOnboardingCompleted(false)
        prefs.restore(PreferencesSnapshot(historicalImportCompleted = true))

        assertThat(prefs.onboardingCompleted.first()).isFalse()
        assertThat(prefs.historicalImportCompleted.first()).isTrue()
    }

    @Test
    fun `existing imported installs skip introduction unless explicitly reset`() = runBlocking {
        val prefs = preferences()
        prefs.setHistoricalImportCompleted(true)
        assertThat(prefs.onboardingCompleted.first()).isTrue()
        prefs.setOnboardingCompleted(false)
        assertThat(prefs.onboardingCompleted.first()).isFalse()
    }
}
