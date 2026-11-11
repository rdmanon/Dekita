package tech.vauldex.dekita.domain

import kotlin.time.Instant

data class InboxMessage(
    val id: String,
    val title: String,
    val body: String,
    val receivedAt: Instant,
    val isRead: Boolean,
    val orderId: String? = null,
)

data class UserProfile(val displayName: String, val email: String)
