import Combine
import Foundation
import SharedLogic

// SwiftUI models over the shared Kotlin view models. Each one owns a Kotlin bridge, republishes its
// state (delivered on the main thread), and stops watching and clears the view models when it goes away.

final class DiscoverModel: ObservableObject {
    private let bridge: DiscoverBridge
    private var watch: FlowWatch?

    @Published private(set) var state: DiscoverUiState

    var viewModel: DiscoverViewModel { bridge.viewModel }
    var walkLimits: [WalkLimit] { bridge.walkLimits }
    var statuses: [StoreAvailability] { bridge.statuses }

    /// The Discover tab passes `modeNeedsCategory: true`: its Eat-in / Takeout filter shows only under a picked
    /// category that has a store offering both. The Search tab keeps the default, an always-visible picker.
    init(modeNeedsCategory: Bool = false) {
        bridge = DiscoverBridge(modeNeedsCategory: modeNeedsCategory)
        state = bridge.currentState
        watch = bridge.watch { [weak self] state in self?.state = state }
    }

    deinit {
        watch?.cancel()
        bridge.close()
    }
}

/// The location chip on Discover and its picker sheet.
final class PlacePickerModel: ObservableObject {
    private let bridge = PlacePickerBridge()
    private var watch: FlowWatch?

    @Published private(set) var state: PlacePickerUiState

    var viewModel: PlacePickerViewModel { bridge.viewModel }

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

final class CheckInModel: ObservableObject {
    private let bridge: CheckInBridge
    private var watch: FlowWatch?

    @Published private(set) var state: CheckInUiState

    var viewModel: CheckInViewModel { bridge.viewModel }

    init(orderId: String) {
        bridge = CheckInBridge(orderId: orderId)
        state = bridge.currentState
        watch = bridge.watch { [weak self] state in self?.state = state }
    }

    deinit {
        watch?.cancel()
        bridge.close()
    }
}

final class OrderTicketModel: ObservableObject {
    private let bridge: OrderTicketBridge
    private var watches: [FlowWatch] = []

    @Published private(set) var state: OrderTicketUiState

    /// Set when the ticket opens on an auto-completed, unrated order (RCPT-01). Published, so a view that
    /// subscribes a moment after the event still sees it. The view clears it once handled.
    @Published var openRatingRequested = false

    var viewModel: OrderTicketViewModel { bridge.viewModel }

    init(orderId: String) {
        bridge = OrderTicketBridge(orderId: orderId)
        state = bridge.currentState
        watches = [
            bridge.watch { [weak self] state in self?.state = state },
            bridge.watchEvents { [weak self] event in
                if event is OrderTicketEventOpenRating { self?.openRatingRequested = true }
            },
        ]
    }

    deinit {
        watches.forEach { $0.cancel() }
        bridge.close()
    }
}

final class RateOrderModel: ObservableObject {
    private let bridge: RateOrderBridge
    private var watch: FlowWatch?

    @Published private(set) var state: RateOrderUiState

    var viewModel: RateOrderViewModel { bridge.viewModel }

    init(orderId: String) {
        bridge = RateOrderBridge(orderId: orderId)
        state = bridge.currentState
        watch = bridge.watch { [weak self] state in self?.state = state }
    }

    deinit {
        watch?.cancel()
        bridge.close()
    }
}

/// SEAT-01 seat verification for one checked-in draft.
final class SeatCheckModel: ObservableObject {
    private let bridge: SeatCheckBridge
    private var watches: [FlowWatch] = []

    @Published private(set) var state: SeatCheckUiState
    let events = PassthroughSubject<any SeatCheckEvent, Never>()

    var viewModel: SeatCheckViewModel { bridge.viewModel }

    init(orderId: String) {
        bridge = SeatCheckBridge(orderId: orderId)
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

final class QrScannerModel: ObservableObject {
    private let bridge: QrScannerBridge
    private var watch: FlowWatch?

    @Published private(set) var state: QrScannerUiState

    var viewModel: QrScannerViewModel { bridge.viewModel }

    init(orderId: String) {
        bridge = QrScannerBridge(orderId: orderId)
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

    var viewModel: OrdersViewModel { bridge.viewModel }

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

final class OtpModel: ObservableObject {
    private let bridge: OtpBridge
    private var watches: [FlowWatch] = []

    @Published private(set) var state: OtpUiState
    let events = PassthroughSubject<any AuthEvent, Never>()

    var viewModel: OtpViewModel { bridge.viewModel }

    /// `contactKey` is `AuthContact.key`: an email address, or a phone number in E.164 form.
    init(contactKey: String) {
        bridge = OtpBridge(contactKey: contactKey)
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

/// The payment sheet: for the cart, or for the in-store draft `draftOrderId` (PAY-01).
final class PaymentModel: ObservableObject {
    private let bridge: PaymentBridge
    private var watches: [FlowWatch] = []

    @Published private(set) var state: PaymentUiState
    let events = PassthroughSubject<any PaymentEvent, Never>()

    var viewModel: PaymentViewModel { bridge.viewModel }

    init(draftOrderId: String? = nil) {
        bridge = PaymentBridge(draftOrderId: draftOrderId)
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
