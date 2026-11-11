package tech.vauldex.dekita.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.viewmodel.compose.viewModel
import platform.UIKit.UIViewController
import tech.vauldex.dekita.di.AppContainer
import tech.vauldex.dekita.presentation.account.AccountViewModel
import tech.vauldex.dekita.presentation.orders.OrdersViewModel
import tech.vauldex.dekita.presentation.store.StoreDetailViewModel
import tech.vauldex.dekita.ui.account.AccountScreen
import tech.vauldex.dekita.ui.debug.DebugPanel
import tech.vauldex.dekita.ui.debug.DebugPanelContent
import tech.vauldex.dekita.ui.debug.LocalOpenDebugPanel
import tech.vauldex.dekita.ui.debug.twoFingerLongPress
import tech.vauldex.dekita.ui.orders.OrdersScreen
import tech.vauldex.dekita.ui.store.StoreDetailScreen
import tech.vauldex.dekita.ui.theme.DekitaTheme

// Compose screens the SwiftUI app embeds until they get their own iOS design (later tickets).
// Swift calls these as `ComposeScreensKt.OrdersViewController(isDebug:)` and so on.

fun OrdersViewController(isDebug: Boolean): UIViewController = ComposeUIViewController {
    HostedScreen(isDebug) {
        OrdersScreen(viewModel = viewModel { OrdersViewModel(AppContainer.instance.orderRepository) })
    }
}

fun AccountViewController(isDebug: Boolean): UIViewController = ComposeUIViewController {
    HostedScreen(isDebug) {
        val container = AppContainer.instance
        AccountScreen(
            viewModel = viewModel {
                AccountViewModel(container.userProfile, container.paymentRepository, container.inboxRepository)
            },
        )
    }
}

/** The store screen's content. SwiftUI draws the navigation bar, so the Compose top bar is off. */
fun StoreDetailViewController(storeId: String, isDebug: Boolean): UIViewController = ComposeUIViewController {
    HostedScreen(isDebug) {
        val container = AppContainer.instance
        StoreDetailScreen(
            viewModel = viewModel { StoreDetailViewModel(storeId, container.storeRepository, container.menuRepository) },
            onBack = {},
            showTopBar = false,
        )
    }
}

/** The debug panel's contents, for a native SwiftUI sheet. Swift only presents this in debug builds. */
fun DebugPanelViewController(): UIViewController = ComposeUIViewController {
    DekitaTheme {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            DebugPanelContent(AppContainer.instance, Modifier.padding(top = 24.dp))
        }
    }
}

/** Theme plus the debug panel and its two-finger gesture, as [tech.vauldex.dekita.App] gives the Android shell. */
@Composable
private fun HostedScreen(isDebug: Boolean, content: @Composable () -> Unit) {
    DekitaTheme {
        var showDebugPanel by remember { mutableStateOf(false) }
        val openDebugPanel = remember(isDebug) { if (isDebug) fun() { showDebugPanel = true } else null }

        CompositionLocalProvider(LocalOpenDebugPanel provides openDebugPanel) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (openDebugPanel != null) Modifier.twoFingerLongPress(openDebugPanel) else Modifier),
            ) {
                content()
            }
            if (openDebugPanel != null && showDebugPanel) {
                DebugPanel(AppContainer.instance, onDismiss = { showDebugPanel = false })
            }
        }
    }
}
