import SwiftUI
import SharedLogic

/// The app's tabs. On iOS 26 the system draws the tab bar in Liquid Glass and floats the Search tab
/// as its own round button beside it, as in the design; on iOS 18–25 the same code gives the standard tab bar.
/// Every screen is SwiftUI; the shared Kotlin code supplies view models and data only.
/// Sign-in opens as a sheet over the current screen, or full screen after sign-out.
/// Check-in and the order ticket open full screen over every tab, one at a time, so paying at the
/// seat check swaps check-in for the ticket.
struct ContentView: View {
    @StateObject private var flow = AuthFlow()
    @ObservedObject private var notifier = OrderNotifier.shared
    @State private var cover: AppCover?

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
        .fullScreenCover(item: $cover) { cover in
            switch cover {
            case .checkIn(let orderId): CheckInFlow(orderId: orderId)
            case .ticket(let orderId): OrderTicketFlow(orderId: orderId)
            case .rate(let orderId): RateOrderFlow(orderId: orderId)
            }
        }
        // Outside the cover, so check-in can open the ticket after paying.
        .environment(\.openCheckIn) { cover = .checkIn($0) }
        .environment(\.openOrderTicket) { cover = .ticket($0) }
        .environment(\.openRating) { cover = .rate($0) }
        .onReceive(notifier.$openedOrderId.compactMap { $0 }) { orderId in
            notifier.openedOrderId = nil
            // The id comes from outside the app's screens: open only a paid order, and only while signed in.
            if notifier.canOpenTicket(orderId) { cover = .ticket(orderId) }
        }
    }
}

/// What is open full screen over the tabs.
enum AppCover: Identifiable, Hashable {
    case checkIn(String)
    case ticket(String)
    case rate(String)

    var id: String {
        switch self {
        case .checkIn(let orderId): return "check-in-\(orderId)"
        case .ticket(let orderId): return "ticket-\(orderId)"
        case .rate(let orderId): return "rate-\(orderId)"
        }
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
