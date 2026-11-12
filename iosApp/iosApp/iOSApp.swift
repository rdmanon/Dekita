import SwiftUI

@main
struct iOSApp: App {
    init() {
        // Before the first scene, so a notification tap that launches the app reaches the delegate.
        OrderNotifier.shared.start()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}