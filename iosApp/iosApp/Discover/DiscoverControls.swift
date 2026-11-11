import SwiftUI
import SharedUI

/// Where a Discover tap leads. Both the Discover and the Search tab register these destinations.
enum DiscoverRoute: Hashable {
    case favorites
    case history
    case store(id: String, name: String)
}

/// Opens the screen for a route. Attach with `.navigationDestination(for: DiscoverRoute.self)`.
struct DiscoverDestination: View {
    let route: DiscoverRoute

    var body: some View {
        switch route {
        case .favorites: FavoritesView()
        case .history: HistoryView()
        case let .store(id, name): StoreScreen(storeId: id, name: name)
        }
    }
}

/// The In-store / Pickup selector, shown before any store. "All" clears it.
/// A native segmented control: capsule-shaped with a glass thumb on iOS 26.
struct ModePicker: View {
    let mode: FulfillmentMode?
    let onSelect: (FulfillmentMode?) -> Void

    private enum Option: Hashable { case all, inStore, pickup }

    var body: some View {
        Picker("How you'll be served", selection: Binding(get: { option(for: mode) }, set: { onSelect(fulfillmentMode(for: $0)) })) {
            Text("All").tag(Option.all)
            Text("In-store").tag(Option.inStore)
            Text("Pickup").tag(Option.pickup)
        }
        .pickerStyle(.segmented)
    }

    private func option(for mode: FulfillmentMode?) -> Option {
        if mode == FulfillmentMode.inStore { return .inStore }
        if mode == FulfillmentMode.pickup { return .pickup }
        return .all
    }

    private func fulfillmentMode(for option: Option) -> FulfillmentMode? {
        switch option {
        case .all: return nil
        case .inStore: return FulfillmentMode.inStore
        case .pickup: return FulfillmentMode.pickup
        }
    }
}

/// The decided customer's shortcuts. Search is the floating Search tab.
struct DecidedShortcuts: View {
    let favoriteItemCount: Int32
    let lastOrderStoreName: String?

    var body: some View {
        HStack(spacing: 10) {
            NavigationLink(value: DiscoverRoute.favorites) {
                ShortcutTile(systemImage: "star", title: "Favorites", subtitle: favoritesSubtitle)
            }
            NavigationLink(value: DiscoverRoute.history) {
                ShortcutTile(
                    systemImage: "clock.arrow.circlepath",
                    title: "History",
                    subtitle: lastOrderStoreName.map { "Last: \($0)" } ?? "No orders yet"
                )
            }
        }
        .buttonStyle(.plain)
    }

    private var favoritesSubtitle: String {
        switch favoriteItemCount {
        case 0: return "None yet"
        case 1: return "1 item"
        default: return "\(favoriteItemCount) items"
        }
    }
}

private struct ShortcutTile: View {
    let systemImage: String
    let title: String
    let subtitle: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: systemImage)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(.tint)
                .frame(width: 36, height: 36)
                .background(Palette.tileIcon, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.subheadline.weight(.semibold))
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(12)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .contentShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

/// Capsule category chips, multi-select. The selected ones turn dark, as in the design.
struct CategoryChips: View {
    let categories: [String]
    let selected: Set<String>
    let onToggle: (String) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(categories, id: \.self) { category in
                    let isOn = selected.contains(category)
                    Button { onToggle(category) } label: {
                        Text(category)
                            .font(.subheadline.weight(isOn ? .semibold : .medium))
                            .foregroundStyle(isOn ? Color(.systemBackground) : Color.primary)
                            .padding(.horizontal, 14)
                            .frame(height: 34)
                            .background(isOn ? Color.primary : Palette.card, in: Capsule())
                            // Keeps the touch target at 44 pt while the chip stays 34 pt tall.
                            .padding(.vertical, 5)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(isOn ? .isSelected : [])
                }
            }
            .padding(.horizontal, 16)
        }
        .padding(.horizontal, -16)
    }
}

struct SectionHeader: View {
    let title: String

    var body: some View {
        Text(title)
            .font(.title3.weight(.bold))
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityAddTraits(.isHeader)
    }
}

/// Shown above a filtered list: how many stores, and a way back to all of them.
struct ResultsHeader: View {
    let count: Int
    let onClear: () -> Void

    var body: some View {
        HStack {
            SectionHeader(title: count == 1 ? "1 store" : "\(count) stores")
            Button("Clear filters", action: onClear)
                .font(.subheadline.weight(.semibold))
        }
    }
}

struct NoMatchesCard: View {
    let onClear: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("No stores match")
                .font(.headline)
            Text("Try another search, or turn off some filters.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Button("Clear filters", action: onClear)
                .font(.subheadline.weight(.semibold))
                .padding(.top, 4)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}

struct LoadErrorView: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        ContentUnavailableView {
            Label("Couldn't load stores", systemImage: "wifi.exclamationmark")
        } description: {
            Text(message)
        } actions: {
            Button("Retry", action: onRetry)
                .buttonStyle(.borderedProminent)
        }
    }
}

/// Grey shapes of the content, gently pulsing, while the store feed loads.
struct LoadingPlaceholder: View {
    @State private var dimmed = false

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 8) {
                ForEach(0..<4, id: \.self) { _ in Capsule().frame(width: 76, height: 34) }
            }
            RoundedRectangle(cornerRadius: 24, style: .continuous).frame(height: 190)
            RoundedRectangle(cornerRadius: 24, style: .continuous).frame(height: 240)
        }
        .foregroundStyle(Color(.systemFill))
        .opacity(dimmed ? 0.5 : 1)
        .animation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true), value: dimmed)
        .onAppear { dimmed = true }
        .accessibilityElement()
        .accessibilityLabel("Loading stores")
    }
}
