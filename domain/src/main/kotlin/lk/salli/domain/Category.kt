package lk.salli.domain

data class Category(
    val id: Long? = null,
    val name: String,
    val iconName: String,
    val colorSeed: Int,
    val isSystem: Boolean = true,
)

data class SubCategory(
    val id: Long? = null,
    val categoryId: Long,
    val name: String,
    val iconName: String? = null,
)
