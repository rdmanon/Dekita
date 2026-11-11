import SwiftUI
import SharedUI

/// The floating Search tab. Matches store names and item names, together with the mode and filters.
/// It keeps its own search and filters; the In-store / Pickup choice is shared with Discover.
struct SearchView: View {
    @StateObject private var model = DiscoverModel()
    @State private var query = ""
    @State private var showFilters = false

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
        .searchable(text: $query, prompt: "Stores or dishes")
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
