package tech.vauldex.dekita.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import tech.vauldex.dekita.data.InboxRepository
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.PaymentRepository
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.domain.InboxMessage
import tech.vauldex.dekita.domain.PaymentMethod
import tech.vauldex.dekita.domain.UserProfile
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS

data class AccountUiState(
    val profile: UserProfile,
    val isLoading: Boolean = true,
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val inbox: List<InboxMessage> = emptyList(),
)

class AccountViewModel(
    profile: UserProfile,
    paymentRepository: PaymentRepository,
    private val inboxRepository: InboxRepository,
) : ViewModel() {

    val state: StateFlow<AccountUiState> =
        combine(paymentRepository.methods, inboxRepository.messages) { methods, messages ->
            AccountUiState(
                profile = profile,
                isLoading = methods is LoadState.Loading || messages is LoadState.Loading,
                paymentMethods = methods.dataOrNull().orEmpty(),
                inbox = messages.dataOrNull().orEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AccountUiState(profile))

    fun markRead(messageId: String) = inboxRepository.markRead(messageId)
}
