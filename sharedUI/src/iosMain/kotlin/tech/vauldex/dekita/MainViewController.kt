package tech.vauldex.dekita

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** iOS entry point, called from Swift as `MainViewControllerKt.MainViewController(isDebug:)`. */
fun MainViewController(isDebug: Boolean): UIViewController = ComposeUIViewController { App(isDebug = isDebug) }
