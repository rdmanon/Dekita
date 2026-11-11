package tech.vauldex.dekita

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * The whole app as one Compose screen, with the Android-style shell. The iOS app no longer uses it:
 * it has a SwiftUI shell (iosApp/ContentView.swift) that embeds single screens from ios/ComposeScreens.kt.
 * Kept for comparing the two shells; Swift calls it as `MainViewControllerKt.MainViewController(isDebug:)`.
 */
fun MainViewController(isDebug: Boolean): UIViewController = ComposeUIViewController { App(isDebug = isDebug) }
