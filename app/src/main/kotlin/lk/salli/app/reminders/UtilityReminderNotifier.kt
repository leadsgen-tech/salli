package lk.salli.app.reminders

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
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import lk.salli.app.MainActivity
import lk.salli.app.R
import lk.salli.data.db.entities.BillEntity
import lk.salli.data.db.entities.FuelPassRecordEntity

/**
 * Posts bill-due and Fuel Pass reminders on their own `reminders` channel, separate from the
 * high-priority transaction prompts so the user can tune (or mute) them independently.
 * Notification ids live in their own ranges so they never collide with transaction ids.
 */
@Singleton
class UtilityReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun billDueSoon(bill: BillEntity, daysLeft: Int) {
        val amount = owed(bill)
        val dayWord = if (daysLeft == 1) "tomorrow" else "in $daysLeft days"
        post(
            id = BILL_ID_BASE + bill.id.toInt(),
            title = "${bill.biller} bill due $dayWord",
            text = "$amount for ${bill.accountRef}" + (bill.dueDate?.let { " · due ${date(it)}" } ?: ""),
        )
    }

    /** [daysLeft] is 0 on the due day and negative once it has passed. */
    fun billDueToday(bill: BillEntity, daysLeft: Int = 0) {
        post(
            id = BILL_ID_BASE + bill.id.toInt(),
            title = if (daysLeft < 0) "${bill.biller} bill overdue" else "${bill.biller} bill due today",
            text = "${owed(bill)} for ${bill.accountRef}" +
                (if (daysLeft < 0) bill.dueDate?.let { " · was due ${date(it)}" } ?: "" else ""),
        )
    }

    /** What is still owed: part-payments already recorded are subtracted. */
    private fun owed(bill: BillEntity): String =
        money((bill.amountDueMinor - (bill.paidAmountMinor ?: 0L)).coerceAtLeast(0L), bill.currency)

    fun fuelLastDay(record: FuelPassRecordEntity, resetsOn: LocalDate) {
        val litres = "%.1f".format(Locale.US, record.weeklyBalanceMilli / 1000.0)
        post(
            id = FUEL_ID_BASE + record.id.toInt(),
            title = "${record.vehicle}: last day to use $litres L",
            text = "Your Fuel Pass quota resets ${resetsOn.format(dayFmt)}. Today is your eligible day.",
        )
    }

    fun recurringDueTomorrow(series: lk.salli.data.db.entities.RecurringSeriesEntity) {
        post(
            id = 4_000_000 + series.id.toInt(),
            title = "${series.displayName} due tomorrow",
            text = "Usually ${money(series.typicalAmountMinor, series.currency)}" +
                (series.nextAt?.let { " · expected ${date(it)}" } ?: ""),
        )
    }

    fun recurringFailing(series: lk.salli.data.db.entities.RecurringSeriesEntity) {
        val times = if (series.declinedAttempts == 1) "once" else "${series.declinedAttempts} times"
        post(
            id = 4_500_000 + series.id.toInt(),
            title = "${series.displayName} keeps getting declined",
            text = "Your bank refused it $times. Update the card on that service, or cancel it.",
        )
    }

    private fun post(id: Int, title: String, text: String) {
        if (!canPost()) return
        ensureChannel()
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
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** False on Android 13+ until the user grants notifications; callers must not mark anything sent. */
    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Bill due dates and Fuel Pass quota reminders."
            },
        )
    }

    private fun money(minor: Long, currency: String): String {
        val symbol = if (currency == "LKR") "Rs " else "$currency "
        val abs = kotlin.math.abs(minor)
        return "$symbol${NumberFormat.getIntegerInstance(Locale.US).format(abs / 100)}.${"%02d".format(abs % 100)}"
    }

    private fun date(millis: Long): String =
        java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.of("Asia/Colombo")).toLocalDate().format(dayFmt)

    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    companion object {
        const val CHANNEL_ID = "reminders"
        private const val BILL_ID_BASE = 2_000_000
        private const val FUEL_ID_BASE = 3_000_000
    }
}
