import Foundation
import SharedUI

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
