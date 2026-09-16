package lk.salli.data.seed

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.CategoryEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `categories.color_seed` changed meaning: it used to be a raw ARGB hue and is now an index
 * into the design module's 12-colour ramp. There is no schema change to hang a Room migration
 * on, so [Seeder] re-points existing system rows on startup — and the two things that matter
 * are that it actually converts an old install, and that it never touches a category the user
 * made or recoloured.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryPaletteRemapTest {

    private lateinit var db: SalliDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SalliDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `seed colour seeds are palette indices, not ARGB`() {
        SeedCategories.all.forEach { category ->
            assertThat(category.colorSeed).isAtLeast(0)
            assertThat(category.colorSeed).isLessThan(PALETTE_SIZE)
        }
    }

    @Test
    fun `every seed category is reachable from both lookup maps`() {
        SeedCategories.all.forEach { category ->
            assertThat(SeedCategories.paletteByName[category.name]).isEqualTo(category.colorSeed)
            assertThat(SeedCategories.iconByName[category.name]).isEqualTo(category.iconName)
        }
    }

    @Test
    fun `the eleven everyday spending categories each get their own hue`() {
        // Transfers, Cash, Fees and Other deliberately share the neutral slot, and Salary
        // reuses the income green; everything that shows up in a spend chart must be distinct
        // or adjacent bars become unreadable.
        val spendingHues = listOf(
            "Groceries", "Food & Dining", "Transport", "Fuel", "Utilities",
            "Online Subscriptions", "Shopping", "Healthcare", "Education",
            "Entertainment", "Rent",
        ).map { SeedCategories.paletteByName.getValue(it) }
        assertThat(spendingHues.toSet()).hasSize(spendingHues.size)
    }

    @Test
    fun `an upgrade re-points system categories that still carry old ARGB seeds`() = runBlocking {
        val legacy = SeedCategories.all.map { seed ->
            // What a pre-upgrade install looks like: right names, 2014 Material hues.
            seed.copy(colorSeed = 0xFF4CAF50.toInt())
        }
        db.categories().insertAll(legacy)

        Seeder(db).run()

        val byName = db.categories().all().associateBy { it.name }
        SeedCategories.all.forEach { seed ->
            assertThat(byName.getValue(seed.name).colorSeed).isEqualTo(seed.colorSeed)
            assertThat(byName.getValue(seed.name).iconName).isEqualTo(seed.iconName)
        }
    }

    @Test
    fun `a category the user created keeps its own colour and icon`() = runBlocking {
        db.categories().insertAll(
            listOf(
                CategoryEntity(
                    name = "Cricket",
                    iconName = "sports_cricket",
                    colorSeed = 0xFF00BCD4.toInt(),
                    isSystem = false,
                ),
            ),
        )

        Seeder(db).run()

        val mine = db.categories().all().first { it.name == "Cricket" }
        assertThat(mine.colorSeed).isEqualTo(0xFF00BCD4.toInt())
        assertThat(mine.iconName).isEqualTo("sports_cricket")
    }

    @Test
    fun `the remap is idempotent`() = runBlocking {
        val seeder = Seeder(db)
        seeder.run()
        val afterFirst = db.categories().all().sortedBy { it.id }

        seeder.run()
        val afterSecond = db.categories().all().sortedBy { it.id }

        // Same rows, same ids, same styling: a second boot must not churn the table.
        assertThat(afterSecond).isEqualTo(afterFirst)
    }

    private companion object {
        /** Mirrors `CATEGORY_PALETTE_SIZE` in `:design`, which `:data` cannot depend on. */
        const val PALETTE_SIZE = 12
    }
}
