package tech.vauldex.dekita.presentation.auth

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
import tech.vauldex.dekita.data.mock.SeedAuth

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {

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

    private fun TestScope.requestReset(email: String): ForgotPasswordUiState {
        val viewModel = ForgotPasswordViewModel(repository)
        viewModel.onEmailChange(email)
        viewModel.send()
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
        return viewModel.state.value
    }

    @Test
    fun knownAndUnknownEmailsGetTheSameConfirmation() = runTest(mainDispatcher) {
        val known = requestReset(SeedAuth.TEST_EMAIL)
        val unknown = requestReset("nobody@example.com")
        assertTrue(known.isSent)
        assertEquals(known.copy(email = ""), unknown.copy(email = ""))
    }

    @Test
    fun invalidEmailIsRejectedInline() = runTest(mainDispatcher) {
        val state = requestReset("nobody@")
        assertEquals(AuthCopy.INVALID_EMAIL, state.emailError)
        assertFalse(state.isSent)
    }
}
