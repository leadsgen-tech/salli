package lk.salli.parser

import lk.salli.parser.templates.AmanaTemplate
import lk.salli.parser.templates.BocOnlineTemplate
import lk.salli.parser.templates.BocTemplate
import lk.salli.parser.templates.CombankTemplate
import lk.salli.parser.templates.DfccTemplate
import lk.salli.parser.templates.HnbTemplate
import lk.salli.parser.templates.NdbTemplate
import lk.salli.parser.templates.NtbTemplate
import lk.salli.parser.templates.PanAsiaTemplate
import lk.salli.parser.templates.PeoplesBankTemplate
import lk.salli.parser.templates.PeoplesCardTemplate
import lk.salli.parser.templates.SampathTemplate
import lk.salli.parser.templates.SeylanTemplate

/**
 * Central registry of bank templates. Kept here (not discovered via reflection) so the list is
 * visible and reviewable in code review — adding a bank means adding a line to [all].
 */
object Templates {
    /**
     * Populated as templates are implemented. Order matters only when two templates could
     * claim the same sender — the first match wins.
     *
     * Templates marked *provisional* in their KDoc were written from publicly observable
     * format evidence, not from redacted real samples; their fixtures are reconstructions.
     * Anything they can't match still lands in the Unknown queue, so a wrong guess costs a
     * review row, never a phantom transaction.
     */
    val all: List<BankTemplate> = listOf(
        BocTemplate,
        BocOnlineTemplate,
        PeoplesBankTemplate,
        PeoplesCardTemplate,
        CombankTemplate,
        HnbTemplate,
        SeylanTemplate,
        AmanaTemplate,
        SampathTemplate,
        DfccTemplate,
        NdbTemplate,
        NtbTemplate,
        PanAsiaTemplate,
    )

    /**
     * Sender IDs of banks that have no template yet (banks with a template never reach this
     * list: the dispatcher already queues their unmatched bodies). Messages from these go to
     * the Unknown queue instead of being dropped, which is how new formats get reported. Some
     * IDs are educated guesses; a wrong guess simply never matches. Wallets (Genie, eZ Cash,
     * Frimi) are deliberately absent: on real inboxes they are almost entirely promos.
     */
    private val knownBankSenders: List<Regex> = listOf(
        "NSB", "NSBSMS",
        "HSBC", "HSBCLK",
        "StanChart", "SCB", "SCBSMS",
        "Cargills", "CargillsBnk", "CBC",
        "CDB", "CDBSMS",
        "UnionBank", "UBSMS",
    ).map { Regex("^${Regex.escape(it)}$", RegexOption.IGNORE_CASE) }

    fun forSender(sender: String): List<BankTemplate> =
        all.filter { it.handlesSender(sender) }

    fun isKnownBankSender(sender: String): Boolean =
        knownBankSenders.any { it.matches(sender.trim()) }
}
