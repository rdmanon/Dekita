import SwiftUI
import SharedLogic

/// Where a Discover tap leads. Both the Discover and the Search tab register these destinations.
enum DiscoverRoute: Hashable {
    case favorites
    case history
    case store(id: String, name: String)
    case cart
    /// "See All" on Recommended. Needs the Discover model, so only the Discover tab can open it.
    case recommended
}

/// Opens the screen for a route. Attach with `.navigationDestination(for: DiscoverRoute.self)`.
struct DiscoverDestination: View {
    let route: DiscoverRoute
    /// The Discover tab's model, so "See All" keeps the chip and mode that are on. Nil on the Search tab.
    var discover: DiscoverModel? = nil

    var body: some View {
        switch route {
        case .recommended:
            if let discover { RecommendedListView(model: discover) }
        case .favorites: FavoritesView()
        case .history: HistoryView()
        case let .store(id, name): StoreScreen(storeId: id, name: name)
        case .cart: CartReviewView()
        }
    }
}

/// The In-store / Pickup selector, shown before any store. "All" clears it.
struct ModePicker: View {
    let mode: FulfillmentMode?
    let onSelect: (FulfillmentMode?) -> Void

    var body: some View {
        SegmentedCapsule(
            label: "How you'll be served",
            options: [
                ("All", Optional<FulfillmentMode>.none),
                ("In-store", .some(FulfillmentMode.inStore)),
                ("Pickup", .some(FulfillmentMode.pickup)),
            ],
            selection: mode,
            onSelect: onSelect
        )
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

/// Capsule chips. Selected ones turn dark, as in the design. Discover allows several; the store tabs pass one.
struct CategoryChips: View {
    let categories: [String]
    let selected: Set<String>
    var horizontalPadding: CGFloat = 14
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
                            .padding(.horizontal, horizontalPadding)
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
            Text("No stores match your filters.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Button("Clear filters", action: onClear)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Palette.link)
                .frame(minHeight: 44, alignment: .leading)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.top, 16)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}

/// "See All" on Recommended: every recommended store as a list. It reads the Discover model, so the mode
/// and category chip that were on when it opened still apply.
struct RecommendedListView: View {
    @ObservedObject var model: DiscoverModel

    var body: some View {
        ScrollView {
            if model.state.recommended.isEmpty {
                NoMatchesCard { model.viewModel.clearFilters() }
            } else {
                StoreListCard(matches: model.state.recommended)
            }
        }
        .contentMargins(16, for: .scrollContent)
        .background(Palette.screen)
        .navigationTitle("Recommended")
        .navigationBarTitleDisplayMode(.inline)
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

/// The latest paid order that isn't closed yet, with its live step. Tapping it opens the ticket (ORDER-01).
struct ActiveOrderBanner: View {
    let order: Order
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: 10) {
                Image(systemName: "receipt")
                    .font(.system(size: 17, weight: .semibold))
                    .frame(width: 36, height: 36)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Order #\(order.number) · \(order.currentStep?.label ?? "")")
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    Text(order.storeName)
                        .font(.caption)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Text("View").font(.subheadline.weight(.semibold))
            }
            .foregroundStyle(Palette.orderNumber.foreground)
            .padding(12)
            .background(Palette.orderNumber.background, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Order number \(order.number), \(order.currentStep?.label ?? ""), \(order.storeName)")
        .accessibilityAddTraits(.isButton)
    }
}

/// An in-store draft waiting for check-in. Tapping it goes back to the "On your way" screen.
struct DraftBanner: View {
    let draft: Order
    let onResume: () -> Void

    var body: some View {
        Button(action: onResume) {
            HStack(spacing: 10) {
                Image(systemName: "fork.knife")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.tint)
                    .frame(width: 36, height: 36)
                    .background(Palette.tileIcon, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                VStack(alignment: .leading, spacing: 1) {
                    Text("Your Eat-In order is waiting").font(.subheadline.weight(.semibold))
                    Text("\(draft.storeName) · \(draft.itemsLabel)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Text("Resume")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.tint)
            }
            .padding(12)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }
}
