package tech.vauldex.dekita.di

import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import tech.vauldex.dekita.data.CartRepository
import tech.vauldex.dekita.data.InboxRepository
import tech.vauldex.dekita.data.MenuRepository
import tech.vauldex.dekita.data.OrderRepository
import tech.vauldex.dekita.data.PaymentRepository
import tech.vauldex.dekita.data.StoreRepository
import tech.vauldex.dekita.data.mock.FakeCartRepository
import tech.vauldex.dekita.data.mock.FakeInboxRepository
import tech.vauldex.dekita.data.mock.FakeMenuRepository
import tech.vauldex.dekita.data.mock.FakeOrderRepository
import tech.vauldex.dekita.data.mock.FakePaymentRepository
import tech.vauldex.dekita.data.mock.FakeStoreRepository
import tech.vauldex.dekita.data.mock.MockConfig
import tech.vauldex.dekita.data.mock.StoreFeedSimulator
import tech.vauldex.dekita.data.mock.seedUser
import tech.vauldex.dekita.domain.UserProfile

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

    val storeRepository: StoreRepository get() = stores
    val menuRepository: MenuRepository get() = menus
    val cartRepository: CartRepository get() = cart
    val orderRepository: OrderRepository get() = orders
    val paymentRepository: PaymentRepository get() = payments
    val inboxRepository: InboxRepository get() = inbox

    val userProfile: UserProfile = seedUser

    /** Debug panel only: live changes to the store feed. */
    val storeFeedSimulator: StoreFeedSimulator get() = stores

    /** Debug panel only: no favorites and no orders, to check the empty Favorites and History states. */
    fun simulateNewUser() {
        stores.clearFavorites()
        orders.clearAll()
    }

    /** Puts every repository back to the seed data, with the loading delay. */
    fun resetMockData() {
        listOf(stores, menus, cart, orders, payments, inbox).forEach { it.reset() }
    }

    companion object {
        /**
         * One container per process. Android can recreate the activity while ViewModels survive,
         * so the app must not build a second container that those ViewModels don't see.
         */
        val instance: AppContainer by lazy { AppContainer() }
    }
}
