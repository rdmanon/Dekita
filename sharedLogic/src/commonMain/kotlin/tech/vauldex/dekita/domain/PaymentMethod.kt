package tech.vauldex.dekita.domain

enum class PaymentType { APPLE_PAY, GOOGLE_PAY, CARD, PAYPAY, RAKUTEN_PAY }

/** A saved payment method as shown in the list. Holds a display label only, never card details. */
data class PaymentMethod(
    val id: String,
    val type: PaymentType,
    val label: String,
    val isDefault: Boolean = false,
)

/** The phone the app runs on. Decides which wallet the payment sheet offers. */
enum class ClientPlatform { ANDROID, IOS }

/** Apple Pay is offered on iOS only and Google Pay on Android only; the rest on both. */
fun PaymentType.isOfferedOn(platform: ClientPlatform): Boolean = when (this) {
    PaymentType.APPLE_PAY -> platform == ClientPlatform.IOS
    PaymentType.GOOGLE_PAY -> platform == ClientPlatform.ANDROID
    PaymentType.CARD, PaymentType.PAYPAY, PaymentType.RAKUTEN_PAY -> true
}

/** What the customer confirms with before the charge, outside the payment sheet. */
enum class PaymentApproval {
    /** A saved card is charged as soon as Pay is tapped. */
    NONE,

    /** PayPay and Rakuten Pay open their own app or page, where the customer approves or cancels. */
    REDIRECT,

    /** Apple Pay and Google Pay show the system wallet sheet. */
    WALLET_SHEET,
}

val PaymentType.approval: PaymentApproval
    get() = when (this) {
        PaymentType.CARD -> PaymentApproval.NONE
        PaymentType.PAYPAY, PaymentType.RAKUTEN_PAY -> PaymentApproval.REDIRECT
        PaymentType.APPLE_PAY, PaymentType.GOOGLE_PAY -> PaymentApproval.WALLET_SHEET
    }

/** Why the demo add-card form can't be saved. */
enum class CardFormError { NUMBER, EXPIRY, CVC, NOT_A_TEST_CARD }

/**
 * Checks the demo add-card form (PAY-01). Demo only: the mockup never stores, sends or logs what is typed,
 * and this must not become the production card form, which uses the payment provider's SDK or hosted fields
 * so card data never reaches the app. Only the shape is checked here; the fake then accepts published
 * test numbers only (NOT_A_TEST_CARD otherwise).
 */
object DemoCardForm {
    private val numberPattern = Regex("[0-9]{13,19}")
    private val expiryPattern = Regex("(0[1-9]|1[0-2])/[0-9]{2}")
    private val cvcPattern = Regex("[0-9]{3,4}")

    /** The card number without the spaces people type between groups. */
    fun digits(number: String): String = number.filterNot { it == ' ' }

    /** The first problem with the form, or null when its shape is fine. */
    fun validate(number: String, expiry: String, cvc: String): CardFormError? = when {
        !numberPattern.matches(digits(number)) -> CardFormError.NUMBER
        !expiryPattern.matches(expiry) -> CardFormError.EXPIRY
        !cvcPattern.matches(cvc) -> CardFormError.CVC
        else -> null
    }
}
