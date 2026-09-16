package lk.salli.parser.utility

/**
 * Sender IDs for non-bank SMS Salli tracks: utility bills and the National Fuel Pass.
 * These never carry bank transactions, so the ingest layer routes them to [BillParser] /
 * [FuelPassParser] before the bank templates ever see them.
 */
object UtilitySenders {
    const val FUEL_PASS = "1919"

    private val billSenders: List<Regex> = listOf(
        "SLTBILL", "SLTMOBITEL", "SLT-MOBITEL", "SLT",
        "Dialog", "DialogDIA", "DialogBill",
        "CEB", "CEB e-Bill", "CEBSMS",
        "NWSDB", "NWSDB Bill",
    ).map { Regex("^${Regex.escape(it)}$", RegexOption.IGNORE_CASE) }

    fun isBillSender(sender: String): Boolean = billSenders.any { it.matches(sender.trim()) }

    fun isFuelPassSender(sender: String): Boolean = sender.trim() == FUEL_PASS

    fun isUtilitySender(sender: String): Boolean = isBillSender(sender) || isFuelPassSender(sender)
}
