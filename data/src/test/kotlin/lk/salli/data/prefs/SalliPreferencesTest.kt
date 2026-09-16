package lk.salli.data.prefs

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SalliPreferencesTest {

    /**
     * DataStore re-emits every key on any write. Insights and Timeline reset their date range
     * when the period changes, so an unrelated write (theme, name, the summary worker's markers)
     * must not look like a period change.
     */
    @Test
    fun `unrelated writes do not re-emit period settings`(): Unit = runBlocking {
        val prefs = SalliPreferences(ApplicationProvider.getApplicationContext())
        prefs.setMonthStartDay(1)
        val days = Collections.synchronizedList(mutableListOf<Int>())
        val periods = Collections.synchronizedList(mutableListOf<PeriodSettings>())
        val jobs = listOf(
            launch(Dispatchers.Default) { prefs.monthStartDay.collect { days.add(it) } },
            launch(Dispatchers.Default) { prefs.period.collect { periods.add(it) } },
        )
        try {
            delay(400)
            prefs.setUserName("someone")
            prefs.setThemeMode(ThemeMode.DARK)
            prefs.setSummaryHour(21)
            prefs.setBillReminderDays(5)
            delay(400)
            assertThat(days).hasSize(1)
            assertThat(periods).hasSize(1)

            prefs.setMonthStartDay(25)
            delay(400)
            assertThat(days).containsExactly(1, 25).inOrder()
        } finally {
            jobs.forEach { it.cancel() }
            prefs.setMonthStartDay(1)
            prefs.setUserName("")
            prefs.setThemeMode(ThemeMode.SYSTEM)
            prefs.setSummaryHour(SalliPreferences.DEFAULT_SUMMARY_HOUR)
            prefs.setBillReminderDays(SalliPreferences.DEFAULT_BILL_REMINDER_DAYS)
        }
    }

    @Test
    fun `widget and app lock settings default off and only re-emit on their own changes`(): Unit = runBlocking {
        val prefs = SalliPreferences(ApplicationProvider.getApplicationContext())
        assertThat(prefs.widgetHideAmounts.first()).isFalse()
        assertThat(prefs.appLock.first()).isEqualTo(AppLockSettings(enabled = false, lockAfterSeconds = 0, hideInRecents = false))
        assertThat(prefs.hideInRecents.first()).isFalse()

        val hides = Collections.synchronizedList(mutableListOf<Boolean>())
        val locks = Collections.synchronizedList(mutableListOf<AppLockSettings>())
        val jobs = listOf(
            launch(Dispatchers.Default) { prefs.widgetHideAmounts.collect { hides.add(it) } },
            launch(Dispatchers.Default) { prefs.appLock.collect { locks.add(it) } },
        )
        try {
            delay(400)
            prefs.setUserName("someone")
            prefs.setMonthStartDay(12)
            delay(400)
            assertThat(hides).hasSize(1)
            assertThat(locks).hasSize(1)

            prefs.setWidgetHideAmounts(true)
            prefs.setAppLockAfterSeconds(60)
            delay(400)
            // Only the offered choices are stored; anything else means immediately.
            prefs.setAppLockAfterSeconds(45)
            delay(400)
            assertThat(hides).containsExactly(false, true).inOrder()
            assertThat(locks.map { it.lockAfterSeconds }).containsExactly(0, 60, 0).inOrder()
        } finally {
            jobs.forEach { it.cancel() }
            prefs.setUserName("")
            prefs.setMonthStartDay(1)
            prefs.setWidgetHideAmounts(false)
            prefs.setAppLockAfterSeconds(0)
        }
    }

    @Test
    fun `restore keeps this device's widget masking and security settings`(): Unit = runBlocking {
        val prefs = SalliPreferences(ApplicationProvider.getApplicationContext())
        try {
            prefs.setWidgetHideAmounts(true)
            prefs.setAppLockEnabled(true)
            prefs.setAppLockAfterSeconds(300)
            prefs.setHideInRecents(true)
            val snap = prefs.snapshot()
            assertThat(snap.widgetHideAmounts).isTrue()
            assertThat(snap.appLockEnabled).isTrue()
            assertThat(snap.appLockAfterSeconds).isEqualTo(300)
            assertThat(snap.hideInRecents).isTrue()

            prefs.restore(PreferencesSnapshot())
            assertThat(prefs.widgetHideAmounts.first()).isTrue()
            assertThat(prefs.appLock.first()).isEqualTo(AppLockSettings(enabled = true, lockAfterSeconds = 300, hideInRecents = true))

            prefs.setWidgetHideAmounts(false)
            prefs.setAppLockEnabled(false)
            prefs.setAppLockAfterSeconds(0)
            prefs.setHideInRecents(false)
            prefs.restore(snap)
            assertThat(prefs.widgetHideAmounts.first()).isFalse()
            assertThat(prefs.appLock.first()).isEqualTo(AppLockSettings(enabled = false, lockAfterSeconds = 0, hideInRecents = false))
            assertThat(prefs.hideInRecents.first()).isFalse()
        } finally {
            prefs.restore(PreferencesSnapshot())
        }
    }
}
