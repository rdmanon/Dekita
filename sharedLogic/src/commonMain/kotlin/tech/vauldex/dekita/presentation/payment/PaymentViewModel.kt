package tech.vauldex.dekita.presentation.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.random.Random
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tech.vauldex.dekita.data.CartRepository
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.OrderRepository
import tech.vauldex.dekita.data.PaymentRepository
import tech.vauldex.dekita.data.PaymentRequest
import tech.vauldex.dekita.data.PaymentResult
import tech.vauldex.dekita.data.PaymentService
import tech.vauldex.dekita.data.StoreRepository
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.domain.CardFormError
import tech.vauldex.dekita.domain.ClientPlatform
import tech.vauldex.dekita.domain.DemoCardForm
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.Money
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.PaymentApproval
import tech.vauldex.dekita.domain.PaymentMethod
import tech.vauldex.dekita.domain.approval
import tech.vauldex.dekita.domain.isOfferedOn
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS
import tech.vauldex.dekita.presentation.choiceLabel
import tech.vauldex.dekita.presentation.itemCountLabel
import tech.vauldex.dekita.presentation.orderLabel

/** What the payment sheet charges. */
sealed interface PaymentTarget {
    /** "Pay now" from Cart Review: In-store-only, Pickup-only and Takeout carts. */
    data object Cart : PaymentTarget

    /** Eat-In: the checked-in draft, charged once the seat check (SEAT-01) confirms a seat. */
    data class Draft(val orderId: String) : PaymentTarget
}

enum class PaymentPhase {
    /** Choosing a method. Pay is on. */
    READY,

    /** The mock PayPay or Rakuten Pay page, or the mock wallet sheet, is open for the selected method. */
    APPROVING,

    /** Charging. Pay is off and the sheet can't be closed. */
    PROCESSING,

    /** The method was declined; nothing was charged. PAY-02 adds retry handling; until then the sheet says so. */
    DECLINED,

    /** The customer cancelled on the mock approval screen; nothing was charged. */
    CANCELLED,

    /** The draft can't be paid any more, for example after a reset. Nothing was charged. */
    UNAVAILABLE,

    /** Paid. The order ticket opens. */
    PAID,
}

data class PaymentUiState(
    val isLoading: Boolean = true,
    /** "Test Ramen Bar · Takeout · 2 items". Empty when there is nothing to pay. */
    val summary: String = "",
    val storeName: String = "",
    /** The amount charged: Cart Review's total, or the draft's. Null when there is nothing to pay. */
    val total: Money? = null,
    /** The methods this phone offers: Apple Pay on iOS only, Google Pay on Android only. */
    val methods: List<PaymentMethod> = emptyList(),
    val selectedMethod: PaymentMethod? = null,
    val phase: PaymentPhase = PaymentPhase.READY,
    val isEatIn: Boolean = false,
) {
    val hasSomethingToPay: Boolean get() = total != null

    val isProcessing: Boolean get() = phase == PaymentPhase.PROCESSING

    /** Pay is on when a method is chosen and no charge or approval is under way. */
    val canPay: Boolean
        get() = !isLoading && total != null && selectedMethod != null && phase in retryablePhases

    /** The method list and Add card are locked while a charge or an approval is under way, and after paying. */
    val canChangeMethod: Boolean get() = phase in retryablePhases

    /** The sheet stays open while charging, so the result is never missed. */
    val canDismiss: Boolean get() = phase != PaymentPhase.PROCESSING && phase != PaymentPhase.PAID

    /** The method whose mock approval screen is open, or null. */
    val approvingMethod: PaymentMethod? get() = selectedMethod?.takeIf { phase == PaymentPhase.APPROVING }

    val payButtonLabel: String get() = total?.let { "Pay ${it.formatted()}" } ?: "Pay"

    /** Under the methods: why the last attempt failed. Null when there is nothing to say. */
    val errorMessage: String?
        get() = when (phase) {
            PaymentPhase.DECLINED -> "Payment declined. You weren't charged. Try again or choose another method."
            PaymentPhase.CANCELLED ->
                "Payment cancelled. You weren't charged, and your ${if (isEatIn) "order" else "cart"} is kept."
            PaymentPhase.UNAVAILABLE -> "This order can't be paid any more. You weren't charged."
            else -> null
        }

    private companion object {
        val retryablePhases = setOf(PaymentPhase.READY, PaymentPhase.DECLINED, PaymentPhase.CANCELLED)
    }
}

