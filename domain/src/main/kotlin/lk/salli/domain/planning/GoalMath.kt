package lk.salli.domain.planning

import lk.salli.domain.DateRange

data class GoalProgress(
    val savedMinor: Long,
    val targetMinor: Long,
    val percent: Int,
    val remainingMinor: Long,
    /** Spending cycles left until the target date, this one included; null without a date. */
    val cyclesLeft: Int?,
    /** Even share per cycle needed to reach the target by the date; null without a date. */
    val perCycleMinor: Long?,
    /** What still needs saving in the current cycle after contributions already made in it. */
    val toSaveThisCycleMinor: Long?,
    val isComplete: Boolean,
    val isOverdue: Boolean,
)

/**
 * Savings goal arithmetic. Cycles follow the user's month-start day, so "save Rs X per period"
 * lines up with the spending period on every other screen.
 */
object GoalMath {

    /**
     * @param savedBeforeCycleMinor what was saved before the current cycle began. The per-cycle
     * share is computed from that, so a contribution made this cycle lowers "left to save this
     * cycle" instead of also shrinking the share itself.
     */
    fun progress(
        targetMinor: Long,
        savedMinor: Long,
        savedBeforeCycleMinor: Long,
        targetDate: Long?,
        now: Long,
        monthStartDay: Int,
    ): GoalProgress {
        val remaining = (targetMinor - savedMinor).coerceAtLeast(0L)
        val complete = targetMinor > 0L && savedMinor >= targetMinor
        val percent = if (targetMinor <= 0L) 0 else ((savedMinor * 100) / targetMinor).toInt().coerceIn(0, 100)
        if (targetDate == null) {
            return GoalProgress(savedMinor, targetMinor, percent, remaining, null, null, null, complete, isOverdue = false)
        }
        val overdue = !complete && targetDate < now
        val targetCycle = DateRange.cycleFor(targetDate, monthStartDay)
        val cyclesLeft = if (overdue) 1 else (DateRange.cycleMonthOffset(targetCycle, monthStartDay) { now } + 1).coerceAtLeast(1)
        val remainingAtCycleStart = (targetMinor - savedBeforeCycleMinor).coerceAtLeast(0L)
        val perCycle = (remainingAtCycleStart + cyclesLeft - 1) / cyclesLeft
        val savedThisCycle = (savedMinor - savedBeforeCycleMinor).coerceAtLeast(0L)
        val toSave = if (complete) 0L else (perCycle - savedThisCycle).coerceAtLeast(0L)
        return GoalProgress(savedMinor, targetMinor, percent, remaining, cyclesLeft, perCycle, toSave, complete, overdue)
    }
}
