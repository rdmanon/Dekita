package tech.vauldex.dekita.domain

enum class SsoProvider(val label: String) {
    GOOGLE("Google"),
    APPLE("Apple"),
}

enum class SignInMethod { PHONE, EMAIL, GOOGLE, APPLE }

/** Who is signed in and how. The mockup keeps it in memory only: there is no token and nothing on disk. */
data class Session(val profile: UserProfile, val method: SignInMethod)
