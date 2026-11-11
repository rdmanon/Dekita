package tech.vauldex.dekita.ios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.mock.SeedAuth
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.di.AppContainer
import tech.vauldex.dekita.domain.DialCode
import tech.vauldex.dekita.domain.Menu
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
import tech.vauldex.dekita.presentation.discover.DiscoverUiState
import tech.vauldex.dekita.presentation.discover.DiscoverViewModel
import tech.vauldex.dekita.presentation.discover.FavoritesUiState
import tech.vauldex.dekita.presentation.discover.FavoritesViewModel
import tech.vauldex.dekita.presentation.discover.HistoryUiState
import tech.vauldex.dekita.presentation.discover.HistoryViewModel
import tech.vauldex.dekita.presentation.discover.WalkLimit
import tech.vauldex.dekita.presentation.orders.OrdersUiState
import tech.vauldex.dekita.presentation.orders.OrdersViewModel
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
        DiscoverViewModel(container.storeRepository, container.orderRepository, container.cartRepository)
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
        StoreDetailViewModel(storeId, container.storeRepository, container.menuRepository, container.cartRepository)
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
        OrdersViewModel(container.orderRepository, container.authRepository)
    }

    val currentState: OrdersUiState get() = viewModel.state.value

    fun watch(onState: (OrdersUiState) -> Unit): FlowWatch = viewModel.state.watch(onState)
}

class AccountBridge : ScreenBridge() {
    val viewModel: AccountViewModel = makeViewModel(AccountViewModel::class) {
        AccountViewModel(container.authRepository, container.paymentRepository, container.inboxRepository)
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
        CartReviewViewModel(container.cartRepository, container.storeRepository, container.authRepository)
    }

    val currentState: CartReviewUiState get() = viewModel.state.value

    fun watch(onState: (CartReviewUiState) -> Unit): FlowWatch = viewModel.state.watch(onState)

    fun watchEvents(onEvent: (CartReviewEvent) -> Unit): FlowWatch = viewModel.events.watch(onEvent)
}

/** What the debug panel shows. Durations are milliseconds, which Swift can use directly. */
data class DebugState(
    val loadingDelayMillis: Long,
    val liveQueue: Boolean,
    val forceSsoFailure: Boolean,
    /** "guest", or "signed in with phone" and so on. */
    val session: String,
    /** Null until the store feed has loaded. */
    val stores: List<Store>?,
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
        forceSsoFailure = config.forceSsoFailure.value,
        session = sessionLabel(container.authRepository.session.value?.method),
        stores = container.storeRepository.stores.value.dataOrNull(),
    )

    fun watch(onState: (DebugState) -> Unit): FlowWatch =
        combine(
            config.loadingDelay,
            config.liveQueue,
            config.forceSsoFailure,
            container.authRepository.session,
            container.storeRepository.stores,
        ) { _, _, _, _, _ -> currentState }.watch(onState)

    fun setLoadingDelayMillis(millis: Long) {
        config.loadingDelay.value = millis.milliseconds
    }

    fun setLiveQueue(on: Boolean) {
        config.liveQueue.value = on
    }

    fun setForceSsoFailure(on: Boolean) {
        config.forceSsoFailure.value = on
    }

    fun togglePaused(store: Store) {
        val paused = store.availability == StoreAvailability.PAUSED
        container.storeFeedSimulator.setAvailability(store.id, if (paused) StoreAvailability.OPEN else StoreAvailability.PAUSED)
    }

    fun failNextStoreLoad() {
        container.storeFeedSimulator.failNextLoad()
        container.storeRepository.refresh()
    }

    fun simulateNewUser() = container.simulateNewUser()

    fun fillSampleCart() = container.fillSampleCart()

    fun resetMockData() = container.resetMockData()

    private fun sessionLabel(method: SignInMethod?): String =
        method?.let { "signed in with ${it.name.lowercase()}" } ?: "guest"
}
