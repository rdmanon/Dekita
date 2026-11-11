import SwiftUI
import SharedUI

/// The app's tabs. On iOS 26 the system draws the tab bar in Liquid Glass and floats the Search tab
/// as its own round button beside it, as in the design; on iOS 18–25 the same code gives the standard tab bar.
/// Discover, Search, Favorites, History and the store screen are SwiftUI. Orders and Account are still
/// the shared Compose screens until their iOS designs are built.
struct ContentView: View {
    @State private var selection: AppTab = .discover

    var body: some View {
        TabView(selection: $selection) {
            Tab("Discover", systemImage: "safari", value: AppTab.discover) {
                NavigationStack { DiscoverView() }
            }
            Tab("Orders", systemImage: "receipt", value: AppTab.orders) {
                ComposeController { ComposeScreensKt.OrdersViewController(isDebug: AppBuild.isDebug) }
                    .ignoresSafeArea()
            }
            Tab("Account", systemImage: "person.crop.circle", value: AppTab.account) {
                ComposeController { ComposeScreensKt.AccountViewController(isDebug: AppBuild.isDebug) }
                    .ignoresSafeArea()
            }
            Tab(value: AppTab.search, role: .search) {
                NavigationStack { SearchView() }
            }
        }
        .modifier(MinimizeTabBarOnScroll())
    }
}

enum AppTab: Hashable {
    case discover, orders, account, search
}

/// iOS 26 shrinks the glass tab bar while the user scrolls down. Earlier versions keep it as is.
private struct MinimizeTabBarOnScroll: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.tabBarMinimizeBehavior(.onScrollDown)
        } else {
            content
        }
    }
}

enum AppBuild {
    /// Debug builds show the debug panel. Release builds never do.
    static let isDebug: Bool = {
        #if DEBUG
        return true
        #else
        return false
        #endif
    }()
}
