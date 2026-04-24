package lk.salli.parser

import lk.salli.parser.templates.AmanaTemplate
import lk.salli.parser.templates.BocOnlineTemplate
import lk.salli.parser.templates.BocTemplate
import lk.salli.parser.templates.CombankTemplate
import lk.salli.parser.templates.HnbTemplate
import lk.salli.parser.templates.PeoplesBankTemplate
import lk.salli.parser.templates.SeylanTemplate

/**
 * Central registry of bank templates. Kept here (not discovered via reflection) so the list is
 * visible and reviewable in code review — adding a bank means adding a line to [all].
 */
object Templates {
    /**
     * Populated as templates are implemented. Order matters only when two templates could
     * claim the same sender — the first match wins.
     */
    val all: List<BankTemplate> = listOf(
        BocTemplate,
        BocOnlineTemplate,
        PeoplesBankTemplate,
        CombankTemplate,
        HnbTemplate,
        SeylanTemplate,
        AmanaTemplate,
    )

    fun forSender(sender: String): List<BankTemplate> =
        all.filter { it.handlesSender(sender) }
}
