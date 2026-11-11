package tech.vauldex.dekita.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.vauldex.dekita.data.AuthFailure
import tech.vauldex.dekita.data.AuthRepository
import tech.vauldex.dekita.data.AuthResult
import tech.vauldex.dekita.domain.AuthInput

enum class EmailAuthMode { SIGN_UP, LOG_IN }

data class EmailAuthUiState(
    val mode: EmailAuthMode,
    val email: String = "",
    /** Kept in this ViewModel only, never in saved state, so it doesn't outlive the screen. */
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    /** An error about the whole form, such as a wrong email or password. */
    val formError: String? = null,
    val isSubmitting: Boolean = false,
    /** Errors appear on the first submit, then follow the user's edits. */
    val hasSubmitted: Boolean = false,
) {
    override fun toString() =
        "EmailAuthUiState(mode=$mode, email=••••, password=••••, emailError=$emailError, " +
            "passwordError=$passwordError, formError=$formError, isSubmitting=$isSubmitting)"
}

class EmailAuthViewModel(
    initialMode: EmailAuthMode,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EmailAuthUiState(mode = initialMode))
    val state: StateFlow<EmailAuthUiState> = _state.asStateFlow()

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events: Flow<AuthEvent> = _events.receiveAsFlow()

    fun onEmailChange(email: String) = _state.update { it.copy(email = email, formError = null).validatedIfSubmitted() }

    fun onPasswordChange(password: String) =
        _state.update { it.copy(password = password, formError = null).validatedIfSubmitted() }

    fun switchMode() = _state.update {
        val mode = if (it.mode == EmailAuthMode.SIGN_UP) EmailAuthMode.LOG_IN else EmailAuthMode.SIGN_UP
        EmailAuthUiState(mode = mode, email = it.email)
    }

    fun submit() {
        if (_state.value.isSubmitting) return
        val checked = _state.value.copy(hasSubmitted = true, formError = null).validated()
        _state.value = checked
        if (checked.emailError != null || checked.passwordError != null) return

        _state.update { it.copy(isSubmitting = true) }
        val email = AuthInput.normalizeEmail(checked.email)
        viewModelScope.launch {
            val result = when (checked.mode) {
                EmailAuthMode.SIGN_UP -> authRepository.signUpWithEmail(email, checked.password)
                EmailAuthMode.LOG_IN -> authRepository.signInWithEmail(email, checked.password)
            }
            when (result) {
                AuthResult.Success -> {
                    _state.update { it.copy(isSubmitting = false) }
                    _events.send(AuthEvent.SignedIn)
                }
                is AuthResult.Failure -> _state.update {
                    when (result.reason) {
                        AuthFailure.EMAIL_TAKEN -> it.copy(isSubmitting = false, emailError = AuthCopy.EMAIL_TAKEN)
                        else -> it.copy(isSubmitting = false, formError = AuthCopy.WRONG_CREDENTIALS)
                    }
                }
            }
        }
    }

    private fun EmailAuthUiState.validatedIfSubmitted() = if (hasSubmitted) validated() else this

    private fun EmailAuthUiState.validated(): EmailAuthUiState {
        val emailOk = AuthInput.isValidEmail(AuthInput.normalizeEmail(email))
        // Sign-up enforces the strength rule; log-in only needs something typed, so older passwords still work.
        val passwordError = when (mode) {
            EmailAuthMode.SIGN_UP -> AuthCopy.WEAK_PASSWORD.takeUnless { AuthInput.isValidPassword(password) }
            EmailAuthMode.LOG_IN -> AuthCopy.MISSING_PASSWORD.takeIf { password.isEmpty() }
        }
        return copy(emailError = AuthCopy.INVALID_EMAIL.takeUnless { emailOk }, passwordError = passwordError)
    }
}
