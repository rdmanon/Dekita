package tech.vauldex.dekita.domain

/** How an in-store customer showed they are at the store. */
enum class CheckInMethod {
    /** The phone's location was inside the store's geofence. */
    GEOFENCE,

    /** The customer scanned the QR at the store's entrance or counter. */
    STORE_QR,

    /** The customer scanned the QR on their table. */
    TABLE_QR,
}

/** Arrival at the store. [tableNumber] is set only by a table QR, and is shown on the order ticket. */
data class CheckIn(val method: CheckInMethod, val tableNumber: String? = null) {
    init {
        require((method == CheckInMethod.TABLE_QR) == (tableNumber != null)) {
            "Only a table QR check-in has a table number"
        }
    }
}

/** In production the phone's GPS decides this. The mockup's debug panel sets the distance instead. */
object Geofence {
    /** How close the customer must be for [Start Cooking] to turn on. Needs product sign-off. */
    const val RADIUS_METERS = 50

    fun isInRange(distanceMeters: Int): Boolean = distanceMeters <= RADIUS_METERS
}

/** What a scanned code means for the customer's draft. */
sealed interface QrScanResult {
    data class CheckedIn(val checkIn: CheckIn) : QrScanResult

    /** A real Passnize code, but for another store, so it can't check in this draft. */
    data class WrongStore(val storeId: String) : QrScanResult

    /** Not a Passnize check-in code at all, or a damaged one. */
    data object Invalid : QrScanResult
}

/**
 * Reads store and table QR codes. The payload format is a placeholder until the merchant QR
 * generator defines the real one: `passnize://check-in?store=<storeId>` with an optional `&table=<number>`.
 *
 * A scanned code is untrusted input from a camera, so [read] accepts only that exact shape and treats
 * anything else as invalid. It never opens or follows the code.
 */
object CheckInQr {
    private const val PREFIX = "passnize://check-in?"
    private const val MAX_LENGTH = 256
    private val storeIdPattern = Regex("[a-z0-9-]{1,64}")
    private val tablePattern = Regex("[A-Za-z0-9]{1,4}")

    fun storeCode(storeId: String): String = "${PREFIX}store=$storeId"

    fun tableCode(storeId: String, tableNumber: String): String = "${PREFIX}store=$storeId&table=$tableNumber"

    fun isValidTableNumber(tableNumber: String): Boolean = tablePattern.matches(tableNumber)

    fun read(raw: String, draftStoreId: String): QrScanResult {
        if (raw.length > MAX_LENGTH || !raw.startsWith(PREFIX)) return QrScanResult.Invalid
        val params = raw.removePrefix(PREFIX).split('&').map { it.split('=') }
        if (params.any { it.size != 2 }) return QrScanResult.Invalid
        val keys = params.map { it[0] }
        if (keys.toSet().size != keys.size || !keys.all { it == "store" || it == "table" }) return QrScanResult.Invalid

        val values = params.associate { it[0] to it[1] }
        val storeId = values["store"]?.takeIf(storeIdPattern::matches) ?: return QrScanResult.Invalid
        val table = values["table"]
        if (table != null && !isValidTableNumber(table)) return QrScanResult.Invalid

        return when {
            storeId != draftStoreId -> QrScanResult.WrongStore(storeId)
            table != null -> QrScanResult.CheckedIn(CheckIn(CheckInMethod.TABLE_QR, table))
            else -> QrScanResult.CheckedIn(CheckIn(CheckInMethod.STORE_QR))
        }
    }
}
