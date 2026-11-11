package tech.vauldex.dekita.ios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.reflect.KClass
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tech.vauldex.dekita.di.AppContainer
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.presentation.discover.DiscoverUiState
import tech.vauldex.dekita.presentation.discover.DiscoverViewModel
import tech.vauldex.dekita.presentation.discover.FavoritesUiState
import tech.vauldex.dekita.presentation.discover.FavoritesViewModel
import tech.vauldex.dekita.presentation.discover.HistoryUiState
import tech.vauldex.dekita.presentation.discover.HistoryViewModel
import tech.vauldex.dekita.presentation.discover.WalkLimit

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
