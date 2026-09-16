package lk.salli.design.theme

import com.google.common.truth.Truth.assertThat
import lk.salli.design.logo.BankLogos
import org.junit.jupiter.api.Test

/**
 * Sender IDs arrive dirty from the SMS provider — `"COMBANK\n"` as often as `"COMBANK"`, and a
 * couple of real senders are mixed-case (`ComBank_Q+`, `Genie`). Both the logo lookup and the
 * brand colour normalise before matching; if either regresses, an account quietly loses its
 * identity and every card in the row goes grey.
 */
class BankIdentityTest {

    @Test
    fun `a trailing newline still resolves the logo and the brand`() {
        assertThat(BankLogos.resolve("COMBANK\n")).isEqualTo(BankLogos.resolve("COMBANK"))
        assertThat(BankBrand.forSender("COMBANK\n")).isEqualTo(BankBrand.Combank)
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertThat(BankLogos.resolve("  BOC  ")).isEqualTo(BankLogos.resolve("BOC"))
        assertThat(BankBrand.forSender(" HNB ")).isEqualTo(BankBrand.Hnb)
    }

    @Test
    fun `mixed-case senders resolve`() {
        // These are the real sender IDs, not our normalisation of them.
        assertThat(BankLogos.resolve("ComBank_Q+")).isNotNull()
        assertThat(BankBrand.forSender("ComBank_Q+")).isEqualTo(BankBrand.Combank)
        assertThat(BankLogos.resolve("Genie")).isNotNull()
        assertThat(BankBrand.forSender("Genie")).isEqualTo(BankBrand.Genie)
    }

    @Test
    fun `Q plus shares ComBank's logo and colour`() {
        assertThat(BankLogos.resolve("COMBANK_Q+")).isEqualTo(BankLogos.resolve("COMBANK"))
    }

    @Test
    fun `an unmapped sender gets the neutral default rather than a neighbour's colour`() {
        // A wrong bank colour is worse than no bank colour.
        assertThat(BankBrand.forSender("SOMETHING_NEW")).isEqualTo(BankBrand.Default)
        assertThat(BankLogos.resolve("SOMETHING_NEW")).isNull()
    }

    @Test
    fun `a null sender is handled`() {
        assertThat(BankLogos.resolve(null)).isNull()
        assertThat(BankBrand.forSender(null)).isEqualTo(BankBrand.Default)
    }

    @Test
    fun `People's Bank has a brand colour even without a bundled logo`() {
        // The avatar falls back to a brand-coloured initial; that only works if the colour is
        // real. Returning BOC's blue here is the bug this guards.
        assertThat(BankLogos.resolve("PeoplesBank")).isNull()
        assertThat(BankBrand.forSender("PeoplesBank")).isEqualTo(BankBrand.Peoples)
        assertThat(BankBrand.forSender("PeoplesBank")).isNotEqualTo(BankBrand.Boc)
    }

    @Test
    fun `every sender the parser recognises has a brand`() {
        // Mirrors the senderPatterns on each BankTemplate plus Templates.knownBankSenders. If
        // the parser learns a new sender and this list doesn't, that bank's accounts silently
        // render in the neutral default.
        listOf(
            "COMBANK", "ComBank_Q+",
            "BOC", "BOCONLINE",
            "PeoplesBank", "PeoplesCard",
            "SAMPATH", "SampathBank", "SAMPATHTXN", "SAMPCCTXN",
            "HNB",
            "NTB", "NTBSMS", "NationsSMS",
            "DFCC", "DFCCINFO", "DFCC Info", "DFCC Alerts", "DFCC Bank",
            "SEYLAN", "SEYLANBANK",
            "HSBC", "HSBCLK",
            "NSB", "NSBSMS",
            "NDB", "NDBBANK", "NDB CARD", "NDB ALERTS",
            "StanChart", "SCB", "SCBSMS",
            "AMANABANK",
            "PanAsiaBank", "PANASIA", "Pan Asia",
            "Cargills", "CargillsBnk", "CBC",
            "UnionBank", "UBSMS",
            "CDB", "CDBSMS",
            "FRIMISMS", "Genie",
        ).forEach { sender ->
            assertThat(BankBrand.forSender(sender)).isNotEqualTo(BankBrand.Default)
        }
    }

    @Test
    fun `senders that share a bank share its brand`() {
        assertThat(BankBrand.forSender("NationsSMS")).isEqualTo(BankBrand.forSender("NTB"))
        assertThat(BankBrand.forSender("SAMPCCTXN")).isEqualTo(BankBrand.forSender("SAMPATH"))
        assertThat(BankBrand.forSender("NDB CARD")).isEqualTo(BankBrand.forSender("NDB"))
        assertThat(BankBrand.forSender("BOCONLINE")).isEqualTo(BankBrand.forSender("BOC"))
    }
}
