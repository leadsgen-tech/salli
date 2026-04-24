package lk.salli.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import lk.salli.data.categorization.KeywordCategorizer
import lk.salli.data.categorization.TypeCategorizer
import lk.salli.data.db.SalliDatabase
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/**
 * Handles the inline reply from a transaction prompt notification. Writes the typed text
 * back to `transactions.merchant_raw`, re-runs the categoriser so the row re-homes into
 * the right bucket, and dismisses the notification.
 */
@AndroidEntryPoint
class TransactionReplyReceiver : BroadcastReceiver() {

    @Inject lateinit var db: SalliDatabase
    @Inject lateinit var keywordCat: KeywordCategorizer
    @Inject lateinit var typeCat: TypeCategorizer

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return
        val txId = intent.getLongExtra(EXTRA_TX_ID, -1L)
        if (txId < 0) return
        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val reply = remoteInput
            .getCharSequence(TransactionPromptNotifier.KEY_REPLY_TEXT)
            ?.toString()
            ?.trim()
        if (reply.isNullOrBlank()) return

        val pending = goAsync()
        SCOPE.launch {
            try {
                val tx = db.transactions().byId(txId) ?: return@launch
                // Re-run categorisation using the typed name as the new merchant — gives us
                // an instant keyword hit if it matches the seed table (e.g. "Keells").
                val kwHit = keywordCat.categorize(reply)
                val fallback = if (kwHit == null) {
                    typeCat.categorize(
                        TransactionType.fromId(tx.typeId),
                        TransactionFlow.fromId(tx.flowId),
                    )
                } else null
                val updated = tx.copy(
                    merchantRaw = reply,
                    categoryId = kwHit?.categoryId ?: fallback ?: tx.categoryId,
                    subCategoryId = kwHit?.subCategoryId ?: tx.subCategoryId,
                    userTagged = true,
                    updatedAt = System.currentTimeMillis(),
                )
                db.transactions().update(updated)
                NotificationManagerCompat.from(context).cancel(txId.toInt())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "lk.salli.app.TX_REPLY"
        const val EXTRA_TX_ID = "tx_id"

        // Receiver runs outside any coroutine scope — use a supervisor so one failure
        // doesn't poison the next reply.
        private val SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
