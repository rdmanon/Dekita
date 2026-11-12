package tech.vauldex.dekita.domain

import kotlin.time.Instant

/** The quick tags a customer can add to a rating. Placeholders until the PO picks the final set. */
val RATING_TAGS: List<String> = listOf("Tasty", "Quick", "Friendly staff", "Good value")

/** A customer's rating of a completed order: 1 to 5 stars and any of [RATING_TAGS]. */
data class OrderRating(val stars: Int, val tags: Set<String>, val at: Instant) {
    init {
        require(stars in 1..5) { "A rating is 1 to 5 stars" }
        require(tags.all { it in RATING_TAGS }) { "Unknown rating tag" }
    }
}
