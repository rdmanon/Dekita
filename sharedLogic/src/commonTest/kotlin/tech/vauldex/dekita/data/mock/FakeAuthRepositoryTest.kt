package tech.vauldex.dekita.data.mock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import tech.vauldex.dekita.data.AuthFailure
import tech.vauldex.dekita.data.AuthResult
import tech.vauldex.dekita.domain.AuthContact
import tech.vauldex.dekita.domain.AuthInput
import tech.vauldex.dekita.domain.DialCode
import tech.vauldex.dekita.domain.SignInMethod
import tech.vauldex.dekita.domain.SsoProvider

@OptIn(ExperimentalCoroutinesApi::class)
class FakeAuthRepositoryTest {

    private val phone = AuthContact.Phone(AuthInput.parseMobile(DialCode.JP, "+819000000000")!!)
    private val email = AuthContact.Email("lunchuser@example.com")

    @Test
    fun theTestCodeSignsInWithThePhone() = runTest {
        val repository = FakeAuthRepository(MockConfig())
        repository.requestOtp(phone)
        assertEquals(AuthResult.Success, repository.verifyOtp(phone, SeedAuth.TEST_OTP))
        assertEquals(SignInMethod.PHONE, repository.session.value?.method)
        assertEquals("+81 90-0000-0000", repository.session.value?.profile?.phone)
    }

    @Test
    fun theTestCodeSignsInWithTheEmail() = runTest {
        val repository = FakeAuthRepository(MockConfig())
        repository.requestOtp(email)
        assertEquals(AuthResult.Success, repository.verifyOtp(email, SeedAuth.TEST_OTP))
        assertEquals(SignInMethod.EMAIL, repository.session.value?.method)
        assertEquals("lunchuser@example.com", repository.session.value?.profile?.email)
    }

    @Test
    fun anyOtherCodeFailsAndStaysSignedOut() = runTest {
        val repository = FakeAuthRepository(MockConfig())
        assertEquals(AuthResult.Failure(AuthFailure.INVALID_CODE), repository.verifyOtp(phone, "000000"))
        assertEquals(AuthResult.Failure(AuthFailure.INVALID_CODE), repository.verifyOtp(email, "000000"))
        assertNull(repository.session.value)
    }

    @Test
    fun aCodeRequestTakesTheSameTimeForAnyContact() = runTest {
        // The answer must not show whether the contact already has an account.
        val repository = FakeAuthRepository(MockConfig())
        val start = currentTime
        repository.requestOtp(AuthContact.Email(SeedAuth.TEST_EMAIL))
        val firstTook = currentTime - start
        repository.requestOtp(AuthContact.Email("nobody.new@example.com"))
        assertEquals(firstTook, currentTime - start - firstTook)
    }

    @Test
    fun callsTakeTheLoadingDelay() = runTest {
        val repository = FakeAuthRepository(MockConfig())
        val start = currentTime
        repository.signInWith(SsoProvider.APPLE)
        assertEquals(MockConfig.DEFAULT_LOADING_DELAY.inWholeMilliseconds, currentTime - start)
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
    fun resetSignsOut() = runTest {
        val repository = FakeAuthRepository(MockConfig())
        repository.verifyOtp(email, SeedAuth.TEST_OTP)
        repository.reset()
        assertNull(repository.session.value)
    }
}
