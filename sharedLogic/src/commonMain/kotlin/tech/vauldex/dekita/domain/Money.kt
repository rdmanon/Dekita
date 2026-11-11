package tech.vauldex.dekita.domain

import kotlin.math.absoluteValue

/** An amount in the currency's smallest unit. JPY has no minor unit, so ¥1,550 is `Money(1550)`. */
data class Money(val amount: Long, val currency: String = JPY) {

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Currency mismatch: $currency and ${other.currency}" }
        return Money(amount + other.amount, currency)
    }

    operator fun times(quantity: Int): Money = Money(amount * quantity, currency)

    /** Display text such as "¥1,550". Currencies other than JPY show their code after the amount. */
    fun formatted(): String {
        val digits = amount.absoluteValue.toString()
        val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
        val sign = if (amount < 0) "-" else ""
        return if (currency == JPY) "$sign¥$grouped" else "$sign$grouped $currency"
    }

    companion object {
        const val JPY = "JPY"
        val ZERO = Money(0)

        fun yen(amount: Long) = Money(amount, JPY)
    }
}
