import SwiftUI
import UIKit

/// Hosts one shared Compose screen inside SwiftUI. The controller is made once, when the view first appears.
struct ComposeController: UIViewControllerRepresentable {
    let make: () -> UIViewController

    func makeUIViewController(context: Context) -> UIViewController {
        make()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
