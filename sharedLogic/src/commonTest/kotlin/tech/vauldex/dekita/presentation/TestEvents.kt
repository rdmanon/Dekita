package tech.vauldex.dekita.presentation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope

/** Collects a ViewModel's one-off events for the rest of the test. Call runCurrent() before asserting. */
internal fun <T> TestScope.collectEvents(events: Flow<T>): List<T> {
    val received = mutableListOf<T>()
    backgroundScope.launch { events.collect { received += it } }
    return received
}
