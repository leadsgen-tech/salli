package lk.salli.parser.utility

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Test

class FuelPassParserTest {

    private val colombo = ZoneId.of("Asia/Colombo")

    @Test
    fun `iso stamp with resets-on`() {
        val body = "National Fuel Pass: TRN confirmed.\n2026-09-08 06:02:23 ABC-1234\nQuota used: 8L\nWeekly Balance: 0.000L\nStation code: 102316\n(Resets on - 2026-09-13)"
        val tx = FuelPassParser.parse("1919", body, receivedAt = 0L)!!
        assertThat(tx.vehicle).isEqualTo("ABC-1234")
        assertThat(tx.litresMilli).isEqualTo(8000)
        assertThat(tx.weeklyBalanceMilli).isEqualTo(0)
        assertThat(tx.stationCode).isEqualTo("102316")
        assertThat(tx.timestampMillis).isEqualTo(
            LocalDateTime.of(2026, 9, 8, 6, 2, 23).atZone(colombo).toInstant().toEpochMilli(),
        )
        assertThat(tx.resetsOnMillis).isEqualTo(
            LocalDate.of(2026, 9, 13).atStartOfDay(colombo).toInstant().toEpochMilli(),
        )
    }

    @Test
    fun `dotted stamp with expires-on and fractional litres`() {
        val body = "National Fuel Pass: TRN confirmed.\n05.04.2026 14:14:35 ABC-1234\nQuota used: 2.51L\nWeekly Balance: 4.390L\nStation code: 400109\n(Expires on - 2026-04-11)"
        val tx = FuelPassParser.parse("1919", body, receivedAt = 0L)!!
        assertThat(tx.litresMilli).isEqualTo(2510)
        assertThat(tx.weeklyBalanceMilli).isEqualTo(4390)
        assertThat(tx.timestampMillis).isEqualTo(
            LocalDateTime.of(2026, 4, 5, 14, 14, 35).atZone(colombo).toInstant().toEpochMilli(),
        )
        assertThat(tx.resetsOnMillis).isEqualTo(
            LocalDate.of(2026, 4, 11).atStartOfDay(colombo).toInstant().toEpochMilli(),
        )
    }

    @Test
    fun `otp, registration and qr link messages are ignored`() {
        listOf(
            "National Fuel Pass: One time validation (OTP) 353585\nExpire in 2 minutes",
            "National Fuel Pass: Access link to retrieve your QR codes\nhttps://fuelpass.gov.lk/qr",
            "National Fuel Pass: Dear User, your profile has been successfully created. Thank you for registering with National Fuel Pass\nAccess link to retrieve QR code https://fuelpass.gov.lk/qr",
        ).forEach { assertThat(FuelPassParser.parse("1919", it, 0L)).isNull() }
    }

    @Test
    fun `other senders are ignored even with the same body`() {
        assertThat(FuelPassParser.parse("BOC", "National Fuel Pass: TRN confirmed.\n2026-09-08 06:02:23 ABC-1234\nQuota used: 8L", 0L)).isNull()
    }

    @Test
    fun `millilitre conversion`() {
        assertThat(FuelPassParser.millilitres("8")).isEqualTo(8000)
        assertThat(FuelPassParser.millilitres("4.390")).isEqualTo(4390)
        assertThat(FuelPassParser.millilitres("1.2")).isEqualTo(1200)
    }
}
