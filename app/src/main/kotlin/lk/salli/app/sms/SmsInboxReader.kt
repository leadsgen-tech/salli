package lk.salli.app.sms

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import lk.salli.parser.Templates
import lk.salli.parser.util.SenderId

/**
 * Pages through `content://sms/inbox` returning raw SMS tuples. Used by [HistoricalImporter]
 * during first-run catch-up.
 */
@Singleton
class SmsInboxReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class RawSms(val address: String, val body: String, val dateMillis: Long)

    /**
     * Counts the inbox and names the banks in it without reading a single message body.
     *
     * Onboarding act 3 opens on this ("2,096 messages from 4 banks since March 2025") before the
     * first message is parsed, so it must be fast: the projection is address + date only, which
     * keeps the provider from paging body text across the Binder boundary.
     *
     * [InboxSummary.totalMessages] counts exactly what [readInbox] would hand the importer, so it
     * lines up with `HistoricalImporter.Progress.total` and the "reading 1,204 of 2,096" line
     * can never drift.
     */
    fun summarize(sinceMillis: Long? = null): InboxSummary {
        val resolver: ContentResolver = context.contentResolver
        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE)
        val selection = sinceMillis?.let { "${Telephony.Sms.DATE} >= ?" }
        val args = sinceMillis?.let { arrayOf(it.toString()) }

        var total = 0
        var earliest: Long? = null
        val banks = sortedSetOf<String>()
        // Senders repeat heavily across an inbox; decide "is this a bank?" once per distinct ID.
        val verdicts = HashMap<String, Boolean>()

        val cursor = resolver.query(uri, projection, selection, args, "${Telephony.Sms.DATE} DESC")
            ?: error("The SMS provider did not return an inbox cursor")
        cursor.use { c ->
            val addressIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                // Matches readInbox: a row without a sender is not a message we could ever parse.
                val address = c.getString(addressIdx) ?: continue
                total++
                val date = c.getLong(dateIdx)
                val oldest = earliest
                if (oldest == null || date < oldest) earliest = date
                val id = SenderId.normalize(address)
                if (id.isEmpty()) continue
                if (verdicts.getOrPut(id) { isBank(id) }) banks += id
            }
        }
        return InboxSummary(
            totalMessages = total,
            senders = banks.toList(),
            earliestMillis = earliest,
        )
    }

    private fun isBank(senderId: String): Boolean =
        Templates.forSender(senderId).isNotEmpty() || Templates.isKnownBankSender(senderId)

    /**
     * Returns all SMS received since [sinceMillis] (inclusive), newest first. Null [sinceMillis]
     * returns everything in the inbox.
     */
    fun readInbox(sinceMillis: Long? = null): List<RawSms> {
        val resolver: ContentResolver = context.contentResolver
        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        val selection = sinceMillis?.let { "${Telephony.Sms.DATE} >= ?" }
        val args = sinceMillis?.let { arrayOf(it.toString()) }
        val out = mutableListOf<RawSms>()

        val cursor = resolver.query(uri, projection, selection, args, "${Telephony.Sms.DATE} DESC")
            ?: error("The SMS provider did not return an inbox cursor")
        cursor.use { c ->
            val addressIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val address = c.getString(addressIdx) ?: continue
                val body = c.getString(bodyIdx) ?: ""
                val date = c.getLong(dateIdx)
                out += RawSms(address = address, body = body, dateMillis = date)
            }
        }
        return out
    }
}