sealed interface PaymentEvent {
    /** The charge went through and the order is PAID or QUEUED. Open its ticket. */
    data class Paid(val orderId: String, val orderNumber: Int) : PaymentEvent
}

/**
 * The payment sheet (PAY-01), opened by "Pay now" on Cart Review or by the seat check's Yes (SEAT-01). Every method is
 * simulated. A card is charged when Pay is tapped; PayPay, Rakuten Pay and the wallets first open a mock approval
 * screen. Pay turns off as soon as it is tapped, and the payment service is idempotent on this sheet's checkout id,
 * so a double tap makes one order.
 */
class PaymentViewModel(
    private val target: PaymentTarget,
    private val platform: ClientPlatform,
    private val paymentService: PaymentService,
    private val paymentRepository: PaymentRepository,
    private val cartRepository: CartRepository,
    private val orderRepository: OrderRepository,
    private val storeRepository: StoreRepository,
) : ViewModel() {

    /** What is being paid for, as shown on the sheet. */
    private class Payable(val summary: String, val storeName: String, val total: Money)

    /** The payment service's idempotency key: one per sheet, so every retry of this checkout uses it. */
    private val checkoutId = "checkout-${Random.nextLong().toULong().toString(16)}"

    private val phase = MutableStateFlow(PaymentPhase.READY)
    private val selectedId = MutableStateFlow<String?>(null)

    /** The paid order, so the sheet keeps its summary after the cart empties and the draft moves on. */
    private val paidOrder = MutableStateFlow<Order?>(null)

    /** Null when there is nothing to pay; loading only while the draft's orders load. */
    private val payable: Flow<LoadState<Payable?>> = when (target) {
        PaymentTarget.Cart -> combine(cartRepository.cart, storeRepository.stores, paidOrder) { cart, stores, paid ->
            val storeId = paid?.storeId ?: cart?.storeId
            val store = stores.dataOrNull()?.firstOrNull { it.id == storeId }
            val modeLabel = { mode: FulfillmentMode -> mode.orderLabel(store?.businessType) }
            LoadState.Success(
                paid?.let { payableOf(it, modeLabel(it.mode)) }
                    ?: cart?.takeUnless { it.isEmpty }?.let { Payable(summaryOf(store?.name.orEmpty(), modeLabel(it.mode), it.itemCount), store?.name.orEmpty(), it.total) },
            )
        }
        // Drafts only exist at stores with both modes, so the mode always reads "Eat-In".
        is PaymentTarget.Draft -> combine(orderRepository.orders, paidOrder) { orders, paid ->
            val eatIn = FulfillmentMode.IN_STORE.choiceLabel
            when {
                paid != null -> LoadState.Success(payableOf(paid, eatIn))
                orders is LoadState.Success -> LoadState.Success(payableDraft(orders.data)?.let { payableOf(it, eatIn) })
                else -> LoadState.Loading
            }
        }
    }

    val state: StateFlow<PaymentUiState> =
        combine(payable, paymentRepository.methods, selectedId, phase) { payable, methods, selectedId, phase ->
            val offered = offered(methods.dataOrNull())
            val item = payable.dataOrNull()
            PaymentUiState(
                isLoading = payable is LoadState.Loading || methods is LoadState.Loading,
                summary = item?.summary.orEmpty(),
                storeName = item?.storeName.orEmpty(),
                total = item?.total,
                methods = offered,
                selectedMethod = selected(offered, selectedId),
                phase = phase,
                isEatIn = target is PaymentTarget.Draft,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PaymentUiState(isEatIn = target is PaymentTarget.Draft))

    private val _events = Channel<PaymentEvent>(Channel.BUFFERED)
    val events: Flow<PaymentEvent> = _events.receiveAsFlow()

    /** Picks a method. Ignored while a charge or an approval is under way. Clears the last attempt's error. */
    fun selectMethod(methodId: String) {
        if (phase.value !in retryablePhases) return
        if (offered(paymentRepository.methods.value.dataOrNull()).none { it.id == methodId }) return
        selectedId.value = methodId
        phase.value = PaymentPhase.READY
    }

    /**
     * Pay. A card is charged now; the others open their mock approval screen
     * first. Taps after the first are ignored until the attempt ends.
     */
    fun pay() {
        if (phase.value !in retryablePhases) return
        val method = currentMethod() ?: return
        if (request() == null) return
        if (method.type.approval == PaymentApproval.NONE) charge(method) else phase.value = PaymentPhase.APPROVING
    }

    /** "Approve" on the mock PayPay or Rakuten Pay page, or "Pay" on the mock wallet sheet. */
    fun approve() {
        if (phase.value != PaymentPhase.APPROVING) return
        val method = currentMethod() ?: return
        charge(method)
    }

    /** "Cancel" on a mock approval screen. Nothing is charged and the cart or draft is kept. */
    fun cancelApproval() {
        if (phase.value == PaymentPhase.APPROVING) phase.value = PaymentPhase.CANCELLED
    }

    /** The sheet was closed: drops the last attempt's error. Never interrupts a charge or forgets a payment. */
    fun reset() {
        if (phase.value != PaymentPhase.PROCESSING && phase.value != PaymentPhase.PAID) phase.value = PaymentPhase.READY
    }

    /**
     * Saves the demo add-card form and selects the new card. Returns the problem, or null once saved. The sheet
     * turns Add card off while a charge or an approval is under way.
     * Demo only: the input is checked and dropped here, never stored or logged; only published test numbers
     * are accepted, and only their brand and last four digits are kept.
     */
    fun addTestCard(number: String, expiry: String, cvc: String): CardFormError? {
        if (phase.value !in retryablePhases) return null
        DemoCardForm.validate(number, expiry, cvc)?.let { return it }
        val method = paymentRepository.addTestCard(DemoCardForm.digits(number)) ?: return CardFormError.NOT_A_TEST_CARD
        selectedId.value = method.id
        phase.value = PaymentPhase.READY
        return null
    }

    private fun charge(method: PaymentMethod) {
        val request = request() ?: run {
            phase.value = PaymentPhase.UNAVAILABLE
            return
        }
        // Set before the charge starts, so a second tap in the same frame is already ignored.
        phase.value = PaymentPhase.PROCESSING
        viewModelScope.launch {
            when (val result = paymentService.pay(request, method.id)) {
                is PaymentResult.Paid -> {
                    paidOrder.value = result.order
                    if (target == PaymentTarget.Cart) cartRepository.clear()
                    phase.value = PaymentPhase.PAID
                    _events.trySend(PaymentEvent.Paid(result.order.id, result.order.number))
                }
                PaymentResult.Declined -> phase.value = PaymentPhase.DECLINED
                PaymentResult.OrderUnavailable -> phase.value = PaymentPhase.UNAVAILABLE
            }
        }
    }

    /** The charge for what is on the sheet right now, or null when there is nothing to pay. */
    private fun request(): PaymentRequest? = when (target) {
        PaymentTarget.Cart -> cartRepository.cart.value?.takeUnless { it.isEmpty }?.let { cart ->
            val store = storeRepository.stores.value.dataOrNull()?.firstOrNull { it.id == cart.storeId } ?: return null
            PaymentRequest.PayNow(checkoutId, cart, store.name)
        }
        is PaymentTarget.Draft ->
            payableDraft(orderRepository.orders.value.dataOrNull().orEmpty())?.let { PaymentRequest.PayDraft(checkoutId, it.id) }
    }

    /** The draft, once checked in: the geofence or a store or table QR has recorded arrival. */
    private fun payableDraft(orders: List<Order>): Order? {
        val orderId = (target as? PaymentTarget.Draft)?.orderId ?: return null
        return orders.firstOrNull { it.id == orderId && it.awaitsCheckIn && it.checkIn != null }
    }

    private fun currentMethod(): PaymentMethod? {
        val offered = offered(paymentRepository.methods.value.dataOrNull())
        return selected(offered, selectedId.value)
    }

    private fun offered(methods: List<PaymentMethod>?): List<PaymentMethod> =
        methods.orEmpty().filter { it.type.isOfferedOn(platform) }

    /** The picked method, else the saved default, else the first. */
    private fun selected(offered: List<PaymentMethod>, selectedId: String?): PaymentMethod? =
        offered.firstOrNull { it.id == selectedId } ?: offered.firstOrNull { it.isDefault } ?: offered.firstOrNull()

    private fun payableOf(order: Order, modeLabel: String): Payable =
        Payable(summaryOf(order.storeName, modeLabel, order.itemCount), order.storeName, order.total)

    private fun summaryOf(storeName: String, modeLabel: String, itemCount: Int): String =
        listOf(storeName, modeLabel, itemCountLabel(itemCount)).filter { it.isNotEmpty() }.joinToString(" · ")

    private companion object {
        val retryablePhases = setOf(PaymentPhase.READY, PaymentPhase.DECLINED, PaymentPhase.CANCELLED)
    }
}
