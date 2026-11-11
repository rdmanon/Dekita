package tech.vauldex.dekita.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.vauldex.dekita.data.AuthRepository
import tech.vauldex.dekita.domain.AuthInput

data class ForgotPasswordUiState(
    val email: String = "",
    val emailError: String? = null,
    val isSending: Boolean = false,
    /** Set after any valid request, known email or not, so the screen can't reveal which accounts exist. */
    val isSent: Boolean = false,
) {
    override fun toString() =
        "ForgotPasswordUiState(email=••••, emailError=$emailError, isSending=$isSending, isSent=$isSent)"
}

class ForgotPasswordViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordUiState())
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

    fun onEmailChange(email: String) = _state.update { it.copy(email = email, emailError = null) }

    fun send() {
        val current = _state.value
        if (current.isSending || current.isSent) return
        val email = AuthInput.normalizeEmail(current.email)
        if (!AuthInput.isValidEmail(email)) {
            _state.update { it.copy(emailError = AuthCopy.INVALID_EMAIL) }
            return
        }
        _state.update { it.copy(isSending = true) }
        viewModelScope.launch {
            authRepository.requestPasswordReset(email)
            _state.update { it.copy(isSending = false, isSent = true) }
        }
    }
}
