package lk.salli.app.reminders

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.RecurringSeriesEntity
import lk.salli.data.prefs.SalliPreferences
import lk.salli.domain.FuelPassRules
import lk.salli.domain.recurring.RecurringStatus

/**
 * Runs every six hours and posts, between 08:00 and 21:00 only: at most once per bill a "due in
 * N days" notice (N from Settings) and a "due today" or "overdue" notice; at most once per Fuel
 * Pass week a "last eligible day to use what's left" notice; once per expected charge a "due
 * tomorrow" notice for repeating payments the user confirmed; and once per failing streak a
 * "keeps getting declined" notice. Local data only, no network.
 *
 * Why six hours and `<=` instead of one daily run and `==`: WorkManager can push a periodic run
 * well past its nominal time, and a run that skipped the due day entirely used to mean the "due
 * today" notice never came. Several chances a day plus once-only stages make the outcome
 * independent of exactly when the job runs.
 */
@HiltWorker
class UtilityReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: SalliDatabase,
    private val prefs: SalliPreferences,
    private val notifier: UtilityReminderNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = LocalDateTime.now(colombo)
        // Nothing posts overnight; the next run inside the window catches up.
        if (now.hour < POST_FROM_HOUR || now.hour >= POST_UNTIL_HOUR) return Result.success()
        // Without notification permission nothing can be shown, so nothing may be marked as
        // sent either; otherwise granting permission later would never bring the notices back.
        if (!notifier.canPost()) return Result.success()
        val today = now.toLocalDate()
        val leadDays = prefs.billReminderDays.first()

        for (bill in db.bills().openBills()) {
            val dueMillis = bill.dueDate ?: continue
            val due = Instant.ofEpochMilli(dueMillis).atZone(colombo).toLocalDate()
            val daysLeft = ChronoUnit.DAYS.between(today, due).toInt()
            when {
                daysLeft <= 0 && bill.reminderStage < 2 -> {
                    // A bill long past due that was never reminded (imported history) gets no
                    // late ping; the Bills screen already shows it as overdue.
                    if (daysLeft >= -MAX_LATE_DAYS) notifier.billDueToday(bill, daysLeft)
                    db.bills().setReminderStage(bill.id, 2)
                }
                daysLeft in 1..leadDays && bill.reminderStage < 1 -> {
                    notifier.billDueSoon(bill, daysLeft)
                    db.bills().setReminderStage(bill.id, 1)
                }
            }
        }

        for (record in db.fuelPass().latestPerVehicle()) {
            if (record.reminderSent || record.weeklyBalanceMilli <= 0L) continue
            val resets = record.resetsOn?.let { Instant.ofEpochMilli(it).atZone(colombo).toLocalDate() } ?: continue
            if (!today.isBefore(resets)) continue
            if (FuelPassRules.lastEligibleDayBefore(record.vehicle, resets) == today) {
                notifier.fuelLastDay(record, resets)
                db.fuelPass().markReminderSent(record.id)
            }
        }
        // Repeating payments. A failing subscription is announced once until it recovers; a
        // confirmed charge due tomorrow is announced once per expected charge.
        val tomorrow = today.plusDays(1)
        for (series in db.recurring().all()) {
            if (series.userState == RecurringSeriesEntity.DISMISSED || !series.isDetected) continue
            if (series.status == RecurringStatus.FAILING.name) {
                if (!series.failingNotified) {
                    notifier.recurringFailing(series)
                    db.recurring().setFailingNotified(series.id, true)
                }
                continue
            }
            val next = series.nextAt ?: continue
            if (series.userState != RecurringSeriesEntity.CONFIRMED || series.flowId != 0 || series.notifiedDueAt == next) continue
            if (Instant.ofEpochMilli(next).atZone(colombo).toLocalDate() == tomorrow) {
                notifier.recurringDueTomorrow(series)
                db.recurring().setNotifiedDueAt(series.id, next)
            }
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "salli.utility.reminders"
        private const val POST_FROM_HOUR = 8
        private const val POST_UNTIL_HOUR = 21
        private const val MAX_LATE_DAYS = 3
        private val colombo: ZoneId = ZoneId.of("Asia/Colombo")

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UtilityReminderWorker>(6, TimeUnit.HOURS)
                .addTag(UNIQUE_NAME)
                .build()
            // UPDATE rather than KEEP: installs that already enqueued the old 24-hour request
            // move to the new interval.
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
