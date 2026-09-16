package lk.salli.domain.split

/** How an expense's total is divided among the people in it. */
enum class SplitMethod { EQUAL, EXACT, WEIGHTS }

/**
 * One person in an expense. [value] depends on the method: ignored for [SplitMethod.EQUAL], the
 * person's amount in minor units for [SplitMethod.EXACT], and a non-negative relative weight for
 * [SplitMethod.WEIGHTS] (percent, shares or nights all work: only the ratios matter).
 */
data class SplitParticipant(val memberId: Long, val value: Long = 0L)

/** Why a split could not be computed or recorded. */
enum class SplitError {
    NO_PARTICIPANTS,
    NON_POSITIVE_TOTAL,
    DUPLICATE_PARTICIPANT,
    NEGATIVE_VALUE,
    /** EXACT amounts do not add up to the total. */
    EXACT_MISMATCH,
    /** WEIGHTS are all zero, or one is above [SplitMath.MAX_WEIGHT]. */
    INVALID_WEIGHTS,
    /** A group keeps one currency; anything in another is refused rather than converted. */
    CURRENCY_MISMATCH,
    UNKNOWN_GROUP,
    UNKNOWN_MEMBER,
    /** A settlement from a person to themselves. */
    SAME_MEMBER,
}

sealed interface SplitResult {
    /** Share per member id, in participant order, summing exactly to the total. */
    data class Ok(val shares: Map<Long, Long>) : SplitResult
    data class Invalid(val error: SplitError) : SplitResult
}

data class ExpenseInput(val paidBy: Long, val amountMinor: Long, val shares: Map<Long, Long>)

data class SettlementInput(val from: Long, val to: Long, val amountMinor: Long)

/** A suggested payment: [from] pays [to]. */
data class Transfer(val from: Long, val to: Long, val amountMinor: Long)

/**
 * Integer-only maths for shared expenses. Every amount is in minor units, so totals reconcile
 * exactly: shares always sum to the expense and balances always sum to zero.
 */
object SplitMath {

    /** Keeps `remainder × weight` far from Long overflow for any realistic group. */
    const val MAX_WEIGHT: Long = 1_000_000L

    fun shares(totalMinor: Long, participants: List<SplitParticipant>, method: SplitMethod): SplitResult {
        if (participants.isEmpty()) return SplitResult.Invalid(SplitError.NO_PARTICIPANTS)
        if (totalMinor <= 0L) return SplitResult.Invalid(SplitError.NON_POSITIVE_TOTAL)
        if (participants.mapTo(HashSet()) { it.memberId }.size != participants.size) {
            return SplitResult.Invalid(SplitError.DUPLICATE_PARTICIPANT)
        }
        return when (method) {
            SplitMethod.EQUAL -> SplitResult.Ok(largestRemainder(totalMinor, participants.map { it.memberId to 1L }))
            SplitMethod.EXACT -> when {
                participants.any { it.value < 0L } -> SplitResult.Invalid(SplitError.NEGATIVE_VALUE)
                // Checked before summing so absurd inputs cannot overflow the sum.
                participants.any { it.value > totalMinor } -> SplitResult.Invalid(SplitError.EXACT_MISMATCH)
                participants.sumOf { it.value } != totalMinor -> SplitResult.Invalid(SplitError.EXACT_MISMATCH)
                else -> SplitResult.Ok(participants.associate { it.memberId to it.value })
            }
            SplitMethod.WEIGHTS -> when {
                participants.any { it.value < 0L } -> SplitResult.Invalid(SplitError.NEGATIVE_VALUE)
                participants.any { it.value > MAX_WEIGHT } || participants.all { it.value == 0L } ->
                    SplitResult.Invalid(SplitError.INVALID_WEIGHTS)
                else -> SplitResult.Ok(largestRemainder(totalMinor, participants.map { it.memberId to it.value }))
            }
        }
    }

    /** Null when [currency] may be used in a group kept in [groupCurrency]. */
    fun currencyError(groupCurrency: String, currency: String): SplitError? =
        if (groupCurrency.equals(currency, ignoreCase = true)) null else SplitError.CURRENCY_MISMATCH

    /**
     * Net position per member: what they paid for others minus what others paid for them, after
     * settlements. Positive means the member is owed; the values always sum to zero.
     */
    fun balances(
        memberIds: Collection<Long>,
        expenses: List<ExpenseInput>,
        settlements: List<SettlementInput>,
    ): Map<Long, Long> {
        val net = LinkedHashMap<Long, Long>()
        memberIds.forEach { net[it] = 0L }
        for (e in expenses) {
            net[e.paidBy] = (net[e.paidBy] ?: 0L) + e.amountMinor
            e.shares.forEach { (member, share) -> net[member] = (net[member] ?: 0L) - share }
        }
        for (s in settlements) {
            net[s.from] = (net[s.from] ?: 0L) + s.amountMinor
            net[s.to] = (net[s.to] ?: 0L) - s.amountMinor
        }
        return net
    }

    /**
     * Suggested payments that clear every balance: repeatedly the biggest debtor pays the biggest
     * creditor. Ties break on member id so the suggestion never reshuffles between renders.
     */
    fun simplify(balances: Map<Long, Long>): List<Transfer> {
        val byAmountThenId = compareByDescending<Pair<Long, Long>> { it.second }.thenBy { it.first }
        val creditors = balances.filterValues { it > 0L }.map { it.key to it.value }.toMutableList()
        val debtors = balances.filterValues { it < 0L }.map { it.key to -it.value }.toMutableList()
        val out = ArrayList<Transfer>()
        while (creditors.isNotEmpty() && debtors.isNotEmpty()) {
            creditors.sortWith(byAmountThenId)
            debtors.sortWith(byAmountThenId)
            val (creditor, owed) = creditors[0]
            val (debtor, owes) = debtors[0]
            val amount = minOf(owed, owes)
            out += Transfer(from = debtor, to = creditor, amountMinor = amount)
            if (owed == amount) creditors.removeAt(0) else creditors[0] = creditor to owed - amount
            if (owes == amount) debtors.removeAt(0) else debtors[0] = debtor to owes - amount
        }
        return out
    }

    /**
     * Largest-remainder apportionment: floor every exact share, then hand the leftover minor units
     * one each to the largest fractional parts (earlier participants win ties).
     */
    private fun largestRemainder(total: Long, weights: List<Pair<Long, Long>>): Map<Long, Long> {
        val sum = weights.sumOf { it.second }
        val quotient = total / sum
        val remainder = total % sum
        val base = LongArray(weights.size)
        val fraction = LongArray(weights.size)
        weights.forEachIndexed { i, (_, w) ->
            // total × w / sum without overflow: (q·sum + r) × w / sum = q·w + r·w / sum.
            base[i] = quotient * w + (remainder * w) / sum
            fraction[i] = (remainder * w) % sum
        }
        var leftover = total - base.sum()
        val order = weights.indices.sortedWith(compareByDescending<Int> { fraction[it] }.thenBy { it })
        for (i in order) {
            if (leftover == 0L) break
            base[i] += 1L
            leftover--
        }
        return weights.indices.associate { weights[it].first to base[it] }
    }
}
