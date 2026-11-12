package tech.vauldex.dekita.data.mock

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import tech.vauldex.dekita.data.LoadState

/** Settings for the fake data layer. The debug panel changes these at runtime. */
class MockConfig(loadingDelay: Duration = DEFAULT_LOADING_DELAY) {
    val loadingDelay = MutableStateFlow(loadingDelay)

    /** When false, the mock queue counts stop changing, so QA can check exact values. */
    val liveQueue = MutableStateFlow(true)

    /**
     * When false, orders paid in this session stop moving through their statuses and stop auto-completing,
     * so QA can check one status. The ticket's debug buttons still move them.
     */
    val liveOrders = MutableStateFlow(true)

    /** When true, "Continue with Google" and "Continue with Apple" fail after the loading delay. */
    val forceSsoFailure = MutableStateFlow(false)

    companion object {
        val DEFAULT_LOADING_DELAY = 800.milliseconds
        val QUEUE_TICK_INTERVAL = 5.seconds

        /** How long a live order stays in each status before the mock kitchen moves it on. */
        val ORDER_STEP_INTERVAL = 15.seconds

        /**
         * How long a Ready or Served order waits for [Confirm] before it completes on its own.
         * A short mock value: the real time is an open question for the PO.
         */
        val AUTO_COMPLETE_AFTER = 60.seconds
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
