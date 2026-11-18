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
import tech.vauldex.dekita.data.AuthRepository
import tech.vauldex.dekita.data.AuthResult
import tech.vauldex.dekita.domain.AuthContact
import tech.vauldex.dekita.domain.AuthInput
import tech.vauldex.dekita.domain.SsoProvider

data class SignInUiState(
    /** What the "Phone number or email" field holds, as typed. [AuthInput.parseContact] reads it. */
    val input: String = "",
    /** The code is being sent. Every option is disabled meanwhile. */
    val isSending: Boolean = false,
    /** The provider whose simulated sign-in is running. Every option is disabled meanwhile. */
    val ssoInProgress: SsoProvider? = null,
    /** The provider that just failed. The screen offers "Try another way". */
    val ssoError: SsoProvider? = null,
) {
    /** The field read as an email or a mobile number. Null when it is empty or neither. */
    val contact: AuthContact? get() = AuthInput.parseContact(input)

    /** Text typed that is neither an email nor a mobile number. The hint turns into an error. */
    val hasInputError: Boolean get() = input.isNotBlank() && contact == null

    /** The line under the field: what will happen for this input, or what is wrong with it. */
    val hint: String
        get() = when {
            input.isBlank() -> AuthCopy.HINT_IDLE
            contact is AuthContact.Email -> AuthCopy.HINT_EMAIL
            contact is AuthContact.Phone -> AuthCopy.HINT_PHONE
            else -> AuthCopy.HINT_INVALID
        }

    val canSend: Boolean get() = contact != null && !isBusy

    val isBusy: Boolean get() = isSending || ssoInProgress != null

    // Keeps the typed email or number out of anything that prints this state, such as a crash report.
    override fun toString() =
        "SignInUiState(input=••••, isSending=$isSending, ssoInProgress=$ssoInProgress, ssoError=$ssoError)"
}

/** The sign-in screen: one field for a phone number or an email, plus Google and Apple. */
class SignInViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events: Flow<AuthEvent> = _events.receiveAsFlow()

    fun onInputChange(raw: String) = _state.update { it.copy(input = raw.take(MAX_INPUT_LENGTH)) }

    /** Sends the 6-digit code to the number or address in the field. Does nothing while the field isn't valid. */
    fun sendCode() {
        val current = _state.value
        val contact = current.contact ?: return
        if (current.isBusy) return
        _state.update { it.copy(isSending = true, ssoError = null) }
        viewModelScope.launch {
            authRepository.requestOtp(contact)
            _state.update { it.copy(isSending = false) }
            _events.send(AuthEvent.CodeSent(contact))
        }
    }

    fun continueWith(provider: SsoProvider) {
        if (_state.value.isBusy) return
        _state.update { it.copy(ssoInProgress = provider, ssoError = null) }
        viewModelScope.launch {
            when (authRepository.signInWith(provider)) {
                AuthResult.Success -> {
                    _state.update { it.copy(ssoInProgress = null) }
                    _events.send(AuthEvent.SignedIn)
                }
                is AuthResult.Failure -> _state.update { it.copy(ssoInProgress = null, ssoError = provider) }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(ssoError = null) }

    private companion object {
        // An email is at most 254 characters, so anything longer can't be valid; stops a pasted wall of text.
        const val MAX_INPUT_LENGTH = 320
    }
}
