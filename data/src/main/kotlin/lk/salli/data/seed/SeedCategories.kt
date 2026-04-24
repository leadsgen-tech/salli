package lk.salli.data.seed

import lk.salli.data.db.entities.CategoryEntity

/**
 * Seed categories shipped with the app on first install. IDs are assigned by Room; the order
 * here determines what the user sees by default. All keep `isSystem = true` so they can't be
 * deleted (though they can be renamed / re-coloured).
 */
object SeedCategories {
    val all: List<CategoryEntity> = listOf(
        CategoryEntity(name = "Groceries", iconName = "shopping_cart", colorSeed = 0xFF4CAF50.toInt()),
        CategoryEntity(name = "Food & Dining", iconName = "restaurant", colorSeed = 0xFFFF9800.toInt()),
        CategoryEntity(name = "Transport", iconName = "directions_car", colorSeed = 0xFF2196F3.toInt()),
        CategoryEntity(name = "Fuel", iconName = "local_gas_station", colorSeed = 0xFF795548.toInt()),
        CategoryEntity(name = "Utilities", iconName = "bolt", colorSeed = 0xFFFFC107.toInt()),
        CategoryEntity(name = "Online Subscriptions", iconName = "subscriptions", colorSeed = 0xFF9C27B0.toInt()),
        CategoryEntity(name = "Shopping", iconName = "shopping_bag", colorSeed = 0xFFE91E63.toInt()),
        CategoryEntity(name = "Healthcare", iconName = "medical_services", colorSeed = 0xFFF44336.toInt()),
        CategoryEntity(name = "Education", iconName = "school", colorSeed = 0xFF3F51B5.toInt()),
        CategoryEntity(name = "Entertainment", iconName = "movie", colorSeed = 0xFF673AB7.toInt()),
        CategoryEntity(name = "Rent", iconName = "home", colorSeed = 0xFF607D8B.toInt()),
        CategoryEntity(name = "Salary", iconName = "payments", colorSeed = 0xFF009688.toInt()),
        CategoryEntity(name = "Transfers", iconName = "swap_horiz", colorSeed = 0xFF9E9E9E.toInt()),
        CategoryEntity(name = "Cash", iconName = "local_atm", colorSeed = 0xFF757575.toInt()),
        CategoryEntity(name = "Fees", iconName = "receipt_long", colorSeed = 0xFFB71C1C.toInt()),
        CategoryEntity(name = "Other", iconName = "category", colorSeed = 0xFF455A64.toInt()),
    )

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
