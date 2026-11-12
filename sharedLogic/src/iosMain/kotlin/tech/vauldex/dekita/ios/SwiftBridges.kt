package tech.vauldex.dekita.ios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.math.roundToInt
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.mock.SeedAuth
import tech.vauldex.dekita.data.mock.availabilityStatus
import tech.vauldex.dekita.data.mock.debugLabel
import tech.vauldex.dekita.data.mock.defaultAvailabilityStoreId
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.di.AppContainer
import tech.vauldex.dekita.domain.BusinessType
import tech.vauldex.dekita.domain.CheckInQr
import tech.vauldex.dekita.domain.ClientPlatform
import tech.vauldex.dekita.domain.DialCode
import tech.vauldex.dekita.domain.Geofence
import tech.vauldex.dekita.domain.Menu
import tech.vauldex.dekita.domain.MenuItem
import tech.vauldex.dekita.domain.ModifierOption
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.SignInMethod
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.presentation.account.AccountUiState
import tech.vauldex.dekita.presentation.account.AccountViewModel
import tech.vauldex.dekita.presentation.auth.AuthEvent
import tech.vauldex.dekita.presentation.auth.EmailAuthMode
import tech.vauldex.dekita.presentation.auth.EmailAuthUiState
import tech.vauldex.dekita.presentation.auth.EmailAuthViewModel
import tech.vauldex.dekita.presentation.auth.ForgotPasswordUiState
import tech.vauldex.dekita.presentation.auth.ForgotPasswordViewModel
import tech.vauldex.dekita.presentation.auth.OtpUiState
import tech.vauldex.dekita.presentation.auth.OtpViewModel
import tech.vauldex.dekita.presentation.auth.PhoneEntryUiState
import tech.vauldex.dekita.presentation.auth.PhoneEntryViewModel
import tech.vauldex.dekita.presentation.auth.SignInUiState
import tech.vauldex.dekita.presentation.auth.SignInViewModel
import tech.vauldex.dekita.presentation.cart.CartReviewEvent
import tech.vauldex.dekita.presentation.cart.CartReviewUiState
import tech.vauldex.dekita.presentation.cart.CartReviewViewModel
import tech.vauldex.dekita.presentation.checkin.CheckInUiState
import tech.vauldex.dekita.presentation.checkin.CheckInViewModel
import tech.vauldex.dekita.presentation.checkin.QrScannerUiState
import tech.vauldex.dekita.presentation.checkin.QrScannerViewModel
import tech.vauldex.dekita.presentation.checkin.SeatCheckEvent
import tech.vauldex.dekita.presentation.checkin.SeatCheckUiState
import tech.vauldex.dekita.presentation.checkin.SeatCheckViewModel
import tech.vauldex.dekita.presentation.discover.DiscoverUiState
import tech.vauldex.dekita.presentation.discover.DiscoverViewModel
import tech.vauldex.dekita.presentation.discover.FavoritesUiState
import tech.vauldex.dekita.presentation.discover.FavoritesViewModel
import tech.vauldex.dekita.presentation.discover.HistoryUiState
import tech.vauldex.dekita.presentation.discover.HistoryViewModel
import tech.vauldex.dekita.presentation.discover.WalkLimit
import tech.vauldex.dekita.presentation.orders.OrderNotice
import tech.vauldex.dekita.presentation.orders.OrderTicketEvent
import tech.vauldex.dekita.presentation.orders.OrderTicketUiState
import tech.vauldex.dekita.presentation.orders.OrderTicketViewModel
import tech.vauldex.dekita.presentation.orders.OrdersUiState
import tech.vauldex.dekita.presentation.orders.RateOrderUiState
import tech.vauldex.dekita.presentation.orders.RateOrderViewModel
import tech.vauldex.dekita.presentation.orders.OrdersViewModel
import tech.vauldex.dekita.presentation.payment.PaymentEvent
import tech.vauldex.dekita.presentation.payment.PaymentTarget
import tech.vauldex.dekita.presentation.payment.PaymentUiState
import tech.vauldex.dekita.presentation.payment.PaymentViewModel
import tech.vauldex.dekita.presentation.store.StoreDetailUiState
import tech.vauldex.dekita.presentation.store.StoreDetailViewModel

