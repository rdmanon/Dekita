package tech.vauldex.dekita.domain

data class Menu(
    val storeId: String,
    val categories: List<MenuCategory>,
    val items: List<MenuItem>,
) {
    fun itemsIn(categoryId: String): List<MenuItem> = items.filter { it.categoryId == categoryId }

    /** Every option on the menu once, in menu order. Items that share an option share its sold-out state. */
    val options: List<ModifierOption>
        get() = items.flatMap { item -> item.modifierGroups.flatMap { it.options } }.distinctBy { it.id }
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
    /** UI-01: a dish illustration. Null shows the store's art instead. */
    val artKey: String? = null,
    /** UI-01: listed under the store's Popular tab. */
    val isPopular: Boolean = false,
) {
    /** True when this version of the item has [option] sold out, or no longer offers it. */
    fun hasSoldOut(option: ModifierOption): Boolean =
        modifierGroups.flatMap { it.options }.firstOrNull { it.id == option.id }?.isSoldOut ?: true
}

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
