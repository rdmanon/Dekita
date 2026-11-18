package tech.vauldex.dekita.presentation.auth

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
import tech.vauldex.dekita.domain.AuthContact
import tech.vauldex.dekita.domain.SignInMethod
import tech.vauldex.dekita.domain.SsoProvider
import tech.vauldex.dekita.presentation.collectEvents

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val config = MockConfig()
    private val repository = FakeAuthRepository(config)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.finishRequest() {
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
    }

    @Test
    fun ssoShowsLoadingThenSignsIn() = runTest(mainDispatcher) {
        val viewModel = SignInViewModel(repository)
        val events = collectEvents(viewModel.events)

        viewModel.continueWith(SsoProvider.GOOGLE)
        assertEquals(SsoProvider.GOOGLE, viewModel.state.value.ssoInProgress)
        assertTrue(viewModel.state.value.isBusy)
        // A second tap while loading is ignored.
        viewModel.continueWith(SsoProvider.APPLE)
        assertEquals(SsoProvider.GOOGLE, viewModel.state.value.ssoInProgress)

        finishRequest()
        assertFalse(viewModel.state.value.isBusy)
        assertEquals(listOf<AuthEvent>(AuthEvent.SignedIn), events)
        assertEquals(SignInMethod.GOOGLE, repository.session.value?.method)
    }

    @Test
    fun forcedFailureOffersAnotherWay() = runTest(mainDispatcher) {
        config.forceSsoFailure.value = true
        val viewModel = SignInViewModel(repository)
        val events = collectEvents(viewModel.events)

        viewModel.continueWith(SsoProvider.GOOGLE)
        finishRequest()

        assertEquals(SsoProvider.GOOGLE, viewModel.state.value.ssoError)
        assertFalse(viewModel.state.value.isBusy, "Phone and Email stay available")
        assertTrue(events.isEmpty())
        assertNull(repository.session.value)

        viewModel.dismissError()
        assertNull(viewModel.state.value.ssoError)
    }

    @Test
    fun appleSignsInWithApple() = runTest(mainDispatcher) {
        val viewModel = SignInViewModel(repository)
        viewModel.continueWith(SsoProvider.APPLE)
        finishRequest()
        assertEquals(SignInMethod.APPLE, repository.session.value?.method)
    }

    @Test
    fun theHintFollowsWhatIsTyped() = runTest(mainDispatcher) {
        val viewModel = SignInViewModel(repository)
        assertEquals(AuthCopy.HINT_IDLE, viewModel.state.value.hint)
        assertFalse(viewModel.state.value.canSend)
        assertFalse(viewModel.state.value.hasInputError, "An empty field isn't an error yet")

        viewModel.onInputChange("lunchuser@example.com")
        assertEquals(AuthCopy.HINT_EMAIL, viewModel.state.value.hint)
        assertTrue(viewModel.state.value.canSend)

        viewModel.onInputChange("090-1234-5678")
        assertEquals(AuthCopy.HINT_PHONE, viewModel.state.value.hint)
        assertTrue(viewModel.state.value.canSend)

        viewModel.onInputChange("090-12")
        assertEquals(AuthCopy.HINT_INVALID, viewModel.state.value.hint)
        assertTrue(viewModel.state.value.hasInputError)
        assertFalse(viewModel.state.value.canSend)

        viewModel.onInputChange("")
        assertEquals(AuthCopy.HINT_IDLE, viewModel.state.value.hint)
        assertFalse(viewModel.state.value.hasInputError)
    }

    @Test
    fun sendingACodeToAnEmailOrAPhoneMovesToTheCodeStep() = runTest(mainDispatcher) {
        val viewModel = SignInViewModel(repository)
        val events = collectEvents(viewModel.events)

        viewModel.onInputChange("lunchuser@example.com")
        viewModel.sendCode()
        assertTrue(viewModel.state.value.isSending)
        assertTrue(viewModel.state.value.isBusy, "Every option is off while the code is sent")
        // A second tap while sending is ignored.
        viewModel.sendCode()
        finishRequest()
        assertFalse(viewModel.state.value.isSending)
        assertEquals(listOf<AuthEvent>(AuthEvent.CodeSent(AuthContact.Email("lunchuser@example.com"))), events)

        viewModel.onInputChange("090-1234-5678")
        viewModel.sendCode()
        finishRequest()
        val sent = events.last() as AuthEvent.CodeSent
        assertEquals("+819012345678", sent.contact.key)
        assertNull(repository.session.value, "A code is only sent: signing in takes the code step")
    }

    @Test
    fun aFieldThatIsNeitherSendsNothing() = runTest(mainDispatcher) {
        val viewModel = SignInViewModel(repository)
        val events = collectEvents(viewModel.events)

        viewModel.onInputChange("not an email or a number")
        viewModel.sendCode()
        finishRequest()

        assertFalse(viewModel.state.value.isSending)
        assertTrue(events.isEmpty())
    }

    @Test
    fun theTypedContactStaysOutOfThePrintedState() = runTest(mainDispatcher) {
        val viewModel = SignInViewModel(repository)
        viewModel.onInputChange("lunchuser@example.com")
        assertFalse(viewModel.state.value.toString().contains("lunchuser"))
    }
}
