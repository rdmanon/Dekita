package tech.vauldex.dekita

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform