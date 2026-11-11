package tech.vauldex.dekita.domain

/**
 * The choices in the item sheet: one set of option ids per modifier group, and a quantity.
 * Immutable; every change returns a new selection. Sold-out options can't be picked.
 */
data class ItemSelection(
    val item: MenuItem,
    val selected: Map<String, Set<String>>,
    val quantity: Int,
) {
    init {
        require(quantity in 1..MAX_QUANTITY) { "Quantity must be 1 to $MAX_QUANTITY, was $quantity" }
    }

    /** Selected options in menu order, as they are stored on the cart line. */
    val selectedOptions: List<ModifierOption>
        get() = item.modifierGroups.flatMap { group ->
            val ids = selected[group.id].orEmpty()
            group.options.filter { it.id in ids }
        }

    /** Required groups that still have too few choices. */
    val missingRequired: List<ModifierGroup>
        get() = item.modifierGroups.filter { selected[it.id].orEmpty().size < it.minSelections }

    /** Sold-out choices: the item itself, or an option picked before it sold out. */
    val hasSoldOutChoice: Boolean
        get() = item.isSoldOut || selectedOptions.any { it.isSoldOut }

    val canSave: Boolean get() = missingRequired.isEmpty() && !hasSoldOutChoice

    val unitPrice: Money get() = selectedOptions.fold(item.basePrice) { total, option -> total + option.priceDelta }

    val total: Money get() = unitPrice * quantity

    /**
     * Picks or unpicks an option. A one-choice group swaps to the new option. A group at its limit
     * ignores new picks until something is unpicked. Sold-out options are ignored, but can be unpicked.
     */
    fun toggle(groupId: String, optionId: String): ItemSelection {
        val group = item.modifierGroups.firstOrNull { it.id == groupId } ?: return this
        val option = group.options.firstOrNull { it.id == optionId } ?: return this
        val current = selected[groupId].orEmpty()
        val updated = when {
            optionId in current -> if (group.maxSelections == 1 && group.isRequired) current else current - optionId
            option.isSoldOut -> return this
            group.maxSelections == 1 -> setOf(optionId)
            current.size >= group.maxSelections -> return this
            else -> current + optionId
        }
        return copy(selected = selected + (groupId to updated))
    }

    fun withQuantity(quantity: Int): ItemSelection = copy(quantity = quantity.coerceIn(1, MAX_QUANTITY))

    fun toLine(lineId: String): CartLine = CartLine(lineId, item, selectedOptions, quantity)

    companion object {
        const val MAX_QUANTITY = 10

        /** A new pick: each required one-choice group starts on its first option that is not sold out. */
        fun start(item: MenuItem): ItemSelection = ItemSelection(
            item = item,
            selected = item.modifierGroups
                .filter { it.isRequired && it.maxSelections == 1 }
                .mapNotNull { group -> group.options.firstOrNull { !it.isSoldOut }?.let { group.id to setOf(it.id) } }
                .toMap(),
            quantity = 1,
        )

        /** Editing a cart line: start from its choices, against the live [item] from the menu. */
        fun fromLine(line: CartLine, item: MenuItem = line.item): ItemSelection {
            val ids = line.selectedOptions.map { it.id }.toSet()
            return ItemSelection(
                item = item,
                selected = item.modifierGroups.associate { group -> group.id to group.options.filter { it.id in ids }.map { it.id }.toSet() },
                quantity = line.quantity.coerceIn(1, MAX_QUANTITY),
            )
        }
    }
}
