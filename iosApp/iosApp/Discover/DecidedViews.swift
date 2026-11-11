import SwiftUI
import SharedLogic

/// Favorited items, each with its store, then favorited stores. Every row opens its store.
struct FavoritesView: View {
    @StateObject private var model = FavoritesModel()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let state = model.state
        Group {
            if state.isLoading {
                ProgressView("Loading favorites…")
            } else if let error = state.error {
                LoadErrorView(message: error) { model.viewModel.retry() }
            } else if state.isEmpty {
                ContentUnavailableView {
                    Label("No favorites yet", systemImage: "star")
                } description: {
                    Text("Save the stores and dishes you order often, and they'll show up here for a quick reorder.")
                } actions: {
                    Button("Browse stores") { dismiss() }
                        .buttonStyle(.bordered)
                }
            } else {
                List {
                    if !state.items.isEmpty {
                        Section("Items") {
                            ForEach(state.items, id: \.self) { entry in
                                NavigationLink(value: DiscoverRoute.store(id: entry.store.id, name: entry.store.name)) {
                                    FavoriteItemRow(entry: entry)
                                }
                            }
                        }
                    }
                    if !state.stores.isEmpty {
                        Section("Stores") {
                            ForEach(state.stores, id: \.id) { store in
                                NavigationLink(value: DiscoverRoute.store(id: store.id, name: store.name)) {
                                    StoreRowView(store: store, insideList: true)
                                }
                            }
                        }
                    }
                }
                .listStyle(.insetGrouped)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Palette.screen)
        .navigationTitle("Favorites")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct FavoriteItemRow: View {
    let entry: FavoriteItemEntry

    var body: some View {
        HStack(spacing: 12) {
            StoreArtView(store: entry.store)
                .frame(width: 56, height: 56)
            VStack(alignment: .leading, spacing: 3) {
                Text(entry.item.itemName)
                    .font(.body.weight(.semibold))
                    .lineLimit(1)
                Text("\(entry.store.name) · \(entry.item.price.formatted())")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                StatusBadgeView(badge: entry.store.badge)
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }
}

/// Where the customer ordered before: the stores, then each order. Every row opens its store.
struct HistoryView: View {
    @StateObject private var model = HistoryModel()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let state = model.state
        Group {
            if state.isLoading {
                ProgressView("Loading history…")
            } else if let error = state.error {
                LoadErrorView(message: error) { model.viewModel.retry() }
            } else if state.isEmpty {
                ContentUnavailableView {
                    Label("No orders yet", systemImage: "clock.arrow.circlepath")
                } description: {
                    Text("Stores you order from will show up here, so you can go back in one tap.")
                } actions: {
                    Button("Browse stores") { dismiss() }
                        .buttonStyle(.bordered)
                }
            } else {
                List {
                    if !state.recentStores.isEmpty {
                        Section("Recent stores") {
                            ForEach(state.recentStores, id: \.id) { store in
                                NavigationLink(value: DiscoverRoute.store(id: store.id, name: store.name)) {
                                    StoreRowView(store: store, insideList: true)
                                }
                            }
                        }
                    }
                    Section("Recent orders") {
                        ForEach(state.recentOrders, id: \.order.id) { entry in
                            NavigationLink(
                                value: DiscoverRoute.store(id: entry.order.storeId, name: entry.store?.name ?? entry.order.storeName)
                            ) {
                                HistoryOrderRow(entry: entry)
                            }
                        }
                    }
                }
                .listStyle(.insetGrouped)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Palette.screen)
        .navigationTitle("History")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct HistoryOrderRow: View {
    let entry: HistoryOrderEntry

    var body: some View {
        let order = entry.order
        VStack(alignment: .leading, spacing: 2) {
            Text("#\(order.number) · \(entry.store?.name ?? order.storeName)")
                .font(.body.weight(.semibold))
                .lineLimit(1)
            Text(summary(order))
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .lineLimit(1)
            HStack(spacing: 4) {
                Text(order.status.label)
                Text("·")
                Text(Date(timeIntervalSince1970: TimeInterval(order.createdEpochSeconds)), format: .relative(presentation: .named))
            }
            .font(.caption)
            .foregroundStyle(.secondary)
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
    }

    private func summary(_ order: Order) -> String {
        let first = order.lines.first?.item.name ?? ""
        let more = order.lines.count > 1 ? " +\(order.lines.count - 1) more" : ""
        return "\(first)\(more) · \(order.mode.label) · \(order.total.formatted())"
    }
}
