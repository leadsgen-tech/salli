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
class RescanDedupeTest {

    private lateinit var db: SalliDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SalliDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `the same sms stamped days apart by receiver and re-scan is stored once`(): Unit = runBlocking {
        Seeder(db).run()
        val ingestor = TransactionIngestor(db, KeywordCategorizer(db.keywords()), TypeCategorizer(db.categories()))
        val body = "Online Transfer Credit Rs 1000.00 To A/C No XXXXXXXXXX870. Balance available Rs 5638.10 - Thank you for banking with BOC"
        assertThat(ingestor.ingest("BOC", body, 1_000L)).isInstanceOf(IngestResult.Inserted::class.java)
        val threeDaysLater = 1_000L + 3L * 24 * 60 * 60 * 1000
        assertThat(ingestor.ingest("BOC", body, threeDaysLater)).isInstanceOf(IngestResult.Duplicate::class.java)
        assertThat(db.transactions().allForRecategorise()).hasSize(1)
    }
}
