package tech.vauldex.dekita

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tech.vauldex.dekita.di.AppContainer
import tech.vauldex.dekita.ui.debug.DebugPanel
import tech.vauldex.dekita.ui.debug.LocalOpenDebugPanel
import tech.vauldex.dekita.ui.debug.twoFingerLongPress
import tech.vauldex.dekita.ui.navigation.AppShell
import tech.vauldex.dekita.ui.theme.DekitaTheme

/**
 * Root of the Android app. [isDebug] comes from BuildConfig.DEBUG; when it is false the debug panel
 * and its gesture are never composed. The iOS app has its own SwiftUI root.
 * [ticketRequest] holds an order id from a tapped notification until [onTicketRequestHandled].
 */
@Composable
fun App(
    isDebug: Boolean,
    container: AppContainer = AppContainer.instance,
    ticketRequest: StateFlow<String?> = MutableStateFlow(null),
    onTicketRequestHandled: () -> Unit = {},
) {
    DekitaTheme {
        var showDebugPanel by remember { mutableStateOf(false) }
        val openDebugPanel = remember(isDebug) { if (isDebug) fun() { showDebugPanel = true } else null }

        CompositionLocalProvider(LocalOpenDebugPanel provides openDebugPanel) {
            AppShell(
                container = container,
                ticketRequest = ticketRequest,
                onTicketRequestHandled = onTicketRequestHandled,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (openDebugPanel != null) Modifier.twoFingerLongPress(openDebugPanel) else Modifier),
            )
            if (openDebugPanel != null && showDebugPanel) {
                DebugPanel(container, onDismiss = { showDebugPanel = false })
            }
        }
    }
}

@Preview
@Composable
private fun AppPreview() {
    App(isDebug = true)
}
