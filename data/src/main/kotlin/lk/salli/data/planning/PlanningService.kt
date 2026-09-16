package lk.salli.data.planning

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.AccountEntity
import lk.salli.data.db.entities.BillEntity
import lk.salli.data.db.entities.GoalContributionEntity
import lk.salli.data.db.entities.GoalEntity
import lk.salli.data.db.entities.RecurringSeriesEntity
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.data.prefs.PeriodSettings
import lk.salli.data.prefs.SalliPreferences
import lk.salli.data.transactions.TransactionSpending
import lk.salli.domain.DateRange
import lk.salli.domain.TransactionFlow
import lk.salli.domain.planning.AccountBalance
import lk.salli.domain.planning.Commitment
import lk.salli.domain.planning.CommitmentKind
import lk.salli.domain.planning.CycleSpend
import lk.salli.domain.planning.GoalMath
import lk.salli.domain.planning.Runway
import lk.salli.domain.planning.RunwayResult
import lk.salli.domain.planning.SafeToSpend
import lk.salli.domain.planning.SafeToSpendResult
import lk.salli.domain.recurring.RecurringStatus

data class PlanningSnapshot(
    val now: Long,
    val currency: String,
    val cycle: DateRange,
    val userLimitMinor: Long?,
    val safeToSpend: SafeToSpendResult,
    val runway: RunwayResult,
)

/**
 * Assembles safe-to-spend and runway from the database. Every sum excludes declined attempts,
 * own-transfer legs and accounts hidden in Settings, and uses the dominant currency only.
 *
 * Commitments for the rest of the cycle:
 *  - open bills due before the cycle ends, at what is still owed (part-payments subtracted);
 *  - repeating expenses the user confirmed, or that are fixed with confidence ≥ [HIGH_CONFIDENCE],
 *    once per expected charge before the cycle ends, skipping any whose name matches an open
 *    bill's biller (the bill already counts);
 *  - for contribution-based goals with a target date, what is left to save this cycle.
 */
