package lk.salli.design.theme

import androidx.compose.ui.graphics.Color

/**
 * Each Sri Lankan bank has a recognisable brand colour. Using them on account cards turns
 * account markers into useful identity cues. Values are loosely inspired by each bank's
 * actual brand and include a darker companion that remains distinct on cool-neutral surfaces.
 */
data class BankBrand(
    val primary: Color,
    val secondary: Color,
    val onBrand: Color = Color.White,
) {
    companion object {
        val Default = BankBrand(
            primary = Color(0xFF334155),
            secondary = Color(0xFF1E293B),
        )

        // Commercial Bank — royal blue.
        val Combank = BankBrand(
            primary = Color(0xFF3B82F6),
            secondary = Color(0xFF1E40AF),
        )

        // Bank of Ceylon — deep ocean blue.
        val Boc = BankBrand(
            primary = Color(0xFF2563EB),
            secondary = Color(0xFF1E3A8A),
        )

        // People's Bank — their signature warm red.
        val Peoples = BankBrand(
            primary = Color(0xFFEF4444),
            secondary = Color(0xFF991B1B),
        )

        // Sampath — moss green.
        val Sampath = BankBrand(
            primary = Color(0xFF22C55E),
            secondary = Color(0xFF166534),
        )

        // HNB — royal purple.
        val Hnb = BankBrand(
            primary = Color(0xFF8B5CF6),
            secondary = Color(0xFF5B21B6),
        )

        // Nations Trust — teal.
        val Ntb = BankBrand(
            primary = Color(0xFF14B8A6),
            secondary = Color(0xFF0F766E),
        )

        // DFCC — cyan.
        val Dfcc = BankBrand(
            primary = Color(0xFF06B6D4),
            secondary = Color(0xFF0E7490),
        )

        // Seylan — orange.
        val Seylan = BankBrand(
            primary = Color(0xFFF97316),
            secondary = Color(0xFFC2410C),
        )

        // HSBC — red-orange.
        val Hsbc = BankBrand(
            primary = Color(0xFFEF4444),
            secondary = Color(0xFFB91C1C),
        )

        // National Savings Bank — deep blue; their gold mark is illegible at avatar size.
        val Nsb = BankBrand(
            primary = Color(0xFF1D4ED8),
            secondary = Color(0xFF172554),
        )

        // National Development Bank — indigo.
        val Ndb = BankBrand(
            primary = Color(0xFF4F46E5),
            secondary = Color(0xFF3730A3),
        )

        // Standard Chartered — green stop first, blue as the companion.
        val StanChart = BankBrand(
            primary = Color(0xFF0F8A5F),
            secondary = Color(0xFF0B5E7D),
        )

        // Amana Bank — emerald.
        val Amana = BankBrand(
            primary = Color(0xFF10B981),
            secondary = Color(0xFF065F46),
        )

        // FriMi (Nations Trust's wallet) — magenta, distinct from NTB's teal on purpose.
        val Frimi = BankBrand(
            primary = Color(0xFFDB2777),
            secondary = Color(0xFF9D174D),
        )

        // Genie (Dialog Finance) — Dialog's red.
        val Genie = BankBrand(
            primary = Color(0xFFE11D48),
            secondary = Color(0xFF9F1239),
        )

        // Cargills Bank — their green.
        val Cargills = BankBrand(
            primary = Color(0xFF16A34A),
            secondary = Color(0xFF14532D),
        )

        // Union Bank — crimson.
        val Union = BankBrand(
            primary = Color(0xFFDC2626),
            secondary = Color(0xFF7F1D1D),
        )

        // Pan Asia — violet.
        val PanAsia = BankBrand(
            primary = Color(0xFF7C3AED),
            secondary = Color(0xFF4C1D95),
        )

        // Citizens Development Business Finance — amber.
        val Cdb = BankBrand(
            primary = Color(0xFFD97706),
            secondary = Color(0xFF92400E),
        )

        /**
         * Sender IDs arrive dirty — the provider hands over `"COMBANK\n"` as readily as
         * `"COMBANK"`, and a few real senders are mixed-case (`ComBank_Q+`, `Genie`). Trim and
         * upper-case before matching, once, here, rather than at every call site.
         *
         * A bank with no entry falls back to [Default] rather than to a neighbour's colour:
         * a wrong bank colour is worse than a neutral one.
         */
        fun forSender(sender: String?): BankBrand = when (sender?.trim()?.uppercase()) {
            // Every alias below is one the parser actually matches: the lists mirror
            // `senderPatterns` on each BankTemplate and `Templates.knownBankSenders` for the
            // banks we recognise but cannot yet parse. Inventing IDs here would show a
            // confident brand colour for a bank that never sends under that name.
            "COMBANK", "COMBANK_Q+" -> Combank
            "BOC", "BOCONLINE" -> Boc
            "PEOPLESBANK", "PEOPLESCARD" -> Peoples
            "SAMPATH", "SAMPATHBANK", "SAMPATHTXN", "SAMPCCTXN" -> Sampath
            "HNB" -> Hnb
            "NTB", "NTBSMS", "NATIONSSMS" -> Ntb
            "DFCC", "DFCCINFO", "DFCC INFO", "DFCC ALERTS", "DFCC BANK" -> Dfcc
            "SEYLAN", "SEYLANBANK" -> Seylan
            "HSBC", "HSBCLK" -> Hsbc
            "NSB", "NSBSMS" -> Nsb
            "NDB", "NDBBANK", "NDB CARD", "NDB ALERTS" -> Ndb
            "STANCHART", "SCB", "SCBSMS" -> StanChart
            "AMANABANK" -> Amana
            "FRIMISMS", "FRIMI" -> Frimi
            "GENIE" -> Genie
            "CARGILLS", "CARGILLSBNK", "CBC" -> Cargills
            "UNIONBANK", "UBSMS" -> Union
            "PANASIA", "PANASIABANK", "PAN ASIA" -> PanAsia
            "CDB", "CDBSMS" -> Cdb
            else -> Default
        }
    }
}
