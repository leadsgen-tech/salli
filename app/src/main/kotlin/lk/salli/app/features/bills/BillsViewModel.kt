package lk.salli.app.features.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.BillEntity
import lk.salli.data.prefs.SalliPreferences
import lk.salli.domain.Money
import lk.salli.parser.utility.BillKind

data class BillRow(
    val id: Long,
    val biller: String,
    val accountRef: String,
    val amount: Money,
    val dueDate: Long?,
    /** Negative when overdue, null when the biller gave no due date. */
    val daysLeft: Int?,
    val periodLabel: String?,
    val isOverdue: Boolean,
    val paidAt: Long?,
    val paidAmount: Money?,
    /** Paid toward an open bill so far; null until a part-payment arrives. */
    val paidSoFar: Money? = null,
    /** Part-paid, then rolled into a newer statement. */
    val carriedForward: Boolean = false,
    /** The statement showed nothing owed (a zero or credit balance). */
    val inCredit: Boolean = false,
)

data class BillsUiState(
    val open: List<BillRow> = emptyList(),
    val paid: List<BillRow> = emptyList(),
    val reminderDays: Int = SalliPreferences.DEFAULT_BILL_REMINDER_DAYS,
    val loading: Boolean = true,
)

@HiltViewModel
class BillsViewModel @Inject constructor(
    private val db: SalliDatabase,
    private val prefs: SalliPreferences,
) : ViewModel() {

    private val colombo = ZoneId.of("Asia/Colombo")

    val state: StateFlow<BillsUiState> = combine(
        db.bills().observeOpenBills(),
        db.bills().observePaidBills(),
        prefs.billReminderDays,
    ) { open, paid, days ->
        val today = LocalDate.now(colombo)
        BillsUiState(
            open = open.map { it.toRow(today) },
            paid = paid.map { it.toRow(today) },
            reminderDays = days,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillsUiState())

    fun markPaid(id: Long) {
        viewModelScope.launch {
            val bill = db.bills().byId(id) ?: return@launch
            db.bills().markPaidManually(id, paidAt = System.currentTimeMillis(), paidAmountMinor = bill.amountDueMinor)
        }
    }

    fun setReminderDays(days: Int) {
        viewModelScope.launch { prefs.setBillReminderDays(days) }
    }

    private fun BillEntity.toRow(today: LocalDate): BillRow {
        val due = dueDate?.let { Instant.ofEpochMilli(it).atZone(colombo).toLocalDate() }
        val daysLeft = due?.let { ChronoUnit.DAYS.between(today, it).toInt() }
        val paid = paidAmountMinor ?: 0L
        val open = !isPaid && !isSuperseded
        return BillRow(
            id = id,
            biller = biller,
            accountRef = accountRef,
            // Open bills show what is still owed; history rows show the statement total.
            amount = Money(if (open) (amountDueMinor - paid).coerceAtLeast(0L) else kotlin.math.abs(amountDueMinor), currency),
            dueDate = dueDate,
            daysLeft = daysLeft,
            periodLabel = periodLabel,
            isOverdue = open && (kindId == BillKind.OVERDUE.id || (daysLeft != null && daysLeft < 0)),
            paidAt = if (isPaid) paidAt else null,
            paidAmount = paidAmountMinor?.let { Money(it, currency) },
            paidSoFar = if (open && paid > 0L) Money(paid, currency) else null,
            carriedForward = isSuperseded && paid > 0L,
            inCredit = isPaid && amountDueMinor <= 0L,
        )
    }
}
