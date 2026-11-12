package tech.vauldex.dekita.presentation.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.LocationRepository
import tech.vauldex.dekita.data.OrderRepository
import tech.vauldex.dekita.data.StoreRepository
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.domain.CheckIn
import tech.vauldex.dekita.domain.CheckInMethod
import tech.vauldex.dekita.domain.GeoPoint
import tech.vauldex.dekita.domain.Geofence
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS
import tech.vauldex.dekita.presentation.queueLabel

data class CheckInUiState(
    val isLoading: Boolean = true,
    /** The in-store draft. Null when there is no draft with this id waiting for check-in. */
    val draft: Order? = null,
    /** The draft's store from the live feed, for the queue. Null until the feed loads. */
    val store: Store? = null,
    /** Where the customer is. In the mockup the debug panel moves them; production uses GPS. */
    val customerLocation: GeoPoint? = null,
) {
    /** Meters to the draft's store. Null until both the store and the customer's location are known. */
    val distanceMeters: Int?
        get() = store?.location?.let { storeAt -> customerLocation?.let { storeAt.distanceTo(it).roundToInt() } }

    val isInRange: Boolean get() = distanceMeters?.let(Geofence::isInRange) == true

    val checkIn: CheckIn? get() = draft?.checkIn

    /** Location or a QR scan proves arrival. */
    val hasArrived: Boolean get() = isInRange || checkIn != null

    private val isPaused: Boolean get() = store?.availability == StoreAvailability.PAUSED

    /** [Start Cooking] turns on once the customer has arrived, unless the store has stopped taking orders. */
    val canStartCooking: Boolean get() = draft != null && hasArrived && !isPaused

    // Text shared by the Compose UI and the SwiftUI app.

    val headline: String get() = if (hasArrived) "You've arrived" else "On your way"

    val storeLine: String get() = draft?.let { "${it.storeName} · Eat-In" }.orEmpty()

    val orderSummary: String get() = draft?.let { "${it.itemCount} items · ${it.total.formatted()}" }.orEmpty()

    val distanceLabel: String
        get() = when {
            isInRange -> "At the store"
            distanceMeters == null -> "Finding your location…"
            else -> "$distanceMeters m away"
        }

    val queueLabel: String get() = store?.queueLabel()?.ifEmpty { null } ?: "Updating…"

    /** Under [Start Cooking]: why it is off, or what tapping it does. */
    val startCookingHint: String
        get() = when {
            isPaused -> "${store?.name} isn't taking orders right now."
            canStartCooking -> "You pay when you tap Start Cooking."
            else -> "Start Cooking turns on when you're within ${Geofence.RADIUS_METERS} m of the store."
        }
}

/**
 * The "On your way" screen for an in-store draft. In production the geofence turns [Start Cooking] on;
 * a store or table QR is the fallback when GPS fails. Leaving this screen keeps the draft.
 */
class CheckInViewModel(
    private val orderId: String,
    private val orderRepository: OrderRepository,
    storeRepository: StoreRepository,
    locationRepository: LocationRepository,
) : ViewModel() {

    val state: StateFlow<CheckInUiState> =
        combine(orderRepository.orders, storeRepository.stores, locationRepository.customerLocation) { orders, stores, customer ->
            val draft = orders.dataOrNull()?.firstOrNull { it.id == orderId && it.awaitsCheckIn }
            CheckInUiState(
                isLoading = orders is LoadState.Loading,
                draft = draft,
                store = draft?.let { d -> stores.dataOrNull()?.firstOrNull { it.id == d.storeId } },
                customerLocation = customer,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CheckInUiState())

    /**
     * [Start Cooking]. Records a location check-in, or keeps an earlier QR one so its table number
     * isn't lost. Returns true when seat verification should open.
     */
    fun startCooking(): Boolean {
        val current = state.value
        if (!current.canStartCooking) return false
        if (current.checkIn != null) return true
        return orderRepository.checkIn(orderId, CheckIn(CheckInMethod.GEOFENCE))
    }

    /**
     * Pays for the checked-in draft so the kitchen starts. Returns the order id, to open its ticket,
     * or null when the draft isn't checked in or was already paid.
     *
     * shortcut: runs from the seat check placeholder with a mock payment. SEAT-01 and PAY-01 replace both.
     */
    fun payAtCheckIn(): String? = orderRepository.payAtCheckIn(orderId)?.id
}
