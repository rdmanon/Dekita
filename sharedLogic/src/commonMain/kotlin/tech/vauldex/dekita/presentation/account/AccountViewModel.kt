package tech.vauldex.dekita.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import tech.vauldex.dekita.data.AuthRepository
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.PaymentRepository
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.domain.PaymentMethod
import tech.vauldex.dekita.domain.UserProfile
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS

data class AccountUiState(
    /** Null while browsing as a guest. */
    val profile: UserProfile? = null,
    val isLoading: Boolean = true,
    val paymentMethods: List<PaymentMethod> = emptyList(),
) {
    val isSignedIn: Boolean get() = profile != null
}

/** The Account tab: profile and payment methods. The inbox moved to Orders with RCPT-01. */
class AccountViewModel(
    private val authRepository: AuthRepository,
    paymentRepository: PaymentRepository,
) : ViewModel() {

    val state: StateFlow<AccountUiState> =
        combine(authRepository.session, paymentRepository.methods) { session, methods ->
            AccountUiState(
                profile = session?.profile,
                isLoading = methods is LoadState.Loading,
                paymentMethods = methods.dataOrNull().orEmpty(),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            // Start from the current session, so a signed-in user never sees the guest card flash.
            AccountUiState(profile = authRepository.session.value?.profile),
        )

    fun signOut() = authRepository.signOut()
}
