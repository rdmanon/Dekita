package tech.vauldex.dekita.presentation.auth

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import tech.vauldex.dekita.data.mock.FakeAuthRepository
import tech.vauldex.dekita.data.mock.MockConfig
import tech.vauldex.dekita.domain.DialCode
import tech.vauldex.dekita.presentation.collectEvents

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneEntryViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun japanIsTheDefaultDialCode() {
        assertEquals(DialCode.JP, PhoneEntryViewModel(FakeAuthRepository(MockConfig())).state.value.dialCode)
    }

    @Test
    fun validNumberSendsTheCode() = runTest(mainDispatcher) {
        val viewModel = PhoneEntryViewModel(FakeAuthRepository(MockConfig()))
        val events = collectEvents(viewModel.events)
        runCurrent()

        viewModel.onNumberChange("+819000000000")
        viewModel.sendCode()
        assertTrue(viewModel.state.value.isSending)
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()

        assertFalse(viewModel.state.value.isSending)
        val sent = assertIs<AuthEvent.CodeSent>(events.single())
        assertEquals("+819000000000", sent.phone.e164)
    }

    @Test
    fun fullWidthNumberIsAccepted() = runTest(mainDispatcher) {
        val viewModel = PhoneEntryViewModel(FakeAuthRepository(MockConfig()))
        val events = collectEvents(viewModel.events)
        runCurrent()

        viewModel.onNumberChange("０９０－１２３４－５６７８")
        viewModel.sendCode()
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()

        assertEquals("+819012345678", assertIs<AuthEvent.CodeSent>(events.single()).phone.e164)
    }

    @Test
    fun invalidNumberShowsAnErrorAndSendsNothing() = runTest(mainDispatcher) {
        val viewModel = PhoneEntryViewModel(FakeAuthRepository(MockConfig()))
        val events = collectEvents(viewModel.events)
        runCurrent()

        viewModel.onNumberChange("03-1234-5678")
        viewModel.sendCode()
        runCurrent()

        assertEquals(AuthCopy.INVALID_PHONE, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isSending)
        assertTrue(events.isEmpty())

        viewModel.onNumberChange("090")
        assertNull(viewModel.state.value.error, "Editing clears the error until the next submit")
    }
}
