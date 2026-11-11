package tech.vauldex.dekita.presentation.auth

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import tech.vauldex.dekita.data.mock.FakeAuthRepository
import tech.vauldex.dekita.data.mock.MockConfig
import tech.vauldex.dekita.data.mock.SeedAuth
import tech.vauldex.dekita.domain.SignInMethod
import tech.vauldex.dekita.presentation.collectEvents

@OptIn(ExperimentalCoroutinesApi::class)
class OtpViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val repository = FakeAuthRepository(MockConfig())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.newViewModel() = OtpViewModel("+819000000000", repository, testScheduler.timeSource)

    private fun TestScope.finishRequest() {
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
    }

    @Test
    fun showsTheNumberTheCodeWentTo() = runTest(mainDispatcher) {
        assertEquals("+81 90-0000-0000", newViewModel().state.value.phone)
    }

    @Test
    fun resendWaitsSixtySeconds() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        runCurrent()
        assertEquals(60, viewModel.state.value.resendSecondsLeft)
        assertFalse(viewModel.state.value.canResend)

        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(59, viewModel.state.value.resendSecondsLeft)

        advanceTimeBy(58.seconds)
        runCurrent()
        assertEquals(1, viewModel.state.value.resendSecondsLeft)
        viewModel.resend()
        assertFalse(viewModel.state.value.isResending, "Resend must wait for the cooldown")

        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(0, viewModel.state.value.resendSecondsLeft)
        assertTrue(viewModel.state.value.canResend)
    }

    @Test
    fun resendRestartsTheCooldown() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        runCurrent()
        advanceTimeBy(60.seconds)
        runCurrent()

        viewModel.resend()
        assertTrue(viewModel.state.value.isResending)
        assertFalse(viewModel.state.value.canResend)
        finishRequest()
        assertFalse(viewModel.state.value.isResending)
        assertEquals(60, viewModel.state.value.resendSecondsLeft)
    }

    @Test
    fun wrongCodeShowsTheErrorAndStaysOnTheScreen() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        val events = collectEvents(viewModel.events)
        runCurrent()

        viewModel.onCodeChange("000000")
        assertTrue(viewModel.state.value.isVerifying)
        finishRequest()

        assertEquals("Invalid code. Please try again.", viewModel.state.value.error)
        assertEquals("", viewModel.state.value.code)
        assertFalse(viewModel.state.value.isVerifying)
        assertTrue(events.isEmpty())
        assertNull(repository.session.value)
    }

    @Test
    fun typingAgainClearsTheError() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        runCurrent()
        viewModel.onCodeChange("000000")
        finishRequest()
        viewModel.onCodeChange("1")
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun theTestCodeSignsIn() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        val events = collectEvents(viewModel.events)
        runCurrent()

        viewModel.onCodeChange(SeedAuth.TEST_OTP)
        finishRequest()

        assertEquals(listOf<AuthEvent>(AuthEvent.SignedIn), events)
        assertEquals(SignInMethod.PHONE, repository.session.value?.method)
    }

    @Test
    fun fullWidthCodeIsNormalizedAndAccepted() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        val events = collectEvents(viewModel.events)
        runCurrent()

        viewModel.onCodeChange("１２３４５６")
        assertEquals("123456", viewModel.state.value.code)
        finishRequest()

        assertEquals(listOf<AuthEvent>(AuthEvent.SignedIn), events)
    }

    @Test
    fun verifyingAnIncompleteCodeAsksForSixDigits() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        viewModel.onCodeChange("123")
        viewModel.verify()
        assertEquals(AuthCopy.INCOMPLETE_CODE, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isVerifying)
    }

    @Test
    fun stateHidesTheCodeAndNumberWhenPrinted() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        viewModel.onCodeChange("12345")
        val printed = viewModel.state.value.toString()
        assertFalse(printed.contains("12345"))
        assertFalse(printed.contains("0000"))
    }
}
