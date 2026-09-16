package lk.salli.domain.recurring

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Scratch harness: runs [RecurringDetector] over a real transaction export and writes the
 * series next to the input. Only runs when `SALLI_RECURRING_TSV` points at a TSV of
 * `id, counterparty, flow, currency, amountMinor, timestamp, declined, ownTransfer, category, accountHidden`,
 * so CI never sees it and no real data lives in the repo.
 */
@EnabledIfEnvironmentVariable(named = "SALLI_RECURRING_TSV", matches = ".+")
class RecurringRealDataRunner {

    @Test
    fun report() {
        val input = File(System.getenv("SALLI_RECURRING_TSV"))
        val rows = input.readLines().mapNotNull { line ->
            val c = line.split('\t')
            if (c.size < 10 || c[9] == "1") return@mapNotNull null
            RecurringInput(
                id = c[0].toLong(),
                counterparty = c[1].ifBlank { null },
                flowId = c[2].toInt(),
                currency = c[3],
                amountMinor = c[4].toLong(),
                timestamp = c[5].toLong(),
                isDeclined = c[6] == "1",
                isOwnTransfer = c[7] == "1",
                categoryName = c[8].ifBlank { null },
            )
        }
        val now = System.currentTimeMillis()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val out = StringBuilder("inputs=${rows.size}\n")
        for (s in RecurringDetector.detect(rows, now)) {
            out.append(
                "%-26s flow=%d %s %-8s %-10s n=%d declined=%d typical=%.2f cv-fixed=%s last=%s next=%s conf=%.2f\n".format(
                    Locale.US, s.displayName.take(26), s.flowId, s.currency, s.status, s.cadence ?: "-",
                    s.occurrences, s.declinedAttempts, s.typicalAmountMinor / 100.0, s.isFixed,
                    fmt.format(Date(s.lastAt)), s.nextAt?.let { fmt.format(Date(it)) } ?: "-", s.confidence,
                ),
            )
        }
        File(input.path + ".recurring.txt").writeText(out.toString())
        println(out)
    }
}
