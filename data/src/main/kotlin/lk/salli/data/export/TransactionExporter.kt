package lk.salli.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/**
 * Exports the transactions table to a CSV file in the app's external files dir, then hands the
 * user a share sheet so they can move it to Drive / email / wherever. Keeping it user-driven
 * (instead of an automatic cloud sync) is the whole point of Salli's privacy model.
 *
 * `includeRawSms` defaults to false — raw SMS bodies contain account numbers, OTPs that slipped
 * through the guard, and merchant references that the user may not want leaving the device.
 * Set it explicitly for a full audit export.
 */
class TransactionExporter(
    private val db: SalliDatabase,
    private val context: Context,
) {
    suspend fun exportToCsv(includeRawSms: Boolean = false): File {
        val rows = db.transactions().recentAll(sinceTimestamp = 0L)
        val accounts = db.accounts().all().associateBy { it.id }
        val categories = db.categories().all().associateBy { it.id }

        val timestampSlug = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "salli-export-$timestampSlug.csv")

        FileWriter(file).use { writer ->
            val header = buildString {
                append("date,time,account,amount,currency,fee,balance,flow,type,declined,")
                append("merchant,category,note")
                if (includeRawSms) append(",raw_sms")
            }
            writer.appendLine(header)
            val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
            rows.forEach { row ->
                writer.append(dateFmt.format(Date(row.timestamp))).append(',')
                writer.append(timeFmt.format(Date(row.timestamp))).append(',')
                writer.append(csvEscape(accounts[row.accountId]?.displayName ?: "")).append(',')
                writer.append(majorString(row.amountMinor)).append(',')
                writer.append(row.amountCurrency).append(',')
                writer.append(row.feeMinor?.let(::majorString) ?: "").append(',')
                writer.append(row.balanceMinor?.let(::majorString) ?: "").append(',')
                writer.append(flowLabel(row)).append(',')
                writer.append(typeLabel(row)).append(',')
                writer.append(if (row.isDeclined) "yes" else "").append(',')
                writer.append(csvEscape(row.merchantRaw ?: "")).append(',')
                writer.append(csvEscape(row.categoryId?.let { categories[it]?.name } ?: "")).append(',')
                writer.append(csvEscape(row.note ?: ""))
                if (includeRawSms) {
                    writer.append(',').append(csvEscape(row.rawBody ?: ""))
                }
                writer.append('\n')
            }
        }
        return file
    }

    fun shareIntent(file: File): Intent {
        val authority = context.packageName + ".fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Salli export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun majorString(minor: Long): String {
        val sign = if (minor < 0) "-" else ""
        val abs = kotlin.math.abs(minor)
        return "$sign${abs / 100}.${"%02d".format(abs % 100)}"
    }

    private fun csvEscape(raw: String): String {
        if (raw.isEmpty()) return ""
        val needsQuote = raw.contains(',') || raw.contains('"') || raw.contains('\n')
        return if (needsQuote) "\"" + raw.replace("\"", "\"\"") + "\"" else raw
    }

    private fun flowLabel(row: TransactionEntity): String = when (TransactionFlow.fromId(row.flowId)) {
        TransactionFlow.EXPENSE -> "expense"
        TransactionFlow.INCOME -> "income"
        TransactionFlow.TRANSFER -> "transfer"
        TransactionFlow.AMBIGUOUS -> "ambiguous"
    }

    private fun typeLabel(row: TransactionEntity): String =
        TransactionType.fromId(row.typeId).name.lowercase()
}
