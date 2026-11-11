package tech.vauldex.dekita.ui.debug

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.data.mock.SeedAuth
import tech.vauldex.dekita.di.AppContainer
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.getPlatform

/** Opens the debug panel. Null in release builds, so screens can hide their debug entry points. */
val LocalOpenDebugPanel = staticCompositionLocalOf<(() -> Unit)?> { null }

/** A titled block of controls in the debug panel. */
class DebugSection(val title: String, val content: @Composable (AppContainer) -> Unit)

/** The panel's sections, in order. Later tickets add their simulation controls to this list. */
internal val debugSections: List<DebugSection> = listOf(
    DebugSection("Build") { BuildInfo() },
    DebugSection("Mock data") { container -> MockDataControls(container) },
    DebugSection("Discover") { container -> DiscoverControls(container) },
    DebugSection("Auth") { container -> AuthControls(container) },
)

@Composable
internal fun DebugPanel(container: AppContainer, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        DebugPanelContent(container)
    }
}

/** The panel's contents without the sheet. */
@Composable
internal fun DebugPanelContent(container: AppContainer, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
    ) {
        Text("Debug panel", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Debug builds only. Release builds never show this panel.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        debugSections.forEach { section ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                    section.content(container)
                }
            }
        }
    }
}

@Composable
private fun BuildInfo() {
    val platform = remember { getPlatform().name }
    Text("Platform: $platform", style = MaterialTheme.typography.bodyMedium)
    Text("Build: debug", style = MaterialTheme.typography.bodyMedium)
    Text("Data: in-memory mock data, no network", style = MaterialTheme.typography.bodyMedium)
}

private val loadingDelayOptions = listOf(0.milliseconds, 800.milliseconds, 2.seconds)

@Composable
private fun MockDataControls(container: AppContainer) {
    val loadingDelay by container.mockConfig.loadingDelay.collectAsStateWithLifecycle()
    val liveQueue by container.mockConfig.liveQueue.collectAsStateWithLifecycle()
    Text("Loading delay", style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        loadingDelayOptions.forEach { option ->
            FilterChip(
                selected = loadingDelay == option,
                onClick = { container.mockConfig.loadingDelay.value = option },
                label = { Text(option.toString()) },
            )
        }
    }
    FilterChip(
        selected = liveQueue,
        onClick = { container.mockConfig.liveQueue.value = !liveQueue },
        label = { Text("Live queue updates") },
    )
    Button(onClick = { container.resetMockData() }) { Text("Reset mock data") }
}

/** Live store changes for DISC-01: pause a store, break the feed, or start as a new customer. */
@Composable
private fun DiscoverControls(container: AppContainer) {
    val stores by container.storeRepository.stores.collectAsStateWithLifecycle()
    val simulator = container.storeFeedSimulator

    Text(
        "Changes reach Discover at once, with no refresh.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val loaded = stores.dataOrNull()
    if (loaded == null) {
        Text("The store feed is not loaded.", style = MaterialTheme.typography.bodyMedium)
    }
    loaded.orEmpty().forEach { store ->
        val paused = store.availability == StoreAvailability.PAUSED
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                store.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    simulator.setAvailability(store.id, if (paused) StoreAvailability.OPEN else StoreAvailability.PAUSED)
                },
            ) { Text(if (paused) "Resume orders" else "Stop Accepting Orders") }
        }
    }

    OutlinedButton(
        onClick = {
            simulator.failNextLoad()
            container.storeRepository.refresh()
        },
    ) { Text("Simulate store feed error") }
    OutlinedButton(onClick = { container.simulateNewUser() }) { Text("Simulate new user (no favorites or history)") }
}

/** Sign-in simulation for AUTH-01. The values shown are the documented fakes from [SeedAuth]. */
@Composable
private fun AuthControls(container: AppContainer) {
    val session by container.authRepository.session.collectAsStateWithLifecycle()
    val forceSsoFailure by container.mockConfig.forceSsoFailure.collectAsStateWithLifecycle()

    Text(
        "Session: ${session?.method?.name?.lowercase()?.let { "signed in with $it" } ?: "guest"}",
        style = MaterialTheme.typography.bodyMedium,
    )
    FilterChip(
        selected = forceSsoFailure,
        onClick = { container.mockConfig.forceSsoFailure.value = !forceSsoFailure },
        label = { Text("Force SSO failure") },
    )
    Text("Test code: ${SeedAuth.TEST_OTP}", style = MaterialTheme.typography.bodyMedium)
    Text("Test account: ${SeedAuth.TEST_EMAIL} / ${SeedAuth.TEST_PASSWORD}", style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = { container.fillSampleCart() }) { Text("Fill sample cart") }
}

private const val HOLD_MILLIS = 1_000L

/**
 * Calls [onTrigger] when two fingers rest still for about a second, anywhere on screen.
 * It only watches touches and never consumes them, so the screen underneath behaves as usual.
 */
internal fun Modifier.twoFingerLongPress(onTrigger: () -> Unit): Modifier = pointerInput(onTrigger) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var pressed = currentEvent.changes.filter { it.pressed }
        while (pressed.size == 1) {
            pressed = awaitPointerEvent(PointerEventPass.Initial).changes.filter { it.pressed }
        }
        if (pressed.size < 2) return@awaitEachGesture

        val origins = pressed.associate { it.id to it.position }
        val interrupted = withTimeoutOrNull(HOLD_MILLIS) {
            while (true) {
                val now = awaitPointerEvent(PointerEventPass.Initial).changes.filter { it.pressed }
                val moved = now.any { change ->
                    val origin = origins[change.id]
                    origin == null || (change.position - origin).getDistance() > viewConfiguration.touchSlop
                }
                if (now.size < 2 || moved) break
            }
            true
        }
        if (interrupted == null) onTrigger()
    }
}
