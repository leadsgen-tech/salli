package lk.salli.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.work.OutOfQuotaPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * BroadcastReceiver for incoming SMS (SMS_RECEIVED). Extracts all message parts for each
 * sender (multipart messages arrive as separate PDUs) and queues one [SmsIngestWorker] per
 * concatenated body. WorkManager delivery is durable across device reboots and OEM battery
 * optimisations, whereas doing the ingest inline in the receiver would risk the system killing
 * the process mid-parse.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // Group parts by sender address and concatenate their bodies in arrival order.
        val bySender = messages
            .filter { it.originatingAddress != null }
            .groupBy { it.originatingAddress!! }

        val workManager = WorkManager.getInstance(context)
        bySender.forEach { (sender, parts) ->
            val body = parts.joinToString(separator = "") { it.messageBody.orEmpty() }
            val receivedAt = parts.first().timestampMillis.takeIf { it > 0L }
                ?: System.currentTimeMillis()
            val request = OneTimeWorkRequestBuilder<SmsIngestWorker>()
                .setInputData(SmsIngestWorker.inputOf(sender, body, receivedAt))
                .addTag(TAG)
                .apply {
                    // Android 12+ runs expedited work immediately without a foreground
                    // service, so a transfer prompt lands while the user still remembers
                    // the payment instead of after Doze lets the job through. Older APIs
                    // would need FOREGROUND_SERVICE for this, which the app deliberately
                    // does not declare, so they keep the plain request.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    }
                }
                .build()
            workManager.enqueue(request)
        }
    }

    companion object {
        const val TAG = "salli.sms.ingest"
    }
}
