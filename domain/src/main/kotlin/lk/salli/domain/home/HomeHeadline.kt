package lk.salli.domain.home

/**
 * What the Home "Right now" line is talking about. The UI maps each kind to a string resource
 * and fills it from [HeadlineParams] — this module never holds user-visible copy, so Sinhala
 * and Tamil come for free.
 */
enum class HeadlineKind {
    /** At least one open bill whose due date has passed. */
    BILL_OVERDUE,

    /** At least one open bill due today. */
    BILL_DUE_TODAY,

    /** At least one budget spent past its cap. */
    BUDGET_OVER,

    /** Messages from a known bank that no template matched, waiting in the review queue. */
    UNKNOWN_SMS,

    /** Transactions that landed since the user last opened the app. */
    NEW_SINCE_LAST_OPEN,

    /** Nothing needs attention: what is left to spend today. */
    SAFE_TO_SPEND,
}

/**
 * Everything the UI needs to fill the sentence for a [HeadlineKind]. All fields are data the
 * user already owns (a biller, a budget name, an amount) — never chrome.
 *
 * Which fields are populated depends on the kind:
 *
 * | Kind | count | amountMinor / currency | label |
 * |---|---|---|---|
 * | [HeadlineKind.BILL_OVERDUE] | bills overdue | what is still owed on the worst one | biller |
 * | [HeadlineKind.BILL_DUE_TODAY] | bills due today | what is still owed on the largest | biller |
 * | [HeadlineKind.BUDGET_OVER] | budgets over | how far the worst one is over | budget name |
 * | [HeadlineKind.UNKNOWN_SMS] | messages to review | — | — |
 * | [HeadlineKind.NEW_SINCE_LAST_OPEN] | new transactions | — | — |
 * | [HeadlineKind.SAFE_TO_SPEND] | — | what is safe to spend today | — |
 */
data class HeadlineParams(
    val count: Int = 0,
    val amountMinor: Long? = null,
    val currency: String? = null,
    val label: String? = null,
)

data class Headline(
    val kind: HeadlineKind,
    val params: HeadlineParams = HeadlineParams(),
)

/** An open bill, reduced to what the headline needs. [amountMinor] is what is still owed. */
data class HeadlineBill(
    val biller: String,
    val amountMinor: Long,
    val currency: String,
    /** Due date as days since the epoch, in the user's own timezone. */
    val dueEpochDay: Long,
)

/** A budget already past its cap. [overByMinor] is how far past, always positive. */
data class HeadlineBudget(
    val name: String,
    val overByMinor: Long,
    val currency: String,
)

/**
 * Everything the priority list looks at. Callers pass only what they have; an absent source is
 * simply an empty list or a zero, which never wins a tier.
 */
data class HomeHeadlineInput(
    /** Open bills whose due date is strictly before today. */
    val overdueBills: List<HeadlineBill> = emptyList(),
    /** Open bills due today. */
    val billsDueToday: List<HeadlineBill> = emptyList(),
    /** Budgets whose spend has passed the cap for the current period. */
    val budgetsOver: List<HeadlineBudget> = emptyList(),
    /** Messages parked in the review queue. */
    val unknownSmsCount: Int = 0,
    /** Transactions inserted since the user last opened the app. */
    val newSinceLastOpenCount: Int = 0,
    /** What is left to spend today, or null when there is no budget to divide. */
    val safeToSpendTodayMinor: Long? = null,
    /** Currency for [safeToSpendTodayMinor]. */
    val currency: String? = null,
)

/**
 * The one sentence Home shows under the eyebrow.
 *
 * Priority is fixed and total — the first tier with anything to say wins, and everything below
 * it stays silent, because a stack of banners is exactly what this line replaces:
 *
 *  1. an overdue bill
 *  2. a bill due today
 *  3. a budget over its cap
 *  4. messages that need a look
 *  5. transactions new since the last open
 *  6. what is safe to spend today
 *
 * Returns null when no tier applies, and the line disappears (rule 4 of the design spec:
 * a section with nothing to show is not rendered at all).
 *
 * Pure: no clock, no locale, no formatting. The caller decides what "today" means and hands
 * over already-resolved numbers.
 */
object HomeHeadline {

    fun of(input: HomeHeadlineInput): Headline? {
        worstOverdue(input.overdueBills)?.let { bill ->
            return Headline(
                HeadlineKind.BILL_OVERDUE,
                HeadlineParams(
                    count = input.overdueBills.size,
                    amountMinor = bill.amountMinor,
                    currency = bill.currency,
                    label = bill.biller,
                ),
            )
        }

        largest(input.billsDueToday)?.let { bill ->
            return Headline(
                HeadlineKind.BILL_DUE_TODAY,
                HeadlineParams(
                    count = input.billsDueToday.size,
                    amountMinor = bill.amountMinor,
                    currency = bill.currency,
                    label = bill.biller,
                ),
            )
        }

        worstBudget(input.budgetsOver)?.let { budget ->
            return Headline(
                HeadlineKind.BUDGET_OVER,
                HeadlineParams(
                    count = input.budgetsOver.size,
                    amountMinor = budget.overByMinor,
                    currency = budget.currency,
                    label = budget.name,
                ),
            )
        }

        if (input.unknownSmsCount > 0) {
            return Headline(HeadlineKind.UNKNOWN_SMS, HeadlineParams(count = input.unknownSmsCount))
        }

        if (input.newSinceLastOpenCount > 0) {
            return Headline(
                HeadlineKind.NEW_SINCE_LAST_OPEN,
                HeadlineParams(count = input.newSinceLastOpenCount),
            )
        }

        // Only worth saying when there is actually headroom. Zero or negative means the user is
        // already at or past their budget, and "Rs 0 safe to spend" is noise, not news — the
        // over-budget case is tier 3's job.
        val safe = input.safeToSpendTodayMinor
        if (safe != null && safe > 0L) {
            return Headline(
                HeadlineKind.SAFE_TO_SPEND,
                HeadlineParams(amountMinor = safe, currency = input.currency),
            )
        }

        return null
    }

    /** Most overdue first; same day, the bigger bill; same again, alphabetical so ties are stable. */
    private fun worstOverdue(bills: List<HeadlineBill>): HeadlineBill? = bills.minWithOrNull(
        compareBy<HeadlineBill> { it.dueEpochDay }
            .thenByDescending { it.amountMinor }
            .thenBy { it.biller },
    )

    private fun largest(bills: List<HeadlineBill>): HeadlineBill? = bills.maxWithOrNull(
        compareBy<HeadlineBill> { it.amountMinor }.thenByDescending { it.biller },
    )

    private fun worstBudget(budgets: List<HeadlineBudget>): HeadlineBudget? = budgets.maxWithOrNull(
        compareBy<HeadlineBudget> { it.overByMinor }.thenByDescending { it.name },
    )
}
