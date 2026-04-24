package lk.salli.data.export

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lk.salli.data.db.SalliDatabase
import lk.salli.data.prefs.SalliPreferences
import lk.salli.data.seed.Seeder

/**
 * User-triggered "delete all my data" — clears every table, reverts "history has been
 * imported" bookkeeping, then re-seeds the default categories + keywords so the app is
 * usable again without reinstalling.
 */
class DataWiper(
    private val db: SalliDatabase,
    private val seeder: Seeder,
    private val prefs: SalliPreferences,
) {
    suspend fun wipe() {
        // `RoomDatabase.clearAllTables` is a blocking call that asserts off-main-thread, so
        // hop to IO. The subsequent seed runs suspending DAO calls which Room dispatches
        // internally, but doing it on IO too keeps everything off the caller's dispatcher.
        withContext(Dispatchers.IO) {
            db.clearAllTables()
            // Reset the "already backfilled" pref so the next SMS-grant path (or the manual
            // Sync button) triggers a fresh full-inbox scan. Otherwise a wipe leaves Salli
            // empty with no way for the user to bring their history back short of
            // reinstalling.
            prefs.setHistoricalImportCompleted(false)
            seeder.run()
        }
    }
}
