package lk.salli.parser.scratch

import java.io.File
import lk.salli.parser.ParseResult
import lk.salli.parser.SmsParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Scratch harness: pipes a whole inbox dump through [SmsParser.parse] and writes a per-sender
 * coverage report next to the input. Only runs when `SALLI_RAW_INBOX` points at a dump, so
 * CI never sees it and no real inbox data lives in the repo (dumps belong in the gitignored
 * `samples/raw/`). Two input shapes are accepted:
 *
 *  - the raw output of `adb shell content query --uri content://sms/inbox
 *    --projection _id:address:date:body` (records start with `Row: N `), or
 *  - a TSV `id<TAB>sender<TAB>epochMillis<TAB>body` with `\n` escaped in the body.
 *
 *   SALLI_RAW_INBOX=/abs/path/inbox.txt ./gradlew :parser:test --tests '*RawInboxRunner*'
 */
@EnabledIfEnvironmentVariable(named = "SALLI_RAW_INBOX", matches = ".+")
class RawInboxRunner {

    private data class Message(val sender: String, val body: String, val receivedAt: Long)

    @Test
    fun reportCoverage() {
        val input = File(System.getenv("SALLI_RAW_INBOX"))
        val text = input.readText()
        val messages = if (text.contains(Regex("""(?m)^Row: \d+ """))) parseAdbDump(text) else parseTsv(text)
        require(messages.isNotEmpty()) { "No messages parsed from ${input.path}; expected adb 'Row:' output or a 4-column TSV" }
        val out = StringBuilder()
        out.appendLine("Total messages: ${messages.size}")

        val bySender = messages.groupBy { it.sender }
        val interesting = bySender.filter { (sender, list) ->
            list.size >= 2 && !sender.matches(Regex("""\+?\d[\d ]{5,}"""))
        }

        out.appendLine()
        out.appendLine("=== Per-sender outcome breakdown ===")
        for ((sender, list) in interesting.entries.sortedByDescending { it.value.size }) {
            val outcomes = list.groupingBy { label(SmsParser.parse(it.sender, it.body, it.receivedAt)) }
                .eachCount().toList().sortedByDescending { it.second }
            out.appendLine("%-14s %5d  %s".format(sender, list.size, outcomes.joinToString(", ") { "${it.first}=${it.second}" }))
        }

        for ((sender, list) in interesting.entries.sortedByDescending { it.value.size }) {
            val unknown = list.filter { SmsParser.parse(it.sender, it.body, it.receivedAt) is ParseResult.Unknown }
            if (unknown.isEmpty()) continue
            out.appendLine()
            out.appendLine("=== UNKNOWN from $sender (${unknown.size}, unique by first 70 chars) ===")
            unknown.map { it.body.trim() }.distinctBy { it.take(70) }.forEachIndexed { i, body ->
                out.appendLine("[%3d] %s".format(i + 1, body.replace("\n", " | ")))
            }
        }

        out.appendLine()
        out.appendLine("=== Informational reasons (all senders) ===")
        messages.mapNotNull { (SmsParser.parse(it.sender, it.body, it.receivedAt) as? ParseResult.Informational)?.reason }
            .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.take(40)
            .forEach { (reason, n) -> out.appendLine("%5d  %s".format(n, reason)) }

        val report = File(input.path + ".report.txt")
        report.writeText(out.toString())
        println("Report written to ${report.absolutePath}")
    }

    private fun parseAdbDump(text: String): List<Message> {
        val header = Regex("""_id=(\d+), address=(.*?), date=(\d+), body=""", RegexOption.DOT_MATCHES_ALL)
        return text.split(Regex("""(?m)^Row: \d+ """)).mapNotNull { rec ->
            val m = header.find(rec) ?: return@mapNotNull null
            Message(
                // Not trimmed: sender IDs with stray characters are exactly what this must catch.
                sender = m.groupValues[2],
                body = rec.substring(m.range.last + 1).trim(),
                receivedAt = m.groupValues[3].toLongOrNull() ?: 0L,
            )
        }
    }

    private fun parseTsv(text: String): List<Message> {
        var skipped = 0
        val out = text.lines().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val cols = line.split('\t', limit = 4)
            if (cols.size < 4) { skipped++; return@mapNotNull null }
            Message(
                sender = cols[1],
                body = cols[3].replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\"),
                receivedAt = cols[2].toLongOrNull() ?: 0L,
            )
        }
        if (skipped > 0) System.err.println("RawInboxRunner: skipped $skipped malformed TSV line(s)")
        return out
    }

    private fun label(r: ParseResult): String = when (r) {
        is ParseResult.Success -> "OK(${r.tx.type}/${r.tx.flow})"
        is ParseResult.Otp -> "Otp"
        is ParseResult.Informational -> "Info"
        is ParseResult.Unknown -> "UNKNOWN"
    }
}
