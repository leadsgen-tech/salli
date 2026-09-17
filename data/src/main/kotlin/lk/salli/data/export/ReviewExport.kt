package lk.salli.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.UnknownSmsEntity
import lk.salli.parser.OtpGuard
import lk.salli.parser.Templates
import lk.salli.parser.util.Redact
import lk.salli.parser.util.SenderId

/**
 * The "send formats to Salli" file: every bank message the parser couldn't handle, masked on
 * the phone, plus a per-sender coverage table so the maintainer can see what a contributor's
 * inbox looks like without any bodies. Schema 1.
 */
@Serializable
data class ReviewExportDocument(
    val schema: Int = 1,
    val app: String,
    val exportedAt: String,
    val coverage: List<CoverageEntry>,
    val review: List<ReviewEntry>,
    val candidates: List<ReviewEntry>,
)

/** One sender: how many messages parsed into transactions, how many are waiting for review. */
@Serializable
data class CoverageEntry(val sender: String, val parsed: Int, val review: Int)

@Serializable
data class ReviewEntry(
    /** First 12 hex chars of SHA-256 over the unmasked sender + body; lets the maintainer dedupe re-sends. */
    val id: String,
    /** Exactly as the phone delivered it, control characters included; sender bugs must stay visible. */
    val sender: String,
    val senderNormalised: String,
    /** The bank the registry recognises, or null for a sender Salli doesn't list. */
    val bank: String?,
    /** NO_TEMPLATE_MATCH · BANK_KNOWN_NO_TEMPLATE · UNLISTED_SENDER */
    val reason: String,
    val receivedAt: String,
    val body: String,
)

/** A money-looking message from an unlisted sender, found by the optional inbox scan. Never stored. */
data class ReviewCandidate(val sender: String, val body: String, val receivedAt: Long)

object ReviewExportBuilder {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun build(
        appVersion: String,
        nowMillis: Long,
        zone: ZoneId,
        pending: List<UnknownSmsEntity>,
        includeSenders: Set<String>,
        parsedBySender: Map<String, Int>,
        candidates: List<ReviewCandidate>,
    ): ReviewExportDocument {
        val reviewBySender = pending.groupingBy { SenderId.normalize(it.senderAddress) }.eachCount()
        val coverage = (parsedBySender.keys + reviewBySender.keys).sorted().map { sender ->
            CoverageEntry(sender, parsed = parsedBySender[sender] ?: 0, review = reviewBySender[sender] ?: 0)
        }
        val review = pending
            .filter { SenderId.normalize(it.senderAddress) in includeSenders }
            .filterNot { OtpGuard.isOtp(it.body) || Redact.isSensitiveCode(it.body) }
            .map { entry(it.senderAddress, it.body, it.receivedAt, zone) }
        val extra = candidates
            .filterNot { OtpGuard.isOtp(it.body) || Redact.isSensitiveCode(it.body) }
            .map { entry(it.sender, it.body, it.receivedAt, zone) }
        return ReviewExportDocument(
            app = appVersion,
            exportedAt = iso(nowMillis, zone),
            coverage = coverage,
            review = review,
            candidates = extra,
        )
    }

    fun encode(document: ReviewExportDocument): String = json.encodeToString(ReviewExportDocument.serializer(), document)

    private fun entry(sender: String, body: String, receivedAt: Long, zone: ZoneId): ReviewEntry {
        val normalised = SenderId.normalize(sender)
        val reason = when {
            Templates.forSender(normalised).isNotEmpty() -> "NO_TEMPLATE_MATCH"
            Templates.isKnownBankSender(normalised) -> "BANK_KNOWN_NO_TEMPLATE"
            else -> "UNLISTED_SENDER"
        }
        return ReviewEntry(
            id = hash(sender, body),
            sender = sender,
            senderNormalised = normalised,
            bank = normalised.takeIf { reason != "UNLISTED_SENDER" },
            reason = reason,
            receivedAt = iso(receivedAt, zone),
            body = Redact.body(body),
        )
    }

    private fun iso(millis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private fun hash(sender: String, body: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest((sender + "\n" + body).toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(12)
    }
}

/** Writes the document next to the CSV exports and hands back a share intent, like [TransactionExporter]. */
class ReviewExporter(
    private val db: SalliDatabase,
    private val context: Context,
) {
    suspend fun export(appVersion: String, includeSenders: Set<String>, candidates: List<ReviewCandidate>): File {
        val accounts = db.accounts().all().associateBy { it.id }
        val parsedBySender = HashMap<String, Int>()
        db.transactions().countByAccount().forEach { row ->
            val sender = accounts[row.accountId]?.senderAddress?.let(SenderId::normalize) ?: return@forEach
            parsedBySender[sender] = (parsedBySender[sender] ?: 0) + row.count
        }
        val document = ReviewExportBuilder.build(
            appVersion = appVersion,
            nowMillis = System.currentTimeMillis(),
            zone = ZoneId.systemDefault(),
            pending = db.unknownSms().pending(),
            includeSenders = includeSenders,
            parsedBySender = parsedBySender,
            candidates = candidates,
        )
        val slug = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(java.util.Date())
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "salli-formats-$slug.json")
        file.writeText(ReviewExportBuilder.encode(document))
        return file
    }

    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Salli formats to review")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
