package lk.salli.domain.planning

/** An account's last known balance; null when its SMS never carry one. */
data class AccountBalance(val name: String, val balanceMinor: Long?)

data class RunwayResult(
    val balanceMinor: Long,
    val countedAccounts: List<String>,
    /** Accounts left out because Salli has never seen their balance. */
    val notCountedAccounts: List<String>,
    val avgDailySpendMinor: Long,
    val windowDays: Int,
    /** Whole days the counted balance lasts at the average spend; null when there is no spend. */
    val days: Int?,
    val untilMillis: Long?,
)

/** "At my usual spending, how long does the money I can see last?" */
object Runway {

    const val WINDOW_DAYS = 90
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun compute(now: Long, accounts: List<AccountBalance>, spendInWindowMinor: Long, windowDays: Int): RunwayResult {
        val counted = accounts.filter { it.balanceMinor != null }
        val balance = counted.sumOf { it.balanceMinor!! }
        val avg = if (windowDays > 0) spendInWindowMinor / windowDays else 0L
        val days = if (avg <= 0L) null else (balance.coerceAtLeast(0L) / avg).toInt()
        return RunwayResult(
            balanceMinor = balance,
            countedAccounts = counted.map { it.name },
            notCountedAccounts = accounts.filter { it.balanceMinor == null }.map { it.name },
            avgDailySpendMinor = avg,
            windowDays = windowDays,
            days = days,
            untilMillis = days?.let { now + it * DAY_MS },
        )
    }
}
