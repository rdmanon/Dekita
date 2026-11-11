package tech.vauldex.dekita.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckoutTest {

    private val mild = ModifierOption("mild", "Mild")
    private val hot = ModifierOption("hot", "Hot")
    private val soldOutHot = hot.copy(isSoldOut = true)
    private val egg = ModifierOption("egg", "Extra Egg", Money.yen(120))
    private val chashu = ModifierOption("chashu", "Chashu", Money.yen(250))
    private val nori = ModifierOption("nori", "Nori", Money.yen(80))
    private val corn = ModifierOption("corn", "Corn", Money.yen(100), isSoldOut = true)
    private val spice = ModifierGroup("spice", "Spice level", 1, 1, listOf(mild, ModifierOption("medium", "Medium"), hot))
    private val toppings = ModifierGroup("toppings", "Toppings", 0, 2, listOf(egg, chashu, nori, corn))
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

    @Test
    fun replaceLineEditsInPlaceWithoutADuplicate() {
        val cart = Cart("store-a", FulfillmentMode.IN_STORE)
            .addLine(CartLine("l1", ramen, listOf(mild)))
            .addLine(CartLine("l2", gyoza))

        val edited = cart.replaceLine("l1", CartLine("ignored-id", ramen, listOf(hot), quantity = 2))

        assertEquals(listOf("l1", "l2"), edited.lines.map { it.lineId }, "Same lines, same order")
        assertEquals(listOf(hot), edited.line("l1")?.selectedOptions)
        assertEquals(Money.yen(980 * 2 + 450), edited.total)
    }

    @Test
    fun replaceLineRejectsAnotherStoresItem() {
        val cart = Cart("store-a", FulfillmentMode.IN_STORE).addLine(CartLine("l1", gyoza))
        assertFailsWith<IllegalArgumentException> {
            cart.replaceLine("l1", CartLine("l1", gyoza.copy(storeId = "store-b")))
        }
    }

    // Item sheet choices

    @Test
    fun aNewPickStartsOnTheFirstAvailableRequiredOption() {
        val selection = ItemSelection.start(ramen.copy(modifierGroups = listOf(spice.copy(options = listOf(mild.copy(isSoldOut = true), hot)))))
        assertEquals(setOf("hot"), selection.selected["spice"])
        assertTrue(selection.canSave)
    }

    @Test
    fun oneChoiceGroupsSwapAndRequiredOnesCannotBeEmptied() {
        val selection = ItemSelection.start(ramen).toggle("spice", "hot")
        assertEquals(setOf("hot"), selection.selected["spice"])
        assertEquals(setOf("hot"), selection.toggle("spice", "hot").selected["spice"])
    }

    @Test
    fun multiChoiceGroupsStopAtTheirLimitAndSkipSoldOut() {
        val selection = ItemSelection.start(ramen)
            .toggle("toppings", "egg")
            .toggle("toppings", "chashu")
            .toggle("toppings", "nori")
            .toggle("toppings", "corn")
        assertEquals(setOf("egg", "chashu"), selection.selected["toppings"])
        assertEquals(Money.yen(980 + 120 + 250), selection.unitPrice)
    }

    @Test
    fun editingStartsFromTheLinesChoicesAndQuantity() {
        val line = CartLine("l1", ramen, listOf(hot, egg), quantity = 3)
        val selection = ItemSelection.fromLine(line)
        assertEquals(setOf("hot"), selection.selected["spice"])
        assertEquals(setOf("egg"), selection.selected["toppings"])
        assertEquals(3, selection.quantity)
        assertEquals(Money.yen((980 + 120) * 3), selection.total)
    }

    @Test
    fun aChoiceThatSoldOutBlocksSavingUntilChanged() {
        val live = ramen.copy(modifierGroups = listOf(spice.copy(options = listOf(mild, soldOutHot)), toppings))
        val selection = ItemSelection.fromLine(CartLine("l1", ramen, listOf(hot)), live)
        assertFalse(selection.canSave)
        assertTrue(selection.toggle("spice", "mild").canSave)
    }

    @Test
    fun aSoldOutItemCannotBeSaved() {
        assertFalse(ItemSelection.start(gyoza.copy(isSoldOut = true)).canSave)
    }

    @Test
    fun quantityStaysBetweenOneAndTen() {
        val selection = ItemSelection.start(gyoza)
        assertEquals(1, selection.withQuantity(0).quantity)
        assertEquals(ItemSelection.MAX_QUANTITY, selection.withQuantity(99).quantity)
    }
}
