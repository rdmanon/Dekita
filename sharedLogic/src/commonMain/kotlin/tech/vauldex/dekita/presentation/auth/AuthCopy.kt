package tech.vauldex.dekita.presentation.auth

import tech.vauldex.dekita.domain.AuthContact

/** Error and hint text for the auth forms. Kept here so tests can check the exact wording QA scripts look for. */
object AuthCopy {
    const val INCOMPLETE_CODE = "Enter the 6-digit code."
    const val INVALID_CODE = "Invalid code. Please try again."

    // The line under the "Phone number or email" field. The invalid one doesn't say which part is wrong.
    const val HINT_IDLE = "We'll send a 6-digit code to sign you in. New here? This also creates your account."
    const val HINT_EMAIL = "We'll email a 6-digit code to this address."
    const val HINT_PHONE = "We'll text a 6-digit code to this number."
    const val HINT_INVALID = "Enter a valid phone number or email address."
}

/** One-off results that move the user to another screen. */
sealed interface AuthEvent {
    data object SignedIn : AuthEvent

    /** [AuthContact.toString] hides the address or number, so printing this event can't leak it. */
    data class CodeSent(val contact: AuthContact) : AuthEvent
}
