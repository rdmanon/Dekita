package tech.vauldex.dekita.presentation.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tech.vauldex.dekita.data.OrderRepository
import tech.vauldex.dekita.data.QrScanner
import tech.vauldex.dekita.data.StoreRepository
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.domain.CheckInQr
import tech.vauldex.dekita.domain.QrScanResult
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS

data class QrScannerUiState(
    /** The draft's store, for the result text. */
    val storeName: String = "",
    /** Null while the camera is scanning. */
    val result: QrScanResult? = null,
    /** For a code from another store: that store's name, when it is in the feed. */
    val scannedStoreName: String? = null,
) {
    val isScanning: Boolean get() = result == null

    val isCheckedIn: Boolean get() = result is QrScanResult.CheckedIn

    /** Every error offers Scan again. */
    val isError: Boolean get() = result is QrScanResult.WrongStore || result == QrScanResult.Invalid

    // Text shared by the Compose UI and the SwiftUI app.

    val title: String
        get() = when (val result = result) {
            null -> "Scan the store QR"
            is QrScanResult.CheckedIn -> result.checkIn.tableNumber?.let { "Checked in at table $it" } ?: "You're checked in"
            is QrScanResult.WrongStore -> "This QR is for a different store"
            QrScanResult.Invalid -> "This QR code can't be used"
        }

    val message: String
        get() = when (result) {
            null -> "Point your camera at the QR code on the counter, at the entrance, or on your table."
            is QrScanResult.CheckedIn -> "$storeName · Eat-In. Next, make sure there's a seat for you."
            is QrScanResult.WrongStore -> "It's for ${scannedStoreName ?: "another store"}. Scan the QR at $storeName."
            QrScanResult.Invalid -> "It isn't a Passnize check-in code. Scan the QR on the counter or your table."
        }
}

/**
 * The QR fallback for check-in. The mockup has no camera: the scanner gets its codes from the
 * debug panel. A result pauses scanning until the customer continues or taps Scan again.
 */
class QrScannerViewModel(
    private val orderId: String,
    private val orderRepository: OrderRepository,
    storeRepository: StoreRepository,
    scanner: QrScanner,
) : ViewModel() {

    private val result = MutableStateFlow<QrScanResult?>(null)

    val state: StateFlow<QrScannerUiState> =
        combine(orderRepository.orders, storeRepository.stores, result) { orders, stores, result ->
            val storeNames = stores.dataOrNull().orEmpty().associate { it.id to it.name }
            QrScannerUiState(
                storeName = orders.dataOrNull()?.firstOrNull { it.id == orderId }?.storeName.orEmpty(),
                result = result,
                scannedStoreName = (result as? QrScanResult.WrongStore)?.let { storeNames[it.storeId] },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), QrScannerUiState())

    init {
        viewModelScope.launch { scanner.scans.collect(::onScan) }
    }

    fun scanAgain() {
        result.value = null
    }

    private fun onScan(raw: String) {
        if (result.value != null) return
        val draft = orderRepository.orders.value.dataOrNull()?.firstOrNull { it.id == orderId && it.awaitsCheckIn } ?: return
        val read = CheckInQr.read(raw, draft.storeId)
        if (read is QrScanResult.CheckedIn && !orderRepository.checkIn(orderId, read.checkIn)) return
        result.value = read
    }
}
