package tech.vauldex.dekita.data.mock

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import tech.vauldex.dekita.domain.InboxMessage
import tech.vauldex.dekita.domain.PaymentMethod
import tech.vauldex.dekita.domain.PaymentType
import tech.vauldex.dekita.domain.UserProfile

internal object SeedPaymentIds {
    const val APPLE_PAY = "pm-apple-pay"
    const val GOOGLE_PAY = "pm-google-pay"
    const val TEST_CARD = "pm-test-card-4242"

    /** PAY-01: the saved card the mock payment service always declines. */
    const val DECLINE_CARD = "pm-test-card-0002"
    const val PAYPAY = "pm-paypay"
    const val RAKUTEN_PAY = "pm-rakuten-pay"
}

/** example.com is reserved for documentation, so this address can't reach anyone. */
internal val seedUser = UserProfile(displayName = "Test User", email = "test.user@example.com")

/**
 * Labels only. "•••• 4242" and "•••• 0002" end Stripe's published success and decline test cards;
 * no card data exists anywhere.
 */
internal val seedPaymentMethods: List<PaymentMethod> = listOf(
    PaymentMethod(SeedPaymentIds.APPLE_PAY, PaymentType.APPLE_PAY, "Apple Pay"),
    PaymentMethod(SeedPaymentIds.GOOGLE_PAY, PaymentType.GOOGLE_PAY, "Google Pay"),
    PaymentMethod(SeedPaymentIds.TEST_CARD, PaymentType.CARD, "Test Visa •••• 4242", isDefault = true),
    PaymentMethod(SeedPaymentIds.DECLINE_CARD, PaymentType.CARD, "Test Visa •••• 0002 (declines)"),
    PaymentMethod(SeedPaymentIds.PAYPAY, PaymentType.PAYPAY, "PayPay (test account)"),
    PaymentMethod(SeedPaymentIds.RAKUTEN_PAY, PaymentType.RAKUTEN_PAY, "Rakuten Pay (test account)"),
)

/** A payment provider's published test card, which the demo add-card form accepts. */
internal class PublishedTestCard(val number: String, val brand: String, val declines: Boolean) {
    val last4: String get() = number.takeLast(4)
}

/**
 * The only numbers the demo add-card form takes: published test numbers from Stripe's testing docs, since the
 * feature list names Stripe or an equivalent. Check them against the chosen processor's docs before relying on them.
 */
internal val publishedTestCards: List<PublishedTestCard> = listOf(
    PublishedTestCard("4242424242424242", "Visa", declines = false),
    PublishedTestCard("5555555555554444", "Mastercard", declines = false),
    PublishedTestCard("4000000000000002", "Visa", declines = true),
)

internal fun seedInbox(now: Instant): List<InboxMessage> = listOf(
    InboxMessage(
        id = "msg-order-43-ready",
        title = "Order #43 is ready for pickup",
        body = "Pick it up at the Test Mart counter.",
        receivedAt = now - 5.minutes,
        isRead = false,
        orderId = "order-43",
    ),
    InboxMessage(
        id = "msg-order-42-preparing",
        title = "Order #42 is being prepared",
        body = "Test Ramen Bar has started cooking your order.",
        receivedAt = now - 6.minutes,
        isRead = false,
        orderId = "order-42",
    ),
    InboxMessage(
        id = "msg-welcome",
        title = "Welcome to the Passnize mockup",
        body = "Every store, order and payment in this app is sample data.",
        receivedAt = now - 7.days,
        isRead = true,
    ),
)