// Swift-facing wrappers around the shared view models. Swift can't collect a Kotlin Flow, so each
// bridge exposes its state as a typed callback, and owns its view models so they are cleared when
// the SwiftUI screen goes away, as Android's back stack does.

/** A running observation started from Swift. Cancel it when the Swift screen goes away. */
class FlowWatch internal constructor(private val job: Job) {
    fun cancel() {
        job.cancel()
    }
}

private val mainScope = MainScope()

/** Collects on the main thread, so Swift can update its views straight from the callback. */
private fun <T> Flow<T>.watch(onEach: (T) -> Unit): FlowWatch = FlowWatch(mainScope.launch { collect { onEach(it) } })

/** Owns one SwiftUI screen's view models. Call [close] when the screen is gone. */
abstract class ScreenBridge {
    private val store = ViewModelStore()

    protected val container: AppContainer get() = AppContainer.instance

    protected fun <VM : ViewModel> makeViewModel(type: KClass<VM>, create: () -> VM): VM {
        val factory = viewModelFactory { addInitializer(type) { create() } }
        return ViewModelProvider.create(store, factory)[type]
    }

    fun close() {
        store.clear()
    }
}

class DiscoverBridge : ScreenBridge() {
    val viewModel: DiscoverViewModel = makeViewModel(DiscoverViewModel::class) {
        DiscoverViewModel(container.storeRepository, container.orderRepository, container.cartRepository, container.authRepository)
    }

    val currentState: DiscoverUiState get() = viewModel.state.value

    fun watch(onState: (DiscoverUiState) -> Unit): FlowWatch = viewModel.state.watch(onState)

    /** The walking-distance choices, in order, for the filter sheet. */
    val walkLimits: List<WalkLimit> = WalkLimit.entries

    /** The status filter's choices, in order. */
    val statuses: List<StoreAvailability> = StoreAvailability.entries
}

class FavoritesBridge : ScreenBridge() {
    val viewModel: FavoritesViewModel = makeViewModel(FavoritesViewModel::class) {
        FavoritesViewModel(container.storeRepository)
    }

    val currentState: FavoritesUiState get() = viewModel.state.value

    fun watch(onState: (FavoritesUiState) -> Unit): FlowWatch = viewModel.state.watch(onState)
}

class HistoryBridge : ScreenBridge() {
    val viewModel: HistoryViewModel = makeViewModel(HistoryViewModel::class) {
        HistoryViewModel(container.storeRepository, container.orderRepository)
    }

    val currentState: HistoryUiState get() = viewModel.state.value

    fun watch(onState: (HistoryUiState) -> Unit): FlowWatch = viewModel.state.watch(onState)
}

class StoreDetailBridge(storeId: String) : ScreenBridge() {
    val viewModel: StoreDetailViewModel = makeViewModel(StoreDetailViewModel::class) {
        StoreDetailViewModel(
            storeId, container.storeRepository, container.menuRepository, container.cartRepository, container.menuSimulator,
        )
    }

    val currentState: StoreDetailUiState get() = viewModel.state.value

    fun watch(onState: (StoreDetailUiState) -> Unit): FlowWatch = viewModel.state.watch(onState)
}

/** The loaded menu, or null while loading or after an error. Saves Swift from casting the generic LoadState. */
val StoreDetailUiState.loadedMenu: Menu? get() = menu.dataOrNull()

/** The menu's error message, or null. */
val StoreDetailUiState.menuError: String? get() = (menu as? LoadState.Error)?.message

/** The heart button in the iOS store screen's navigation bar. */
class FavoriteStoreBridge(private val storeId: String) {
    private val repository get() = AppContainer.instance.storeRepository

    val isFavorite: Boolean get() = storeId in repository.favoriteStoreIds.value

    fun watch(onChange: (Boolean) -> Unit): FlowWatch =
        repository.favoriteStoreIds.map { storeId in it }.distinctUntilChanged().watch(onChange)

    fun toggle() {
        repository.toggleFavorite(storeId)
    }
}

/** Seconds since 1970, so Swift can build a Date without touching kotlin.time.Instant. */
val Order.createdEpochSeconds: Long get() = createdAt.epochSeconds

