package lk.salli.app.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import lk.salli.app.MainActivity
import lk.salli.app.R
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/**
 * Posts an inline-reply notification for fresh transfer/cheque transactions that arrived
 * without a merchant name — the SMS just says "you sent Rs X to account Y", so we prompt
 * the user to tag the payee/payer right from the notification shade.
 *
 * The reply is handled by [TransactionReplyReceiver]; successful replies write the text
 * back to `transactions.merchant_raw` and re-run keyword categorisation so the row lands
 * in the right bucket automatically.
 *
 * We only prompt for transactions that are:
 *   - fresh (reached the phone within the last few hours; bulk imports never prompt)
 *   - missing `merchantRaw`
 *   - a transfer / cheque / mobile-payment type (POS/ATM/CDM are self-explanatory)
 *   - not declined, not part of an internal-transfer group (we already know the pair)
 */
@Singleton
class TransactionPromptNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: SalliDatabase,
) {
    /** One place that maps an ingest outcome to notification side effects. */
    suspend fun handle(result: lk.salli.data.ingest.IngestResult, receivedAt: Long) {
        when (result) {
            is lk.salli.data.ingest.IngestResult.Inserted -> notifyIfNeeded(result.transactionId, receivedAt)
            // The counterpart may already carry a "Who did you pay?" prompt from when its leg
            // arrived. Both legs are now known to be the user's own accounts, so withdraw both.
            is lk.salli.data.ingest.IngestResult.Paired -> {
                cancelFor(result.transactionId)
                cancelFor(result.counterpartId)
            }
            is lk.salli.data.ingest.IngestResult.Merged -> refreshFor(result.transactionId)
            else -> Unit
        }
    }

    /** Withdraws the prompt for [transactionId], e.g. once its leg is paired as an own transfer. */
    fun cancelFor(transactionId: Long) {
        NotificationManagerCompat.from(context).cancel(transactionId.toInt())
    }

    /**
     * @param receivedAt when the SMS landed on the phone. Freshness is judged on the later of
     * this and the bank's own timestamp: bank clocks drift and WorkManager can hold the job
     * for minutes under Doze, and either used to push a real transfer past the old 10-minute
     * window so the prompt silently never fired.
     */
    suspend fun notifyIfNeeded(transactionId: Long, receivedAt: Long = System.currentTimeMillis()) {
        if (!canPostNotifications()) return
        val tx = db.transactions().byId(transactionId) ?: return
        if (!shouldPrompt(tx, receivedAt)) return
        val account = db.accounts().byId(tx.accountId)
        post(tx = tx, accountLabel = account?.displayName ?: tx.senderAddress.orEmpty())
    }

    /**
     * Re-evaluate an already-posted prompt after the row changed underneath it. A People's
     * Bank debit prompts "Who did you pay?" and seconds later the confirm SMS merges in with
     * the payee name — the question is answered, so the prompt is withdrawn.
     */
    suspend fun refreshFor(transactionId: Long) {
        val tx = db.transactions().byId(transactionId) ?: return cancelFor(transactionId)
        if (!tx.merchantRaw.isNullOrBlank() || tx.transferGroupId != null) cancelFor(transactionId)
    }

    private fun shouldPrompt(tx: TransactionEntity, receivedAt: Long): Boolean {
        if (!tx.merchantRaw.isNullOrBlank()) return false
        if (tx.isDeclined) return false
        if (tx.transferGroupId != null) return false
        val freshestKnown = maxOf(tx.timestamp, receivedAt)
        if (System.currentTimeMillis() - freshestKnown > FRESH_WINDOW_MS) return false
        val type = TransactionType.fromId(tx.typeId)
        return type in PROMPTABLE_TYPES
    }

    private fun post(tx: TransactionEntity, accountLabel: String) {
        ensureChannel()

        val flow = TransactionFlow.fromId(tx.flowId)
        val isDebit = flow == TransactionFlow.EXPENSE
        val amountStr = formatMoney(tx.amountMinor, tx.amountCurrency)
        val title =
            if (isDebit) "$amountStr debit from $accountLabel"
            else "$amountStr credit to $accountLabel"
        val question =
            if (isDebit) "Who did you pay? Reply to tag this transaction."
            else "Who paid you? Reply to tag this transaction."
        val replyLabel = if (isDebit) "Paid to…" else "Received from…"

        val remoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel(replyLabel)
            .build()

        // Each tx id gets its own PendingIntent request code so replies don't collide.
        val replyIntent = Intent(context, TransactionReplyReceiver::class.java).apply {
            action = TransactionReplyReceiver.ACTION_REPLY
            putExtra(TransactionReplyReceiver.EXTRA_TX_ID, tx.id)
            // Explicit component so implicit broadcasts on newer Android versions work.
            setPackage(context.packageName)
        }
        val pendingReply = PendingIntent.getBroadcast(
            context,
            tx.id.toInt(),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        // Tap-the-notification (without replying) opens the main app — lets the user edit
        // from the tx detail sheet if they prefer.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            context,
            tx.id.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val action = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_edit, "Tag", pendingReply,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(question)
            .setStyle(NotificationCompat.BigTextStyle().bigText(question))
            .setContentIntent(pendingOpen)
            .addAction(action)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_KEY)
            .build()

        val nm = NotificationManagerCompat.from(context)
        nm.notify(tx.id.toInt(), notification)
        postOrUpdateSummary(nm)
    }

    /**
     * Collapsible summary shown above the group. Android auto-groups from 4+ anyway but we
     * post one explicitly so the stack has a clear title ("Tag 3 transfers") and tapping
     * the group header opens the app.
     */
    private fun postOrUpdateSummary(nm: NotificationManagerCompat) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            context,
            SUMMARY_REQ_CODE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Untagged transactions")
            .setContentText("Tap to review and tag")
            .setContentIntent(pendingOpen)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        nm.notify(SUMMARY_ID, summary)
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Transaction prompts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Quick-reply notifications asking who a transfer was for."
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)
    }

    private fun formatMoney(minor: Long, currency: String): String {
        val symbol = when (currency) {
            "LKR" -> "Rs "
            "USD" -> "$"
            else -> "$currency "
        }
        val abs = kotlin.math.abs(minor)
        val major = abs / 100
        val cents = abs % 100
        val fmt = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US)
        return "$symbol${fmt.format(major)}.${"%02d".format(cents)}"
    }

    companion object {
        const val CHANNEL_ID = "tx_prompts"
        const val KEY_REPLY_TEXT = "merchant_input"
        private const val GROUP_KEY = "salli.tx_prompts"
        // Integer.MAX_VALUE keeps the summary out of the tx-id namespace so individual
        // cancels never collide with it.
        private const val SUMMARY_ID = Int.MAX_VALUE
        private const val SUMMARY_REQ_CODE = Int.MAX_VALUE - 1

        // Prompt only for SMS that reached the phone in the last few hours: long enough to
        // survive Doze deferrals and a pull-to-refresh catching up on a quiet morning, short
        // enough that the historical import never spams. The importer and full resync do not
        // call the notifier at all; only the live receiver and the 3-day refresh do.
        private const val FRESH_WINDOW_MS = 6L * 60 * 60 * 1000

        private val PROMPTABLE_TYPES = setOf(
            TransactionType.CEFT,
            TransactionType.ONLINE_TRANSFER,
            TransactionType.SLIPS,
            TransactionType.CHEQUE,
            TransactionType.MOBILE_PAYMENT,
        )
    }
}
