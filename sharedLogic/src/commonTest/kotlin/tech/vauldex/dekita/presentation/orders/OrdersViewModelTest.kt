package tech.vauldex.dekita.presentation.orders

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
import tech.vauldex.dekita.data.mock.FakeAuthRepository
import tech.vauldex.dekita.data.mock.FakeInboxRepository
import tech.vauldex.dekita.data.mock.FakeOrderRepository
import tech.vauldex.dekita.data.mock.MockConfig
import tech.vauldex.dekita.domain.InboxKind
import tech.vauldex.dekita.domain.SsoProvider

/** RCPT-01: the Orders inbox. Active first, then inbox messages, then receipts, each newest first. */
@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModelTest {

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

    private class Fixture(val orders: FakeOrderRepository, val inbox: FakeInboxRepository, val auth: FakeAuthRepository) {
        val viewModel = OrdersViewModel(orders, inbox, auth)
    }

    private suspend fun TestScope.fixture(): Fixture {
        val f = Fixture(
            FakeOrderRepository(backgroundScope, MockConfig(), fixedClock),
            FakeInboxRepository(backgroundScope, MockConfig(), fixedClock),
            FakeAuthRepository(MockConfig()),
        )
        backgroundScope.launch { f.viewModel.state.collect {} }
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
        f.auth.signInWith(SsoProvider.APPLE)
        runCurrent()
        return f
    }

    @Test
    fun sectionsAreNewestFirst() = runTest(mainDispatcher) {
        val f = fixture()
        val state = f.viewModel.state.value

        assertTrue(state.isSignedIn)
        assertEquals(listOf("order-44", "order-43", "order-42"), state.active.map { it.id })
        assertEquals(listOf("msg-order-43-ready", "msg-order-42-preparing", "msg-welcome"), state.inbox.map { it.message.id })
        assertEquals(listOf("order-37", "order-29", "order-18"), state.receipts.map { it.id })
        assertFalse(state.isEmpty)
    }

    @Test
    fun aRatePromptIsDoneOnceItsOrderIsRated() = runTest(mainDispatcher) {
        val f = fixture()
        f.inbox.add("msg-rate-order-29", "Rate order #29", "How was it?", InboxKind.RATE_ORDER, "order-29")
        runCurrent()
        val prompt = f.viewModel.state.value.inbox.first()
        assertTrue(prompt.isRatePrompt)
        assertFalse(prompt.isDone)

        f.orders.rate("order-29", 3, emptySet())
        runCurrent()
        assertTrue(f.viewModel.state.value.inbox.first().isDone)
    }

    @Test
    fun openingAMessageMarksItRead() = runTest(mainDispatcher) {
        val f = fixture()
        f.viewModel.openMessage("msg-order-43-ready")
        runCurrent()
        assertTrue(f.viewModel.state.value.inbox.first { it.message.id == "msg-order-43-ready" }.message.isRead)
    }

    @Test
    fun aNewCustomerSeesTheEmptyState() = runTest(mainDispatcher) {
        val f = fixture()
        f.orders.clearAll()
        f.inbox.clearAll()
        runCurrent()
        assertTrue(f.viewModel.state.value.isEmpty)
    }
}
