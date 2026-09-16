package lk.salli.design.theme

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The category palette is addressed by index from the database (`categories.color_seed`), so
 * the two things that must never drift are the *size* of the ramp and the fact that any Int
 * folds into it. Both are silent failures if they break: a wrong index just shows the wrong
 * colour, and an out-of-range one crashes on a row the user happened to create.
 */
class SalliColorsTest {

    @Test
    fun `both palettes have the advertised size`() {
        assertThat(LightSalliColors.categoryPalette).hasSize(CATEGORY_PALETTE_SIZE)
        assertThat(DarkSalliColors.categoryPalette).hasSize(CATEGORY_PALETTE_SIZE)
    }

    @Test
    fun `light and dark ramps are the same length so an index means the same hue in both`() {
        assertThat(DarkSalliColors.categoryPalette)
            .hasSize(LightSalliColors.categoryPalette.size)
    }

    @Test
    fun `seeds inside the ramp map to their own slot`() {
        for (index in 0 until CATEGORY_PALETTE_SIZE) {
            assertThat(LightSalliColors.categoryHue(index))
                .isEqualTo(LightSalliColors.categoryPalette[index])
        }
    }

    @Test
    fun `seeds beyond the ramp wrap instead of falling off the end`() {
        assertThat(LightSalliColors.categoryHue(CATEGORY_PALETTE_SIZE))
            .isEqualTo(LightSalliColors.categoryPalette[0])
        assertThat(LightSalliColors.categoryHue(CATEGORY_PALETTE_SIZE + 5))
            .isEqualTo(LightSalliColors.categoryPalette[5])
    }

    @Test
    fun `negative seeds fold into range`() {
        // User-created categories carry a raw ARGB colour seed, which is negative as an Int
        // for anything with the high alpha bit set — i.e. all of them. A naive `% size`
        // returns a negative index and throws.
        val argbLikeSeed = 0xFF4CAF50.toInt()
        assertThat(argbLikeSeed).isLessThan(0)
        val hue = LightSalliColors.categoryHue(argbLikeSeed)
        assertThat(LightSalliColors.categoryPalette).contains(hue)

        assertThat(LightSalliColors.categoryHue(-1))
            .isEqualTo(LightSalliColors.categoryPalette[CATEGORY_PALETTE_SIZE - 1])
    }

    @Test
    fun `Int MIN_VALUE folds into range`() {
        val hue = DarkSalliColors.categoryHue(Int.MIN_VALUE)
        assertThat(DarkSalliColors.categoryPalette).contains(hue)
    }

    @Test
    fun `every hue in a ramp is distinct`() {
        assertThat(LightSalliColors.categoryPalette.map { it.accent }.toSet())
            .hasSize(CATEGORY_PALETTE_SIZE)
        assertThat(DarkSalliColors.categoryPalette.map { it.accent }.toSet())
            .hasSize(CATEGORY_PALETTE_SIZE)
    }

    @Test
    fun `the lime accent is identical in both themes`() {
        // The nav lozenge and the "under pace" pill are the brand's signature; they must not
        // shift hue between palettes.
        assertThat(DarkSalliColors.positive).isEqualTo(LightSalliColors.positive)
        assertThat(DarkSalliColors.onPositive).isEqualTo(LightSalliColors.onPositive)
        assertThat(LightSalliColors.positive).isEqualTo(SalliBrandColors.AcidLime)
    }

    @Test
    fun `the hero surface is cobalt in both themes`() {
        assertThat(LightSalliColors.hero).isEqualTo(SalliBrandColors.Cobalt)
        assertThat(DarkSalliColors.hero).isEqualTo(SalliBrandColors.Cobalt)
    }

    @Test
    fun `expense is ink, not red`() {
        // A deliberate product decision: a spending app where every row is red is a wall of
        // alarm. Red is reserved for `negative`.
        assertThat(LightSalliColors.expense).isNotEqualTo(LightSalliColors.negative)
        assertThat(DarkSalliColors.expense).isNotEqualTo(DarkSalliColors.negative)
    }
}
