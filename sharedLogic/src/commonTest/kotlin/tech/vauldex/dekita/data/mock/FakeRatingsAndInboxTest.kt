package tech.vauldex.dekita.data.mock

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.domain.FavoriteItem
import tech.vauldex.dekita.domain.InboxKind
import tech.vauldex.dekita.domain.Money

/** RCPT-01: ratings recorded once, item favorites, and inbox messages added in the session. */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeRatingsAndInboxTest {

    private val fixedClock = object : Clock {
        override fun now() = Instant.fromEpochSeconds(1_800_000_000)
    }

    private fun TestScope.finishLoading() {
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
    }

    private fun FakeOrderRepository.order(id: String) = orders.value.dataOrNull().orEmpty().first { it.id == id }

    @Test
    fun aCompletedOrderIsRatedOnceWithValidStarsAndTags() = runTest {
        val repository = FakeOrderRepository(backgroundScope, MockConfig(), fixedClock)
        finishLoading()

        assertFalse(repository.rate("order-42", 4, emptySet()), "Still cooking")
        assertFalse(repository.rate("order-29", 0, emptySet()), "Below one star")
        assertFalse(repository.rate("order-29", 6, emptySet()), "Above five stars")
        assertFalse(repository.rate("order-29", 4, setOf("<script>")), "Unknown tag")
        assertNull(repository.order("order-29").rating)

        assertTrue(repository.rate("order-29", 4, setOf("Tasty")))
        assertFalse(repository.rate("order-29", 1, emptySet()), "A second tap records nothing")
        assertEquals(4, repository.order("order-29").rating?.stars)
        assertEquals(setOf("Tasty"), repository.order("order-29").rating?.tags)
        assertEquals(5, repository.order("order-18").rating?.stars, "Seed #18 is already rated")
    }

    @Test
    fun favoriteItemsToggleNewestFirst() = runTest {
        val repository = FakeStoreRepository(backgroundScope, MockConfig())
        val gyoza = FavoriteItem(SeedStoreIds.RAMEN_BAR, "item-gyoza", "Gyoza", Money(450))

        repository.toggleFavoriteItem(gyoza)
        assertEquals(gyoza, repository.favoriteItems.value.first())

        repository.toggleFavoriteItem(gyoza)
        assertTrue(repository.favoriteItems.value.none { it.itemId == "item-gyoza" })
        assertEquals(seedFavoriteItems, repository.favoriteItems.value)
    }

    @Test
    fun anInboxMessageIsAddedOnceAndClearedByReset() = runTest {
        val inbox = FakeInboxRepository(backgroundScope, MockConfig(), fixedClock)
        finishLoading()
        val before = inbox.messages.value.dataOrNull().orEmpty().size

        inbox.add("msg-rate-order-29", "Rate order #29", "How was it?", InboxKind.RATE_ORDER, "order-29")
        inbox.add("msg-rate-order-29", "Rate order #29", "How was it?", InboxKind.RATE_ORDER, "order-29")

        val messages = inbox.messages.value.dataOrNull().orEmpty()
        assertEquals(before + 1, messages.size)
        assertEquals(InboxKind.RATE_ORDER, messages.first().kind)
        assertEquals(fixedClock.now(), messages.first().receivedAt)
        assertFalse(messages.first().isRead)

        inbox.reset()
        finishLoading()
        assertEquals(before, inbox.messages.value.dataOrNull().orEmpty().size)
    }
}
