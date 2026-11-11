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
import tech.vauldex.dekita.domain.AuthInput
import tech.vauldex.dekita.domain.DialCode

data class PhoneEntryUiState(
    val dialCode: DialCode = DialCode.DEFAULT,
    /** As typed. [AuthInput.parseMobile] normalizes it when the code is sent. */
    val number: String = "",
    val error: String? = null,
    val isSending: Boolean = false,
) {
    override fun toString() = "PhoneEntryUiState(dialCode=$dialCode, number=••••, error=$error, isSending=$isSending)"
}

class PhoneEntryViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(PhoneEntryUiState())
    val state: StateFlow<PhoneEntryUiState> = _state.asStateFlow()

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events: Flow<AuthEvent> = _events.receiveAsFlow()

    fun onDialCodeChange(dialCode: DialCode) = _state.update { it.copy(dialCode = dialCode, error = null) }

    fun onNumberChange(number: String) = _state.update { it.copy(number = number, error = null) }

    fun sendCode() {
        val current = _state.value
        if (current.isSending) return
        val phone = AuthInput.parseMobile(current.dialCode, current.number)
        if (phone == null) {
            _state.update { it.copy(error = AuthCopy.INVALID_PHONE) }
            return
        }
        _state.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            authRepository.requestOtp(phone)
            _state.update { it.copy(isSending = false) }
            _events.send(AuthEvent.CodeSent(phone))
        }
    }
}
