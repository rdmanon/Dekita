package tech.vauldex.dekita.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tech.vauldex.dekita.data.mock.SeedStoreIds
import tech.vauldex.dekita.data.mock.seedItem

class ItemDraftTest {

    private val shoyu = ItemDraft(seedItem(SeedStoreIds.RAMEN_BAR, "item-shoyu-ramen"))

    @Test
    fun requiredSpiceMustBeChosenBeforeAdding() {
        assertFalse(shoyu.canAdd)
        assertEquals(listOf("ramen-spice"), shoyu.missingRequiredGroups.map { it.id })

        val chosen = shoyu.toggle("ramen-spice", "ramen-spice-medium")
        assertTrue(chosen.canAdd)
    }

    @Test
    fun singleChoiceSwapsAndCannotBeEmptied() {
        val medium = shoyu.toggle("ramen-spice", "ramen-spice-medium")
        val hot = medium.toggle("ramen-spice", "ramen-spice-hot")
        assertEquals(listOf("ramen-spice-hot"), hot.selected.getValue("ramen-spice").map { it.id })
        assertEquals(hot, hot.toggle("ramen-spice", "ramen-spice-hot"))
    }

    @Test
    fun multiChoiceStopsAtItsMaximumAndSkipsSoldOutOptions() {
        val group = ModifierGroup(
            "toppings", "Toppings", minSelections = 0, maxSelections = 2,
            options = listOf(
                ModifierOption("a", "A"), ModifierOption("b", "B"), ModifierOption("c", "C"),
                ModifierOption("gone", "Gone", isSoldOut = true),
            ),
        )
        val item = MenuItem("i", "s", "c", "Item", "", Money.yen(500), modifierGroups = listOf(group))
        val two = ItemDraft(item).toggle("toppings", "a").toggle("toppings", "b")

        assertEquals(two, two.toggle("toppings", "c"), "A third choice is blocked at the maximum of 2")
        assertEquals(two, two.toggle("toppings", "gone"))
        assertEquals(listOf("b"), two.toggle("toppings", "a").selected.getValue("toppings").map { it.id })
    }

    @Test
    fun priceFollowsOptionsAndQuantity() {
        val draft = shoyu.toggle("ramen-spice", "ramen-spice-medium").toggle("ramen-toppings", "topping-egg")
        assertEquals(Money.yen(1_100), draft.totalPrice)
        assertEquals(Money.yen(2_200), draft.withQuantity(2).totalPrice)
    }

    @Test
    fun quantityStaysBetweenOneAndTheMaximum() {
        assertEquals(1, shoyu.withQuantity(0).quantity)
        assertEquals(MAX_QUANTITY, shoyu.withQuantity(MAX_QUANTITY + 5).quantity)
    }

    @Test
    fun soldOutItemCannotBeAdded() {
        val soldOut = shoyu.toggle("ramen-spice", "ramen-spice-medium").let { it.withItem(it.item.copy(isSoldOut = true)) }
        assertFalse(soldOut.canAdd)
    }

    @Test
    fun sameChoicesMakeTheSameLineId() {
        val a = shoyu.toggle("ramen-toppings", "topping-egg").toggle("ramen-toppings", "topping-nori")
        val b = shoyu.toggle("ramen-toppings", "topping-nori").toggle("ramen-toppings", "topping-egg")
        assertEquals(a.toCartLine().lineId, b.toCartLine().lineId)
    }
}
