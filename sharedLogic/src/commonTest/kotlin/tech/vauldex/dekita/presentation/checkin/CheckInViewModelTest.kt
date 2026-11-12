package tech.vauldex.dekita.presentation.checkin

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.data.mock.FakeAuthRepository
import tech.vauldex.dekita.data.mock.FakeCartRepository
import tech.vauldex.dekita.data.mock.FakeCheckInSimulator
import tech.vauldex.dekita.data.mock.FakeLocationRepository
import tech.vauldex.dekita.data.mock.FakeOrderRepository
import tech.vauldex.dekita.data.mock.FakeQrScanner
import tech.vauldex.dekita.data.mock.FakeStoreRepository
import tech.vauldex.dekita.data.mock.MockConfig
import tech.vauldex.dekita.domain.CheckIn
import tech.vauldex.dekita.domain.CheckInMethod
import tech.vauldex.dekita.domain.QrScanResult
import tech.vauldex.dekita.domain.SsoProvider
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.presentation.discover.DiscoverViewModel

/** CHECKIN-01: the geofence, the QR fallback and keeping the draft. */
@OptIn(ExperimentalCoroutinesApi::class)
class CheckInViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    private val fixedClock = object : Clock {
        override fun now() = Instant.fromEpochSeconds(1_800_000_000)
    }

    private val draftId = "order-44"

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture(scope: CoroutineScope, clock: Clock) {
        val stores = FakeStoreRepository(scope, MockConfig())
        val orders = FakeOrderRepository(scope, MockConfig(), clock)
        val location = FakeLocationRepository()
        val scanner = FakeQrScanner()
        val simulator = FakeCheckInSimulator(orders, location, scanner)
    }

    private fun TestScope.fixture(): Fixture = Fixture(backgroundScope, fixedClock)

    private fun TestScope.finishLoading() {
        runCurrent()
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
    }

    private fun TestScope.checkInViewModel(fixture: Fixture) =
        CheckInViewModel(draftId, fixture.orders, fixture.stores, fixture.location).also { viewModel ->
            backgroundScope.launch { viewModel.state.collect {} }
        }

    private fun TestScope.scannerViewModel(fixture: Fixture) =
        QrScannerViewModel(draftId, fixture.orders, fixture.stores, fixture.scanner).also { viewModel ->
            backgroundScope.launch { viewModel.state.collect {} }
        }

    @Test
    fun startCookingIsOffUntilArrivalThenOpensSeatVerification() = runTest(mainDispatcher) {
        // Scenario 1: check-in via simulated geofence.
        val fixture = fixture()
        val viewModel = checkInViewModel(fixture)
        finishLoading()

        val before = viewModel.state.value
        assertEquals("Test Ramen Bar · Eat-In", before.storeLine)
        assertEquals("2 items · ¥1,550", before.orderSummary)
        assertEquals("3 ahead · ~8 min", before.queueLabel)
        assertEquals("On your way", before.headline)
        assertFalse(before.canStartCooking)
        assertEquals("Start Cooking turns on when you're within 50 m of the store.", before.startCookingHint)
        assertFalse(viewModel.startCooking(), "Tapping a disabled button must do nothing")
        assertNull(fixture.orders.draft().checkIn)

        fixture.simulator.simulateArrival()
        runCurrent()
        val arrived = viewModel.state.value
        assertTrue(arrived.canStartCooking)
        assertEquals("You've arrived", arrived.headline)
        assertEquals("At the store", arrived.distanceLabel)

        assertTrue(viewModel.startCooking())
        assertEquals(CheckIn(CheckInMethod.GEOFENCE), fixture.orders.draft().checkIn)
    }

    @Test
    fun leavingTheGeofenceTurnsStartCookingOffAgain() = runTest(mainDispatcher) {
        val fixture = fixture()
        val viewModel = checkInViewModel(fixture)
        finishLoading()

        fixture.simulator.simulateArrival()
        runCurrent()
        fixture.simulator.simulateLeaving()
        runCurrent()

        assertFalse(viewModel.state.value.canStartCooking)
        assertEquals("320 m away", viewModel.state.value.distanceLabel)
    }

    @Test
    fun aPausedStoreKeepsStartCookingOff() = runTest(mainDispatcher) {
        val fixture = fixture()
        val viewModel = checkInViewModel(fixture)
        finishLoading()

        fixture.simulator.simulateArrival()
        fixture.stores.setAvailability("store-test-ramen-bar", StoreAvailability.PAUSED)
        runCurrent()

        assertFalse(viewModel.state.value.canStartCooking)
        assertEquals("Test Ramen Bar isn't taking orders right now.", viewModel.state.value.startCookingHint)
    }

    @Test
    fun tableQrChecksInAndRecordsTheTable() = runTest(mainDispatcher) {
        // Scenario 2: check-in via table QR when GPS fails.
        val fixture = fixture()
        val checkIn = checkInViewModel(fixture)
        val scanner = scannerViewModel(fixture)
        finishLoading()
        assertFalse(checkIn.state.value.isInRange)

        fixture.simulator.scanTableQr("12")
        runCurrent()

        assertTrue(scanner.state.value.isCheckedIn)
        assertEquals("Checked in at table 12", scanner.state.value.title)
        assertEquals("12", fixture.orders.draft().checkIn?.tableNumber)

        // Start Cooking keeps the QR check-in, so the ticket still gets the table.
        assertTrue(checkIn.state.value.canStartCooking)
        assertTrue(checkIn.startCooking())
        assertEquals(CheckIn(CheckInMethod.TABLE_QR, "12"), fixture.orders.draft().checkIn)
    }

    @Test
    fun storeQrChecksIn() = runTest(mainDispatcher) {
        val fixture = fixture()
        val scanner = scannerViewModel(fixture)
        finishLoading()

        fixture.simulator.scanStoreQr()
        runCurrent()

        assertEquals("You're checked in", scanner.state.value.title)
        assertEquals(CheckIn(CheckInMethod.STORE_QR), fixture.orders.draft().checkIn)
    }

    @Test
    fun anotherStoresQrShowsAnErrorAndAllowsARescan() = runTest(mainDispatcher) {
        // Scenario 3: wrong store QR.
        val fixture = fixture()
        val scanner = scannerViewModel(fixture)
        finishLoading()

        fixture.simulator.scanOtherStoreQr()
        runCurrent()

        val state = scanner.state.value
        assertTrue(state.isError)
        assertEquals("This QR is for a different store", state.title)
        assertEquals("It's for Sample Curry Stand. Scan the QR at Test Ramen Bar.", state.message)
        assertNull(fixture.orders.draft().checkIn)

        // A result pauses the camera until Scan again.
        fixture.simulator.scanStoreQr()
        runCurrent()
        assertTrue(scanner.state.value.result is QrScanResult.WrongStore)

        scanner.scanAgain()
        runCurrent()
        assertTrue(scanner.state.value.isScanning)
        fixture.simulator.scanStoreQr()
        runCurrent()
        assertTrue(scanner.state.value.isCheckedIn)
    }

    @Test
    fun anInvalidQrShowsAnErrorAndAllowsARescan() = runTest(mainDispatcher) {
        val fixture = fixture()
        val scanner = scannerViewModel(fixture)
        finishLoading()

        fixture.simulator.scanInvalidQr()
        runCurrent()
        assertEquals(QrScanResult.Invalid, scanner.state.value.result)
        assertTrue(scanner.state.value.isError)
        assertNull(fixture.orders.draft().checkIn)

        scanner.scanAgain()
        runCurrent()
        assertTrue(scanner.state.value.isScanning)
    }

    @Test
    fun anInvalidTableNumberFromTheDebugPanelIsIgnored() = runTest(mainDispatcher) {
        val fixture = fixture()
        val scanner = scannerViewModel(fixture)
        finishLoading()

        fixture.simulator.scanTableQr("12345")
        runCurrent()

        assertTrue(scanner.state.value.isScanning)
    }

    @Test
    fun theDraftStaysAndDiscoverOffersToResumeIt() = runTest(mainDispatcher) {
        // Draft retention: leaving check-in clears its view model, but the draft is in the repository.
        val fixture = fixture()
        val checkIn = checkInViewModel(fixture)
        val auth = FakeAuthRepository(MockConfig())
        val discover = DiscoverViewModel(fixture.stores, fixture.orders, FakeCartRepository(), auth)
        backgroundScope.launch { discover.state.collect {} }
        finishLoading()
        assertNotNull(checkIn.state.value.draft)
        // The banner names the customer's own draft, so guests don't see it.
        assertNull(discover.state.value.draftToResume)
        auth.signInWith(SsoProvider.APPLE)
        runCurrent()

        assertEquals(draftId, discover.state.value.draftToResume?.id)
        assertEquals("Test Ramen Bar", discover.state.value.lastOrderStoreName)
        assertEquals("store-test-ramen-bar", fixture.simulator.draftStoreId)
    }

    @Test
    fun onlyAnInStoreDraftCanBeCheckedIn() = runTest(mainDispatcher) {
        val fixture = fixture()
        finishLoading()

        assertFalse(fixture.orders.checkIn("order-42", CheckIn(CheckInMethod.GEOFENCE)), "Already cooking")
        assertFalse(fixture.orders.checkIn("order-43", CheckIn(CheckInMethod.GEOFENCE)), "Pickup order")
        assertFalse(fixture.orders.checkIn("no-such-order", CheckIn(CheckInMethod.GEOFENCE)))
        assertTrue(fixture.orders.checkIn(draftId, CheckIn(CheckInMethod.GEOFENCE)))
    }

    private fun FakeOrderRepository.draft() = orders.value.dataOrNull().orEmpty().first { it.id == draftId }
}
