package lk.salli.design.theme

import androidx.compose.ui.graphics.Color

/**
 * Each Sri Lankan bank has a recognisable brand colour. Using them on account cards turns
 * the accounts carousel into a little wallet of real-looking cards — far livelier than a
 * row of identical grey tiles. Values are loosely inspired by each bank's actual brand but
 * nudged to work on a dark UI (we tint down, desaturate slightly, and always provide a
 * paired darker shade for the gradient's far stop).
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

        fun forSender(sender: String?): BankBrand = when (sender) {
            "COMBANK" -> Combank
            "BOC", "BOCONLINE" -> Boc
            "PeoplesBank" -> Peoples
            "SAMPATH" -> Sampath
            "HNB" -> Hnb
            "NTB" -> Ntb
            "DFCC", "DFCCINFO" -> Dfcc
            "SEYLAN", "SEYLANBANK" -> Seylan
            "HSBC" -> Hsbc
            else -> Default
        }
    }
}
