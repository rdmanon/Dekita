package tech.vauldex.dekita.data.mock

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import tech.vauldex.dekita.data.LoadState

/** Settings for the fake data layer. The debug panel changes these at runtime. */
class MockConfig(loadingDelay: Duration = DEFAULT_LOADING_DELAY) {
    val loadingDelay = MutableStateFlow(loadingDelay)

    companion object {
        val DEFAULT_LOADING_DELAY = 800.milliseconds
    }
}

/** A fake repository whose data can be put back to the seed. */
interface Resettable {
    fun reset()
}

/** Emits Loading, waits the configured delay, then emits the produced data. */
internal fun <T> CoroutineScope.simulateLoad(
    target: MutableStateFlow<LoadState<T>>,
    config: MockConfig,
    produce: () -> T,
): Job = launch {
    target.value = LoadState.Loading
    delay(config.loadingDelay.value)
    target.value = LoadState.Success(produce())
}

/** Emits Loading, waits the configured delay, then emits an error, as a failed request would. */
internal fun <T> CoroutineScope.simulateFailure(
    target: MutableStateFlow<LoadState<T>>,
    config: MockConfig,
    message: String,
): Job = launch {
    target.value = LoadState.Loading
    delay(config.loadingDelay.value)
    target.value = LoadState.Error(message)
}
