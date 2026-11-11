package tech.vauldex.dekita.domain

enum class PaymentType { APPLE_PAY, GOOGLE_PAY, CARD, PAYPAY, RAKUTEN_PAY }

/** A saved payment method as shown in the list. Holds a display label only, never card details. */
data class PaymentMethod(
    val id: String,
    val type: PaymentType,
    val label: String,
    val isDefault: Boolean = false,
)
