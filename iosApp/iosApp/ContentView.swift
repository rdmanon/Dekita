import SwiftUI
import SharedUI

/// The app's tabs. On iOS 26 the system draws the tab bar in Liquid Glass and floats the Search tab
/// as its own round button beside it, as in the design; on iOS 18–25 the same code gives the standard tab bar.
/// Discover, Search, Favorites and History are SwiftUI. Orders, Account and the store screen are still
/// the shared Compose screens until their iOS designs are built.
struct ContentView: View {
    @State private var selection: AppTab = .discover
    @StateObject private var discoverNavigation = NavigationModel()
    @StateObject private var searchNavigation = NavigationModel()

    var body: some View {
        TabView(selection: $selection) {
            Tab("Discover", systemImage: "safari", value: AppTab.discover) {
                NavigationStack(path: $discoverNavigation.path) { DiscoverView() }
                    .environmentObject(discoverNavigation)
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
                NavigationStack(path: $searchNavigation.path) { SearchView() }
                    .environmentObject(searchNavigation)
            }
        }
        .modifier(MinimizeTabBarOnScroll())
    }
}

/// One tab's navigation stack, so a screen can replace itself (Cart → Check-in) or return to the tab's root.
final class NavigationModel: ObservableObject {
    @Published var path = NavigationPath()

    /// Replaces the top screen with [route], so Back skips the screen that was replaced.
    func replaceTop(with route: DiscoverRoute) {
        if !path.isEmpty { path.removeLast() }
        path.append(route)
    }

    func popToRoot() {
        path = NavigationPath()
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
