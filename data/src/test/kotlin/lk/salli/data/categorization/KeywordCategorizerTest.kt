package lk.salli.data.categorization

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.CategoryEntity
import lk.salli.data.db.entities.KeywordEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeywordCategorizerTest {

    private lateinit var db: SalliDatabase
    private lateinit var categorizer: KeywordCategorizer
    private var groceriesId: Long = 0
    private var subscriptionsId: Long = 0

    @Before
    fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SalliDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        categorizer = KeywordCategorizer(db.keywords())

        db.categories().insertAll(
            listOf(
                CategoryEntity(name = "Groceries", iconName = "x", colorSeed = 0),
                CategoryEntity(name = "Online Subscriptions", iconName = "x", colorSeed = 0),
            ),
        )
        val cats = db.categories().all()
        groceriesId = cats.first { it.name == "Groceries" }.id
        subscriptionsId = cats.first { it.name == "Online Subscriptions" }.id

        categorizer.seed(
            listOf(
                KeywordEntity(keyword = "keells", categoryId = groceriesId, source = "seed"),
                KeywordEntity(keyword = "apple.com", categoryId = subscriptionsId, source = "seed"),
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `matches embedded keyword inside a longer merchant string`() = runBlocking {
        val hit = categorizer.categorize("KEELLS SUPER COLOMBO 04")
        assertThat(hit).isNotNull()
        assertThat(hit!!.categoryId).isEqualTo(groceriesId)
    }

    @Test
    fun `matches with different casing`() = runBlocking {
        val hit = categorizer.categorize("apple.com/bill singapore sg")
        assertThat(hit).isNotNull()
        assertThat(hit!!.categoryId).isEqualTo(subscriptionsId)
    }

    @Test
    fun `returns null when nothing matches`() = runBlocking {
        assertThat(categorizer.categorize("RANDOM MERCHANT XYZ")).isNull()
    }

    @Test
    fun `null or blank input is null`() = runBlocking {
        assertThat(categorizer.categorize(null)).isNull()
        assertThat(categorizer.categorize("   ")).isNull()
    }
}
