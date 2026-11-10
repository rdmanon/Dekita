package tech.vauldex.dekita.presentation

/** Keeps upstream flows alive briefly after the last subscriber, so a config change doesn't restart them. */
internal const val STOP_TIMEOUT_MILLIS = 5_000L
