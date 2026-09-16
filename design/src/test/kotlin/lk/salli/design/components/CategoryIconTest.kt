package lk.salli.design.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The previous category-icon mapper lived privately in `BudgetsScreen` and keyed on an invented
 * vocabulary (`grocery`, `car`, `bill`) rather than on the `icon_name` values the seed data
 * actually writes (`shopping_cart`, `directions_car`, `bolt`). The result was that almost every
 * seeded category silently fell through to a generic receipt — a failure that looks like a
 * design choice rather than a bug, which is exactly why it survived.
 *
 * These tests pin the contract: the seed names resolve, and the fallback is reserved for names
 * we genuinely don't know.
 */
class CategoryIconTest {

    /** Every `iconName` that `SeedCategories` ships. Duplicated because `:design` has no
     *  dependency on `:data`; if the seed list grows, this list grows with it. */
    private val seedIconNames = listOf(
        "shopping_cart", "restaurant", "directions_car", "local_gas_station", "bolt",
        "subscriptions", "shopping_bag", "medical_services", "school", "movie", "home",
        "payments", "swap_horiz", "local_atm", "receipt_long", "category",
    )

    @Test
    fun `every seeded icon name resolves to something other than the fallback`() {
        val fallback = categoryIconFor("definitely-not-an-icon")
        // "category" is the seed name for the "Other" bucket, so it maps to the fallback glyph
        // on purpose; every other seeded name falling through would be the bug.
        seedIconNames.filterNot { it == "category" }.forEach { name ->
            assertThat(categoryIconFor(name)).isNotEqualTo(fallback)
        }
    }

    @Test
    fun `the seeded names resolve to distinct glyphs`() {
        // "category" legitimately maps to the same vector as the fallback, so exclude it.
        val distinct = seedIconNames.filterNot { it == "category" }.map { categoryIconFor(it) }
        assertThat(distinct.toSet()).hasSize(distinct.size)
    }

    @Test
    fun `an unknown or missing name falls back to the generic category glyph`() {
        assertThat(categoryIconFor(null)).isEqualTo(Icons.Outlined.Category)
        assertThat(categoryIconFor("")).isEqualTo(Icons.Outlined.Category)
        assertThat(categoryIconFor("sports_cricket")).isEqualTo(Icons.Outlined.Category)
    }

    @Test
    fun `lookup is case and whitespace insensitive`() {
        // Icon names arrive from the database and from user-created rows; neither is trimmed.
        assertThat(categoryIconFor("  Shopping_Cart ")).isEqualTo(categoryIconFor("shopping_cart"))
        assertThat(categoryIconFor("BOLT")).isEqualTo(categoryIconFor("bolt"))
    }

    @Test
    fun `the legacy BudgetsScreen vocabulary still resolves`() {
        // A category the user created under the old build can carry any of these.
        mapOf(
            "grocery" to "shopping_cart",
            "groceries" to "shopping_cart",
            "car" to "directions_car",
            "bill" to "bolt",
            "utilities" to "bolt",
            "fuel" to "local_gas_station",
            "atm" to "local_atm",
            "salary" to "payments",
        ).forEach { (legacy, canonical) ->
            assertThat(categoryIconFor(legacy)).isEqualTo(categoryIconFor(canonical))
        }
    }
}
