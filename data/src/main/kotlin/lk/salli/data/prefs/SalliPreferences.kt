package lk.salli.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import lk.salli.domain.security.AppLockPolicy

/**
 * Thin wrapper over DataStore for cross-session settings. Kept deliberately small — anything
 * that lives in the database (accounts, categories, etc.) goes there instead.
 */
class SalliPreferences private constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.dataStore)

    // Every flow below is distinctUntilChanged. DataStore re-emits all keys on any write, and
    // screens that reset their date range when the period changes must not treat a theme
    // toggle or a worker's bookkeeping write as a period change.

    /**
     * Display name used in the Home greeting. Empty string means "no name set" — the UI
     * falls back to a generic greeting without a name token.
     */
    val userName: Flow<String> = store.data.map { prefs -> prefs[KEY_USER_NAME].orEmpty() }.distinctUntilChanged()

    suspend fun setUserName(name: String) {
        store.edit { it[KEY_USER_NAME] = name.trim() }
    }

    /**
     * Theme preference: System / Light / Dark. [ThemeMode.LIGHT] is the default: every install opens
     * in light, and dark or follow-system is a deliberate choice in Settings → Appearance.
     *
     * Installs that only ever used the old flat `dark_theme` toggle fall back to it, so a user
     * sitting on dark stays on dark instead of being silently handed back to the system. The
     * migration happens on the first write, never on a read: a read-triggered write races with
     * process start and would pin genuine "System" users to whatever the boolean happened to be.
     */
    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        prefs[KEY_THEME_MODE]?.let(ThemeMode::fromId)
            ?: if (prefs[KEY_DARK_THEME] == true) ThemeMode.DARK else ThemeMode.LIGHT
    }.distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.id
            // Keep the legacy boolean coherent for anything still reading it (the widget's
            // snapshot, a downgrade, an old backup being written out again).
            prefs[KEY_DARK_THEME] = mode == ThemeMode.DARK
        }
    }

    /**
     * Legacy flat toggle. Retained so the migration above has something to read and so a
     * downgrade doesn't land on a blank pref; new code reads [themeMode].
     */
    val darkTheme: Flow<Boolean> = store.data.map { prefs -> prefs[KEY_DARK_THEME] ?: false }.distinctUntilChanged()

    /**
     * Whether the one-time full-inbox historical import has completed. Set true after the
     * onboarding import finishes, or after a post-onboarding permission grant triggers the
     * same backfill. Gates [ensureHistoricalImport] so a full re-scan only happens once per
     * install (further refreshes are the cheap 3-day window).
     */
    val historicalImportCompleted: Flow<Boolean> =
        store.data.map { prefs -> prefs[KEY_HISTORICAL_IMPORT_DONE] ?: false }.distinctUntilChanged()

    suspend fun setHistoricalImportCompleted(done: Boolean) {
        store.edit { it[KEY_HISTORICAL_IMPORT_DONE] = done }
    }

    /** First-run introduction is a device choice, independent of SMS permission or import. */
    val onboardingCompleted: Flow<Boolean> = store.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETED] ?: (prefs[KEY_HISTORICAL_IMPORT_DONE] ?: false)
    }.distinctUntilChanged()

    suspend fun setOnboardingCompleted(completed: Boolean) {
        store.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    /** Respect an explicit choice to start with new alerts rather than scan the old inbox. */
    val historicalImportDeferred: Flow<Boolean> = store.data.map { prefs ->
        prefs[KEY_HISTORICAL_IMPORT_DEFERRED] ?: false
    }.distinctUntilChanged()

    suspend fun setHistoricalImportDeferred(deferred: Boolean) {
        store.edit { it[KEY_HISTORICAL_IMPORT_DEFERRED] = deferred }
    }

    /** How many days before a bill's due date the reminder fires. Default 3. */
    val billReminderDays: Flow<Int> = store.data.map { prefs -> prefs[KEY_BILL_REMINDER_DAYS] ?: DEFAULT_BILL_REMINDER_DAYS }.distinctUntilChanged()

    suspend fun setBillReminderDays(days: Int) {
        store.edit { it[KEY_BILL_REMINDER_DAYS] = days.coerceIn(0, 14) }
    }

    // ---- Spending period -------------------------------------------------------------

    /** Day of month (1..28) the user's "month" starts on. 1 = calendar month. */
    val monthStartDay: Flow<Int> = store.data.map { prefs -> (prefs[KEY_MONTH_START_DAY] ?: 1).coerceIn(1, 28) }.distinctUntilChanged()

    suspend fun setMonthStartDay(day: Int) {
        store.edit { it[KEY_MONTH_START_DAY] = day.coerceIn(1, 28) }
    }

    /** ISO day-of-week the user's week starts on (1 = Monday … 7 = Sunday). */
    val weekStartDay: Flow<Int> = store.data.map { prefs -> (prefs[KEY_WEEK_START_DAY] ?: 1).coerceIn(1, 7) }.distinctUntilChanged()

    suspend fun setWeekStartDay(isoDay: Int) {
        store.edit { it[KEY_WEEK_START_DAY] = isoDay.coerceIn(1, 7) }
    }

    /** Both period settings in one emission, for view models that need them together. */
    val period: Flow<PeriodSettings> = store.data.map { prefs ->
        PeriodSettings(
            monthStartDay = (prefs[KEY_MONTH_START_DAY] ?: 1).coerceIn(1, 28),
            weekStartDay = (prefs[KEY_WEEK_START_DAY] ?: 1).coerceIn(1, 7),
        )
    }.distinctUntilChanged()

    /**
     * The user's own monthly spending limit in minor units, or null for "automatic" (safe-to-
     * spend then uses the median of the last three completed cycles).
     */
    val monthlySpendingLimitMinor: Flow<Long?> =
        store.data.map { prefs -> prefs[KEY_SPENDING_LIMIT]?.takeIf { it > 0L } }.distinctUntilChanged()

    suspend fun setMonthlySpendingLimitMinor(limitMinor: Long?) {
        store.edit {
            if (limitMinor == null || limitMinor <= 0L) it.remove(KEY_SPENDING_LIMIT) else it[KEY_SPENDING_LIMIT] = limitMinor
        }
    }

    // ---- Scheduled summaries ---------------------------------------------------------

    val summarySettings: Flow<SummarySettings> = store.data.map { prefs ->
        SummarySettings(
            daily = prefs[KEY_SUMMARY_DAILY] ?: false,
            weekly = prefs[KEY_SUMMARY_WEEKLY] ?: false,
            monthly = prefs[KEY_SUMMARY_MONTHLY] ?: false,
            hour = (prefs[KEY_SUMMARY_HOUR] ?: DEFAULT_SUMMARY_HOUR).coerceIn(0, 23),
        )
    }.distinctUntilChanged()

    suspend fun setSummaryDaily(on: Boolean) = store.edit { it[KEY_SUMMARY_DAILY] = on }
    suspend fun setSummaryWeekly(on: Boolean) = store.edit { it[KEY_SUMMARY_WEEKLY] = on }
    suspend fun setSummaryMonthly(on: Boolean) = store.edit { it[KEY_SUMMARY_MONTHLY] = on }
    suspend fun setSummaryHour(hour: Int) = store.edit { it[KEY_SUMMARY_HOUR] = hour.coerceIn(0, 23) }

    /**
     * The period key ("2026-09-14", "2026-W37", "2026-09") of the last summary posted for
     * each cadence, so the hourly worker posts each one exactly once.
     */
    suspend fun lastSummaryKey(cadence: String): String? =
        store.data.map { it[stringPreferencesKey("summary_last_$cadence")] }.first()

    suspend fun setLastSummaryKey(cadence: String, key: String) {
        store.edit { it[stringPreferencesKey("summary_last_$cadence")] = key }
    }

    // ---- Home-screen widget ----------------------------------------------------------

    /** When true the widget shows "Rs ••••" instead of amounts. Default off. */
    val widgetHideAmounts: Flow<Boolean> =
        store.data.map { prefs -> prefs[KEY_WIDGET_HIDE_AMOUNTS] ?: false }.distinctUntilChanged()

    suspend fun setWidgetHideAmounts(hide: Boolean) {
        store.edit { it[KEY_WIDGET_HIDE_AMOUNTS] = hide }
    }

    // ---- Security --------------------------------------------------------------------

    /** App lock on/off, how long Salli may sit in the background before it locks, and FLAG_SECURE. */
    val appLock: Flow<AppLockSettings> = store.data.map { prefs ->
        AppLockSettings(
            enabled = prefs[KEY_APP_LOCK_ENABLED] ?: false,
            lockAfterSeconds = AppLockPolicy.normaliseLockAfter(prefs[KEY_APP_LOCK_AFTER_SECONDS] ?: AppLockPolicy.IMMEDIATELY_SECONDS),
            hideInRecents = prefs[KEY_HIDE_IN_RECENTS] ?: false,
        )
    }.distinctUntilChanged()

    /** Just the explicit "Hide Salli in recent apps" choice. */
    val hideInRecents: Flow<Boolean> =
        store.data.map { prefs -> prefs[KEY_HIDE_IN_RECENTS] ?: false }.distinctUntilChanged()

    /** Callers must have passed a successful device-credential prompt before flipping this. */
    suspend fun setAppLockEnabled(enabled: Boolean) {
        store.edit { it[KEY_APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setAppLockAfterSeconds(seconds: Int) {
        store.edit { it[KEY_APP_LOCK_AFTER_SECONDS] = AppLockPolicy.normaliseLockAfter(seconds) }
    }

    suspend fun setHideInRecents(hide: Boolean) {
        store.edit { it[KEY_HIDE_IN_RECENTS] = hide }
    }

    // ---- Backup ----------------------------------------------------------------------

    /** Every user-facing setting, for the JSON backup. */
    suspend fun snapshot(): PreferencesSnapshot {
        val p = store.data.first()
        return PreferencesSnapshot(
            userName = p[KEY_USER_NAME].orEmpty(),
            darkTheme = p[KEY_DARK_THEME] ?: false,
            themeModeId = p[KEY_THEME_MODE] ?: (if (p[KEY_DARK_THEME] == true) ThemeMode.DARK.id else ThemeMode.LIGHT.id),
            historicalImportCompleted = p[KEY_HISTORICAL_IMPORT_DONE] ?: false,
            billReminderDays = p[KEY_BILL_REMINDER_DAYS] ?: DEFAULT_BILL_REMINDER_DAYS,
            monthStartDay = p[KEY_MONTH_START_DAY] ?: 1,
            weekStartDay = p[KEY_WEEK_START_DAY] ?: 1,
            summaryDaily = p[KEY_SUMMARY_DAILY] ?: false,
            summaryWeekly = p[KEY_SUMMARY_WEEKLY] ?: false,
            summaryMonthly = p[KEY_SUMMARY_MONTHLY] ?: false,
            summaryHour = p[KEY_SUMMARY_HOUR] ?: DEFAULT_SUMMARY_HOUR,
            monthlySpendingLimitMinor = p[KEY_SPENDING_LIMIT],
            widgetHideAmounts = p[KEY_WIDGET_HIDE_AMOUNTS] ?: false,
            appLockEnabled = p[KEY_APP_LOCK_ENABLED] ?: false,
            appLockAfterSeconds = AppLockPolicy.normaliseLockAfter(p[KEY_APP_LOCK_AFTER_SECONDS] ?: AppLockPolicy.IMMEDIATELY_SECONDS),
            hideInRecents = p[KEY_HIDE_IN_RECENTS] ?: false,
        )
    }

    /**
     * Replaces ordinary settings with a backup's. Security and privacy choices stay as they are
     * on this device: a backup is untrusted input and must never weaken the app lock, its delay,
     * recents protection or widget masking. Summary "last posted" markers are cleared.
     */
    suspend fun restore(s: PreferencesSnapshot) {
        store.edit { e ->
            val widgetHideAmounts = e[KEY_WIDGET_HIDE_AMOUNTS] ?: false
            val appLockEnabled = e[KEY_APP_LOCK_ENABLED] ?: false
            val appLockAfterSeconds = AppLockPolicy.normaliseLockAfter(
                e[KEY_APP_LOCK_AFTER_SECONDS] ?: AppLockPolicy.IMMEDIATELY_SECONDS,
            )
            val hideInRecents = e[KEY_HIDE_IN_RECENTS] ?: false
            val onboardingCompleted = e[KEY_ONBOARDING_COMPLETED]
                ?: (e[KEY_HISTORICAL_IMPORT_DONE] ?: false)
            val historicalImportDeferred = e[KEY_HISTORICAL_IMPORT_DEFERRED] ?: false
            e.clear()
            e[KEY_USER_NAME] = s.userName
            e[KEY_DARK_THEME] = s.darkTheme
            // A backup old enough to predate the setting can still say "this user was on
            // dark", and dropping them back to Light on restore would be a visible loss.
            e[KEY_THEME_MODE] = s.themeModeId?.let { ThemeMode.fromId(it) }
                ?.id
                ?: (if (s.darkTheme) ThemeMode.DARK else ThemeMode.LIGHT).id
            e[KEY_HISTORICAL_IMPORT_DONE] = s.historicalImportCompleted
            e[KEY_BILL_REMINDER_DAYS] = s.billReminderDays
            e[KEY_MONTH_START_DAY] = s.monthStartDay.coerceIn(1, 28)
            e[KEY_WEEK_START_DAY] = s.weekStartDay.coerceIn(1, 7)
            e[KEY_SUMMARY_DAILY] = s.summaryDaily
            e[KEY_SUMMARY_WEEKLY] = s.summaryWeekly
            e[KEY_SUMMARY_MONTHLY] = s.summaryMonthly
            e[KEY_SUMMARY_HOUR] = s.summaryHour.coerceIn(0, 23)
            s.monthlySpendingLimitMinor?.takeIf { it > 0L }?.let { e[KEY_SPENDING_LIMIT] = it }
            e[KEY_WIDGET_HIDE_AMOUNTS] = widgetHideAmounts
            e[KEY_APP_LOCK_ENABLED] = appLockEnabled
            e[KEY_APP_LOCK_AFTER_SECONDS] = appLockAfterSeconds
            e[KEY_HIDE_IN_RECENTS] = hideInRecents
            e[KEY_ONBOARDING_COMPLETED] = onboardingCompleted
            e[KEY_HISTORICAL_IMPORT_DEFERRED] = historicalImportDeferred
        }
    }

    companion object {
        /** Lets persistence tests use an isolated real store without the global Android delegate. */
        internal fun fromStore(store: DataStore<Preferences>) = SalliPreferences(store)
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_HISTORICAL_IMPORT_DEFERRED = booleanPreferencesKey("historical_import_deferred")
        const val DEFAULT_SUMMARY_HOUR = 20
        private val KEY_MONTH_START_DAY = intPreferencesKey("month_start_day")
        private val KEY_WEEK_START_DAY = intPreferencesKey("week_start_day")
        private val KEY_SUMMARY_DAILY = booleanPreferencesKey("summary_daily")
        private val KEY_SUMMARY_WEEKLY = booleanPreferencesKey("summary_weekly")
        private val KEY_SUMMARY_MONTHLY = booleanPreferencesKey("summary_monthly")
        private val KEY_SUMMARY_HOUR = intPreferencesKey("summary_hour")
        private val KEY_SPENDING_LIMIT = longPreferencesKey("monthly_spending_limit_minor")
        const val DEFAULT_BILL_REMINDER_DAYS = 3
        private val KEY_BILL_REMINDER_DAYS = intPreferencesKey("bill_reminder_days")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
        private val KEY_THEME_MODE = intPreferencesKey("theme_mode")
        private val KEY_HISTORICAL_IMPORT_DONE = booleanPreferencesKey("historical_import_done")
        private val KEY_WIDGET_HIDE_AMOUNTS = booleanPreferencesKey("widget_hide_amounts")
        private val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        private val KEY_APP_LOCK_AFTER_SECONDS = intPreferencesKey("app_lock_after_seconds")
        private val KEY_HIDE_IN_RECENTS = booleanPreferencesKey("hide_in_recents")
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "salli_prefs")

/**
 * How Salli picks a palette. IDs are persisted and embedded in backups; never renumber them.
 */
enum class ThemeMode(val id: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2),
    ;

    /** Collapses the preference into the single boolean `SalliTheme` actually needs. */
    fun resolve(systemInDarkMode: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkMode
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromId(id: Int): ThemeMode = entries.firstOrNull { it.id == id } ?: LIGHT
    }
}

/** Month start day (1..28) and ISO week start day (1 = Monday … 7 = Sunday). */
data class PeriodSettings(val monthStartDay: Int, val weekStartDay: Int)

/** Which scheduled summaries are on, and the hour of day they post. */
data class SummarySettings(val daily: Boolean, val weekly: Boolean, val monthly: Boolean, val hour: Int)

/**
 * App lock settings. [lockAfterSeconds] is one of [AppLockPolicy.lockAfterChoicesSeconds];
 * [hideInRecents] sets FLAG_SECURE, which also blocks screenshots.
 */
data class AppLockSettings(val enabled: Boolean, val lockAfterSeconds: Int, val hideInRecents: Boolean)

/** Flat copy of every user setting, embedded in the JSON backup document. */
@kotlinx.serialization.Serializable
data class PreferencesSnapshot(
    val userName: String = "",
    val darkTheme: Boolean = false,
    // Added with the tri-state theme. Nullable rather than defaulted so a document written
    // before the setting existed is distinguishable from one that explicitly chose
    // "System"; [restore] falls back to [darkTheme] for the former.
    val themeModeId: Int? = null,
    val historicalImportCompleted: Boolean = false,
    val billReminderDays: Int = SalliPreferences.DEFAULT_BILL_REMINDER_DAYS,
    val monthStartDay: Int = 1,
    val weekStartDay: Int = 1,
    val summaryDaily: Boolean = false,
    val summaryWeekly: Boolean = false,
    val summaryMonthly: Boolean = false,
    val summaryHour: Int = SalliPreferences.DEFAULT_SUMMARY_HOUR,
    val monthlySpendingLimitMinor: Long? = null,
    // Added with the widget and app lock. Defaults keep older backups restorable.
    val widgetHideAmounts: Boolean = false,
    val appLockEnabled: Boolean = false,
    val appLockAfterSeconds: Int = AppLockPolicy.IMMEDIATELY_SECONDS,
    val hideInRecents: Boolean = false,
)
