import SwiftUI
import SharedLogic

/// The app's tabs. On iOS 26 the system draws the tab bar in Liquid Glass and floats the Search tab
/// as its own round button beside it, as in the design; on iOS 18–25 the same code gives the standard tab bar.
/// Every screen is SwiftUI; the shared Kotlin code supplies view models and data only.
/// Sign-in opens as a sheet over the current screen, or full screen after sign-out.
/// Check-in opens full screen over every tab.
struct ContentView: View {
    @StateObject private var flow = AuthFlow()
    @State private var checkIn: CheckInTarget?

    var body: some View {
        TabView(selection: $flow.selection) {
            Tab("Discover", systemImage: "safari", value: AppTab.discover) {
                NavigationStack(path: $flow.discoverPath) { DiscoverView() }
            }
            Tab("Orders", systemImage: "receipt", value: AppTab.orders) {
                NavigationStack { OrdersView() }
            }
            Tab("Account", systemImage: "person.crop.circle", value: AppTab.account) {
                NavigationStack { AccountView() }
            }
            Tab(value: AppTab.search, role: .search) {
                NavigationStack(path: $flow.searchPath) { SearchView() }
            }
        }
        .modifier(MinimizeTabBarOnScroll())
        .sheet(item: $flow.signInSheet) { _ in
            SignInFlowView(isRoot: false).environmentObject(flow)
        }
        .fullScreenCover(isPresented: $flow.signInAfterSignOut) {
            SignInFlowView(isRoot: true).environmentObject(flow)
        }
        .environmentObject(flow)
        .environment(\.openCheckIn) { checkIn = CheckInTarget(orderId: $0) }
        .fullScreenCover(item: $checkIn) { CheckInFlow(orderId: $0.orderId) }
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
