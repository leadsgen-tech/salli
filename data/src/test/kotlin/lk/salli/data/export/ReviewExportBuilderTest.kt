package lk.salli.data.export

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import lk.salli.data.db.entities.UnknownSmsEntity
import org.junit.Test

class ReviewExportBuilderTest {
    private val zone: ZoneId = ZoneId.of("Asia/Colombo")

    private fun pending(sender: String, body: String, id: Long = 1L) =
        UnknownSmsEntity(id = id, senderAddress = sender, body = body, receivedAt = 1_757_998_800_000L)

    @Test
    fun `masks bodies keeps the raw sender and reports the reason`() {
        val doc = ReviewExportBuilder.build(
            appVersion = "0.4.1",
            nowMillis = 1_758_000_000_000L,
            zone = zone,
            pending = listOf(pending("COMBANK\n", "Payment Rs 4,280.00 from A/C 1234567890 to KEELLS")),
            includeSenders = setOf("COMBANK"),
            parsedBySender = mapOf("COMBANK" to 812),
            candidates = emptyList(),
        )
        val entry = doc.review.single()
        assertThat(entry.sender).isEqualTo("COMBANK\n")
        assertThat(entry.senderNormalised).isEqualTo("COMBANK")
        assertThat(entry.reason).isEqualTo("NO_TEMPLATE_MATCH")
        assertThat(entry.body).isEqualTo("Payment Rs 4,280.00 from A/C XXXXXX7890 to KEELLS")
        assertThat(entry.receivedAt).endsWith("+05:30")
        assertThat(entry.id).hasLength(12)
        assertThat(doc.coverage).containsExactly(CoverageEntry("COMBANK", parsed = 812, review = 1))
    }

    @Test
    fun `otps never leave the phone and deselected senders are left out`() {
        val doc = ReviewExportBuilder.build(
            appVersion = "0.4.1",
            nowMillis = 0L,
            zone = zone,
            pending = listOf(
                pending("BOC", "Please use 273546 as your OTP for Log-in to BOC Flex.", id = 1),
                pending("NSB", "Your A/C 7012345678 credited Rs 5,000.00", id = 2),
                pending("HSBC", "Card ending 4273 used for LKR 900.00", id = 3),
            ),
            includeSenders = setOf("BOC", "NSB"),
            parsedBySender = emptyMap(),
            candidates = listOf(ReviewCandidate("EZCASH", "Your eZ Cash A/C debited Rs 250.00", 0L)),
        )
        assertThat(doc.review.map { it.senderNormalised }).containsExactly("NSB")
        assertThat(doc.review.single().reason).isEqualTo("BANK_KNOWN_NO_TEMPLATE")
        assertThat(doc.candidates.single().reason).isEqualTo("UNLISTED_SENDER")
        assertThat(doc.candidates.single().bank).isNull()
        assertThat(doc.coverage.map { it.sender }).containsExactly("BOC", "HSBC", "NSB").inOrder()
    }

    @Test
    fun `encodes to pretty json with the schema version`() {
        val text = ReviewExportBuilder.encode(
            ReviewExportBuilder.build("0.4.1", 0L, zone, emptyList(), emptySet(), emptyMap(), emptyList()),
        )
        assertThat(text).contains("\"schema\": 1")
        assertThat(text).contains("\"app\": \"0.4.1\"")
    }
}
