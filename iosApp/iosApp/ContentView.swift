import SwiftUI
import UIKit
import SharedUI

/// Hosts the shared Compose UI. Navigation, including the edge swipe-back gesture, is handled
/// inside Compose, so this view must not be wrapped in a NavigationStack.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        #if DEBUG
        let isDebug = true
        #else
        let isDebug = false
        #endif
        return MainViewControllerKt.MainViewController(isDebug: isDebug)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
