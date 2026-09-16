package lk.salli.app.summaries

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import lk.salli.data.summary.SummaryService

/**
 * Runs about hourly. [SummaryService] decides what is due (the chosen hour has passed and
 * that period has not been posted yet); this worker only posts and marks. Cheap when nothing
 * is due, so the hourly cadence costs almost nothing.
 */
@HiltWorker
class SummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val service: SummaryService,
    private val notifier: SummaryNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        for (due in service.due()) {
            notifier.post(due)
            service.markPosted(due)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "salli.summaries"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SummaryWorker>(1, TimeUnit.HOURS)
                .addTag(UNIQUE_NAME)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
