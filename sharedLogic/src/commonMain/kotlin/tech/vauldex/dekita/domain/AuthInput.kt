package tech.vauldex.dekita.domain

/**
 * Normalizes and checks what people type into the sign-in forms. A Japanese IME often types full-width
 * characters ("０９０", "＠"), so every check runs on the half-width form. Passwords are never rewritten.
 */
object AuthInput {

    const val OTP_LENGTH = 6
    private const val MAX_EMAIL_LENGTH = 254

    // Deliberately loose: one @, no spaces, a dot in the domain. The real check is the confirmation email.
    private val emailPattern = Regex("""^[^\s@]+@[^\s@]+\.[^\s@.]{2,}$""")

    /** Maps full-width ASCII to half-width, plus the ideographic space and the dashes an IME types for "-". */
    fun toHalfWidth(text: String): String = buildString(text.length) {
        for (char in text) {
            append(
                when (char) {
                    in '！'..'～' -> char - 0xFEE0
                    '　' -> ' '
                    'ー', '−', '‐' -> '-'
                    else -> char
                },
            )
        }
    }

    /** Half-width digits only, at most [OTP_LENGTH] of them. Anything else is dropped. */
    fun normalizeOtp(raw: String): String = toHalfWidth(raw).filter { it in '0'..'9' }.take(OTP_LENGTH)

    fun isValidOtp(code: String): Boolean = code.length == OTP_LENGTH && code.all { it in '0'..'9' }

    fun normalizeEmail(raw: String): String = toHalfWidth(raw).trim()

    fun isValidEmail(email: String): Boolean = email.length <= MAX_EMAIL_LENGTH && emailPattern.matches(email)

    /**
     * Reads what was typed into the one "Phone number or email" field. An "@" makes it an email. Anything else
     * is tried as a mobile number for each [DialCode] in order, so a number without a country code is a Japan
     * number and the Philippines needs "+63". A national number can fit both countries (for example 901 234 5678),
     * and Japan wins. Returns null when it is neither a valid email nor a valid mobile number.
     */
    fun parseContact(raw: String): AuthContact? {
        val text = toHalfWidth(raw).trim()
        if (text.isEmpty()) return null
        if ('@' in text) {
            val email = normalizeEmail(text)
            return if (isValidEmail(email)) AuthContact.Email(email) else null
        }
        return DialCode.entries.firstNotNullOfOrNull { parseMobile(it, text) }?.let(AuthContact::Phone)
    }

    /**
     * Reads a mobile number typed for [dialCode]. Accepts full-width digits, spaces, hyphens and brackets,
     * a trunk 0 ("090…") and the international form ("+8190…"). Returns null when it isn't a mobile number.
     */
    fun parseMobile(dialCode: DialCode, raw: String): PhoneNumber? {
        val compact = toHalfWidth(raw).filterNot { it in " -().\t" }
        val digits = when {
            compact.startsWith(dialCode.prefix) -> compact.removePrefix(dialCode.prefix)
            compact.startsWith("+") -> return null
            else -> compact
        }
        if (digits.any { it !in '0'..'9' }) return null
        val national = if (digits.length == DialCode.NATIONAL_LENGTH + 1 && digits.startsWith("0")) digits.drop(1) else digits
        return if (dialCode.isMobile(national)) PhoneNumber(dialCode, national) else null
    }

    /** The reverse of [PhoneNumber.e164]. Returns null for a number no [DialCode] accepts. */
    fun parseE164(e164: String): PhoneNumber? =
        DialCode.entries.firstOrNull { e164.startsWith(it.prefix) }?.let { parseMobile(it, e164) }
}

/** Where a sign-in code goes: an email address or a mobile number. */
sealed interface AuthContact {
    /** What the customer sees: the address, or the number as "+81 90-1234-5678". */
    val display: String

    /**
     * A string that [AuthInput.parseContact] reads back into the same contact: the address, or the E.164 number.
     * Screens pass it between steps. Treat it as personal data: don't log it.
     */
    val key: String

    data class Email(val address: String) : AuthContact {
        override val display: String get() = address
        override val key: String get() = address

        // Keeps the address out of anything that prints this object, such as a crash report.
        override fun toString(): String = "Email(••••)"
    }

    data class Phone(val number: PhoneNumber) : AuthContact {
        override val display: String get() = number.formatted()
        override val key: String get() = number.e164

        override fun toString(): String = "Phone(••••)"
    }
}

/** Calling codes the mockup offers. Both countries use 10-digit mobile numbers after the trunk 0. */
enum class DialCode(
    val prefix: String,
    val country: String,
    private val mobilePrefixes: List<String>,
    private val groups: List<Int>,
    private val separator: String,
) {
    JP("+81", "Japan", listOf("70", "80", "90"), listOf(2, 4, 4), "-"),
    PH("+63", "Philippines", listOf("9"), listOf(3, 3, 4), " "),
    ;

    internal fun isMobile(national: String): Boolean =
        national.length == NATIONAL_LENGTH && national.all { it in '0'..'9' } && mobilePrefixes.any(national::startsWith)

    internal fun group(national: String): String {
        var start = 0
        return groups.joinToString(separator) { size -> national.substring(start, start + size).also { start += size } }
    }

    companion object {
        val DEFAULT = JP
        internal const val NATIONAL_LENGTH = 10
    }
}

/** A mobile number without its trunk 0, such as JP and "9012345678". */
data class PhoneNumber(val dialCode: DialCode, val national: String) {
    init {
        require(dialCode.isMobile(national)) { "Not a ${dialCode.country} mobile number" }
    }

    val e164: String get() = dialCode.prefix + national

    /** "+81 90-1234-5678" for Japan, "+63 917 123 4567" for the Philippines. */
    fun formatted(): String = "${dialCode.prefix} ${dialCode.group(national)}"

    // Keeps the number out of anything that prints this object, such as a crash report.
    override fun toString(): String = "PhoneNumber(${dialCode.name}, ••••)"
}
