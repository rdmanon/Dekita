package tech.vauldex.dekita.domain

data class Menu(
    val storeId: String,
    val categories: List<MenuCategory>,
    val items: List<MenuItem>,
) {
    fun itemsIn(categoryId: String): List<MenuItem> = items.filter { it.categoryId == categoryId }
}

data class MenuCategory(val id: String, val name: String)

data class MenuItem(
    val id: String,
    val storeId: String,
    val categoryId: String,
    val name: String,
    val description: String,
    val basePrice: Money,
    val isSoldOut: Boolean = false,
    /** Items can be limited to one mode even when the store offers both. */
    val availableModes: Set<FulfillmentMode> = FulfillmentMode.entries.toSet(),
    val modifierGroups: List<ModifierGroup> = emptyList(),
)

data class ModifierGroup(
    val id: String,
    val name: String,
    val minSelections: Int,
    val maxSelections: Int,
    val options: List<ModifierOption>,
) {
    init {
        require(minSelections in 0..maxSelections) { "Invalid selection range $minSelections..$maxSelections" }
    }

    val isRequired: Boolean get() = minSelections > 0
}

data class ModifierOption(
    val id: String,
    val name: String,
    val priceDelta: Money = Money.ZERO,
    val isSoldOut: Boolean = false,
)
