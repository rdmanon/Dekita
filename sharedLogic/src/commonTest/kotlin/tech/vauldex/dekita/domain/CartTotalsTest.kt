package tech.vauldex.dekita.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CartTotalsTest {

    private val egg = ModifierOption("egg", "Extra Egg", Money.yen(120))
    private val chashu = ModifierOption("chashu", "Chashu", Money.yen(250))
    private val medium = ModifierOption("medium", "Medium")
    private val ramen = MenuItem("ramen", "store-a", "ramen", "Shoyu Ramen", "", Money.yen(980))
    private val gyoza = MenuItem("gyoza", "store-a", "sides", "Gyoza", "", Money.yen(450))

    private fun designCart() = Cart("store-a", FulfillmentMode.IN_STORE)
        .addLine(CartLine("l1", ramen, listOf(medium, egg)))
        .addLine(CartLine("l2", gyoza))

    @Test
    fun unitPriceAddsSelectedOptionPrices() {
        assertEquals(Money.yen(1350), CartLine("l1", ramen, listOf(egg, chashu)).unitPrice)
    }

    @Test
    fun freeOptionsDoNotChangeThePrice() {
        assertEquals(Money.yen(980), CartLine("l1", ramen, listOf(medium)).unitPrice)
    }

    @Test
    fun lineTotalMultipliesUnitPriceByQuantity() {
        assertEquals(Money.yen(2200), CartLine("l1", ramen, listOf(egg), quantity = 2).lineTotal)
    }

    @Test
    fun cartTotalsMatchTheDesignExample() {
        val cart = designCart()
        assertEquals(2, cart.itemCount)
        assertEquals(Money.yen(1550), cart.subtotal)
        assertEquals(Money.yen(1550), cart.total)
    }

    @Test
    fun emptyCartTotalsZero() {
        val cart = Cart("store-a", FulfillmentMode.PICKUP)
        assertTrue(cart.isEmpty)
        assertEquals(0, cart.itemCount)
        assertEquals(Money.ZERO, cart.total)
    }

    @Test
    fun updatingQuantityRecomputesTotals() {
        val cart = designCart().updateQuantity("l2", 3)
        assertEquals(4, cart.itemCount)
        assertEquals(Money.yen(1100 + 450 * 3), cart.total)
    }

    @Test
    fun quantityZeroRemovesTheLine() {
        val cart = designCart().updateQuantity("l1", 0)
        assertEquals(listOf("l2"), cart.lines.map { it.lineId })
        assertEquals(Money.yen(450), cart.total)
    }

    @Test
    fun negativeQuantityIsRejected() {
        assertFailsWith<IllegalArgumentException> { designCart().updateQuantity("l1", -1) }
    }

    @Test
    fun lineQuantityBelowOneIsRejected() {
        assertFailsWith<IllegalArgumentException> { CartLine("l1", gyoza, quantity = 0) }
    }

    @Test
    fun updatingAnUnknownLineIsRejected() {
        assertFailsWith<IllegalArgumentException> { designCart().updateQuantity("missing", 1) }
    }

    @Test
    fun itemFromAnotherStoreIsRejected() {
        val otherStoreItem = gyoza.copy(storeId = "store-b")
        assertFailsWith<IllegalArgumentException> { designCart().addLine(CartLine("l3", otherStoreItem)) }
    }

    @Test
    fun moneyFormatsAsYenWithGrouping() {
        assertEquals("¥0", Money.ZERO.formatted())
        assertEquals("¥980", Money.yen(980).formatted())
        assertEquals("¥1,550", Money.yen(1550).formatted())
        assertEquals("¥12,345", Money.yen(12345).formatted())
        assertEquals("¥1,000,000", Money.yen(1_000_000).formatted())
        assertEquals("-¥120", Money.yen(-120).formatted())
    }

    @Test
    fun addingDifferentCurrenciesIsRejected() {
        assertFailsWith<IllegalArgumentException> { Money.yen(100) + Money(100, "USD") }
    }

    @Test
    fun sameItemAndOptionsMergeIntoOneLine() {
        val cart = designCart().addLine(CartLine("l3", ramen, listOf(egg, medium), quantity = 2))
        assertEquals(2, cart.lines.size)
        assertEquals(3, cart.lines.first().quantity)
    }

    @Test
    fun differentOptionsMakeANewLine() {
        val cart = designCart().addLine(CartLine("l3", ramen, listOf(medium)))
        assertEquals(3, cart.lines.size)
    }
}