class OrdersBridge : ScreenBridge() {
    val viewModel: OrdersViewModel = makeViewModel(OrdersViewModel::class) {
        OrdersViewModel(container.orderRepository, container.inboxRepository, container.authRepository)
    }

    val currentState: OrdersUiState get() = viewModel.state.value

    fun watch(onState: (OrdersUiState) -> Unit): FlowWatch = viewModel.state.watch(onState)
}

/** ORDER-01: one order's ticket, with live status and [Confirm]. */
class OrderTicketBridge(orderId: String) : ScreenBridge() {
    val viewModel: OrderTicketViewModel = makeViewModel(OrderTicketViewModel::class) {
        OrderTicketViewModel(
            orderId,
            container.orderRepository,
            container.authRepository,
            container.paymentRepository,
            container.inboxRepository,
            container.orderSimulator,
            container.storeRepository,
        )
    }

    val currentState: OrderTicketUiState get() = viewModel.state.value

    fun watch(onState: (OrderTicketUiState) -> Unit): FlowWatch = viewModel.state.watch(onState)

    fun watchEvents(onEvent: (OrderTicketEvent) -> Unit): FlowWatch = viewModel.events.watch(onEvent)
}

/** RCPT-01: Rate & Add to Favorites for one completed order. */
class RateOrderBridge(orderId: String) : ScreenBridge() {
    val viewModel: RateOrderViewModel = makeViewModel(RateOrderViewModel::class) {
        RateOrderViewModel(
            orderId,
            container.orderRepository,
            container.storeRepository,
            container.inboxRepository,
            container.authRepository,
        )
    }

    val currentState: RateOrderUiState get() = viewModel.state.value

    fun watch(onState: (RateOrderUiState) -> Unit): FlowWatch = viewModel.state.watch(onState)
}

/**
 * The app-wide feed for iOS system notifications. Unlike the screen bridges it is watched for the
 * app's whole life, so notices keep arriving while no order screen is open.
 */
class OrderNoticeBridge {
    private val notifications get() = AppContainer.instance.orderNotifications

    fun watchNotices(onNotice: (OrderNotice) -> Unit): FlowWatch = notifications.notices.watch(onNotice)

    fun watchSignedOut(onSignedOut: () -> Unit): FlowWatch = notifications.signedOut.watch { onSignedOut() }

    /** A notification tap opens the ticket only for a paid order while signed in. */
    fun canOpenTicket(orderId: String): Boolean = notifications.canOpenTicket(orderId)
}

/** "On your way" and the seat check placeholder, for one in-store draft. */
class CheckInBridge(orderId: String) : ScreenBridge() {
    val viewModel: CheckInViewModel = makeViewModel(CheckInViewModel::class) {
        CheckInViewModel(orderId, container.orderRepository, container.storeRepository, container.locationRepository)
    }

    val currentState: CheckInUiState get() = viewModel.state.value

    fun watch(onState: (CheckInUiState) -> Unit): FlowWatch = viewModel.state.watch(onState)
}

/** SEAT-01 seat verification for one checked-in draft. Its Yes opens PAY-01's payment sheet ([PaymentBridge]). */
class SeatCheckBridge(orderId: String) : ScreenBridge() {
    val viewModel: SeatCheckViewModel = makeViewModel(SeatCheckViewModel::class) {
        SeatCheckViewModel(orderId, container.orderRepository, container.storeRepository, container.cartRepository)
    }

    val currentState: SeatCheckUiState get() = viewModel.state.value

    fun watch(onState: (SeatCheckUiState) -> Unit): FlowWatch = viewModel.state.watch(onState)

    fun watchEvents(onEvent: (SeatCheckEvent) -> Unit): FlowWatch = viewModel.events.watch(onEvent)
}

/** The QR fallback. Its view model listens for scans for as long as the screen is open. */
class QrScannerBridge(orderId: String) : ScreenBridge() {
    val viewModel: QrScannerViewModel = makeViewModel(QrScannerViewModel::class) {
        QrScannerViewModel(orderId, container.orderRepository, container.storeRepository, container.qrScanner)
    }

    val currentState: QrScannerUiState get() = viewModel.state.value

    fun watch(onState: (QrScannerUiState) -> Unit): FlowWatch = viewModel.state.watch(onState)
}

