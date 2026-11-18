import SwiftUI
import SharedLogic

/// Discover, the start of every order. From the top: the location chip and the title, a banner for an active
/// order and one for an Eat-in draft waiting for check-in, the categories, then the Eat-in / Takeout selector
/// (only under a picked category that has a store offering both), Nearby, Recommended, and last the decided
/// customer's Favorites and History (Search is the floating Search tab). The mode and the category chip filter
/// those sections in place; a search or a Filters-sheet filter swaps them for one result list.
struct DiscoverView: View {
    @StateObject private var model = DiscoverModel(modeNeedsCategory: true)
    @StateObject private var placeModel = PlacePickerModel()
    @State private var showFilters = false
    @State private var showPlaces = false
    @State private var showDebugPanel = false
    @Environment(\.openCheckIn) private var openCheckIn
    @Environment(\.openOrderTicket) private var openOrderTicket

    var body: some View {
        let state = model.state
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                // The artboard puts the location chip above the title, which a system large title can't do.
                VStack(alignment: .leading, spacing: 2) {
                    LocationChip(placeName: placeModel.state.selected.name) { showPlaces = true }
                    Text("Discover")
                        .font(.largeTitle.weight(.bold))
                        .accessibilityAddTraits(.isHeader)
                }
                if let order = state.activeOrder {
                    ActiveOrderBanner(order: order) { openOrderTicket(order.id) }
                }
                if let draft = state.draftToResume {
                    DraftBanner(draft: draft) { openCheckIn(draft.id) }
                }
                StoreSections(
                    state: state,
                    viewModel: model.viewModel,
                    showsRecommended: true,
                    showsModePicker: state.showsModeFilter
                )
                DecidedShortcuts(
                    favoriteItemCount: state.favoriteItemCount,
                    lastOrderStoreName: state.lastOrderStoreName
                )
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
        .background(Palette.screen)
        // The title is drawn in the content, so the bar keeps only its buttons. The name stays for the back button.
        .navigationTitle("Discover")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) { Color.clear.accessibilityHidden(true) }
            ToolbarItem(placement: .topBarTrailing) {
                FilterButton(count: Int(state.filters.sheetFilterCount)) { showFilters = true }
            }
            if AppBuild.isDebug {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showDebugPanel = true } label: {
                        Label("Debug panel", systemImage: "ladybug")
                    }
                }
            }
        }
        .sheet(isPresented: $showFilters) { FilterSheet(model: model) }
        // Closing without a pick empties the sheet's search, so it opens fresh next time.
        .sheet(isPresented: $showPlaces, onDismiss: { placeModel.viewModel.clearQuery() }) {
            PlaceSheetView(model: placeModel)
        }
        .sheet(isPresented: $showDebugPanel) { DebugPanelView() }
        .navigationDestination(for: DiscoverRoute.self) { DiscoverDestination(route: $0, discover: model) }
    }
}

/// The store sections: loading, error, then the categories, the optional Eat-in / Takeout selector, Nearby (or
/// the results) and Recommended. Shared by the Discover and Search tabs. Discover puts the selector under the
/// chips, and only when the shared state says so; Search keeps its own selector above, always on screen.
struct StoreSections: View {
    let state: DiscoverUiState
    let viewModel: DiscoverViewModel
    let showsRecommended: Bool
    /// Draws the Eat-in / Takeout selector directly under the category chips.
    var showsModePicker = false

    var body: some View {
        if state.isLoading {
            LoadingPlaceholder()
        } else if let error = state.error {
            LoadErrorView(message: error) { viewModel.retry() }
        } else {
            VStack(alignment: .leading, spacing: 10) {
                SectionHeader(title: "Browse by category")
                CategoryChips(categories: state.categories, selected: state.filters.categories) {
                    viewModel.toggleCategory(category: $0)
                }
            }

            if showsModePicker {
                ModePicker(mode: state.filters.mode) { viewModel.selectMode(mode: $0) }
            }

            if state.showsResults {
                if !state.stores.isEmpty {
                    ResultsHeader(count: state.stores.count) { viewModel.clearFilters() }
                }
            } else if !state.stores.isEmpty {
                SectionHeader(title: "Nearby")
            }
            if state.hasNoMatches {
                NoMatchesCard { viewModel.clearFilters() }
            }
            if !state.stores.isEmpty {
                StoreListCard(matches: state.stores)
            }

            if !state.showsResults && showsRecommended && !state.recommended.isEmpty {
                RecommendedSection(matches: state.recommended)
            }
        }
    }
}

private struct RecommendedSection: View {
    let matches: [StoreMatch]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                SectionHeader(title: "Recommended")
                NavigationLink(value: DiscoverRoute.recommended) {
                    Text("See All").font(.subheadline)
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                }
                .foregroundStyle(Palette.link)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 12) {
                    ForEach(matches, id: \.store.id) { match in
                        NavigationLink(value: DiscoverRoute.store(id: match.store.id, name: match.store.name)) {
                            RecommendedCard(store: match.store)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .scrollTargetLayout()
                .padding(.horizontal, 16)
            }
            .scrollTargetBehavior(.viewAligned)
            .padding(.horizontal, -16)
        }
    }
}

/// The toolbar's filter button. On iOS 26 it is a Liquid Glass button; it fills in when filters are on.
struct FilterButton: View {
    let count: Int
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label(
                "Filters",
                systemImage: count > 0 ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle"
            )
        }
        .accessibilityValue(count > 0 ? "\(count) on" : "Off")
    }
}
