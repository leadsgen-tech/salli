package lk.salli.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Debug-only shortcut into the ingest pipeline. Accepts a fake SMS payload via `adb shell`:
 *
 *   adb shell am broadcast \
 *     -a lk.salli.app.DEBUG_INJECT_SMS \
 *     -n lk.salli.app/.sms.DebugSmsInjectReceiver \
 *     --es sender "BOC" \
 *     --es body "Online Transfer Debit Rs 12500.00 From A/C No XXXXXXXXXX870. Balance available Rs 916.60 - Thank you for banking with BOC"
 *
 * Hands off to the same [SmsIngestWorker] a real [SmsReceiver] would — so a successful inject
 * proves the whole SMS → parse → persist → UI path works end-to-end, without having to wait
 * for a real bank alert.
 *
 * Registered in `app/src/debug/AndroidManifest.xml` so it never ships in release.
 */
class DebugSmsInjectReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val sender = intent.getStringExtra(EXTRA_SENDER)?.trim().orEmpty()
        val body = intent.getStringExtra(EXTRA_BODY)?.trim().orEmpty()
        if (sender.isEmpty() || body.isEmpty()) {
            android.util.Log.w(TAG, "missing sender or body — skipping inject")
            return
        }
        val receivedAt = System.currentTimeMillis()

        android.util.Log.i(TAG, "inject: sender=$sender, body len=${body.length}")
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<SmsIngestWorker>()
                .setInputData(SmsIngestWorker.inputOf(sender, body, receivedAt))
                .addTag(TAG)
                .build(),
        )
    }

    companion object {
        const val ACTION = "lk.salli.app.DEBUG_INJECT_SMS"
        const val EXTRA_SENDER = "sender"
        const val EXTRA_BODY = "body"
        private const val TAG = "salli.sms.inject"
    }
}