class AccountBridge : ScreenBridge() {
    val viewModel: AccountViewModel = makeViewModel(AccountViewModel::class) {
        AccountViewModel(container.authRepository, container.paymentRepository)
    }

    val currentState: AccountUiState get() = viewModel.state.value

    fun watch(onState: (AccountUiState) -> Unit): FlowWatch = viewModel.state.watch(onState)
}

// Sign-in screens. Each also exposes its one-off events, such as "signed in", as a callback.

class SignInBridge : ScreenBridge() {
    val viewModel: SignInViewModel = makeViewModel(SignInViewModel::class) { SignInViewModel(container.authRepository) }

    val currentState: SignInUiState get() = viewModel.state.value

    fun watch(onState: (SignInUiState) -> Unit): FlowWatch = viewModel.state.watch(onState)

    fun watchEvents(onEvent: (AuthEvent) -> Unit): FlowWatch = viewModel.events.watch(onEvent)
}

class PhoneEntryBridge : ScreenBridge() {
    val viewModel: PhoneEntryViewModel = makeViewModel(PhoneEntryViewModel::class) {
        PhoneEntryViewModel(container.authRepository)
    }

    val currentState: PhoneEntryUiState get() = viewModel.state.value

    /** The country code menu's choices, in order. */
    val dialCodes: List<DialCode> = DialCode.entries

    fun watch(onState: (PhoneEntryUiState) -> Unit): FlowWatch = viewModel.state.watch(onState)

    fun watchEvents(onEvent: (AuthEvent) -> Unit): FlowWatch = viewModel.events.watch(onEvent)
}

class OtpBridge(phoneE164: String) : ScreenBridge() {
    val viewModel: OtpViewModel = makeViewModel(OtpViewModel::class) { OtpViewModel(phoneE164, container.authRepository) }

    val currentState: OtpUiState get() = viewModel.state.value

    fun watch(onState: (OtpUiState) -> Unit): FlowWatch = viewModel.state.watch(onState)

    fun watchEvents(onEvent: (AuthEvent) -> Unit): FlowWatch = viewModel.events.watch(onEvent)
}

class EmailAuthBridge(signUp: Boolean) : ScreenBridge() {
    val viewModel: EmailAuthViewModel = makeViewModel(EmailAuthViewModel::class) {
        EmailAuthViewModel(if (signUp) EmailAuthMode.SIGN_UP else EmailAuthMode.LOG_IN, container.authRepository)
    }

    val currentState: EmailAuthUiState get() = viewModel.state.value

    fun watch(onState: (EmailAuthUiState) -> Unit): FlowWatch = viewModel.state.watch(onState)

    fun watchEvents(onEvent: (AuthEvent) -> Unit): FlowWatch = viewModel.events.watch(onEvent)
}

class ForgotPasswordBridge : ScreenBridge() {
    val viewModel: ForgotPasswordViewModel = makeViewModel(ForgotPasswordViewModel::class) {
        ForgotPasswordViewModel(container.authRepository)
    }

    val currentState: ForgotPasswordUiState get() = viewModel.state.value

    fun watch(onState: (ForgotPasswordUiState) -> Unit): FlowWatch = viewModel.state.watch(onState)
}

class CartReviewBridge : ScreenBridge() {
    val viewModel: CartReviewViewModel = makeViewModel(CartReviewViewModel::class) {
        CartReviewViewModel(
            container.cartRepository,
            container.storeRepository,
            container.menuRepository,
            container.orderRepository,
            container.authRepository,
        )
    }

    val currentState: CartReviewUiState get() = viewModel.state.value

    fun watch(onState: (CartReviewUiState) -> Unit): FlowWatch = viewModel.state.watch(onState)

    fun watchEvents(onEvent: (CartReviewEvent) -> Unit): FlowWatch = viewModel.events.watch(onEvent)
}

/**
 * The payment sheet: for the cart, or for the in-store draft when [draftOrderId] is set (PAY-01). Offers the iOS methods:
 * Apple Pay, never Google Pay.
 */
class PaymentBridge(draftOrderId: String?) : ScreenBridge() {
    val viewModel: PaymentViewModel = makeViewModel(PaymentViewModel::class) {
        PaymentViewModel(
            draftOrderId?.let { PaymentTarget.Draft(it) } ?: PaymentTarget.Cart,
            ClientPlatform.IOS,
            container.paymentService,
            container.paymentRepository,
            container.cartRepository,
            container.orderRepository,
            container.storeRepository,
        )
    }

