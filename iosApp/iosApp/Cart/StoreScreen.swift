import SwiftUI
import SharedUI

/// The store screen. SwiftUI draws the navigation bar (back and a Liquid Glass favorite button on iOS 26)
/// and the View cart button; the menu itself is the shared Compose screen until its iOS design is built.
/// Tapping an item opens the native item sheet.
struct StoreScreen: View {
    let storeId: String
    let name: String
    @StateObject private var favorite: FavoriteStoreModel
    @StateObject private var cartSummary: CartSummaryModel
    @State private var sheet: ItemSheetTarget?

    init(storeId: String, name: String) {
        self.storeId = storeId
        self.name = name
        _favorite = StateObject(wrappedValue: FavoriteStoreModel(storeId: storeId))
        _cartSummary = StateObject(wrappedValue: CartSummaryModel(storeId: storeId))
    }

    var body: some View {
        ComposeController {
            ComposeScreensKt.StoreDetailViewController(storeId: storeId, isDebug: AppBuild.isDebug) { itemId in
                sheet = ItemSheetTarget(storeId: storeId, itemId: itemId, editingLineId: nil)
            }
        }
        .ignoresSafeArea(edges: .bottom)
        .navigationTitle(name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { favorite.toggle() } label: {
                    Image(systemName: favorite.isFavorite ? "heart.fill" : "heart")
                }
                .accessibilityLabel(favorite.isFavorite ? "Remove from favorites" : "Add to favorites")
            }
        }
        .safeAreaInset(edge: .bottom) {
            let summary = cartSummary.summary
            if summary.itemCount > 0 {
                NavigationLink(value: DiscoverRoute.cart) {
                    Label("View cart (\(summary.itemCount)) · \(summary.total)", systemImage: "bag")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 44)
                }
                .modifier(ProminentGlassButton())
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
            }
        }
        .sheet(item: $sheet) { target in ItemSheetView(target: target) }
    }
}

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
