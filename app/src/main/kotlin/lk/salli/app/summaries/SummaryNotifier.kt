package lk.salli.app.summaries

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import lk.salli.app.MainActivity
import lk.salli.app.R
import lk.salli.data.summary.SummaryService

/** Posts scheduled spending summaries on their own `summaries` channel (mutable in system settings). */
@Singleton
class SummaryNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun post(due: SummaryService.Due) {
        if (!canPost()) return
        ensureChannel()
        val id = ID_BASE + due.cadence.ordinal
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(due.summary.title)
            .setContentText(due.summary.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(due.summary.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Spending summaries", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Daily, weekly and monthly spending recaps you opted into."
            },
        )
    }

    companion object {
        const val CHANNEL_ID = "summaries"
        private const val ID_BASE = 4_000_000
    }
}
