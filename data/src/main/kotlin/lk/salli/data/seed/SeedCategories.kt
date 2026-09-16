package lk.salli.data.seed

import lk.salli.data.db.entities.CategoryEntity

/**
 * Seed categories shipped with the app on first install. IDs are assigned by Room; the order
 * here determines what the user sees by default. All keep `isSystem = true` so they can't be
 * deleted (though they can be renamed / re-coloured).
 *
 * **`colorSeed` is a palette index, not an ARGB colour.** It used to hold raw 2014 Material
 * hues (`0xFF4CAF50`) which were never tone-mapped, so every category looked like it came from
 * a different app and dark mode got the light-mode hue at full chroma. Now it names a slot in
 * the design module's 12-hue ramp (`SalliColors.categoryPalette`), which ships a tuned
 * light/dark container pair per hue. User-created categories may still carry an arbitrary ARGB
 * seed; `SalliColors.categoryHue` folds any Int into the ramp, so both kinds resolve.
 *
 * The first eleven spending categories each get their own hue. Salary reuses the green
 * (it's income and never shares a chart with Groceries), and the four money-plumbing
 * categories deliberately share the neutral slot — transfers, cash, fees and "other" are
 * exactly the rows that should not shout.
 *
 * [Seeder] re-applies these on upgrade, so changing an index here migrates existing installs.
 */
object SeedCategories {

    // Palette slots — see SalliColors.categoryPalette for the hues themselves.
    private const val GREEN = 0
    private const val AMBER = 1
    private const val COBALT = 2
    private const val CLAY = 3
    private const val OCHRE = 4
    private const val VIOLET = 5
    private const val MAGENTA = 6
    private const val RED = 7
    private const val INDIGO = 8
    private const val TEAL = 9
    private const val SLATE = 10
    private const val NEUTRAL = 11

    val all: List<CategoryEntity> = listOf(
        CategoryEntity(name = "Groceries", iconName = "shopping_cart", colorSeed = GREEN),
        CategoryEntity(name = "Food & Dining", iconName = "restaurant", colorSeed = AMBER),
        CategoryEntity(name = "Transport", iconName = "directions_car", colorSeed = COBALT),
        CategoryEntity(name = "Fuel", iconName = "local_gas_station", colorSeed = CLAY),
        CategoryEntity(name = "Utilities", iconName = "bolt", colorSeed = OCHRE),
        CategoryEntity(name = "Online Subscriptions", iconName = "subscriptions", colorSeed = VIOLET),
        CategoryEntity(name = "Shopping", iconName = "shopping_bag", colorSeed = MAGENTA),
        CategoryEntity(name = "Healthcare", iconName = "medical_services", colorSeed = RED),
        CategoryEntity(name = "Education", iconName = "school", colorSeed = INDIGO),
        CategoryEntity(name = "Entertainment", iconName = "movie", colorSeed = TEAL),
        CategoryEntity(name = "Rent", iconName = "home", colorSeed = SLATE),
        CategoryEntity(name = "Salary", iconName = "payments", colorSeed = GREEN),
        CategoryEntity(name = "Transfers", iconName = "swap_horiz", colorSeed = NEUTRAL),
        CategoryEntity(name = "Cash", iconName = "local_atm", colorSeed = NEUTRAL),
        CategoryEntity(name = "Fees", iconName = "receipt_long", colorSeed = NEUTRAL),
        CategoryEntity(name = "Other", iconName = "category", colorSeed = NEUTRAL),
    )

    /** Name → palette index, for [Seeder]'s idempotent upgrade remap. */
    val paletteByName: Map<String, Int> = all.associate { it.name to it.colorSeed }

    /** Name → Material icon name, so the same upgrade pass can heal drifted icons. */
    val iconByName: Map<String, String> = all.associate { it.name to it.iconName }

    // Stable 0-based indices that keyword seed data will reference as (arbitrary) ordinals.
    // Actual DB IDs come from autoGenerate; the seeder resolves names → IDs after insertion.
    const val GROCERIES = "Groceries"
    const val FOOD = "Food & Dining"
    const val TRANSPORT = "Transport"
    const val FUEL = "Fuel"
    const val UTILITIES = "Utilities"
    const val SUBSCRIPTIONS = "Online Subscriptions"
    const val SHOPPING = "Shopping"
    const val ENTERTAINMENT = "Entertainment"
    const val TRANSFERS = "Transfers"
    const val CASH = "Cash"
    const val FEES = "Fees"
    const val OTHER = "Other"
}