    val currentState: PaymentUiState get() = viewModel.state.value

    fun watch(onState: (PaymentUiState) -> Unit): FlowWatch = viewModel.state.watch(onState)

    fun watchEvents(onEvent: (PaymentEvent) -> Unit): FlowWatch = viewModel.events.watch(onEvent)
}

/** What the debug panel shows. Durations are milliseconds, which Swift can use directly. */
data class DebugState(
    val loadingDelayMillis: Long,
    val liveQueue: Boolean,
    /** ORDER-01: orders paid in this session move through their statuses on a timer. */
    val liveOrders: Boolean,
    val forceSsoFailure: Boolean,
    /** PAY-01: every payment is declined, whatever the method. */
    val forcePaymentDecline: Boolean,
    /** "guest", or "signed in with phone" and so on. */
    val session: String,
    /** Null until the store feed has loaded. */
    val stores: List<Store>?,
    /** AVAIL-01: the store the "Live availability" controls act on. Null until the store feed has loaded. */
    val availabilityStore: Store?,
    /** That store's live state in one line, such as "Paused · Both". Empty until the feed has loaded. */
    val availabilityStatus: String,
    /** That store's menu items, for "Stop Selling". Empty before its menu loads. */
    val availabilityItems: List<MenuItem>,
    /** Every option on that store's menu once, for "Stop Selling" an option. */
    val availabilityOptions: List<ModifierOption>,
    /** The in-store draft's check-in status, such as "Draft #44 at Test Ramen Bar: 320 m, out of range". Null with no draft. */
    val checkInStatus: String?,
)

/** The SwiftUI debug panel's controls over the mock data layer. Swift only shows the panel in debug builds. */
class DebugBridge {
    private val container get() = AppContainer.instance
    private val config get() = container.mockConfig

    val loadingDelayOptionsMillis: List<Long> = listOf(0L, 800L, 2_000L)
    val testCode: String = SeedAuth.TEST_OTP
    val testEmail: String = SeedAuth.TEST_EMAIL
    val testPassword: String = SeedAuth.TEST_PASSWORD

    val currentState: DebugState get() = DebugState(
        loadingDelayMillis = config.loadingDelay.value.inWholeMilliseconds,
        liveQueue = config.liveQueue.value,
        liveOrders = config.liveOrders.value,
        forceSsoFailure = config.forceSsoFailure.value,
        forcePaymentDecline = config.forcePaymentDecline.value,
        session = sessionLabel(container.authRepository.session.value?.method),
        stores = container.storeRepository.stores.value.dataOrNull(),
        availabilityStore = availabilityStore(),
        availabilityStatus = availabilityStore()?.availabilityStatus().orEmpty(),
        availabilityItems = availabilityMenu()?.items.orEmpty(),
        availabilityOptions = availabilityMenu()?.options.orEmpty(),
        checkInStatus = checkInStatus(),
    )

    // AVAIL-01: the store picked in the panel, else the cart's store, else Test Ramen Bar.
    private val pickedStoreId = MutableStateFlow<String?>(null)

    private fun availabilityStoreId(): String =
        pickedStoreId.value ?: defaultAvailabilityStoreId(container.cartRepository.cart.value)

    private fun availabilityStore(): Store? =
        container.storeRepository.stores.value.dataOrNull()?.firstOrNull { it.id == availabilityStoreId() }

    private fun availabilityMenu(): Menu? = container.menuRepository.menu(availabilityStoreId()).value.dataOrNull()

    /** Every service mode, in the order the panel lists them. */
    val businessTypes: List<BusinessType> = BusinessType.entries

    fun businessTypeLabel(type: BusinessType): String = type.debugLabel

    fun watch(onState: (DebugState) -> Unit): FlowWatch =
        combine(
            config.loadingDelay,
            config.liveQueue,
            config.liveOrders,
            config.forceSsoFailure,
            config.forcePaymentDecline,
            container.authRepository.session,
            container.storeRepository.stores,
            container.orderRepository.orders,
            container.locationRepository.customerLocation,
        ) { _ -> }
            .combine(availabilityMenuChanges()) { _, _ -> currentState }
            .watch(onState)

