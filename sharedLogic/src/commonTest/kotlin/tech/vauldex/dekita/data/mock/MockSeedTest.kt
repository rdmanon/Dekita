package tech.vauldex.dekita.data.mock

import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tech.vauldex.dekita.domain.AuthInput
import tech.vauldex.dekita.domain.BusinessType
import tech.vauldex.dekita.domain.CompletionReason
import tech.vauldex.dekita.domain.Money
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.OrderStatusMachine
import tech.vauldex.dekita.domain.PaymentType
import tech.vauldex.dekita.domain.StoreAvailability

class MockSeedTest {

    private val now = Instant.fromEpochSeconds(1_800_000_000)
    private val obviouslyFake = Regex("""\b(Test|Sample|Mock)\b""", RegexOption.IGNORE_CASE)

    @Test
    fun everyBusinessTypeIsSeeded() {
        assertEquals(BusinessType.entries.toSet(), seedStores.map { it.businessType }.toSet())
    }

    @Test
    fun ticketExampleStoresHaveTheirBusinessType() {
        val byName = seedStores.associateBy { it.name }
        assertEquals(BusinessType.IN_STORE_ONLY, byName.getValue("Sample Massage Studio").businessType)
        assertEquals(BusinessType.PICKUP_ONLY, byName.getValue("Test Mart").businessType)
        assertEquals(BusinessType.BOTH, byName.getValue("Test Ramen Bar").businessType)
    }

    @Test
    fun pausedAndFullStoresAreSeeded() {
        val availability = seedStores.map { it.availability }
        assertTrue(StoreAvailability.PAUSED in availability)
        assertTrue(StoreAvailability.FULL in availability)
    }

    @Test
    fun eachStoreIsItsWalkingTimeFromTheMockCustomer() {
        for (store in seedStores) {
            val meters = seedCustomerLocation.distanceTo(store.location).roundToInt()
            assertEquals(store.walkMinutes * METERS_PER_WALK_MINUTE, meters, store.name)
        }
    }

    @Test
    fun queueCountsCoverLowAndHigh() {
        val ahead = seedStores.mapNotNull { it.queue?.ordersAhead }
        assertTrue(ahead.any { it in 0..2 }, "Needs a queue of 0 to 2, got $ahead")
        assertTrue(ahead.any { it >= 15 }, "Needs a queue of 15 or more, got $ahead")
    }

    @Test
    fun soldOutItemsAndOptionsAreSeeded() {
        val items = seedMenus.values.flatMap { it.items }
        assertTrue(items.any { it.isSoldOut })
        assertTrue(items.flatMap { it.modifierGroups }.flatMap { it.options }.any { it.isSoldOut })
    }

    @Test
    fun everyStoreHasAMenuOfItsOwnItems() {
        for (store in seedStores) {
            val menu = seedMenus.getValue(store.id)
            assertTrue(menu.items.isNotEmpty(), "${store.name} has no items")
            assertTrue(menu.items.all { it.storeId == store.id }, "${store.name} has another store's items")
            val categoryIds = menu.categories.map { it.id }.toSet()
            assertTrue(menu.items.all { it.categoryId in categoryIds }, "${store.name} has an item without a category")
        }
    }

    @Test
    fun favoritesPointAtSeededStores() {
        assertTrue(seedFavoriteStoreIds.isNotEmpty())
        assertTrue(seedFavoriteStoreIds.all { id -> seedStores.any { it.id == id } })
    }

    @Test
    fun favoriteItemsPointAtSeededItems() {
        assertTrue(seedFavoriteItems.isNotEmpty())
        for (favorite in seedFavoriteItems) {
            val item = seedMenus.getValue(favorite.storeId).items.first { it.id == favorite.itemId }
            assertEquals(item.name, favorite.itemName)
        }
        assertTrue(seedFavoriteItems.any { it.itemName == "Shoyu Ramen" && it.storeId == SeedStoreIds.RAMEN_BAR })
    }

    @Test
    fun recommendationsPointAtSeededStores() {
        assertTrue(seedRecommendedStoreIds.isNotEmpty())
        assertTrue(seedRecommendedStoreIds.all { id -> seedStores.any { it.id == id } })
    }

    @Test
    fun feedCarriesItemNamesForSearch() {
        for (store in seedStores) {
            assertEquals(seedMenus.getValue(store.id).items.map { it.name }, store.itemNames, store.name)
        }
    }

    @Test
    fun categoriesIncludeNonRestaurantBusinesses() {
        val categories = seedStores.map { it.category }.toSet()
        assertTrue("Salon" in categories)
        assertTrue("Convenience store" in categories)
    }

    @Test
    fun aLongJapaneseStoreNameIsSeededForTruncationChecks() {
        val japanese = Regex("[\u3040-\u30FF\u4E00-\u9FFF]")
        assertTrue(seedStores.any { japanese.containsMatchIn(it.name) && it.name.length > 25 })
    }

    @Test
    fun orderHistoryHasBothCompletionReasons() {
        val completed = seedOrders(now).filter { it.status == OrderStatus.COMPLETED }
        assertEquals(CompletionReason.entries.toSet(), completed.mapNotNull { it.completionReason }.toSet())
    }

    @Test
    fun seededOrdersOnlyMoveThroughAllowedTransitions() {
        for (order in seedOrders(now)) {
            val statuses = order.history.map { it.status }
            assertEquals(OrderStatus.DRAFT, statuses.first())
            statuses.zipWithNext().forEach { (from, to) ->
                assertTrue(OrderStatusMachine.canTransition(from, to, order.mode), "Order ${order.number}: $from to $to")
            }
            assertEquals(order.status, statuses.last())
        }
    }

    @Test
    fun activeTicketMatchesTheDesign() {
        val order = seedOrders(now).first { it.number == 42 }
        assertEquals(2, order.itemCount)
        assertEquals(Money.yen(1550), order.total)
    }

    @Test
    fun namesAndAccountsAreObviouslyFake() {
        seedStores.forEach { assertTrue(obviouslyFake.containsMatchIn(it.name), "Store name looks real: ${it.name}") }
        assertTrue(obviouslyFake.containsMatchIn(seedUser.displayName))
        assertTrue(seedUser.email.orEmpty().endsWith("@example.com"))
        seedPaymentMethods
            .filter { it.type == PaymentType.CARD || it.type == PaymentType.PAYPAY || it.type == PaymentType.RAKUTEN_PAY }
            .forEach { assertTrue(obviouslyFake.containsMatchIn(it.label), "Payment label looks real: ${it.label}") }
    }

    @Test
    fun authValuesAreTheDocumentedFakes() {
        assertEquals("123456", SeedAuth.TEST_OTP)
        assertTrue(SeedAuth.TEST_EMAIL.endsWith("@example.com"))
        // No word boundary inside "Test1234", so check the prefix rather than the obviouslyFake regex.
        assertTrue(SeedAuth.TEST_PASSWORD.startsWith("Test"))
        // The test account must also pass the sign-up rule, so it works on either form.
        assertTrue(AuthInput.isValidPassword(SeedAuth.TEST_PASSWORD))
        assertTrue(seedAccounts.keys.all { it == it.lowercase() && it.endsWith("@example.com") })
    }

    @Test
    fun sampleCartMatchesTheDesign() {
        val cart = seedSampleCart()
        assertEquals(2, cart.itemCount)
        assertEquals(Money.yen(1550), cart.total)
    }
}
