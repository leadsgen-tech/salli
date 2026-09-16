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

    // ---------------------------------------------------------------- summarize

    /** Newest first, as the provider returns it. */
    private fun summaryCursor(vararg rows: Pair<String?, Long>) = MatrixCursor(
        arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE),
    ).apply { rows.forEach { (address, date) -> addRow(arrayOf(address, date)) } }

    @Test
    fun `summary counts the inbox, names only banks and finds the oldest message`() {
        val provider = InboxProvider(
            summaryCursor(
                "COMBANK" to 5_000L,
                "BOC" to 4_000L,
                "COMBANK" to 3_000L,
                "DIALOG" to 2_000L, // a promo: counted, never named
                "HSBC" to 1_000L, // known bank, no template yet
            ),
        )
        ShadowContentResolver.registerProviderInternal("sms", provider)

        val summary = SmsInboxReader(ApplicationProvider.getApplicationContext()).summarize()

        assertThat(summary.totalMessages).isEqualTo(5)
        assertThat(summary.senders).containsExactly("BOC", "COMBANK", "HSBC").inOrder()
        assertThat(summary.earliestMillis).isEqualTo(1_000L)
    }

    @Test
    fun `summary never asks the provider for message bodies`() {
        val provider = InboxProvider(summaryCursor("COMBANK" to 1L))
        ShadowContentResolver.registerProviderInternal("sms", provider)

        SmsInboxReader(ApplicationProvider.getApplicationContext()).summarize()

        assertThat(provider.lastProjection?.toList())
            .containsExactly(Telephony.Sms.ADDRESS, Telephony.Sms.DATE)
    }

    @Test
    fun `summary forwards the since filter`() {
        val provider = InboxProvider(summaryCursor("COMBANK" to 1234L))
        ShadowContentResolver.registerProviderInternal("sms", provider)

        SmsInboxReader(ApplicationProvider.getApplicationContext()).summarize(1000L)

        assertThat(provider.lastSelection).isEqualTo("${Telephony.Sms.DATE} >= ?")
        assertThat(provider.lastSelectionArgs?.toList()).containsExactly("1000")
    }

    @Test
    fun `a dirty sender id is normalised before it is named`() {
        // A real Pixel returns ComBank's alerts with a trailing line break.
        ShadowContentResolver.registerProviderInternal(
            "sms",
            InboxProvider(summaryCursor("COMBANK\n" to 2L, "​BOC " to 1L)),
        )

        val summary = SmsInboxReader(ApplicationProvider.getApplicationContext()).summarize()

        assertThat(summary.senders).containsExactly("BOC", "COMBANK").inOrder()
    }

    @Test
    fun `rows without a sender are skipped so the count matches what the importer reads`() {
        ShadowContentResolver.registerProviderInternal(
            "sms",
            InboxProvider(summaryCursor("COMBANK" to 3L, null to 2L, "DIALOG" to 1L)),
        )

        val summary = SmsInboxReader(ApplicationProvider.getApplicationContext()).summarize()

        assertThat(summary.totalMessages).isEqualTo(2)
        assertThat(summary.earliestMillis).isEqualTo(1L)
    }

    @Test
    fun `an empty inbox summarises to nothing at all`() {
        ShadowContentResolver.registerProviderInternal("sms", InboxProvider(summaryCursor()))

        val summary = SmsInboxReader(ApplicationProvider.getApplicationContext()).summarize()

        assertThat(summary.totalMessages).isEqualTo(0)
        assertThat(summary.senders).isEmpty()
        assertThat(summary.earliestMillis).isNull()
    }

    @Test
    fun `a null summary cursor is an error rather than a believable empty inbox`() {
        ShadowContentResolver.registerProviderInternal("sms", InboxProvider(null))

        val error = assertThrows(IllegalStateException::class.java) {
            SmsInboxReader(ApplicationProvider.getApplicationContext()).summarize()
        }

        assertThat(error).hasMessageThat().contains("did not return an inbox cursor")
    }

    private class InboxProvider(private val result: Cursor?) : ContentProvider() {
        var lastSelection: String? = null
        var lastSelectionArgs: Array<out String>? = null
        var lastProjection: Array<out String>? = null

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
            lastProjection = projection
            return result
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}
