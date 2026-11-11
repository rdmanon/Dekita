import Combine
import Foundation
import SharedLogic

// SwiftUI models over the shared Kotlin view models. Each one owns a Kotlin bridge, republishes its
// state (delivered on the main thread), and stops watching and clears the view models when it goes away.

final class DiscoverModel: ObservableObject {
    private let bridge = DiscoverBridge()
    private var watch: FlowWatch?

    @Published private(set) var state: DiscoverUiState

    var viewModel: DiscoverViewModel { bridge.viewModel }
    var walkLimits: [WalkLimit] { bridge.walkLimits }
    var statuses: [StoreAvailability] { bridge.statuses }

    init() {
        state = bridge.currentState
        watch = bridge.watch { [weak self] state in self?.state = state }
    }

    deinit {
        watch?.cancel()
        bridge.close()
    }
}

final class FavoritesModel: ObservableObject {
    private let bridge = FavoritesBridge()
    private var watch: FlowWatch?

    @Published private(set) var state: FavoritesUiState

    var viewModel: FavoritesViewModel { bridge.viewModel }

    init() {
        state = bridge.currentState
        watch = bridge.watch { [weak self] state in self?.state = state }
    }

    deinit {
        watch?.cancel()
        bridge.close()
    }
}

final class HistoryModel: ObservableObject {
    private let bridge = HistoryBridge()
    private var watch: FlowWatch?

    @Published private(set) var state: HistoryUiState

    var viewModel: HistoryViewModel { bridge.viewModel }

    init() {
        state = bridge.currentState
        watch = bridge.watch { [weak self] state in self?.state = state }
    }

    deinit {
        watch?.cancel()
        bridge.close()
    }
}

/// The heart in the store screen's navigation bar.
final class FavoriteStoreModel: ObservableObject {
    private let bridge: FavoriteStoreBridge
    private var watch: FlowWatch?

    @Published private(set) var isFavorite: Bool

    init(storeId: String) {
        bridge = FavoriteStoreBridge(storeId: storeId)
        isFavorite = bridge.isFavorite
        watch = bridge.watch { [weak self] value in self?.isFavorite = value.boolValue }
    }

    func toggle() {
        bridge.toggle()
    }

    deinit {
        watch?.cancel()
    }
}

final class StoreDetailModel: ObservableObject {
    private let bridge: StoreDetailBridge
    private var watch: FlowWatch?

    @Published private(set) var state: StoreDetailUiState

    /** Actions (open an item, add to cart and so on) go straight to the shared view model. */
    var viewModel: StoreDetailViewModel { bridge.viewModel }

    init(storeId: String) {
        bridge = StoreDetailBridge(storeId: storeId)
        state = bridge.currentState
        watch = bridge.watch { [weak self] state in self?.state = state }
    }

    deinit {
        watch?.cancel()
        bridge.close()
    }
}

final class OrdersModel: ObservableObject {
    private let bridge = OrdersBridge()
    private var watch: FlowWatch?

    @Published private(set) var state: OrdersUiState

    init() {
        state = bridge.currentState
        watch = bridge.watch { [weak self] state in self?.state = state }
    }

    deinit {
        watch?.cancel()
        bridge.close()
    }
}

final class AccountModel: ObservableObject {
    private let bridge = AccountBridge()
    private var watch: FlowWatch?

    @Published private(set) var state: AccountUiState

    var viewModel: AccountViewModel { bridge.viewModel }

    init() {
        state = bridge.currentState
        watch = bridge.watch { [weak self] state in self?.state = state }
    }

    deinit {
        watch?.cancel()
        bridge.close()
    }
}

// Sign-in models also republish the view model's one-off events, for `.onReceive`.

final class SignInModel: ObservableObject {
    private let bridge = SignInBridge()
    private var watches: [FlowWatch] = []

    @Published private(set) var state: SignInUiState
    let events = PassthroughSubject<any AuthEvent, Never>()

    var viewModel: SignInViewModel { bridge.viewModel }

    init() {
        state = bridge.currentState
        watches = [
            bridge.watch { [weak self] state in self?.state = state },
            bridge.watchEvents { [weak self] event in self?.events.send(event) },
        ]
    }

    deinit {
        watches.forEach { $0.cancel() }
        bridge.close()
    }
}

