package tech.vauldex.dekita.presentation.orders

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import tech.vauldex.dekita.data.AuthRepository
import tech.vauldex.dekita.data.OrderRepository
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.presentation.ORDER_PLACED_MESSAGE
import tech.vauldex.dekita.presentation.orderReadyMessage

/** One system notification about an order. Holds the order number and store name only, no personal data. */
data class OrderNotice(val orderId: String, val title: String, val body: String)

/**
 * The notification for this status, or null when there is none:
 * - Paid before going to the store (pickup, takeout, in-store-only): "Your order will be ready shortly…".
 * - Ready for pickup: "Your order #42 is ready for pickup".
 *
 * An Eat-In order paid at check-in gets no "come to the store" notice, because the customer is already there.
 */
fun Order.notice(): OrderNotice? {
    val body = when {
        status == OrderStatus.PAID || (status == OrderStatus.QUEUED && checkIn == null) -> ORDER_PLACED_MESSAGE
        status == OrderStatus.READY_FOR_PICKUP -> orderReadyMessage(number)
        else -> return null
    }
    return OrderNotice(orderId = id, title = "Order #$number · $storeName", body = body)
}

/**
 * What the platforms turn into system notifications. Lives as long as the app process, not a screen,
 * so notices still arrive while the app is in the background.
 */
class OrderNotifications(private val orderRepository: OrderRepository, private val authRepository: AuthRepository) {

    /** Notices from now on. None while signed out, so a signed-out phone never shows someone's order. */
    val notices: Flow<OrderNotice> = orderRepository.statusChanges
        .filter { authRepository.session.value != null }
        .mapNotNull { it.notice() }

    /** Emits each time the customer signs out, so the platforms can clear notifications already shown. */
    val signedOut: Flow<Unit> = flow {
        var wasSignedIn = false
        authRepository.session.collect { session ->
            val isSignedIn = session != null
            if (wasSignedIn && !isSignedIn) emit(Unit)
            wasSignedIn = isSignedIn
        }
    }

    /**
     * True when [orderId] is a paid order and someone is signed in. A notification tap carries an id from
     * outside the app's own screens, so the platforms open a ticket only when this holds.
     */
    fun canOpenTicket(orderId: String): Boolean {
        if (authRepository.session.value == null) return false
        return orderRepository.orders.value.dataOrNull()?.any { it.id == orderId && it.status != OrderStatus.DRAFT } == true
    }
}
