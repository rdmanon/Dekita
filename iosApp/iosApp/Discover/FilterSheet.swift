import SwiftUI
import SharedUI

/// Walking distance, category and status filters. They apply as you tap; the button just closes the sheet.
struct FilterSheet: View {
    @ObservedObject var model: DiscoverModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let filters = model.state.filters
        let viewModel = model.viewModel
        let count = model.state.stores.count
        NavigationStack {
            Form {
                Section("Walking distance") {
                    Picker("Walking distance", selection: walkLimitBinding) {
                        Text("Any distance").tag(WalkLimit?.none)
                        ForEach(model.walkLimits, id: \.self) { limit in
                            Text(limit.label).tag(WalkLimit?.some(limit))
                        }
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                }
                Section("Category") {
                    ForEach(model.state.categories, id: \.self) { category in
                        Toggle(category, isOn: Binding(
                            get: { filters.categories.contains(category) },
                            set: { _ in viewModel.toggleCategory(category: category) }
                        ))
                    }
                }
                Section("Status") {
                    ForEach(model.statuses, id: \.self) { status in
                        Toggle(status.filterLabel, isOn: Binding(
                            get: { filters.statuses.contains(status) },
                            set: { _ in viewModel.toggleStatus(status: status) }
                        ))
                    }
                }
            }
            .navigationTitle("Filters")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Clear All") { viewModel.clearFilters() }
                }
            }
            .safeAreaInset(edge: .bottom) {
                Button { dismiss() } label: {
                    Text(count == 1 ? "Show 1 store" : "Show \(count) stores")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .padding()
            }
        }
        .presentationDetents([.medium, .large])
    }

    /// Kotlin's walk limit toggles off when picked again, so map the picker's choice onto that.
    private var walkLimitBinding: Binding<WalkLimit?> {
        Binding(
            get: { model.state.filters.walkLimit },
            set: { newValue in
                let current = model.state.filters.walkLimit
                if let newValue {
                    if newValue != current { model.viewModel.selectWalkLimit(limit: newValue) }
                } else if let current {
                    model.viewModel.selectWalkLimit(limit: current)
                }
            }
        )
    }
}
