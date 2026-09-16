package lk.salli.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.concurrent.CancellationException

/**
 * Rolls the widget over to a new day: "spent today" back to zero and a fresh safe-to-spend.
 * Data changes refresh the widget straight away (see SalliApplication); this job covers the
 * midnight nobody transacts through.
 *
 * Periodic with a daily interval, but each schedule pins the next run to just after local
 * midnight with setNextScheduleTimeOverride. That override lasts one run, so the worker schedules
 * again every time it runs (UPDATE keeps the one unique job). No dependencies, so WorkManager's
 * default factory builds it.
 */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            SalliWidget.refresh(applicationContext)
            reschedule(applicationContext)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "salli.widget.day-rollover"
        private const val AFTER_MIDNIGHT_MS = 60_000L

        fun schedule(context: Context, now: Long = System.currentTimeMillis()) {
            enqueue(context, now, ExistingPeriodicWorkPolicy.KEEP)
        }

        private fun reschedule(context: Context, now: Long = System.currentTimeMillis()) {
            enqueue(context, now, ExistingPeriodicWorkPolicy.UPDATE)
        }

        private fun enqueue(context: Context, now: Long, policy: ExistingPeriodicWorkPolicy) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(1, TimeUnit.DAYS)
                .setNextScheduleTimeOverride(nextMidnight(now) + AFTER_MIDNIGHT_MS)
                .addTag(UNIQUE_NAME)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, policy, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        private fun nextMidnight(now: Long): Long {
            val zone = ZoneId.systemDefault()
            val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            return today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        }
    }
}
