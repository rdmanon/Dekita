package tech.vauldex.dekita.di

import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import tech.vauldex.dekita.data.AuthRepository
import tech.vauldex.dekita.data.CartRepository
import tech.vauldex.dekita.data.InboxRepository
import tech.vauldex.dekita.data.LocationRepository
import tech.vauldex.dekita.data.MenuRepository
import tech.vauldex.dekita.data.OrderRepository
import tech.vauldex.dekita.data.PaymentRepository
import tech.vauldex.dekita.data.QrScanner
import tech.vauldex.dekita.data.StoreRepository
import tech.vauldex.dekita.data.mock.CheckInSimulator
import tech.vauldex.dekita.data.mock.FakeAuthRepository
import tech.vauldex.dekita.data.mock.FakeCartRepository
import tech.vauldex.dekita.data.mock.FakeCheckInSimulator
import tech.vauldex.dekita.data.mock.FakeInboxRepository
import tech.vauldex.dekita.data.mock.FakeLocationRepository
import tech.vauldex.dekita.data.mock.FakeMenuRepository
import tech.vauldex.dekita.data.mock.FakeOrderRepository
import tech.vauldex.dekita.data.mock.FakePaymentRepository
import tech.vauldex.dekita.data.mock.FakeQrScanner
import tech.vauldex.dekita.data.mock.FakeStoreRepository
import tech.vauldex.dekita.data.mock.MockConfig
import tech.vauldex.dekita.data.mock.MenuSimulator
import tech.vauldex.dekita.data.mock.OrderSimulator
import tech.vauldex.dekita.data.mock.StoreFeedSimulator
import tech.vauldex.dekita.data.mock.seedSampleCart
import tech.vauldex.dekita.presentation.orders.OrderNotifications

/** Manual wiring for the mockup. Every repository is an in-memory fake over seed data. */
class AppContainer(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    clock: Clock = Clock.System,
) {
    val mockConfig = MockConfig()

    private val stores = FakeStoreRepository(scope, mockConfig)
    private val menus = FakeMenuRepository(scope, mockConfig)
    private val cart = FakeCartRepository()
    private val orders = FakeOrderRepository(scope, mockConfig, clock)
    private val payments = FakePaymentRepository(scope, mockConfig)
    private val inbox = FakeInboxRepository(scope, mockConfig, clock)
    private val auth = FakeAuthRepository(mockConfig)
    private val location = FakeLocationRepository()
    private val scanner = FakeQrScanner()

    val storeRepository: StoreRepository get() = stores
    val menuRepository: MenuRepository get() = menus
    val cartRepository: CartRepository get() = cart
    val orderRepository: OrderRepository get() = orders
    val paymentRepository: PaymentRepository get() = payments
    val inboxRepository: InboxRepository get() = inbox
    val authRepository: AuthRepository get() = auth
    val locationRepository: LocationRepository get() = location
    val qrScanner: QrScanner get() = scanner

    /** Debug panel only: live changes to the store feed. */
    val storeFeedSimulator: StoreFeedSimulator get() = stores

    /** Debug only: items selling out while a customer looks at them. */
    val menuSimulator: MenuSimulator get() = menus

    /** Debug only: the store moving an order on, and auto-complete, from the ticket's debug buttons. */
    val orderSimulator: OrderSimulator get() = orders

    /** Order notices for the platforms' system notifications. One per process, like the repositories. */
    val orderNotifications: OrderNotifications = OrderNotifications(orders, auth)

    /** Debug panel only: arrival and QR scans for check-in, in place of GPS and the camera. */
    val checkInSimulator: CheckInSimulator = FakeCheckInSimulator(orders, location, scanner)

    /** Debug panel only: no favorites, orders or messages, to check the empty Favorites, History and Orders states. */
    fun simulateNewUser() {
        stores.clearFavorites()
        orders.clearAll()
        inbox.clearAll()
    }

    /** Debug panel only: replaces the cart with the design's two-item cart, to try the checkout sign-in. */
    fun fillSampleCart() {
        val sample = seedSampleCart()
        cart.clear()
        sample.lines.forEach { cart.addLine(sample.mode, it) }
    }

    /** Puts every repository back to the seed data, with the loading delay. Also signs out. */
    fun resetMockData() {
        listOf(stores, menus, cart, orders, payments, inbox, auth, location).forEach { it.reset() }
    }

    companion object {
        /**
         * One container per process. Android can recreate the activity while ViewModels survive,
         * so the app must not build a second container that those ViewModels don't see.
         */
        val instance: AppContainer by lazy { AppContainer() }
    }
}
