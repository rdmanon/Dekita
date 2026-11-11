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
import tech.vauldex.dekita.domain.SsoProvider

data class SignInUiState(
    /** The provider whose simulated sign-in is running. Every option is disabled meanwhile. */
    val ssoInProgress: SsoProvider? = null,
    /** The provider that just failed. The screen offers "Try another way". */
    val ssoError: SsoProvider? = null,
) {
    val isBusy: Boolean get() = ssoInProgress != null
}

class SignInViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events: Flow<AuthEvent> = _events.receiveAsFlow()

    fun continueWith(provider: SsoProvider) {
        if (_state.value.isBusy) return
        _state.value = SignInUiState(ssoInProgress = provider)
        viewModelScope.launch {
            when (authRepository.signInWith(provider)) {
                AuthResult.Success -> {
                    _state.value = SignInUiState()
                    _events.send(AuthEvent.SignedIn)
                }
                is AuthResult.Failure -> _state.value = SignInUiState(ssoError = provider)
            }
        }
    }

    fun dismissError() = _state.update { it.copy(ssoError = null) }
}