final class PhoneEntryModel: ObservableObject {
    private let bridge = PhoneEntryBridge()
    private var watches: [FlowWatch] = []

    @Published private(set) var state: PhoneEntryUiState
    let events = PassthroughSubject<any AuthEvent, Never>()

    var viewModel: PhoneEntryViewModel { bridge.viewModel }
    var dialCodes: [DialCode] { bridge.dialCodes }

    init() {
        state = bridge.currentState
        watches = [
            bridge.watch { [weak self] state in self?.state = state },
            bridge.watchEvents { [weak self] event in self?.events.send(event) },
        ]
    }

    deinit {
        watches.forEach { $0.cancel() }
        bridge.close()
    }
}

final class OtpModel: ObservableObject {
    private let bridge: OtpBridge
    private var watches: [FlowWatch] = []

    @Published private(set) var state: OtpUiState
    let events = PassthroughSubject<any AuthEvent, Never>()

    var viewModel: OtpViewModel { bridge.viewModel }

    init(phoneE164: String) {
        bridge = OtpBridge(phoneE164: phoneE164)
        state = bridge.currentState
        watches = [
            bridge.watch { [weak self] state in self?.state = state },
            bridge.watchEvents { [weak self] event in self?.events.send(event) },
        ]
    }

    deinit {
        watches.forEach { $0.cancel() }
        bridge.close()
    }
}

final class EmailAuthModel: ObservableObject {
    private let bridge: EmailAuthBridge
    private var watches: [FlowWatch] = []

    @Published private(set) var state: EmailAuthUiState
    let events = PassthroughSubject<any AuthEvent, Never>()

    var viewModel: EmailAuthViewModel { bridge.viewModel }

    init(signUp: Bool) {
        bridge = EmailAuthBridge(signUp: signUp)
        state = bridge.currentState
        watches = [
            bridge.watch { [weak self] state in self?.state = state },
            bridge.watchEvents { [weak self] event in self?.events.send(event) },
        ]
    }

    deinit {
        watches.forEach { $0.cancel() }
        bridge.close()
    }
}

final class ForgotPasswordModel: ObservableObject {
    private let bridge = ForgotPasswordBridge()
    private var watch: FlowWatch?

    @Published private(set) var state: ForgotPasswordUiState

    var viewModel: ForgotPasswordViewModel { bridge.viewModel }

    init() {
        state = bridge.currentState
        watch = bridge.watch { [weak self] state in self?.state = state }
    }

    deinit {
        watch?.cancel()
        bridge.close()
    }
}

final class CartReviewModel: ObservableObject {
    private let bridge = CartReviewBridge()
    private var watches: [FlowWatch] = []

    @Published private(set) var state: CartReviewUiState
    let events = PassthroughSubject<any CartReviewEvent, Never>()

    var viewModel: CartReviewViewModel { bridge.viewModel }

    init() {
        state = bridge.currentState
        watches = [
            bridge.watch { [weak self] state in self?.state = state },
            bridge.watchEvents { [weak self] event in self?.events.send(event) },
        ]
    }

    deinit {
        watches.forEach { $0.cancel() }
        bridge.close()
    }
}

/// SEAT-01 check-in for one draft order.
final class CheckInModel: ObservableObject {
    private let bridge: CheckInBridge
    private var watches: [FlowWatch] = []

    @Published private(set) var state: CheckInUiState
    let events = PassthroughSubject<any CheckInEvent, Never>()

    var viewModel: CheckInViewModel { bridge.viewModel }

    init(orderId: String) {
        bridge = CheckInBridge(orderId: orderId)
        state = bridge.currentState
        watches = [
            bridge.watch { [weak self] state in self?.state = state },
            bridge.watchEvents { [weak self] event in self?.events.send(event) },
        ]
    }

    deinit {
        watches.forEach { $0.cancel() }
        bridge.close()
    }
}

/// The debug panel's view of the mock data layer. Debug builds only.
final class DebugModel: ObservableObject {
    let bridge = DebugBridge()
    private var watch: FlowWatch?

    @Published private(set) var state: DebugState

    init() {
        state = bridge.currentState
        watch = bridge.watch { [weak self] state in self?.state = state }
    }

    deinit {
        watch?.cancel()
    }
}
