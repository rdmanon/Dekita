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
import tech.vauldex.dekita.di.AppContainer
import tech.vauldex.dekita.ui.debug.DebugPanel
import tech.vauldex.dekita.ui.debug.LocalOpenDebugPanel
import tech.vauldex.dekita.ui.debug.twoFingerLongPress
import tech.vauldex.dekita.ui.navigation.AppShell
import tech.vauldex.dekita.ui.theme.DekitaTheme

/**
 * Root of the mockup on both platforms. [isDebug] comes from the app build (BuildConfig.DEBUG on
 * Android, `#if DEBUG` on iOS); when it is false the debug panel and its gesture are never composed.
 */
@Composable
fun App(isDebug: Boolean, container: AppContainer = AppContainer.instance) {
    DekitaTheme {
        var showDebugPanel by remember { mutableStateOf(false) }
        val openDebugPanel = remember(isDebug) { if (isDebug) fun() { showDebugPanel = true } else null }

        CompositionLocalProvider(LocalOpenDebugPanel provides openDebugPanel) {
            AppShell(
                container = container,
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
