import SwiftUI

/// Where sign-in was started, so finishing it returns to the right place.
enum SignInEntry: Identifiable {
    /// From Account or Orders: finish on a fresh Discover.
    case standard
    /// From "Pay now": finish back on the cart, which stays underneath the sheet with its items.
    case checkout

    var id: Self { self }
}

/// App-wide navigation for sign-in and sign-out. ContentView owns it and shares it as an environment object.
final class AuthFlow: ObservableObject {
    @Published var selection: AppTab = .discover
    @Published var discoverPath = NavigationPath()
    @Published var searchPath = NavigationPath()
    /// Sign-in as a sheet over the current screen.
    @Published var signInSheet: SignInEntry?
    /// After sign-out, sign-in covers the whole app, so there is nothing signed-in to swipe back to.
    @Published var signInAfterSignOut = false

    func requestSignIn(from entry: SignInEntry) {
        signInSheet = entry
    }

    func finishSignIn() {
        let returnsToCart = signInSheet == .checkout
        signInSheet = nil
        signInAfterSignOut = false
        if !returnsToCart { showFreshDiscover() }
    }

    func signedOut() {
        showFreshDiscover()
        signInAfterSignOut = true
    }

    func browseAsGuest() {
        signInAfterSignOut = false
        showFreshDiscover()
    }

    /// Replaces the top screen of the current tab's stack with [routes], so Back skips it.
    /// CART-01: cart → check-in. SEAT-01: check-in → store → cart (Switch to Takeout).
    func replaceTop(with routes: DiscoverRoute...) {
        if selection == .search {
            if !searchPath.isEmpty { searchPath.removeLast() }
            routes.forEach { searchPath.append($0) }
        } else {
            if !discoverPath.isEmpty { discoverPath.removeLast() }
            routes.forEach { discoverPath.append($0) }
        }
    }

    /// SEAT-01: opens check-in for a kept draft from Orders. Check-in lives in the Discover tab, with the
    /// cart and store it can lead back to.
    func resumeCheckIn(orderId: String) {
        selection = .discover
        discoverPath.append(DiscoverRoute.checkIn(orderId: orderId))
    }

    /// After paying at check-in: back to the tab's first screen, then the Orders tab.
    func showOrders() {
        popToRoot()
        selection = .orders
    }

    /// Back to the current tab's first screen.
    func popToRoot() {
        if selection == .search {
            searchPath = NavigationPath()
        } else {
            discoverPath = NavigationPath()
        }
    }

    private func showFreshDiscover() {
        discoverPath = NavigationPath()
        searchPath = NavigationPath()
        selection = .discover
    }
}
