package tech.vauldex.dekita.presentation.auth

import tech.vauldex.dekita.domain.PhoneNumber

/** Error text for the auth forms. Kept here so tests can check the exact wording QA scripts look for. */
object AuthCopy {
    const val INVALID_PHONE = "Enter a valid mobile number, like 090-1234-5678."
    const val INCOMPLETE_CODE = "Enter the 6-digit code."
    const val INVALID_CODE = "Invalid code. Please try again."
    const val INVALID_EMAIL = "Enter a valid email address, like name@example.com."
    const val WEAK_PASSWORD = "Use at least 8 characters with at least 1 number and 1 letter."
    const val MISSING_PASSWORD = "Enter your password."
    const val WRONG_CREDENTIALS = "Email or password is incorrect."
    const val EMAIL_TAKEN = "An account with this email already exists. Log in instead."
}

/** One-off results that move the user to another screen. */
sealed interface AuthEvent {
    data object SignedIn : AuthEvent

    /** [PhoneNumber.toString] hides the number, so printing this event can't leak it. */
    data class CodeSent(val phone: PhoneNumber) : AuthEvent
}
