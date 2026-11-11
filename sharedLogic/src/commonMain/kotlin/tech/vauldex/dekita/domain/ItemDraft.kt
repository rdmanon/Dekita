package tech.vauldex.dekita.domain

// shortcut: the PO hasn't set a maximum quantity (MENU-01 open question); 10 until they answer.
const val MAX_QUANTITY = 10

/** The customer's choices for one menu item before it goes in the cart. Both UIs use these rules. */
data class ItemDraft(
    val item: MenuItem,
    val selected: Map<String, List<ModifierOption>> = emptyMap(),
    val quantity: Int = 1,
) {
    /** Required groups that still have too few choices, in menu order. */
    val missingRequiredGroups: List<ModifierGroup>
        get() = item.modifierGroups.filter { selectedIn(it).size < it.minSelections }

    val canAdd: Boolean get() = !item.isSoldOut && missingRequiredGroups.isEmpty()

    val totalPrice: Money get() = toCartLine().lineTotal

    fun selectedIn(group: ModifierGroup): List<ModifierOption> = selected[group.id].orEmpty()

    fun isSelected(group: ModifierGroup, option: ModifierOption): Boolean = selectedIn(group).any { it.id == option.id }

    /**
     * A single-choice group swaps its choice; a required one can't be emptied. Other groups add or remove
     * an option and stop at their maximum. Sold-out options can't be picked.
     */
    fun toggle(groupId: String, optionId: String): ItemDraft {
        val group = item.modifierGroups.firstOrNull { it.id == groupId } ?: return this
        val option = group.options.firstOrNull { it.id == optionId } ?: return this
        val current = selectedIn(group)
        val updated = when {
            current.any { it.id == optionId } ->
                if (group.maxSelections == 1 && group.isRequired) return this else current.filterNot { it.id == optionId }
            option.isSoldOut -> return this
            group.maxSelections == 1 -> listOf(option)
            current.size >= group.maxSelections -> return this
            else -> current + option
        }
        return copy(selected = selected + (groupId to updated))
    }

    fun withQuantity(quantity: Int): ItemDraft = copy(quantity = quantity.coerceIn(1, MAX_QUANTITY))

    /** Keeps the choices but takes the latest version of the item, for example after it sold out. */
    fun withItem(latest: MenuItem): ItemDraft = copy(item = latest)

    /** The line id comes from the item and its options, so the same choices always make the same line. */
    fun toCartLine(): CartLine {
        val options = item.modifierGroups.flatMap { selectedIn(it) }
        val lineId = (listOf(item.id) + options.map { it.id }.sorted()).joinToString("|")
        return CartLine(lineId, item, options, quantity)
    }
}
