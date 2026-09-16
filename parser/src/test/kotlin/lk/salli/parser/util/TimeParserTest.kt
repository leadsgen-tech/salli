package lk.salli.parser.util

import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Test

class TimeParserTest {
    private fun colombo(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0): Long =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(ZoneId.of("Asia/Colombo")).toInstant().toEpochMilli()

    @Test
    fun `hnb card stamp parses in any month case`() {
        val expected = colombo(2026, 9, 14, 18, 42, 10)
        assertThat(TimeParser.parseHnbCard("14-Sep-2026 06:42:10 PM")).isEqualTo(expected)
        assertThat(TimeParser.parseHnbCard("14-SEP-2026 06:42:10 PM")).isEqualTo(expected)
        assertThat(TimeParser.parseHnbCard("14-sep-2026 06:42:10 pm")).isEqualTo(expected)
    }

    @Test
    fun `dfcc stamps parse`() {
        assertThat(TimeParser.parseDfccCard("14/Sep/2026 18:42")).isEqualTo(colombo(2026, 9, 14, 18, 42))
        assertThat(TimeParser.parseDfccCard("14/SEP/2026 18:42")).isEqualTo(colombo(2026, 9, 14, 18, 42))
        assertThat(TimeParser.parseDayMonthNameYear("14 Sep 2026")).isEqualTo(colombo(2026, 9, 14, 0, 0))
    }

    @Test
    fun `iso and sampath stamps parse`() {
        assertThat(TimeParser.parseIsoDateTime("2026-09-14 18:42:10")).isEqualTo(colombo(2026, 9, 14, 18, 42, 10))
        assertThat(TimeParser.parseSampathAccount("14/09/2026", "10:15:00")).isEqualTo(colombo(2026, 9, 14, 10, 15))
    }

    @Test
    fun `garbage returns null instead of throwing`() {
        assertThat(TimeParser.parseHnbCard("yesterday")).isNull()
        assertThat(TimeParser.parseDfccCard("")).isNull()
    }
}
