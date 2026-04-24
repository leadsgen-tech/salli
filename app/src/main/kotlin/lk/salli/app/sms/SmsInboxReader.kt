package lk.salli.app.sms

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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

        resolver.query(uri, projection, selection, args, "${Telephony.Sms.DATE} DESC")?.use { c ->
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