class PlanningService(
    private val db: SalliDatabase,
    private val prefs: SalliPreferences,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private data class Sources(
        val period: PeriodSettings,
        val limitMinor: Long?,
        val accounts: List<AccountEntity>,
        val bills: List<BillEntity>,
        val series: List<RecurringSeriesEntity>,
        val goals: List<GoalEntity>,
        val contributions: List<GoalContributionEntity>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<PlanningSnapshot> {
        val planning = combine(
            db.recurring().observeAll(),
            db.goals().observeGoals(),
            db.goals().observeContributions(),
        ) { series, goals, contributions -> Triple(series, goals, contributions) }

        return combine(
            prefs.period,
            prefs.monthlySpendingLimitMinor,
            db.accounts().observeAll(),
            db.bills().observeOpenBills(),
            planning,
        ) { period, limit, accounts, bills, rows ->
            Sources(period, limit, accounts, bills, rows.first, rows.second, rows.third)
        }.flatMapLatest { src ->
            val now = clock()
            db.transactions().observeInRange(windowStart(now, src.period.monthStartDay), Long.MAX_VALUE)
                .map { rows -> build(src, rows, now, db.transactions().earliestTimestamp()) }
        }
    }

    private fun windowStart(now: Long, startDay: Int): Long {
        var c = DateRange.cycleFor(now, startDay)
        repeat(SafeToSpend.MEDIAN_CYCLES) { c = DateRange.prevCycle(c, startDay) }
        return minOf(c.fromMillis, now - Runway.WINDOW_DAYS * DAY_MS)
    }

    private fun build(src: Sources, rows: List<TransactionEntity>, now: Long, earliest: Long?): PlanningSnapshot {
        val hidden = src.accounts.filter { it.isHidden }.mapTo(HashSet()) { it.id }
        val day = src.period.monthStartDay
        val cycle = DateRange.cycleFor(now, day)
        val currency = TransactionSpending.dominantCurrency(
            rows.filter { it.timestamp >= cycle.fromMillis && it.timestamp < cycle.untilMillis },
            hidden,
        )
        val spend = rows.filter { TransactionSpending.counts(it, hidden) && it.amountCurrency == currency }

        fun spentIn(r: DateRange) = spend.filter { it.timestamp >= r.fromMillis && it.timestamp < r.untilMillis }.sumOf { it.amountMinor }

        val completed = ArrayList<CycleSpend>()
        var c = cycle
        repeat(SafeToSpend.MEDIAN_CYCLES) {
            c = DateRange.prevCycle(c, day)
            // A cycle history only partly covers would drag the median down; leave it out.
            if (earliest != null && earliest <= c.fromMillis) completed += CycleSpend(c.label, spentIn(c))
        }

        val commitments = ArrayList<Commitment>()
        // A repeating payment that is really this period's bill payment (paying SLT from People's
        // shows up as a series) must not be counted twice. Only bills actually counted here can
        // stand in for a series; a bill due next period or in another currency covers nothing.
        val countedBillWords = HashSet<String>()
        for (bill in src.bills) {
            val due = bill.dueDate ?: continue
            if (due >= cycle.untilMillis || bill.currency != currency) continue
            val owed = (bill.amountDueMinor - (bill.paidAmountMinor ?: 0L)).coerceAtLeast(0L)
            if (owed > 0L) {
                commitments += Commitment("${bill.biller} bill", owed, due, CommitmentKind.BILL)
                countedBillWords += words(bill.biller)
            }
        }
        for (s in src.series) {
            if (!countsAsCommitment(s, currency)) continue
            if (words(s.displayName).any { it in countedBillWords }) continue
            val n = SafeToSpend.occurrencesBefore(s.nextAt!!, s.intervalDays!!, cycle.untilMillis)
            if (n > 0) {
                val label = if (n == 1) s.displayName else "${s.displayName} ×$n"
                commitments += Commitment(label, s.typicalAmountMinor * n, s.nextAt, CommitmentKind.RECURRING)
            }
        }
        val byGoal = src.contributions.groupBy { it.goalId }
        for (g in src.goals) {
            if (g.isArchived || g.linkedAccountId != null || g.targetDate == null || g.currency != currency) continue
            val list = byGoal[g.id].orEmpty()
            val progress = GoalMath.progress(
                targetMinor = g.targetMinor,
                savedMinor = list.sumOf { it.amountMinor },
                savedBeforeCycleMinor = list.filter { it.at < cycle.fromMillis }.sumOf { it.amountMinor },
                targetDate = g.targetDate,
                now = now,
                monthStartDay = day,
            )
            val toSave = progress.toSaveThisCycleMinor ?: 0L
            if (toSave > 0L) commitments += Commitment("Goal: ${g.name}", toSave, g.targetDate, CommitmentKind.GOAL)
        }

        val safe = SafeToSpend.compute(
            now = now,
            cycle = cycle,
            userLimitMinor = src.limitMinor,
            completedCycles = completed,
            spentMinor = spentIn(cycle),
            commitments = commitments.sortedBy { it.dueAt ?: Long.MAX_VALUE },
        )

        val windowDays = earliest?.let { ((now - it) / DAY_MS).toInt().coerceIn(1, Runway.WINDOW_DAYS) } ?: Runway.WINDOW_DAYS
        val windowFrom = now - windowDays * DAY_MS
        val runway = Runway.compute(
            now = now,
            accounts = src.accounts.filter { !it.isHidden }.map { a ->
                AccountBalance(a.displayName, a.balanceMinor?.takeIf { a.currency == currency })
            },
            spendInWindowMinor = spend.filter { it.timestamp in windowFrom..now }.sumOf { it.amountMinor },
            windowDays = windowDays,
        )
        return PlanningSnapshot(now, currency, cycle, src.limitMinor, safe, runway)
    }

    private fun countsAsCommitment(s: RecurringSeriesEntity, currency: String): Boolean =
        s.flowId == TransactionFlow.EXPENSE.id && s.currency == currency && s.isDetected &&
            s.userState != RecurringSeriesEntity.DISMISSED && s.nextAt != null && s.intervalDays != null &&
            (s.status == RecurringStatus.ACTIVE.name || s.status == RecurringStatus.DUE_SOON.name) &&
            (s.userState == RecurringSeriesEntity.CONFIRMED || (s.isFixed && s.confidence >= HIGH_CONFIDENCE))

    private fun words(text: String): Set<String> =
        text.lowercase().split(Regex("""[^a-z]+""")).filter { it.length >= 3 && it !in genericWords }.toSet()

    companion object {
        const val HIGH_CONFIDENCE = 0.7
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private val genericWords = setOf("bill", "bank", "plc", "ltd", "the", "and", "pvt", "limited")
    }
}
