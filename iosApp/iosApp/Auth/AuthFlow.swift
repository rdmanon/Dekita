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

    /// Closes the top screen of the current tab's stack. CART-01: the cart, once it is a draft order.
    func popTop() {
        if selection == .search {
            if !searchPath.isEmpty { searchPath.removeLast() }
        } else {
            if !discoverPath.isEmpty { discoverPath.removeLast() }
        }
    }

    /// Back to the current tab's first screen.
    func popToRoot() {
        if selection == .search {
            searchPath = NavigationPath()
        } else {
            discoverPath = NavigationPath()
        }
    }

    /// SEAT-01 Switch to Takeout: Cart Review on the store's menu, so "Add more items" and Back lead there.
    func showTakeoutCart(storeId: String, storeName: String) {
        let routes = NavigationPath([DiscoverRoute.store(id: storeId, name: storeName), DiscoverRoute.cart])
        if selection == .search {
            searchPath = routes
        } else {
            selection = .discover
            discoverPath = routes
        }
    }

    /// SEAT-01 Cancel / Exit: back to Discover. The draft is kept.
    func showDiscover() {
        selection = .discover
        discoverPath = NavigationPath()
    }

    /// After paying at the seat check: the Orders tab, where the order now shows as in the queue.
    func showOrders() {
        popToRoot()
        selection = .orders
    }

    private func showFreshDiscover() {
        discoverPath = NavigationPath()
        searchPath = NavigationPath()
        selection = .discover
    }
}
