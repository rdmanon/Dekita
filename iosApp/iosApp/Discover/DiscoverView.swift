import SwiftUI
import SharedLogic

/// Discover, the start of every order. From the top: the In-store / Pickup selector, a banner for an
/// in-store draft waiting for check-in, the decided
/// customer's Favorites and History (Search is the floating Search tab), then the undecided
/// customer's categories, Recommended and Nearby. A category or filter swaps the home sections
/// for one result list.
struct DiscoverView: View {
    @StateObject private var model = DiscoverModel()
    @State private var showFilters = false
    @State private var showDebugPanel = false
    @Environment(\.openCheckIn) private var openCheckIn
    @Environment(\.openOrderTicket) private var openOrderTicket

    var body: some View {
        let state = model.state
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ModePicker(mode: state.filters.mode) { model.viewModel.selectMode(mode: $0) }
                if let order = state.activeOrder {
                    ActiveOrderBanner(order: order) { openOrderTicket(order.id) }
                }
                if let draft = state.draftToResume {
                    DraftBanner(draft: draft) { openCheckIn(draft.id) }
                }
                DecidedShortcuts(
                    favoriteItemCount: state.favoriteItemCount,
                    lastOrderStoreName: state.lastOrderStoreName
                )
                StoreSections(state: state, viewModel: model.viewModel, showsRecommended: true)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
        .background(Palette.screen)
        .navigationTitle("Discover")
        .modifier(NavigationSubtitle(text: "Lunchtime near you"))
        .toolbar {
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
        .sheet(isPresented: $showDebugPanel) { DebugPanelView() }
        .navigationDestination(for: DiscoverRoute.self) { DiscoverDestination(route: $0) }
    }
}

/// Everything below the shortcuts: loading, error, categories, Recommended, Nearby or results.
/// Shared by the Discover and Search tabs.
struct StoreSections: View {
    let state: DiscoverUiState
    let viewModel: DiscoverViewModel
    let showsRecommended: Bool

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

            if state.showsResults {
                if !state.stores.isEmpty {
                    ResultsHeader(count: state.stores.count) { viewModel.clearFilters() }
                }
            } else if showsRecommended && !state.recommended.isEmpty {
                RecommendedSection(matches: state.recommended)
            }

            if !state.showsResults && !state.stores.isEmpty {
                SectionHeader(title: "Nearby")
            }
            if state.hasNoMatches {
                NoMatchesCard { viewModel.clearFilters() }
            }
            if !state.stores.isEmpty {
                StoreListCard(matches: state.stores)
            }
        }
    }
}

private struct RecommendedSection: View {
    let matches: [StoreMatch]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader(title: "Recommended")
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

/// iOS 26 shows a subtitle under the large title. Earlier versions show the title alone.
private struct NavigationSubtitle: ViewModifier {
    let text: String

    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.navigationSubtitle(text)
        } else {
            content
        }
    }
}
