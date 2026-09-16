package lk.salli.data.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lk.salli.data.db.SalliDatabase
import lk.salli.data.prefs.SalliPreferences

/**
 * Full-fidelity export and restore of everything the app knows, as one JSON document the
 * user owns. Export writes next to the CSV exports (same FileProvider path) and hands back a
 * share intent; restore replaces every table inside one transaction so a bad file can never
 * leave the database half-written.
 */
class BackupManager(
    private val db: SalliDatabase,
    private val prefs: SalliPreferences,
    private val context: Context,
    private val appVersion: String? = null,
    private val schemaVersion: Int = SCHEMA_VERSION,
) {

    suspend fun buildDocument(): BackupDocument {
        val dao = db.backup()
        return BackupDocument(
            exportedAt = System.currentTimeMillis(),
            appVersion = appVersion,
            schemaVersion = schemaVersion,
            tables = BackupTables(
                accounts = dao.allAccounts(),
                categories = dao.allCategories(),
                subCategories = dao.allSubCategories(),
                merchants = dao.allMerchants(),
                merchantAliases = dao.allMerchantAliases(),
                keywords = dao.allKeywords(),
                transactions = dao.allTransactions(),
                transferGroups = dao.allTransferGroups(),
                budgets = dao.allBudgets(),
                budgetLines = dao.allBudgetLines(),
                budgetAccounts = dao.allBudgetAccounts(),
                unknownSms = dao.allUnknownSms(),
                bills = dao.allBills(),
                fuelPassRecords = dao.allFuelPassRecords(),
                recurringSeries = dao.allRecurringSeries(),
                goals = dao.allGoals(),
                goalContributions = dao.allGoalContributions(),
                splitGroups = dao.allSplitGroups(),
                splitMembers = dao.allSplitMembers(),
                splitExpenses = dao.allSplitExpenses(),
                splitShares = dao.allSplitShares(),
                splitSettlements = dao.allSplitSettlements(),
            ),
            preferences = prefs.snapshot(),
        )
    }

    suspend fun exportToFile(): File = withContext(Dispatchers.IO) {
        val text = BackupCodec.encode(buildDocument())
        val slug = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        File(dir, "salli-backup-$slug.json").apply { writeText(text) }
    }

    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Salli backup")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Parses and validates without writing anything; the UI shows the counts before confirming. */
    suspend fun inspect(input: InputStream): BackupDocument = withContext(Dispatchers.IO) {
        BackupCodec.decode(input.bufferedReader().readText())
    }

    /** Replaces every table and the settings with the document's contents. */
    suspend fun restore(document: BackupDocument) {
        withContext(Dispatchers.IO) {
            db.withTransaction { replaceTables(document.tables) }
            prefs.restore(document.preferences)
        }
    }

    suspend fun restore(input: InputStream) = restore(inspect(input))

    private suspend fun replaceTables(t: BackupTables) {
        val dao = db.backup()
        // Children first so foreign keys never block a delete…
        dao.deleteBudgetAccounts(); dao.deleteBudgetLines(); dao.deleteBudgets()
        dao.deleteTransferGroups(); dao.deleteTransactions()
        dao.deleteMerchantAliases(); dao.deleteMerchants(); dao.deleteKeywords()
        dao.deleteSubCategories(); dao.deleteCategories(); dao.deleteAccounts()
        dao.deleteUnknownSms(); dao.deleteBills(); dao.deleteFuelPassRecords()
        dao.deleteRecurringSeries(); dao.deleteGoalContributions(); dao.deleteGoals()
        dao.deleteSplitShares(); dao.deleteSplitSettlements(); dao.deleteSplitExpenses()
        dao.deleteSplitMembers(); dao.deleteSplitGroups()
        // …and parents first on the way back in.
        dao.insertCategories(t.categories)
        dao.insertSubCategories(t.subCategories)
        dao.insertAccounts(t.accounts)
        dao.insertMerchants(t.merchants)
        dao.insertMerchantAliases(t.merchantAliases)
        dao.insertKeywords(t.keywords)
        dao.insertTransactions(t.transactions)
        dao.insertTransferGroups(t.transferGroups)
        dao.insertBudgets(t.budgets)
        dao.insertBudgetLines(t.budgetLines)
        dao.insertBudgetAccounts(t.budgetAccounts)
        dao.insertUnknownSms(t.unknownSms)
        dao.insertBills(t.bills)
        dao.insertFuelPassRecords(t.fuelPassRecords)
        dao.insertRecurringSeries(t.recurringSeries)
        dao.insertGoals(t.goals)
        dao.insertGoalContributions(t.goalContributions)
        dao.insertSplitGroups(t.splitGroups)
        dao.insertSplitMembers(t.splitMembers)
        dao.insertSplitExpenses(t.splitExpenses)
        dao.insertSplitShares(t.splitShares)
        dao.insertSplitSettlements(t.splitSettlements)
    }

    companion object {
        /** Mirrors `@Database(version = …)`; recorded in the file for diagnostics only. */
        const val SCHEMA_VERSION = 10
    }
}
