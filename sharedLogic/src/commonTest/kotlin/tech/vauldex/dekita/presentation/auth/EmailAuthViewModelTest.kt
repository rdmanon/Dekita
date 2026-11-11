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
import tech.vauldex.dekita.data.AuthResult
import tech.vauldex.dekita.data.mock.FakeAuthRepository
import tech.vauldex.dekita.data.mock.MockConfig
import tech.vauldex.dekita.data.mock.SeedAuth
import tech.vauldex.dekita.domain.SignInMethod
import tech.vauldex.dekita.presentation.collectEvents

@OptIn(ExperimentalCoroutinesApi::class)
class EmailAuthViewModelTest {

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

    private fun TestScope.finishRequest() {
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
    }

    @Test
    fun weakPasswordIsRejectedInlineOnSubmit() = runTest(mainDispatcher) {
        val viewModel = EmailAuthViewModel(EmailAuthMode.SIGN_UP, repository)
        val events = collectEvents(viewModel.events)
        viewModel.onEmailChange("lunchuser@example.com")
        viewModel.onPasswordChange("password")
        assertNull(viewModel.state.value.passwordError, "No error before submit")

        viewModel.submit()
        finishRequest()

        assertEquals(
            "Use at least 8 characters with at least 1 number and 1 letter.",
            viewModel.state.value.passwordError,
        )
        assertNull(viewModel.state.value.emailError)
        assertFalse(viewModel.state.value.isSubmitting)
        assertTrue(events.isEmpty())
        assertNull(repository.session.value)
        // No account was created, so the email is still free.
        assertEquals(AuthResult.Success, repository.signUpWithEmail("lunchuser@example.com", "Pass1234!"))
    }

    @Test
    fun validSignUpSignsIn() = runTest(mainDispatcher) {
        val viewModel = EmailAuthViewModel(EmailAuthMode.SIGN_UP, repository)
        val events = collectEvents(viewModel.events)
        viewModel.onEmailChange("lunchuser@example.com")
        viewModel.onPasswordChange("Pass1234!")

        viewModel.submit()
        assertTrue(viewModel.state.value.isSubmitting)
        finishRequest()

        assertEquals(listOf<AuthEvent>(AuthEvent.SignedIn), events)
        assertEquals(SignInMethod.EMAIL, repository.session.value?.method)
        assertEquals("lunchuser@example.com", repository.session.value?.profile?.email)
    }

    @Test
    fun invalidEmailIsRejectedAndTheErrorFollowsEdits() = runTest(mainDispatcher) {
        val viewModel = EmailAuthViewModel(EmailAuthMode.SIGN_UP, repository)
        viewModel.onEmailChange("lunchuser@example")
        viewModel.onPasswordChange("Pass1234!")
        viewModel.submit()
        assertEquals(AuthCopy.INVALID_EMAIL, viewModel.state.value.emailError)
        assertFalse(viewModel.state.value.isSubmitting)

        viewModel.onEmailChange("lunchuser@example.com")
        assertNull(viewModel.state.value.emailError)
    }

    @Test
    fun fullWidthEmailIsNormalizedBeforeSigningUp() = runTest(mainDispatcher) {
        val viewModel = EmailAuthViewModel(EmailAuthMode.SIGN_UP, repository)
        viewModel.onEmailChange("ｌｕｎｃｈｕｓｅｒ＠ｅｘａｍｐｌｅ．ｃｏｍ")
        viewModel.onPasswordChange("Pass1234!")
        viewModel.submit()
        finishRequest()
        assertEquals("lunchuser@example.com", repository.session.value?.profile?.email)
    }

    @Test
    fun takenEmailShowsAnInlineError() = runTest(mainDispatcher) {
        val viewModel = EmailAuthViewModel(EmailAuthMode.SIGN_UP, repository)
        viewModel.onEmailChange(SeedAuth.TEST_EMAIL)
        viewModel.onPasswordChange("Pass1234!")
        viewModel.submit()
        finishRequest()
        assertEquals(AuthCopy.EMAIL_TAKEN, viewModel.state.value.emailError)
        assertNull(repository.session.value)
    }

    @Test
    fun logInWithTheSeedAccount() = runTest(mainDispatcher) {
        val viewModel = EmailAuthViewModel(EmailAuthMode.LOG_IN, repository)
        val events = collectEvents(viewModel.events)
        viewModel.onEmailChange(SeedAuth.TEST_EMAIL)
        viewModel.onPasswordChange(SeedAuth.TEST_PASSWORD)
        viewModel.submit()
        finishRequest()
        assertEquals(listOf<AuthEvent>(AuthEvent.SignedIn), events)
    }

    @Test
    fun wrongPasswordShowsOneErrorForTheForm() = runTest(mainDispatcher) {
        val viewModel = EmailAuthViewModel(EmailAuthMode.LOG_IN, repository)
        viewModel.onEmailChange(SeedAuth.TEST_EMAIL)
        viewModel.onPasswordChange("Wrong1234")
        viewModel.submit()
        finishRequest()
        assertEquals("Email or password is incorrect.", viewModel.state.value.formError)
        assertNull(viewModel.state.value.emailError)
        assertNull(viewModel.state.value.passwordError)
        assertNull(repository.session.value)
    }

    @Test
    fun logInOnlyNeedsAPasswordTyped() = runTest(mainDispatcher) {
        val viewModel = EmailAuthViewModel(EmailAuthMode.LOG_IN, repository)
        viewModel.onEmailChange(SeedAuth.TEST_EMAIL)
        viewModel.submit()
        assertEquals(AuthCopy.MISSING_PASSWORD, viewModel.state.value.passwordError)
    }

    @Test
    fun switchingModeKeepsTheEmailAndClearsErrors() = runTest(mainDispatcher) {
        val viewModel = EmailAuthViewModel(EmailAuthMode.SIGN_UP, repository)
        viewModel.onEmailChange("lunchuser@example.com")
        viewModel.onPasswordChange("short")
        viewModel.submit()
        viewModel.switchMode()
        with(viewModel.state.value) {
            assertEquals(EmailAuthMode.LOG_IN, mode)
            assertEquals("lunchuser@example.com", email)
            assertEquals("", password)
            assertNull(passwordError)
            assertFalse(hasSubmitted)
        }
    }

    @Test
    fun stateHidesThePasswordWhenPrinted() {
        val viewModel = EmailAuthViewModel(EmailAuthMode.LOG_IN, repository)
        viewModel.onPasswordChange("Secret1234")
        assertFalse(viewModel.state.value.toString().contains("Secret1234"))
    }
}
