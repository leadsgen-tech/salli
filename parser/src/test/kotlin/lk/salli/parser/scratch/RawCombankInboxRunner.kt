package lk.salli.parser.scratch

import java.io.File
import lk.salli.parser.ParseResult
import lk.salli.parser.SmsParser
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Scratch harness: pipes every SMS body in the user's raw COMBANK inbox dump through
 * [SmsParser.parse] and prints an outcome breakdown. Disabled by default so it doesn't run
 * in CI; flip with `-PrunRawInbox=true` or remove the @Disabled to use locally. The raw file
 * lives outside the repo (gitignored under `samples/raw/`) and the path is absolute so this
 * is intentionally not portable.
 */
@Disabled("Local-only — enable to validate template coverage against real inbox dumps")
class RawCombankInboxRunner {

    private val rawFile = File(
        "/Users/nabilahamed/projects/salli/samples/raw/" +
            "Messages with COMBANK 2026-04-28 07:56:23.csv",
    )

    @Test
    fun reportCoverage() {
        val messages = if (rawFile.extension.equals("csv", ignoreCase = true))
            parseCsv(rawFile) else parseDump(rawFile)
        println("Total messages parsed from dump: ${messages.size}")

        val byOutcome = messages.groupBy { (sender, body, receivedAt) ->
            when (val r = SmsParser.parse(sender, body, receivedAt)) {
                is ParseResult.Success -> "Success(${r.tx.type}/${r.tx.flow})"
                is ParseResult.Otp -> "Otp"
                is ParseResult.Informational -> "Informational"
                is ParseResult.Unknown -> "Unknown"
            }
        }

        println()
        println("=== Outcome breakdown ===")
        byOutcome
            .toSortedMap(compareByDescending { byOutcome[it]!!.size })
            .forEach { (label, list) -> println("%-50s %d".format(label, list.size)) }

        println()
        println("=== Unknown sample (first 30, deduped by first 80 chars) ===")
        byOutcome["Unknown"].orEmpty()
            .map { (_, body, _) -> body.trim() }
            .distinctBy { it.take(80) }
            .take(30)
            .forEachIndexed { i, body ->
                println("[%2d] %s".format(i + 1, body.take(160)))
            }

        println()
        println("=== Informational sample (first 10 unique reasons) ===")
        byOutcome["Informational"].orEmpty()
            .mapNotNull { (sender, body, ts) ->
                (SmsParser.parse(sender, body, ts) as? ParseResult.Informational)?.reason
            }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(10)
            .forEach { (reason, count) -> println("%-60s %d".format(reason, count)) }
    }

    /** One SMS triple. */
    private data class Message(val sender: String, val body: String, val receivedAt: Long)

    /**
     * Parses the SMS-Exporter dump format. Each entry looks like:
     *   `Received from COMBANK on YYYY-MM-DD HH:MM\n\n <body lines…>\n\n----…`
     */
    private fun parseDump(file: File): List<Message> {
        val text = file.readText()
        val blocks = text.split(Regex("""(?m)^-{40,}\s*$"""))
        val header = Regex("""Received from (\S+) on (\d{4}-\d{2}-\d{2}) (\d{2}:\d{2})""")
        return blocks.mapNotNull { raw ->
            val match = header.find(raw) ?: return@mapNotNull null
            val sender = match.groupValues[1]
            val date = match.groupValues[2]
            val time = match.groupValues[3]
            val ts = parseColomboTimestamp(date, time)
            // The body is everything after the header line; trim leading whitespace lines.
            val afterHeader = raw.substring(match.range.last + 1).trim()
            if (afterHeader.isBlank()) null
            else Message(sender = sender, body = afterHeader, receivedAt = ts)
        }
    }

    private fun parseColomboTimestamp(date: String, time: String): Long {
        return java.time.LocalDateTime
            .parse("$date" + "T" + "$time:00")
            .atZone(java.time.ZoneId.of("Asia/Colombo"))
            .toInstant().toEpochMilli()
    }

    /**
     * Parses the SMS Exporter CSV format. Header row is
     * `DateTime,Direction,Contact,Phone,Content,Type` and the `Content` column may contain
     * embedded newlines + commas (quoted, with `""` for an internal quote).
     */
    private fun parseCsv(file: File): List<Message> {
        val text = file.readText()
        val rows = splitCsvRows(text)
        // Skip preamble + blank rows; find the header.
        val headerIdx = rows.indexOfFirst { it.firstOrNull() == "DateTime" }
        if (headerIdx < 0) return emptyList()
        val header = rows[headerIdx]
        val dt = header.indexOf("DateTime")
        val cn = header.indexOf("Contact")
        val ct = header.indexOf("Content")
        return rows.drop(headerIdx + 1).mapNotNull { cols ->
            if (cols.size <= maxOf(dt, cn, ct)) return@mapNotNull null
            val datetime = cols[dt].trim()
            val sender = cols[cn].trim()
            val body = cols[ct]
            if (body.isBlank() || sender.isBlank()) return@mapNotNull null
            val ts = runCatching {
                java.time.LocalDateTime.parse(datetime.replace(" ", "T"))
                    .atZone(java.time.ZoneId.of("Asia/Colombo"))
                    .toInstant().toEpochMilli()
            }.getOrDefault(0L)
            Message(sender = sender, body = body, receivedAt = ts)
        }
    }

    /** Tiny CSV splitter that honours quoted cells with embedded newlines and `""` escapes. */
    private fun splitCsvRows(text: String): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>()
        var cur = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    cur.append('"'); i += 2; continue
                }
                c == '"' -> { inQuotes = !inQuotes; i++; continue }
                !inQuotes && c == ',' -> { row.add(cur.toString()); cur = StringBuilder(); i++; continue }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    row.add(cur.toString()); cur = StringBuilder()
                    if (row.any { it.isNotEmpty() }) rows.add(row)
                    row = mutableListOf()
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    i++; continue
                }
                else -> { cur.append(c); i++ }
            }
        }
        if (cur.isNotEmpty() || row.isNotEmpty()) {
            row.add(cur.toString()); if (row.any { it.isNotEmpty() }) rows.add(row)
        }
        return rows
    }
}
