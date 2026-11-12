import SwiftUI
import SharedLogic

/// The floating Search tab. Matches store names and item names, together with the mode and filters.
/// It keeps its own search and filters; the In-store / Pickup choice is shared with Discover.
struct SearchView: View {
    @StateObject private var model = DiscoverModel()
    @State private var query = ""
    @State private var showFilters = false
    @FocusState private var searchFocused: Bool
    @EnvironmentObject private var flow: AuthFlow

    var body: some View {
        let state = model.state
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ModePicker(mode: state.filters.mode) { model.viewModel.selectMode(mode: $0) }
                StoreSections(state: state, viewModel: model.viewModel, showsRecommended: false)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
        .background(Palette.screen)
        .navigationTitle("Search")
        .modifier(SearchField(query: $query))
        .searchFocused($searchFocused)
        // Opening the Search tab starts typing straight away. Only on tab switches, so coming back
        // from a store page doesn't pop the keyboard up again.
        .onChange(of: flow.selection, initial: true) { _, tab in
            if tab == .search { searchFocused = true }
        }
        .onChange(of: query) { _, newValue in
            model.viewModel.setQuery(query: newValue)
        }
        // Clear filters empties the Kotlin query; empty the field with it.
        .onChange(of: state.filters.query) { _, newValue in
            if newValue.isEmpty { query = "" }
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                FilterButton(count: Int(state.filters.sheetFilterCount)) { showFilters = true }
            }
        }
        .sheet(isPresented: $showFilters) { FilterSheet(model: model) }
        .navigationDestination(for: DiscoverRoute.self) { DiscoverDestination(route: $0) }
    }
}

/// iOS 26 turns the `.search` tab itself into the field. On iOS 27 the tab is `.prominent` (so it still floats
/// apart from the bar), which leaves a field that hides until pulled down, so keep it showing there.
private struct SearchField: ViewModifier {
    @Binding var query: String

    func body(content: Content) -> some View {
        if #available(iOS 27, *) {
            content.searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "Stores or dishes")
        } else {
            content.searchable(text: $query, prompt: "Stores or dishes")
        }
    }
}
