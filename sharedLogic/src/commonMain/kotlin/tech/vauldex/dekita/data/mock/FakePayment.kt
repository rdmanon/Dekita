package tech.vauldex.dekita.data.mock

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tech.vauldex.dekita.data.PaymentRequest
import tech.vauldex.dekita.data.PaymentResult
import tech.vauldex.dekita.data.PaymentService
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.domain.Order

/**
 * Simulated charges (PAY-01). No payment SDK or network: every charge waits the loading delay in the caller's
 * coroutine, so a test dispatcher runs it on virtual time. Decline cards, and every method while
 * [MockConfig.forcePaymentDecline] is on, are declined; everything else is paid.
 */
class FakePaymentService internal constructor(
    private val orders: FakeOrderRepository,
    private val payments: FakePaymentRepository,
    private val config: MockConfig,
) : PaymentService, Resettable {

    // Checkouts are paid one at a time, so two calls with one checkout id can't both create an order.
    private val lock = Mutex()

    /** Paid checkouts by id, so a repeated call returns the first call's order. */
    private val paid = mutableMapOf<String, Order>()

    override suspend fun pay(request: PaymentRequest, methodId: String): PaymentResult {
        delay(config.loadingDelay.value)
        return lock.withLock {
            paid[request.checkoutId]?.let { return@withLock PaymentResult.Paid(it) }
            val known = payments.methods.value.dataOrNull().orEmpty().any { it.id == methodId }
            if (!known || payments.declines(methodId) || config.forcePaymentDecline.value) {
                return@withLock PaymentResult.Declined
            }
            val order = when (request) {
                is PaymentRequest.PayNow -> orders.placeOrder(request.cart, request.storeName, methodId)
                is PaymentRequest.PayDraft -> orders.payAtCheckIn(request.orderId, methodId)
            } ?: return@withLock PaymentResult.OrderUnavailable
            paid[request.checkoutId] = order
            PaymentResult.Paid(order)
        }
    }

    override fun reset() {
        paid.clear()
    }
}
