package tech.vauldex.dekita.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckInQrTest {

    private val ramen = "store-test-ramen-bar"
    private val curry = "store-sample-curry-stand"

    @Test
    fun storeCodeChecksInWithoutATable() {
        val result = CheckInQr.read(CheckInQr.storeCode(ramen), ramen)
        assertEquals(QrScanResult.CheckedIn(CheckIn(CheckInMethod.STORE_QR)), result)
    }

    @Test
    fun tableCodeRecordsTheTableNumber() {
        val result = CheckInQr.read(CheckInQr.tableCode(ramen, "12"), ramen)
        assertEquals(QrScanResult.CheckedIn(CheckIn(CheckInMethod.TABLE_QR, "12")), result)
    }

    @Test
    fun anotherStoresCodeIsAWrongStore() {
        assertEquals(QrScanResult.WrongStore(curry), CheckInQr.read(CheckInQr.storeCode(curry), ramen))
        assertEquals(QrScanResult.WrongStore(curry), CheckInQr.read(CheckInQr.tableCode(curry, "3"), ramen))
    }

    @Test
    fun anythingButTheExactShapeIsInvalid() {
        val invalid = listOf(
            "",
            "https://example.com/lunch-menu",
            "passnize://check-in?",
            "passnize://check-in?table=12",
            "passnize://check-in?store=",
            "passnize://check-in?store=$ramen&table=",
            "passnize://check-in?store=$ramen&table=12345",
            "passnize://check-in?store=$ramen&table=1 2",
            "passnize://check-in?store=$ramen&store=$curry",
            "passnize://check-in?store=$ramen&next=https://example.com",
            "passnize://check-in?store=$ramen&table=12=3",
            "passnize://check-in?store=STORE<script>",
            "PASSNIZE://check-in?store=$ramen",
            "passnize://check-in?store=" + "a".repeat(300),
        )
        invalid.forEach { raw -> assertEquals(QrScanResult.Invalid, CheckInQr.read(raw, ramen), "\"$raw\"") }
    }

    @Test
    fun tableNumbersAreShortAndAlphanumeric() {
        assertTrue(CheckInQr.isValidTableNumber("12"))
        assertTrue(CheckInQr.isValidTableNumber("A3"))
        assertFalse(CheckInQr.isValidTableNumber(""))
        assertFalse(CheckInQr.isValidTableNumber("12345"))
        assertFalse(CheckInQr.isValidTableNumber("1-2"))
    }

    @Test
    fun onlyATableCheckInHasATableNumber() {
        assertFailsWith<IllegalArgumentException> { CheckIn(CheckInMethod.TABLE_QR) }
        assertFailsWith<IllegalArgumentException> { CheckIn(CheckInMethod.GEOFENCE, "12") }
    }

    @Test
    fun geofenceRadiusIsInclusive() {
        assertTrue(Geofence.isInRange(Geofence.RADIUS_METERS))
        assertFalse(Geofence.isInRange(Geofence.RADIUS_METERS + 1))
    }
}
