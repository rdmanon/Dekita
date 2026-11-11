package tech.vauldex.dekita.data.mock

/**
 * Fixed sign-in values for the mockup. None of them is a secret: no SMS is ever sent, no account exists
 * outside this process, and example.com is reserved for documentation. Public so the debug panel can show them.
 */
object SeedAuth {
    /** The only code the fake accepts. Any other 6 digits fail as an invalid code. */
    const val TEST_OTP = "123456"

    /** The account that exists before anyone signs up. */
    const val TEST_EMAIL = "test.user@example.com"

    /** Obviously fake, and passes the 8-character, letter-and-number rule. */
    const val TEST_PASSWORD = "Test1234"
}

/** Mock accounts by lowercase email. Only the fake reads this; see [FakeAuthRepository]. */
internal val seedAccounts: Map<String, String> = mapOf(SeedAuth.TEST_EMAIL to SeedAuth.TEST_PASSWORD)
