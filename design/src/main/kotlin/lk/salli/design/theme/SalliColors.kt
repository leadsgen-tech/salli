package lk.salli.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The semantic layer that sits on top of the M3 [androidx.compose.material3.ColorScheme].
 *
 * M3 gives us `primary` / `error` / `tertiary`; it does not give us "this number is money
 * coming in", "this bill is due in two days" or "you are under pace". Those are the decisions
 * Salli actually makes, so they get named tokens and every screen reads them from
 * [LocalSalliColors] instead of re-deciding what green means.
 *
 * Deliberate call: **expenses are not red.** A spending app whose every row is red is a wall
 * of alarm, so `expense` is plain ink and red is reserved for [negative] — over budget,
 * overdue, declined.
 */
@Immutable
data class SalliColors(
    /** Credits, positive day nets. */
    val income: Color,
    val onIncome: Color,
    val incomeContainer: Color,
    val onIncomeContainer: Color,

    /** Debits. Ink, not red — see the class doc. */
    val expense: Color,

    /** Under pace, eligible today, "3 new". The lime. */
    val positive: Color,
    val onPositive: Color,

    /** Hot pace, due soon, needs a look. */
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,

    /** Over budget, overdue, declined. */
    val negative: Color,
    val onNegative: Color,
    val negativeContainer: Color,
    val onNegativeContainer: Color,

    /** Own transfers and fees — money that moved but wasn't spent. */
    val transfer: Color,

    /** Hero surfaces (Home hero card, onboarding stage). Identical in both themes. */
    val hero: Color,
    val onHero: Color,

    /** Hairline between rows inside a [lk.salli.design.components.GroupedList]. */
    val hairline: Color,

    /** The 12-hue category ramp, indexed by `categorySeed % size`. */
    val categoryPalette: List<CategoryHue>,
) {
    /**
     * Resolves a category's hue from its stored `colorSeed`. The seed doubles as the palette
     * index for system categories (see `SeedCategories`); user-created categories keep an
     * arbitrary ARGB seed, which we fold into the ramp so they still land on a tuned hue.
     */
    fun categoryHue(colorSeed: Int): CategoryHue {
        val size = categoryPalette.size
        if (size == 0) return CategoryHue(expense, incomeContainer, expense)
        val index = ((colorSeed % size) + size) % size
        return categoryPalette[index]
    }
}

/**
 * One category hue: the saturated [accent] for bars and icons, plus the [container] /
 * [onContainer] pair for the tinted 40 dp leading tile behind an icon.
 */
@Immutable
data class CategoryHue(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
)

/**
 * 12 hues tuned to the cobalt system: cool-leaning, mid-chroma, and evenly spaced round the
 * wheel so adjacent bars in a category chart never read as the same colour. Light containers
 * are ~10 % tints of the accent on white; dark containers are ~18 % shades on ink, with the
 * `onContainer` lifted so an icon stays legible.
 */
private val LightCategoryPalette = listOf(
    CategoryHue(Color(0xFF0B7A55), Color(0xFFDFF1E9), Color(0xFF04402C)), //  0 green
    CategoryHue(Color(0xFFD9731A), Color(0xFFFCEBD9), Color(0xFF572B00)), //  1 amber
    CategoryHue(Color(0xFF1F5FE0), Color(0xFFDEE8FD), Color(0xFF002E75)), //  2 cobalt-blue
    CategoryHue(Color(0xFF7A5A3A), Color(0xFFF0E8E0), Color(0xFF3A2814)), //  3 clay
    CategoryHue(Color(0xFFB8860B), Color(0xFFF8EFD4), Color(0xFF453100)), //  4 ochre
    CategoryHue(Color(0xFF7B3FD4), Color(0xFFEBE1FB), Color(0xFF32006E)), //  5 violet
    CategoryHue(Color(0xFFC4327D), Color(0xFFFBE0EE), Color(0xFF5B0034)), //  6 magenta
    CategoryHue(Color(0xFFC0392B), Color(0xFFFBE2DF), Color(0xFF5A0F07)), //  7 red
    CategoryHue(Color(0xFF3949AB), Color(0xFFE2E5F7), Color(0xFF141E5C)), //  8 indigo
    CategoryHue(Color(0xFF00838F), Color(0xFFD9EFF2), Color(0xFF003B42)), //  9 teal
    CategoryHue(Color(0xFF546E7A), Color(0xFFE6EBEE), Color(0xFF1F2E36)), // 10 slate
    CategoryHue(Color(0xFF5E6B7A), Color(0xFFE9ECF0), Color(0xFF232C35)), // 11 neutral
)

