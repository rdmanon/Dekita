package tech.vauldex.dekita.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.vauldex.dekita.data.AuthRepository
import tech.vauldex.dekita.data.AuthResult
import tech.vauldex.dekita.domain.AuthInput

data class OtpUiState(
    /** The number the code went to, formatted for display. */
    val phone: String,
    val code: String = "",
    val error: String? = null,
    val isVerifying: Boolean = false,
    val isResending: Boolean = false,
    val resendSecondsLeft: Int = OtpViewModel.RESEND_COOLDOWN.inWholeSeconds.toInt(),
) {
    val canResend: Boolean get() = resendSecondsLeft == 0 && !isResending && !isVerifying

    override fun toString() =
        "OtpUiState(phone=••••, code=••••, error=$error, isVerifying=$isVerifying, " +
            "isResending=$isResending, resendSecondsLeft=$resendSecondsLeft)"
}

/**
 * The 6-digit code step. The resend cooldown is measured with [timeSource] rather than by counting ticks,
 * so it stays right when the app sits in the background. Tests pass the test scheduler's time source.
 */
class OtpViewModel(
    phoneE164: String,
    private val authRepository: AuthRepository,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : ViewModel() {

    private val phone = requireNotNull(AuthInput.parseE164(phoneE164)) { "OtpViewModel needs a mobile number" }

    private val _state = MutableStateFlow(OtpUiState(phone = phone.formatted()))
    val state: StateFlow<OtpUiState> = _state.asStateFlow()

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events: Flow<AuthEvent> = _events.receiveAsFlow()

    private var cooldownJob: Job? = null

    init {
        // The previous screen has just sent the first code.
        startCooldown()
    }

    /** Takes what the field holds; full-width digits become half-width. Verifies once 6 digits are in. */
    fun onCodeChange(raw: String) {
        if (_state.value.isVerifying) return
        val code = AuthInput.normalizeOtp(raw)
        _state.update { it.copy(code = code, error = if (code.isEmpty()) it.error else null) }
        if (AuthInput.isValidOtp(code)) verify()
    }

    fun verify() {
        val current = _state.value
        if (current.isVerifying) return
        if (!AuthInput.isValidOtp(current.code)) {
            _state.update { it.copy(error = AuthCopy.INCOMPLETE_CODE) }
            return
        }
        _state.update { it.copy(isVerifying = true, error = null) }
        viewModelScope.launch {
            when (authRepository.verifyOtp(phone, current.code)) {
                AuthResult.Success -> {
                    cooldownJob?.cancel()
                    _state.update { it.copy(isVerifying = false) }
                    _events.send(AuthEvent.SignedIn)
                }
                // Clear the field so the next attempt starts fresh, and stay on this screen.
                is AuthResult.Failure -> _state.update { it.copy(isVerifying = false, code = "", error = AuthCopy.INVALID_CODE) }
            }
        }
    }

    fun resend() {
        if (!_state.value.canResend) return
        _state.update { it.copy(isResending = true, code = "", error = null) }
        viewModelScope.launch {
            authRepository.requestOtp(phone)
            _state.update { it.copy(isResending = false) }
            startCooldown()
        }
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        val sentAt = timeSource.markNow()
        cooldownJob = viewModelScope.launch {
            while (true) {
                val remaining = RESEND_COOLDOWN - sentAt.elapsedNow()
                val secondsLeft = remaining.wholeSecondsRoundedUp()
                _state.update { it.copy(resendSecondsLeft = secondsLeft) }
                if (secondsLeft == 0) break
                // Wake at the next whole second, so the label never skips a number.
                delay(remaining - (secondsLeft - 1).seconds)
            }
        }
    }

    companion object {
        val RESEND_COOLDOWN = 60.seconds
    }
}

private fun Duration.wholeSecondsRoundedUp(): Int =
    if (this <= Duration.ZERO) 0 else ((inWholeMilliseconds + 999) / 1_000).toInt()
