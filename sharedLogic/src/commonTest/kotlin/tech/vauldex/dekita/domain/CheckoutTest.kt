package tech.vauldex.dekita.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckoutTest {

    private val mild = ModifierOption("mild", "Mild")
    private val hot = ModifierOption("hot", "Hot")
    private val egg = ModifierOption("egg", "Extra Egg", Money.yen(120))
    private val spice = ModifierGroup("spice", "Spice level", 1, 1, listOf(mild, ModifierOption("medium", "Medium"), hot))
    private val toppings = ModifierGroup("toppings", "Toppings", 0, 3, listOf(egg))
    private val ramen = MenuItem("ramen", "store-a", "ramen", "Shoyu Ramen", "", Money.yen(980), modifierGroups = listOf(spice, toppings))
    private val gyoza = MenuItem("gyoza", "store-a", "sides", "Gyoza", "", Money.yen(450))

    // Mode routing by business type

    @Test
    fun singleModeStoresHaveAFixedModeAndPayNow() {
        assertEquals(FulfillmentMode.IN_STORE, BusinessType.IN_STORE_ONLY.defaultMode(FulfillmentMode.PICKUP))
        assertEquals(FulfillmentMode.PICKUP, BusinessType.PICKUP_ONLY.defaultMode(FulfillmentMode.IN_STORE))
        assertFalse(BusinessType.IN_STORE_ONLY.letsCustomerChooseMode)
        assertFalse(BusinessType.PICKUP_ONLY.letsCustomerChooseMode)
        assertEquals(CheckoutAction.PAY_NOW, checkoutAction(BusinessType.IN_STORE_ONLY, FulfillmentMode.IN_STORE))
        assertEquals(CheckoutAction.PAY_NOW, checkoutAction(BusinessType.PICKUP_ONLY, FulfillmentMode.PICKUP))
    }

    @Test
    fun bothModesDefaultToTheDiscoverChoiceThenEatIn() {
        assertTrue(BusinessType.BOTH.letsCustomerChooseMode)
        assertEquals(FulfillmentMode.PICKUP, BusinessType.BOTH.defaultMode(FulfillmentMode.PICKUP))
        assertEquals(FulfillmentMode.IN_STORE, BusinessType.BOTH.defaultMode(null))
    }

    @Test
    fun bothModesEatInHeadsToStoreAndTakeoutPaysNow() {
        assertEquals(CheckoutAction.HEAD_TO_STORE, checkoutAction(BusinessType.BOTH, FulfillmentMode.IN_STORE))
        assertEquals(CheckoutAction.PAY_NOW, checkoutAction(BusinessType.BOTH, FulfillmentMode.PICKUP))
    }

    @Test
    fun anUnsupportedModeFallsBackToTheStoresMode() {
        assertEquals(FulfillmentMode.PICKUP, BusinessType.PICKUP_ONLY.resolveMode(FulfillmentMode.IN_STORE))
        assertEquals(FulfillmentMode.PICKUP, BusinessType.BOTH.resolveMode(FulfillmentMode.PICKUP))
    }

    // Editing lines

    private fun designCart() = Cart("store-a", FulfillmentMode.IN_STORE)
        .addLine(ItemDraft(ramen).toggle("spice", "mild").toCartLine())
        .addLine(ItemDraft(gyoza).toCartLine())

    @Test
    fun replaceLineEditsInPlaceWithoutADuplicate() {
        val cart = designCart()
        val shoyu = cart.lines.first()
        val edited = ItemDraft.fromLine(shoyu).toggle("spice", "hot").withQuantity(2).toCartLine()

        val updated = cart.replaceLine(shoyu.lineId, edited)

        assertEquals(2, updated.lines.size, "No duplicate")
        assertEquals(listOf("Shoyu Ramen", "Gyoza"), updated.lines.map { it.item.name }, "Same position")
        assertEquals(listOf(hot), updated.lines.first().selectedOptions)
        assertEquals(Money.yen(980 * 2 + 450), updated.total)
    }

    @Test
    fun editingToMatchAnotherLineMergesThem() {
        val cart = designCart().addLine(ItemDraft(ramen).toggle("spice", "hot").toCartLine())
        val mildLine = cart.lines.first()

        val updated = cart.replaceLine(mildLine.lineId, ItemDraft.fromLine(mildLine).toggle("spice", "hot").toCartLine())

        assertEquals(2, updated.lines.size)
        assertEquals(2, updated.lines.first { it.item.id == "ramen" }.quantity)
    }

    @Test
    fun replaceLineRejectsAnotherStoresItem() {
        val cart = designCart()
        assertFailsWith<IllegalArgumentException> {
            cart.replaceLine(cart.lines.last().lineId, ItemDraft(gyoza.copy(storeId = "store-b")).toCartLine())
        }
    }

    @Test
    fun aDraftFromALineHasItsChoicesAndQuantity() {
        val line = ItemDraft(ramen).toggle("spice", "hot").toggle("toppings", "egg").withQuantity(3).toCartLine()
        val draft = ItemDraft.fromLine(line)
        assertTrue(draft.isSelected(spice, hot))
        assertTrue(draft.isSelected(toppings, egg))
        assertEquals(3, draft.quantity)
        assertEquals(line.lineTotal, draft.totalPrice)
    }

    @Test
    fun aDraftFromALineUsesTheLatestItem() {
        val line = ItemDraft(gyoza).toCartLine()
        val draft = ItemDraft.fromLine(line, gyoza.copy(isSoldOut = true))
        assertFalse(draft.canAdd)
    }
}
