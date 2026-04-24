package lk.salli.domain

data class Budget(
    val id: Long? = null,
    val name: String,
    val amount: Money,
    val periodType: BudgetPeriod,
    /** For custom or payday-relative periods. Unused for calendar-monthly. */
    val startDate: Long? = null,
)

data class BudgetLine(
    val id: Long? = null,
    val budgetId: Long,
    val categoryId: Long,
    val amount: Money,
)

enum class BudgetPeriod(val id: Int) {
    MONTHLY(0),
    WEEKLY(1),
    CUSTOM(2);

    companion object {
        fun fromId(id: Int): BudgetPeriod = entries.first { it.id == id }
    }
}