private val DarkCategoryPalette = listOf(
    CategoryHue(Color(0xFF72D5AD), Color(0xFF0D3A2B), Color(0xFFB5EFD8)), //  0 green
    CategoryHue(Color(0xFFFFC875), Color(0xFF452800), Color(0xFFFFDDAE)), //  1 amber
    CategoryHue(Color(0xFFA8C0FF), Color(0xFF13275C), Color(0xFFD6E1FF)), //  2 cobalt-blue
    CategoryHue(Color(0xFFD7BFA6), Color(0xFF3A2B1C), Color(0xFFEBDCCB)), //  3 clay
    CategoryHue(Color(0xFFE6C766), Color(0xFF3D2F00), Color(0xFFF4E3AE)), //  4 ochre
    CategoryHue(Color(0xFFC6A8FF), Color(0xFF2E1B57), Color(0xFFE3D3FF)), //  5 violet
    CategoryHue(Color(0xFFF7A3CE), Color(0xFF4E1533), Color(0xFFFBD0E5)), //  6 magenta
    CategoryHue(Color(0xFFFFB4AB), Color(0xFF4C1310), Color(0xFFFFD9D4)), //  7 red
    CategoryHue(Color(0xFFAEB8F0), Color(0xFF1C2352), Color(0xFFD7DCF8)), //  8 indigo
    CategoryHue(Color(0xFF7FD4DF), Color(0xFF07363C), Color(0xFFBCE9EF)), //  9 teal
    CategoryHue(Color(0xFFA6BCC7), Color(0xFF203039), Color(0xFFD2E0E7)), // 10 slate
    CategoryHue(Color(0xFFAFB8C4), Color(0xFF242C36), Color(0xFFD7DCE3)), // 11 neutral
)

/** Number of hues in the ramp. `SeedCategories` keys its `colorSeed` values on this. */
const val CATEGORY_PALETTE_SIZE: Int = 12

internal val LightSalliColors = SalliColors(
    income = Color(0xFF087A55),
    onIncome = Color(0xFFFFFFFF),
    incomeContainer = Color(0xFFE3F3EC),
    onIncomeContainer = Color(0xFF04301F),

    expense = Color(0xFF0A0D14),

    positive = SalliBrandColors.AcidLime,
    onPositive = SalliBrandColors.OnAcidLime,

    warning = Color(0xFFB45309),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFF1DB),
    onWarningContainer = Color(0xFFB45309),

    negative = Color(0xFFB3261E),
    onNegative = Color(0xFFFFFFFF),
    negativeContainer = Color(0xFFFFDAD6),
    onNegativeContainer = Color(0xFFB3261E),

    transfer = Color(0xFF596170),

    hero = SalliBrandColors.Cobalt,
    onHero = SalliBrandColors.OnCobalt,

    hairline = Color(0xFFCBD2DF),

    categoryPalette = LightCategoryPalette,
)

internal val DarkSalliColors = SalliColors(
    income = Color(0xFF72D5AD),
    onIncome = Color(0xFF003824),
    incomeContainer = Color(0xFF0B3E2C),
    onIncomeContainer = Color(0xFFB9F2D8),

    expense = Color(0xFFF4F6FC),

    positive = SalliBrandColors.AcidLime,
    onPositive = SalliBrandColors.OnAcidLime,

    warning = Color(0xFFFFC875),
    onWarning = Color(0xFF4A2A00),
    warningContainer = Color(0xFF4A2A00),
    onWarningContainer = Color(0xFFFFC875),

    negative = Color(0xFFFFB4AB),
    onNegative = Color(0xFF690005),
    negativeContainer = Color(0xFF93000A),
    onNegativeContainer = Color(0xFFFFB4AB),

    transfer = Color(0xFFB8C0CF),

    hero = SalliBrandColors.Cobalt,
    onHero = SalliBrandColors.OnCobalt,

    hairline = Color(0xFF3B4658),

    categoryPalette = DarkCategoryPalette,
)

/**
 * Salli's semantic colours. Always populated inside `SalliTheme`; the light set is the default
 * so a stray `@Preview` outside the theme still renders something sane rather than crashing.
 */
val LocalSalliColors = staticCompositionLocalOf { LightSalliColors }
