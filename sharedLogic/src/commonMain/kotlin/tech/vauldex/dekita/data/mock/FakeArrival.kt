package tech.vauldex.dekita.data.mock

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import tech.vauldex.dekita.data.LocationRepository
import tech.vauldex.dekita.data.OrderRepository
import tech.vauldex.dekita.data.QrScanner
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.domain.CheckInQr
import tech.vauldex.dekita.domain.GeoPoint

// Stand-ins for GPS and the camera (CHECKIN-01). The mockup never asks for location or camera
// permission; the debug panel moves the customer and "shows" codes to the scanner instead.

/** Inside [tech.vauldex.dekita.domain.Geofence.RADIUS_METERS], but far enough out that the map shows the dot beside the store pin. */
private const val ARRIVED_DISTANCE_METERS = 35.0

/** Arrival comes from the south-west, the way the seed customer walks to the ramen bar. */
private const val ARRIVAL_BEARING_DEGREES = 225.0

class FakeLocationRepository : LocationRepository, Resettable {

    private val _customerLocation = MutableStateFlow<GeoPoint?>(seedCustomerLocation)
    override val customerLocation: StateFlow<GeoPoint?> = _customerLocation.asStateFlow()

    /** Moves the customer to just outside [storeId]'s door, inside its geofence. */
    fun arriveAt(storeId: String) {
        val store = seedStores.firstOrNull { it.id == storeId } ?: return
        _customerLocation.value = store.location.offset(ARRIVED_DISTANCE_METERS, ARRIVAL_BEARING_DEGREES)
    }

    /** Puts the customer back where they started, outside every geofence. */
    fun leave() {
        _customerLocation.value = seedCustomerLocation
    }

    override fun reset() {
        leave()
    }
}

class FakeQrScanner : QrScanner {

    // No replay: a code shown while no scanner screen is open is lost, as with a real camera.
    private val _scans = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val scans: Flow<String> = _scans.asSharedFlow()

    fun show(raw: String) {
        _scans.tryEmit(raw)
    }
}

/** Debug-panel hooks for check-in. Each acts on the customer's in-store draft, or does nothing when there is none. */
interface CheckInSimulator {
    /** The store of the in-store draft waiting for check-in, or null when there is no such draft. */
    val draftStoreId: String?

    fun simulateArrival()

    fun simulateLeaving()

    fun scanStoreQr()

    /** Ignored unless [tableNumber] passes [CheckInQr.isValidTableNumber]. */
    fun scanTableQr(tableNumber: String)

    fun scanOtherStoreQr()

    fun scanInvalidQr()
}

internal class FakeCheckInSimulator(
    private val orders: OrderRepository,
    private val location: FakeLocationRepository,
    private val scanner: FakeQrScanner,
) : CheckInSimulator {

    override val draftStoreId: String?
        get() = orders.orders.value.dataOrNull()
            ?.filter { it.awaitsCheckIn }
            ?.maxByOrNull { it.createdAt }
            ?.storeId

    override fun simulateArrival() {
        draftStoreId?.let(location::arriveAt)
    }

    override fun simulateLeaving() {
        if (draftStoreId != null) location.leave()
    }

    override fun scanStoreQr() {
        draftStoreId?.let { scanner.show(CheckInQr.storeCode(it)) }
    }

    override fun scanTableQr(tableNumber: String) {
        if (!CheckInQr.isValidTableNumber(tableNumber)) return
        draftStoreId?.let { scanner.show(CheckInQr.tableCode(it, tableNumber)) }
    }

    override fun scanOtherStoreQr() {
        val draftStore = draftStoreId ?: return
        // Scenario 3 uses the curry stand; fall back to any other store if the draft is there.
        val other = listOf(SeedStoreIds.CURRY_STAND, SeedStoreIds.RAMEN_BAR).first { it != draftStore }
        scanner.show(CheckInQr.storeCode(other))
    }

    override fun scanInvalidQr() {
        if (draftStoreId != null) scanner.show(INVALID_QR)
    }

    private companion object {
        /** A plain web link, as on a poster: a real QR, but not a Passnize check-in code. */
        const val INVALID_QR = "https://example.com/lunch-menu"
    }
}
