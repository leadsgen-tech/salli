package lk.salli.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
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
            workManager.enqueue(
                OneTimeWorkRequestBuilder<SmsIngestWorker>()
                    .setInputData(SmsIngestWorker.inputOf(sender, body, receivedAt))
                    .addTag(TAG)
                    .build(),
            )
        }
    }

    companion object {
        const val TAG = "salli.sms.ingest"
    }
}
