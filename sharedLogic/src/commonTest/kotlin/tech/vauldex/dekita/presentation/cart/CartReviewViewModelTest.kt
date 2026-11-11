package tech.vauldex.dekita.presentation.cart

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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import tech.vauldex.dekita.data.mock.FakeAuthRepository
import tech.vauldex.dekita.data.mock.FakeCartRepository
import tech.vauldex.dekita.data.mock.FakeStoreRepository
import tech.vauldex.dekita.data.mock.MockConfig
import tech.vauldex.dekita.data.mock.seedSampleCart
import tech.vauldex.dekita.domain.SsoProvider
import tech.vauldex.dekita.presentation.collectEvents

@OptIn(ExperimentalCoroutinesApi::class)
class CartReviewViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val config = MockConfig()
    private val cart = FakeCartRepository()
    private val auth = FakeAuthRepository(config)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun guestIsSentToSignInAndTheCartSurvivesIt() = runTest(mainDispatcher) {
        val sample = seedSampleCart()
        sample.lines.forEach { cart.addLine(sample.mode, it) }
        val viewModel = CartReviewViewModel(cart, FakeStoreRepository(backgroundScope, config), auth)
        backgroundScope.launch { viewModel.state.collect {} }
        val events = collectEvents(viewModel.events)
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
        assertEquals("Test Ramen Bar", viewModel.state.value.store?.name)

        viewModel.payNow()
        runCurrent()
        assertEquals(listOf<CartReviewEvent>(CartReviewEvent.RequireSignIn), events)

        auth.signInWith(SsoProvider.APPLE)
        runCurrent()
        assertTrue(viewModel.state.value.isSignedIn)
        assertEquals(sample.lines, viewModel.state.value.cart?.lines)

        viewModel.payNow()
        runCurrent()
        assertEquals(listOf(CartReviewEvent.RequireSignIn, CartReviewEvent.PaymentComingLater), events)
    }

    @Test
    fun emptyCartHasNothingToPay() = runTest(mainDispatcher) {
        val viewModel = CartReviewViewModel(cart, FakeStoreRepository(backgroundScope, config), auth)
        backgroundScope.launch { viewModel.state.collect {} }
        val events = collectEvents(viewModel.events)
        runCurrent()

        assertNull(viewModel.state.value.cart)
        assertFalse(viewModel.state.value.isSignedIn)
        viewModel.payNow()
        runCurrent()
        assertTrue(events.isEmpty())
    }
}
