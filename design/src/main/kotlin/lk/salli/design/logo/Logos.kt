package lk.salli.design.logo

/**
 * Maps a raw merchant string (as it appears in an SMS body) to a bundled logo asset path.
 *
 * Uses a simple substring match because SMS merchant strings are noisy —
 * `KEELLS SUPER COLOMBO 04`, `KEELLS SUPER NAWALA`, `FOOD CITY 045` all need to resolve to
 * their brand logo. First hit wins; ordering inside the list is therefore meaningful (more
 * specific / longer keys before shorter ones).
 *
 * Returns `null` when no logo matches; callers fall back to a generic category icon.
 */
object MerchantLogos {
    private val mappings: List<Pair<String, String>> = listOf(
        "pizza hut" to "merchants/pizzahut.jpg",
        "pizzahut" to "merchants/pizzahut.jpg",
        "pickme food" to "merchants/pickme.jpg",
        "pickme" to "merchants/pickme.jpg",
        "ubereats" to "merchants/ubereats.jpg",
        "uber eats" to "merchants/ubereats.jpg",
        "keells" to "merchants/keells.jpg",
        "food city" to "merchants/foodcity.jpg",
        "foodcity" to "merchants/foodcity.jpg",
        "arpico" to "merchants/arpico.jpg",
        "cargills" to "merchants/foodcity.jpg",
        "glomark" to "merchants/foodcity.jpg",
        "kfc" to "merchants/kfc.jpg",
        "barista" to "merchants/barista.jpg",
        "java lounge" to "merchants/java-lounge.jpg",
        "crepe" to "merchants/crepe.jpg",
        "street burger" to "merchants/streetburger.jpg",
        "streetburger" to "merchants/streetburger.jpg",
        "mimosa" to "merchants/mimosa.jpg",
        "daraz" to "merchants/daraz.jpg",
        "odel" to "merchants/odel.jpg",
        "softlogic" to "merchants/scope.jpg",
        "abans" to "merchants/abans.jpg",
        "dsi" to "merchants/dsi.jpg",
        "fab" to "merchants/fab.jpg",
        "wishque" to "merchants/wishque.jpg",
        "mintpay" to "merchants/mintpay.jpg",
        "sarasavi" to "merchants/sarasavi.jpg",
        "jumpbooks" to "merchants/jumpbooks.jpg",
        "spaceylon" to "merchants/spaceylon.jpg",
        "spa ceylon" to "merchants/spaceylon.jpg",
        "netflix" to "merchants/netflix.jpg",
        "spotify" to "merchants/spotify.jpg",
        "steam" to "merchants/steam.jpg",
        // SLT Mobitel is one brand — Mobitel is SLT's mobile arm — so they share a logo.
        // Dialog is a separate telco; until we bundle a dedicated asset, let it fall through
        // to the Utilities category icon rather than show a competitor's mark.
        "slt" to "merchants/slt.jpg",
        "mobitel" to "merchants/slt.jpg",
        "carnage" to "merchants/carnage.jpg",
        "cool planet" to "merchants/coolplanet.jpg",
        "coolplanet" to "merchants/coolplanet.jpg",
    )

    fun resolve(merchantRaw: String?): String? {
        if (merchantRaw.isNullOrBlank()) return null
        val lower = merchantRaw.lowercase()
        return mappings.firstOrNull { lower.contains(it.first) }?.second
    }

    fun asAssetUri(relativePath: String): String = "file:///android_asset/$relativePath"
}

/**
 * Maps an SMS sender address (bank ID) to a bundled bank-logo asset path. Logos are trademarks
 * of the respective banks; we use them purely for recognition in the UI.
 */
object BankLogos {
    private val mappings: Map<String, String> = mapOf(
        "BOC" to "banks/boc.jpg",
        "BOCONLINE" to "banks/boc.jpg",
        "COMBANK" to "banks/combank.jpg",
        "ComBank_Q+" to "banks/combank.jpg",
        // No People's Bank logo in the bundled set yet — return null so the UI falls back
        // to the generic bank icon rather than miscrediting the transaction to BOC.
        "SAMPATH" to "banks/sampath.jpg",
        "HNB" to "banks/hnb.jpg",
        "NTB" to "banks/ntb.jpg",
        "DFCC" to "banks/dfcc.jpg",
        "DFCCINFO" to "banks/dfcc.jpg",
        "SEYLAN" to "banks/seylan.jpg",
        "SEYLANBANK" to "banks/seylan.jpg",
        "NSB" to "banks/nsb.jpg",
        "NDB" to "banks/ndb.jpg",
        "NDBCARD" to "banks/ndb.jpg",
        "HSBC" to "banks/hsbc.jpg",
        "STANCHART" to "banks/standard.jpg",
        "AMANABANK" to "banks/amana-bank.jpg",
        "FRIMISMS" to "banks/frimi.jpg",
        "Genie" to "banks/genie.jpg",
    )

    fun resolve(sender: String?): String? = mappings[sender]
    fun asAssetUri(relativePath: String): String = "file:///android_asset/$relativePath"
}
