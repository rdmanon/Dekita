package tech.vauldex.dekita.domain

data class CartLine(
    val lineId: String,
    val item: MenuItem,
    val selectedOptions: List<ModifierOption> = emptyList(),
    val quantity: Int = 1,
) {
    init {
        require(quantity >= 1) { "Quantity must be at least 1, was $quantity" }
    }

    val unitPrice: Money get() = selectedOptions.fold(item.basePrice) { total, option -> total + option.priceDelta }

    val lineTotal: Money get() = unitPrice * quantity

    internal val optionIds: Set<String> get() = selectedOptions.mapTo(mutableSetOf()) { it.id }
}

/** A cart holds items from one store only. Totals are for display; the server prices the real order. */
data class Cart(
    val storeId: String,
    val mode: FulfillmentMode,
    val lines: List<CartLine> = emptyList(),
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    val itemCount: Int get() = lines.sumOf { it.quantity }

    val subtotal: Money get() = lines.fold(Money.ZERO) { total, line -> total + line.lineTotal }

    /** Menu prices in Japan include consumption tax, so the total shown equals the subtotal. */
    val total: Money get() = subtotal

    /** Adds a line. The same item with the same options merges into the existing line's quantity. */
    fun addLine(line: CartLine): Cart {
        require(line.item.storeId == storeId) { "Cart belongs to $storeId, item is from ${line.item.storeId}" }
        val same = lines.firstOrNull { it.item.id == line.item.id && it.optionIds == line.optionIds }
        if (same != null) {
            return copy(lines = lines.map { if (it === same) it.copy(quantity = it.quantity + line.quantity) else it })
        }
        require(lines.none { it.lineId == line.lineId }) { "Duplicate line id ${line.lineId}" }
        return copy(lines = lines + line)
    }

    /** Sets a line's quantity. A quantity of 0 removes the line. */
    fun updateQuantity(lineId: String, quantity: Int): Cart {
        require(quantity >= 0) { "Quantity must not be negative, was $quantity" }
        require(lines.any { it.lineId == lineId }) { "No line $lineId in cart" }
        val updated = if (quantity == 0) {
            lines.filterNot { it.lineId == lineId }
        } else {
            lines.map { if (it.lineId == lineId) it.copy(quantity = quantity) else it }
        }
        return copy(lines = updated)
    }

    fun line(lineId: String): CartLine? = lines.firstOrNull { it.lineId == lineId }

    /**
     * Puts an edited line in place of [lineId], in the same position, so editing never adds a line.
     * If the new choices match another line, the two merge into that line.
     */
    fun replaceLine(lineId: String, line: CartLine): Cart {
        require(line.item.storeId == storeId) { "Cart belongs to $storeId, item is from ${line.item.storeId}" }
        require(lines.any { it.lineId == lineId }) { "No line $lineId in cart" }
        val twin = lines.firstOrNull { it.lineId != lineId && it.item.id == line.item.id && it.optionIds == line.optionIds }
        if (twin != null) {
            return copy(
                lines = lines
                    .filterNot { it.lineId == lineId }
                    .map { if (it === twin) it.copy(quantity = it.quantity + line.quantity) else it },
            )
        }
        return copy(lines = lines.map { if (it.lineId == lineId) line else it })
    }
}
