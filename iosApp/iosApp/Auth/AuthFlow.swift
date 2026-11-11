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

    private func showFreshDiscover() {
        discoverPath = NavigationPath()
        searchPath = NavigationPath()
        selection = .discover
    }
}
