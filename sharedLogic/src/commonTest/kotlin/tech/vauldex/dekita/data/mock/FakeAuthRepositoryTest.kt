package tech.vauldex.dekita.data.mock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import tech.vauldex.dekita.data.AuthFailure
import tech.vauldex.dekita.data.AuthResult
import tech.vauldex.dekita.domain.AuthInput
import tech.vauldex.dekita.domain.DialCode
import tech.vauldex.dekita.domain.SignInMethod
import tech.vauldex.dekita.domain.SsoProvider

@OptIn(ExperimentalCoroutinesApi::class)
class FakeAuthRepositoryTest {

    private val phone = AuthInput.parseMobile(DialCode.JP, "+819000000000")!!

    @Test
    fun theTestCodeSignsInWithThePhone() = runTest {
        val repository = FakeAuthRepository(MockConfig())
        repository.requestOtp(phone)
        assertEquals(AuthResult.Success, repository.verifyOtp(phone, SeedAuth.TEST_OTP))
        assertEquals(SignInMethod.PHONE, repository.session.value?.method)
        assertEquals("+81 90-0000-0000", repository.session.value?.profile?.phone)
    }

    @Test
    fun anyOtherCodeFailsAndStaysSignedOut() = runTest {
        val repository = FakeAuthRepository(MockConfig())
        assertEquals(AuthResult.Failure(AuthFailure.INVALID_CODE), repository.verifyOtp(phone, "000000"))
        assertNull(repository.session.value)
    }

    @Test
    fun callsTakeTheLoadingDelay() = runTest {
        val repository = FakeAuthRepository(MockConfig())
        val start = currentTime
        repository.signInWith(SsoProvider.APPLE)
        assertEquals(MockConfig.DEFAULT_LOADING_DELAY.inWholeMilliseconds, currentTime - start)
    }

    @Test
    fun signUpThenSignOutThenLogInWithTheSamePassword() = runTest {
        val repository = FakeAuthRepository(MockConfig())
        assertEquals(AuthResult.Success, repository.signUpWithEmail("lunchuser@example.com", "Pass1234!"))
        assertEquals(SignInMethod.EMAIL, repository.session.value?.method)
        repository.signOut()
        assertNull(repository.session.value)
        assertEquals(AuthResult.Success, repository.signInWithEmail("LunchUser@example.com", "Pass1234!"))
        assertEquals("LunchUser@example.com", repository.session.value?.profile?.email)
    }

    @Test
    fun theSeedAccountLogsIn() = runTest {
        val repository = FakeAuthRepository(MockConfig())
        assertEquals(AuthResult.Success, repository.signInWithEmail(SeedAuth.TEST_EMAIL, SeedAuth.TEST_PASSWORD))
    }

    @Test
    fun wrongPasswordAndUnknownEmailFailTheSameWay() = runTest {
        val repository = FakeAuthRepository(MockConfig())
        val wrongPassword = repository.signInWithEmail(SeedAuth.TEST_EMAIL, "Wrong1234")
        val unknownEmail = repository.signInWithEmail("nobody@example.com", SeedAuth.TEST_PASSWORD)
        assertEquals(AuthResult.Failure(AuthFailure.WRONG_CREDENTIALS), wrongPassword)
        assertEquals(wrongPassword, unknownEmail)
        assertNull(repository.session.value)
    }

    @Test
    fun signingUpWithATakenEmailFails() = runTest {
        val repository = FakeAuthRepository(MockConfig())
        assertEquals(AuthResult.Failure(AuthFailure.EMAIL_TAKEN), repository.signUpWithEmail(SeedAuth.TEST_EMAIL, "Pass1234!"))
        assertNull(repository.session.value)
    }

    @Test
    fun ssoFailsOnlyWhileForced() = runTest {
        val config = MockConfig()
        val repository = FakeAuthRepository(config)
        config.forceSsoFailure.value = true
        assertEquals(AuthResult.Failure(AuthFailure.SSO_FAILED), repository.signInWith(SsoProvider.GOOGLE))
        assertNull(repository.session.value)
        config.forceSsoFailure.value = false
        assertEquals(AuthResult.Success, repository.signInWith(SsoProvider.GOOGLE))
        assertEquals(SignInMethod.GOOGLE, repository.session.value?.method)
    }

    @Test
    fun resetSignsOutAndForgetsNewAccounts() = runTest {
        val repository = FakeAuthRepository(MockConfig())
        repository.signUpWithEmail("lunchuser@example.com", "Pass1234!")
        repository.reset()
        assertNull(repository.session.value)
        assertEquals(
            AuthResult.Failure(AuthFailure.WRONG_CREDENTIALS),
            repository.signInWithEmail("lunchuser@example.com", "Pass1234!"),
        )
    }
}
