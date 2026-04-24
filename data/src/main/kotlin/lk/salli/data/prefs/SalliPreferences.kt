package lk.salli.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import lk.salli.domain.ParseMode

/**
 * Thin wrapper over DataStore for cross-session settings. Kept deliberately small — anything
 * that lives in the database (accounts, categories, etc.) goes there instead.
 */
class SalliPreferences(private val context: Context) {

    private val store: DataStore<Preferences>
        get() = context.dataStore

    val parseMode: Flow<ParseMode> = store.data.map { prefs ->
        ParseMode.fromId(prefs[KEY_PARSE_MODE] ?: ParseMode.STANDARD.id)
    }

    suspend fun setParseMode(mode: ParseMode) {
        store.edit { it[KEY_PARSE_MODE] = mode.id }
    }

    /**
     * Display name used in the Home greeting. Empty string means "no name set" — the UI
     * falls back to a generic greeting without a name token.
     */
    val userName: Flow<String> = store.data.map { prefs -> prefs[KEY_USER_NAME].orEmpty() }

    suspend fun setUserName(name: String) {
        store.edit { it[KEY_USER_NAME] = name.trim() }
    }

    /**
     * Theme preference. `true` = dark mode, `false` = light (default). Deliberately a flat
     * boolean instead of a three-state enum — "follow system" is a v2 concern once we're
     * sure the two palettes feel right on every screen.
     */
    val darkTheme: Flow<Boolean> = store.data.map { prefs -> prefs[KEY_DARK_THEME] ?: false }

    suspend fun setDarkTheme(enabled: Boolean) {
        store.edit { it[KEY_DARK_THEME] = enabled }
    }

    /**
     * Whether the one-time full-inbox historical import has completed. Set true after the
     * onboarding import finishes, or after a post-onboarding permission grant triggers the
     * same backfill. Gates [ensureHistoricalImport] so a full re-scan only happens once per
     * install (further refreshes are the cheap 3-day window).
     */
    val historicalImportCompleted: Flow<Boolean> =
        store.data.map { prefs -> prefs[KEY_HISTORICAL_IMPORT_DONE] ?: false }

    suspend fun setHistoricalImportCompleted(done: Boolean) {
        store.edit { it[KEY_HISTORICAL_IMPORT_DONE] = done }
    }

    private companion object {
        val KEY_PARSE_MODE = intPreferencesKey("parse_mode")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
        val KEY_HISTORICAL_IMPORT_DONE = booleanPreferencesKey("historical_import_done")
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "salli_prefs")
