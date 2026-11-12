package tech.vauldex.dekita.presentation.orders

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.data.mock.FakeAuthRepository
import tech.vauldex.dekita.data.mock.FakeInboxRepository
import tech.vauldex.dekita.data.mock.FakeOrderRepository
import tech.vauldex.dekita.data.mock.FakeStoreRepository
import tech.vauldex.dekita.data.mock.MockConfig
import tech.vauldex.dekita.domain.InboxKind
import tech.vauldex.dekita.domain.SsoProvider

/** RCPT-01: Rate & Add to Favorites, recorded once, with skip leaving one inbox message. */
@OptIn(ExperimentalCoroutinesApi::class)
class RateOrderViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    private val fixedClock = object : Clock {
        override fun now() = Instant.fromEpochSeconds(1_800_000_000)
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture(
        val orders: FakeOrderRepository,
        val stores: FakeStoreRepository,
        val inbox: FakeInboxRepository,
        val auth: FakeAuthRepository,
    ) {
        fun rateMessages() = inbox.messages.value.dataOrNull().orEmpty().filter { it.kind == InboxKind.RATE_ORDER }
    }

    private suspend fun TestScope.fixture(signIn: Boolean = true): Fixture {
        val f = Fixture(
            FakeOrderRepository(backgroundScope, MockConfig(), fixedClock),
            FakeStoreRepository(backgroundScope, MockConfig()),
            FakeInboxRepository(backgroundScope, MockConfig(), fixedClock),
            FakeAuthRepository(MockConfig()),
        )
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
        if (signIn) f.auth.signInWith(SsoProvider.APPLE)
        runCurrent()
        return f
    }

    private fun TestScope.rate(f: Fixture, orderId: String) =
        RateOrderViewModel(orderId, f.orders, f.stores, f.inbox, f.auth).also { viewModel ->
            backgroundScope.launch { viewModel.state.collect {} }
            runCurrent()
        }

    @Test
    fun submitTurnsOnWithStarsAndRecordsOnce() = runTest(mainDispatcher) {
        val f = fixture()
        val rate = rate(f, "order-37")
        assertEquals("Order #37 · Sample Curry Stand", rate.state.value.heading)
        assertFalse(rate.state.value.canSubmit, "No stars yet")
        assertFalse(rate.submit())

        rate.setStars(4)
        rate.toggleTag("Tasty")
        rate.toggleTag("Not a tag")
        runCurrent()
        assertTrue(rate.state.value.canSubmit)
        assertEquals("4 of 5 stars", rate.state.value.starsDescription)

        assertTrue(rate.submit())
        assertFalse(rate.submit(), "A double tap records one rating")
        runCurrent()
        val rating = f.orders.orders.value.dataOrNull()?.first { it.id == "order-37" }?.rating
        assertEquals(4, rating?.stars)
        assertEquals(setOf("Tasty"), rating?.tags)
        assertTrue(rate.state.value.alreadyRated)
        assertFalse(rate.state.value.canSubmit)
    }

    @Test
    fun favoriteIsSavedOnTapAndShowsInDiscoverFavorites() = runTest(mainDispatcher) {
        val f = fixture()
        val rate = rate(f, "order-29")
        val shoyu = rate.state.value.items.single()
        assertTrue(shoyu.isFavorite, "Shoyu Ramen is a seed favorite")

        rate.toggleFavorite(shoyu.item.itemId)
        runCurrent()
        assertFalse(rate.state.value.items.single().isFavorite)
        assertTrue(f.stores.favoriteItems.value.none { it.itemId == shoyu.item.itemId })

        rate.toggleFavorite(shoyu.item.itemId)
        runCurrent()
        assertEquals(shoyu.item, f.stores.favoriteItems.value.first(), "Newest first")
    }

    @Test
    fun skipLeavesOneInboxMessageAndNoneOnceRated() = runTest(mainDispatcher) {
        val f = fixture()
        val rate = rate(f, "order-37")

        rate.skip()
        rate.skip()
        runCurrent()
        assertEquals(1, f.rateMessages().size)
        assertEquals("order-37", f.rateMessages().single().orderId)
        assertEquals("Rate order #37", f.rateMessages().single().title)

        val rated = rate(f, "order-18")
        rated.skip()
        runCurrent()
        assertEquals(1, f.rateMessages().size, "#18 is already rated, so skipping adds nothing")
    }

    @Test
    fun aRatedOrderShowsItsRatingAndIgnoresChanges() = runTest(mainDispatcher) {
        val f = fixture()
        val rate = rate(f, "order-18")

        assertTrue(rate.state.value.alreadyRated)
        assertEquals(5, rate.state.value.stars)
        rate.setStars(1)
        runCurrent()
        assertEquals(5, rate.state.value.stars)
    }

    @Test
    fun noOrderForGuestsOrOrdersNotCompleted() = runTest(mainDispatcher) {
        val f = fixture()
        assertNull(rate(f, "order-42").state.value.order, "Still cooking")
        assertNull(rate(f, "no-such-order").state.value.order)

        val guest = fixture(signIn = false)
        val guestRate = rate(guest, "order-37")
        assertNull(guestRate.state.value.order)
        guestRate.setStars(5)
        assertFalse(guestRate.submit())
        guestRate.skip()
        runCurrent()
        assertTrue(guest.rateMessages().isEmpty())
    }
}
