package lk.salli.data.scratch

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import lk.salli.data.categorization.KeywordCategorizer
import lk.salli.data.categorization.TypeCategorizer
import lk.salli.data.db.SalliDatabase
import lk.salli.data.ingest.IngestResult
import lk.salli.data.ingest.TransactionIngestor
import lk.salli.data.ingest.UtilityIngestor
import lk.salli.data.seed.Seeder
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scratch harness: replays a raw `adb shell content query --uri content://sms/inbox` dump
 * through the real ingest pipeline (Seeder + TransactionIngestor + UtilityIngestor) into an
 * in-memory database, oldest message first, exactly like a full inbox re-scan. Writes outcome
 * counts per sender, stored rows per sender and the first exceptions next to the input.
 * Only runs when SALLI_RAW_INBOX is set; dumps belong in the gitignored samples/raw/.
 *
 *   SALLI_RAW_INBOX=$PWD/samples/raw/inbox.txt ./gradlew :data:testDebugUnitTest --tests '*RawInboxIngestRunner*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RawInboxIngestRunner {

    @Test
    fun replayWholeInbox(): Unit = runBlocking {
        val path = System.getenv("SALLI_RAW_INBOX")
        Assume.assumeTrue("set SALLI_RAW_INBOX to an adb inbox dump", !path.isNullOrBlank())
        val header = Regex("""_id=(\d+), address=(.*?), date=(\d+), body=""", RegexOption.DOT_MATCHES_ALL)
        val messages = File(path).readText().split(Regex("""(?m)^Row: \d+ """)).mapNotNull { rec ->
            val m = header.find(rec) ?: return@mapNotNull null
            // Sender not trimmed: an ID like "COMBANK" plus a line break is what the app really reads.
            Triple(m.groupValues[2], rec.substring(m.range.last + 1).trim(), m.groupValues[3].toLong())
        }.sortedBy { it.third }

        // SALLI_START_DB: optional copy of a real phone database to start from, so device-only
        // state (taught keywords, existing accounts) is reproduced. Otherwise start empty.
        val startDb = System.getenv("SALLI_START_DB")?.takeIf { it.isNotBlank() }
        val db = if (startDb != null) {
            val copy = File.createTempFile("salli-start", ".db").also { File(startDb).copyTo(it, overwrite = true) }
            Room.databaseBuilder(ApplicationProvider.getApplicationContext(), SalliDatabase::class.java, copy.absolutePath)
                .allowMainThreadQueries().build()
        } else {
            Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SalliDatabase::class.java)
                .allowMainThreadQueries().build()
        }
        Seeder(db).run()
        val ingestor = TransactionIngestor(
            db = db,
            categorizer = KeywordCategorizer(db.keywords()),
            typeCategorizer = TypeCategorizer(db.categories()),
            utilityIngestor = UtilityIngestor(db),
        )

        val outcomes = sortedMapOf<String, MutableMap<String, Int>>()
        val errors = mutableListOf<String>()
        for ((sender, body, date) in messages) {
            val label = try {
                when (val r = ingestor.ingest(sender, body, date)) {
                    is IngestResult.Dropped -> "Dropped(${r.reason.take(24)})"
                    else -> r::class.simpleName ?: "?"
                }
            } catch (t: Throwable) {
                if (errors.size < 3) errors += "$sender @ $date -> ${t.stackTraceToString().lines().take(14).joinToString("\n      ")}\n    body: ${body.take(160).replace("\n", " | ")}"
                "EXCEPTION"
            }
            outcomes.getOrPut(sender) { mutableMapOf() }.merge(label, 1, Int::plus)
        }

        val interesting = setOf("COMBANK", "BOC", "BOCONLINE", "PeoplesBank", "HNB", "SLTBILL", "SLTMOBITEL", "1919")
        val report = buildString {
            appendLine("messages replayed: ${messages.size}")
            outcomes.filterKeys { it in interesting }.forEach { (s, m) -> appendLine("$s: $m") }
            appendLine("stored transactions by sender: " + db.transactions().allForRecategorise().groupingBy { it.senderAddress }.eachCount())
            appendLine("accounts: " + db.accounts().all().map { "${it.senderAddress}/${it.accountSuffix}" })
            if (errors.isEmpty()) appendLine("no exceptions") else errors.forEach { appendLine(it) }
        }
        File("$path.ingest.report.txt").writeText(report)
        db.close()
    }
}