    /** Changes when the controls move to another store or that store's menu changes (sold out and back). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun availabilityMenuChanges(): Flow<Any?> =
        combine(pickedStoreId, container.cartRepository.cart) { _, _ -> availabilityStoreId() }
            .distinctUntilChanged()
            .flatMapLatest { storeId -> container.menuRepository.menu(storeId) }

    fun setLoadingDelayMillis(millis: Long) {
        config.loadingDelay.value = millis.milliseconds
    }

    fun setLiveQueue(on: Boolean) {
        config.liveQueue.value = on
    }

    fun setLiveOrders(on: Boolean) {
        config.liveOrders.value = on
    }

    fun setForceSsoFailure(on: Boolean) {
        config.forceSsoFailure.value = on
    }

    fun setForcePaymentDecline(on: Boolean) {
        config.forcePaymentDecline.value = on
    }

    fun failNextStoreLoad() {
        container.storeFeedSimulator.failNextLoad()
        container.storeRepository.refresh()
    }

    fun simulateNewUser() = container.simulateNewUser()

    fun fillSampleCart() = container.fillSampleCart()

    /** SEAT-01: every seat taken, or free again. */
    fun toggleSeatsFull(store: Store) {
        container.storeFeedSimulator.setSeatsFull(store.id, !store.seatsFull)
    }

    // AVAIL-01: live store changes, as the store's own app would push them. Each acts on the picked store.

    fun selectAvailabilityStore(storeId: String) {
        pickedStoreId.value = storeId
    }

    /** "Stop Accepting Orders" (false) or "Resume Accepting Orders" (true). */
    fun setAcceptingOrders(accepting: Boolean) {
        container.storeFeedSimulator.setAvailability(
            availabilityStoreId(),
            if (accepting) StoreAvailability.OPEN else StoreAvailability.PAUSED,
        )
    }

    fun setBusinessType(type: BusinessType) = container.storeFeedSimulator.setBusinessType(availabilityStoreId(), type)

    fun adjustQueue(delta: Int) = container.storeFeedSimulator.adjustQueue(availabilityStoreId(), delta)

    fun setItemSoldOut(item: MenuItem, soldOut: Boolean) =
        container.menuSimulator.setItemSoldOut(item.storeId, item.id, soldOut)

    fun setOptionSoldOut(option: ModifierOption, soldOut: Boolean) =
        container.menuSimulator.setOptionSoldOut(availabilityStoreId(), option.id, soldOut)

    fun resetMockData() = container.resetMockData()

    // CHECKIN-01: arrival and QR scans, in place of GPS and the camera. Each acts on the in-store draft.

    val defaultTableNumber: String = "12"

    fun isValidTableNumber(tableNumber: String): Boolean = CheckInQr.isValidTableNumber(tableNumber)

    fun simulateArrival() = container.checkInSimulator.simulateArrival()

    fun simulateLeaving() = container.checkInSimulator.simulateLeaving()

    fun scanStoreQr() = container.checkInSimulator.scanStoreQr()

    fun scanTableQr(tableNumber: String) = container.checkInSimulator.scanTableQr(tableNumber)

    fun scanOtherStoreQr() = container.checkInSimulator.scanOtherStoreQr()

    fun scanInvalidQr() = container.checkInSimulator.scanInvalidQr()

    private fun checkInStatus(): String? {
        val draft = container.orderRepository.orders.value.dataOrNull()
            ?.filter { it.awaitsCheckIn }
            ?.maxByOrNull { it.createdAt }
            ?: return null
        val storeAt = container.storeRepository.stores.value.dataOrNull()?.firstOrNull { it.id == draft.storeId }?.location
        val distance = container.locationRepository.customerLocation.value?.let { storeAt?.distanceTo(it)?.roundToInt() }
        val where = when {
            distance == null -> "location unknown"
            Geofence.isInRange(distance) -> "$distance m, in range"
            else -> "$distance m, out of range"
        }
        return "Draft #${draft.number} at ${draft.storeName}: $where"
    }

    private fun sessionLabel(method: SignInMethod?): String =
        method?.let { "signed in with ${it.name.lowercase()}" } ?: "guest"
}
