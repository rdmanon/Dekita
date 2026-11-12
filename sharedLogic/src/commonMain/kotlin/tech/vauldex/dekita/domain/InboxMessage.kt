package tech.vauldex.dekita.domain

import kotlin.time.Instant

enum class InboxKind {
    INFO,

    /** Asks the customer to rate an order and save the items they liked (RCPT-01). Opens Rate. */
    RATE_ORDER,
}

data class InboxMessage(
    val id: String,
    val title: String,
    val body: String,
    val receivedAt: Instant,
    val isRead: Boolean,
    val orderId: String? = null,
    val kind: InboxKind = InboxKind.INFO,
)

/** A phone sign-in has no email and an email sign-in has no phone, so both are optional. */
data class UserProfile(val displayName: String, val email: String? = null, val phone: String? = null)
