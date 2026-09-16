package lk.salli.app.sms

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, manifest = Config.NONE, sdk = [34])
class SmsInboxReaderTest {

    @Test
    fun `null provider cursor is an error rather than an empty successful import`() {
        ShadowContentResolver.registerProviderInternal("sms", InboxProvider(null))

        val error = assertThrows(IllegalStateException::class.java) {
            SmsInboxReader(ApplicationProvider.getApplicationContext()).readInbox()
        }

        assertThat(error).hasMessageThat().contains("did not return an inbox cursor")
    }

    @Test
    fun `real empty cursor is returned as an empty inbox`() {
        val cursor = MatrixCursor(
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
        )
        ShadowContentResolver.registerProviderInternal("sms", InboxProvider(cursor))

        val messages = SmsInboxReader(ApplicationProvider.getApplicationContext()).readInbox()

        assertThat(messages).isEmpty()
    }

    @Test
    fun `reader preserves provider rows and forwards the since filter`() {
        val cursor = MatrixCursor(
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
        ).apply {
            addRow(arrayOf("COMBANK", "Paid Rs 100", 1234L))
        }
        val provider = InboxProvider(cursor)
        ShadowContentResolver.registerProviderInternal("sms", provider)

        val messages = SmsInboxReader(ApplicationProvider.getApplicationContext()).readInbox(1000L)

        assertThat(messages).containsExactly(
            SmsInboxReader.RawSms("COMBANK", "Paid Rs 100", 1234L),
        )
        assertThat(provider.lastSelection).isEqualTo("${Telephony.Sms.DATE} >= ?")
        assertThat(provider.lastSelectionArgs?.toList()).containsExactly("1000")
    }

    private class InboxProvider(private val result: Cursor?) : ContentProvider() {
        var lastSelection: String? = null
        var lastSelectionArgs: Array<out String>? = null

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? {
            lastSelection = selection
            lastSelectionArgs = selectionArgs
            return result
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}
