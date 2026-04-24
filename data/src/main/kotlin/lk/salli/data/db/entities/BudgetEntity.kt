package lk.salli.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One user-defined budget — "Monthly Essentials", "Vacation", "Burner card only", etc.
 *
 * Two shapes share this table:
 *  - [capModeId] = 0 → **per-category**: caps live in [BudgetLineEntity] rows, one per category.
 *  - [capModeId] = 1 → **total**: [totalCapMinor] is a single lump cap across all expense.
 *
 * Scope is narrowed further by [BudgetAccountEntity] junction rows — when none exist the budget
 * is "all accounts"; when some exist only transactions from those accounts count.
 *
 * Period: [periodStartDay] lets the user say "my month runs 25→24" (default 1 = calendar month).
 */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    /** 0 = monthly, 1 = weekly, 2 = custom. Mirrors [lk.salli.domain.BudgetPeriod]. */
    @ColumnInfo(name = "period_type_id")
    val periodTypeId: Int = 0,

    @ColumnInfo(name = "currency")
    val currency: String,

    @ColumnInfo(name = "start_date")
    val startDate: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /** 0 = per-category caps, 1 = single total cap. */
    @ColumnInfo(name = "cap_mode_id", defaultValue = "0")
    val capModeId: Int = 0,

    /** Only meaningful when [capModeId] = 1. Minor units (cents). */
    @ColumnInfo(name = "total_cap_minor", defaultValue = "NULL")
    val totalCapMinor: Long? = null,

    /**
     * Day of month the budget period resets on (1–28; clamped at 28 to avoid Feb edge cases).
     * 1 = calendar month.
     */
    @ColumnInfo(name = "period_start_day", defaultValue = "1")
    val periodStartDay: Int = 1,
)

/**
 * A single category's cap inside a [BudgetEntity].
 *
 * `amount_minor` is stored in minor units (cents) as a Long — keeps us consistent with how
 * `TransactionEntity.amountMinor` is stored, avoiding float drift when computing spent/remaining.
 */
@Entity(
    tableName = "budget_lines",
    foreignKeys = [
        ForeignKey(
            entity = BudgetEntity::class,
            parentColumns = ["id"],
            childColumns = ["budget_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("budget_id"), Index("category_id")],
)
data class BudgetLineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "budget_id")
    val budgetId: Long,

    @ColumnInfo(name = "category_id")
    val categoryId: Long,

    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
)

/**
 * Junction row linking a budget to one scoped account. Absence of rows for a given
 * `budget_id` means "all accounts" — keeps the common case zero-cost and avoids having to
 * backfill rows when a new account is discovered from a future SMS.
 */
@Entity(
    tableName = "budget_accounts",
    foreignKeys = [
        ForeignKey(
            entity = BudgetEntity::class,
            parentColumns = ["id"],
            childColumns = ["budget_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["budget_id", "account_id"], unique = true),
        Index("account_id"),
    ],
)
data class BudgetAccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "budget_id")
    val budgetId: Long,

    @ColumnInfo(name = "account_id")
    val accountId: Long,
)
