import SwiftUI
import SharedUI

/// Which item the sheet shows, and the cart line it edits, if any. A new id per opening, so the sheet starts fresh.
struct ItemSheetTarget: Identifiable {
    let id = UUID()
    let storeId: String
    let itemId: String
    let editingLineId: String?
}

/// The main action's style: Liquid Glass tinted with the accent on iOS 26, a filled button before.
struct ProminentGlassButton: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.buttonStyle(.glassProminent).controlSize(.large)
        } else {
            content.buttonStyle(.borderedProminent).controlSize(.large).buttonBorderShape(.capsule)
        }
    }
}
