package tech.vauldex.dekita.data.mock

/**
 * Fixed sign-in values for the mockup. None of them is a secret: no SMS or email is ever sent, no account exists
 * outside this process, and example.com is reserved for documentation. Public so the debug panel can show them.
 */
object SeedAuth {
    /** The only code the fake accepts. Any other 6 digits fail as an invalid code. */
    const val TEST_OTP = "123456"

    /** An address to try the email path with. Any valid address works; the debug panel shows this one. */
    const val TEST_EMAIL = "test.user@example.com"
}
