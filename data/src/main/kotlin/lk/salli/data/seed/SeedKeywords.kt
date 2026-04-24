package lk.salli.data.seed

/**
 * Seed keyword → category mappings tuned for Sri Lankan merchants and common global services.
 * Keys are lowercased substrings; the DAO does a case-insensitive LIKE match, so e.g. `"keells"`
 * will match SMS like `KEELLS SUPER COLOMBO 04`.
 *
 * Organised by category name (resolved to category ID by the seeder at install time).
 *
 * Contributions welcome — keep the list short here; rare or regional merchants should be
 * user-added rather than shipped with the app.
 */
object SeedKeywords {

    /** category name → list of keyword substrings. */
    val byCategory: Map<String, List<String>> = mapOf(
        SeedCategories.GROCERIES to listOf(
            "keells", "cargills", "food city", "foodcity", "arpico", "glomark", "laughs",
            "no limit", "nolimit", "crescat",
        ),
        SeedCategories.FOOD to listOf(
            "pizza hut", "pizzahut", "kfc", "mcdonald", "burger king", "dominos", "domino's",
            "barista", "java lounge", "street burger", "streetburger", "mimosa", "crepe",
            "taco bell", "ubereats", "uber eats", "pickme food", "pickmefood",
            "spaceylon", "spa ceylon", "perera & sons", "perera and sons", "green cabin",
            "green-cabin", "tecroot", "scope", "simplyek", "simply ek",
        ),
        SeedCategories.TRANSPORT to listOf(
            "pickme", "uber", "kangaroo cab", "ktm",
        ),
        SeedCategories.FUEL to listOf(
            "ceypetco", "lioc", "ioc", "ceylon petroleum",
        ),
        SeedCategories.UTILITIES to listOf(
            "slt", "mobitel", "dialog", "hutch", "airtel", "ceb", "nwsdb", "lanka electricity",
            "lech", "water board",
        ),
        SeedCategories.SUBSCRIPTIONS to listOf(
            "netflix", "spotify", "apple.com", "apple com", "google ", "gemini", "anthropic",
            "chatgpt", "openai", "claude", "github", "steam", "youtube premium", "ytm premium",
            "temu", "linear", "railway", "sourcegraph", "capcut", "telegram premium",
        ),
        SeedCategories.SHOPPING to listOf(
            "daraz", "odel", "softlogic", "abans", "dsi", "fab", "wishque", "mintpay",
            "sarasavi", "jumpbooks", "jump books", "kapruka",
        ),
        SeedCategories.ENTERTAINMENT to listOf(
            "carnage", "cool planet", "coolplanet",
        ),
        "Healthcare" to listOf(
            "nawaloka", "asiri", "hemas", "durdans", "lanka hospitals", "ninewells",
            "navaloka",
        ),
        "Education" to listOf(
            "royal college", "s. thomas", "ananda college", "university of",
            "ocas", "gce a/l", "gce o/l",
        ),
    )
}
