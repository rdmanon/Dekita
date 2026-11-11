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
}
