package lk.salli.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

/**
 * The theme preference went from a flat boolean to System / Light / Dark. The interesting part
 * is not the enum, it's that an install which only ever touched the old `dark_theme` key must
 * not be silently handed back to the system palette on upgrade.
 *
 * Each test drives an isolated on-disk store (the app's real `SalliPreferences` code path, not
 * a fake) so nothing leaks between tests or into the process-wide DataStore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeModeTest {

    @get:Rule val folder = TemporaryFolder()
    private val storageJob = SupervisorJob()
    private val store by lazy {
        PreferenceDataStoreFactory.create(scope = CoroutineScope(storageJob + Dispatchers.IO)) {
            folder.root.resolve("theme.preferences_pb")
        }
    }
    private fun preferences() = SalliPreferences.fromStore(store)

    @After
    fun stopStorage() = runBlocking { storageJob.cancelAndJoin() }

    @Test
    fun `ids are stable because they are persisted and embedded in backups`() {
        assertThat(ThemeMode.SYSTEM.id).isEqualTo(0)
        assertThat(ThemeMode.LIGHT.id).isEqualTo(1)
        assertThat(ThemeMode.DARK.id).isEqualTo(2)
    }

    @Test
    fun `an unknown id falls back to following the system`() {
        // A backup written by a future build, or a corrupted pref, must not break the theme.
        assertThat(ThemeMode.fromId(99)).isEqualTo(ThemeMode.SYSTEM)
        assertThat(ThemeMode.fromId(-1)).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun `resolve collapses the preference against the phone's night mode`() {
        assertThat(ThemeMode.SYSTEM.resolve(systemInDarkMode = true)).isTrue()
        assertThat(ThemeMode.SYSTEM.resolve(systemInDarkMode = false)).isFalse()

        // Explicit choices ignore the phone entirely; that is the point of choosing.
        assertThat(ThemeMode.LIGHT.resolve(systemInDarkMode = true)).isFalse()
        assertThat(ThemeMode.DARK.resolve(systemInDarkMode = false)).isTrue()
    }

    @Test
    fun `a fresh install follows the system`() = runBlocking {
        assertThat(preferences().themeMode.first()).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun `an install that only ever set the old boolean keeps its palette`() = runBlocking {
        val legacyDark = booleanPreferencesKey("dark_theme")
        // Exactly the pre-migration state: dark_theme written, theme_mode absent.
        store.edit { it[legacyDark] = true }
        assertThat(preferences().themeMode.first()).isEqualTo(ThemeMode.DARK)

        store.edit { it[legacyDark] = false }
        assertThat(preferences().themeMode.first()).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun `setting a mode also keeps the legacy boolean coherent`() = runBlocking {
        val prefs = preferences()

        prefs.setThemeMode(ThemeMode.DARK)
        assertThat(prefs.themeMode.first()).isEqualTo(ThemeMode.DARK)
        assertThat(prefs.darkTheme.first()).isTrue()

        prefs.setThemeMode(ThemeMode.LIGHT)
        assertThat(prefs.themeMode.first()).isEqualTo(ThemeMode.LIGHT)
        assertThat(prefs.darkTheme.first()).isFalse()

        // "Follow the system" is not "dark", whatever the phone happens to be doing.
        prefs.setThemeMode(ThemeMode.SYSTEM)
        assertThat(prefs.themeMode.first()).isEqualTo(ThemeMode.SYSTEM)
        assertThat(prefs.darkTheme.first()).isFalse()
    }

    @Test
    fun `an explicit choice survives a backup round trip`() = runBlocking {
        val prefs = preferences()
        prefs.setThemeMode(ThemeMode.LIGHT)
        val snapshot = prefs.snapshot()
        assertThat(snapshot.themeModeId).isEqualTo(ThemeMode.LIGHT.id)

        prefs.setThemeMode(ThemeMode.DARK)
        prefs.restore(snapshot)
        assertThat(prefs.themeMode.first()).isEqualTo(ThemeMode.LIGHT)
    }

    @Test
    fun `a backup that predates the setting restores from its dark boolean`() = runBlocking {
        val prefs = preferences()

        // An old JSON document has no themeModeId at all, which is why the field is nullable
        // rather than defaulted: "absent" has to be distinguishable from "chose System".
        val oldDarkBackup = PreferencesSnapshot(darkTheme = true)
        assertThat(oldDarkBackup.themeModeId).isNull()
        prefs.setThemeMode(ThemeMode.LIGHT)
        prefs.restore(oldDarkBackup)
        assertThat(prefs.themeMode.first()).isEqualTo(ThemeMode.DARK)

        val oldLightBackup = PreferencesSnapshot(darkTheme = false)
        prefs.setThemeMode(ThemeMode.DARK)
        prefs.restore(oldLightBackup)
        assertThat(prefs.themeMode.first()).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun `an explicit System choice is not confused with an absent one`() = runBlocking {
        val prefs = preferences()
        prefs.setThemeMode(ThemeMode.SYSTEM)
        val snapshot = prefs.snapshot()
        assertThat(snapshot.themeModeId).isEqualTo(ThemeMode.SYSTEM.id)

        prefs.setThemeMode(ThemeMode.DARK)
        prefs.restore(snapshot)
        assertThat(prefs.themeMode.first()).isEqualTo(ThemeMode.SYSTEM)
    }
}
