package lk.salli.data.ingest

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import lk.salli.data.categorization.KeywordCategorizer
import lk.salli.data.categorization.TypeCategorizer
import lk.salli.data.db.SalliDatabase
import lk.salli.data.seed.Seeder
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SenderNormalisationTest {

    private lateinit var db: SalliDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SalliDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `sender IDs with a trailing line break are stored under the clean ID`(): Unit = runBlocking {
        Seeder(db).run()
        val ingestor = TransactionIngestor(
            db, KeywordCategorizer(db.keywords()), TypeCategorizer(db.categories()),
            utilityIngestor = UtilityIngestor(db),
        )
        val purchase = "Dear Cardholder, Purchase at ThePapare Colombo 02 LK for LKR 278.00 on 16/02/26 09:27 PM has been authorised on your debit card ending #4273."
        assertThat(ingestor.ingest("COMBANK\n", purchase, 1_000L)).isInstanceOf(IngestResult.Inserted::class.java)
        assertThat(db.accounts().all().single().senderAddress).isEqualTo("COMBANK")

        val fuel = "National Fuel Pass: TRN confirmed.\n2026-09-08 06:02:23 ABC-1234\nQuota used: 8L\nWeekly Balance: 0.000L\nStation code: 102316\n(Resets on - 2026-09-13)"
        assertThat(ingestor.ingest("1919\n", fuel, 2_000L)).isInstanceOf(IngestResult.Utility::class.java)
    }
}
