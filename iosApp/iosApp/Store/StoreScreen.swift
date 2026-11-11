import SwiftUI
import SharedUI

/// The store screen: header, live queue, the paused/full banner, then the menu.
/// SwiftUI draws the navigation bar, with a Liquid Glass favorite button on iOS 26.
/// The menu rows are still the SETUP-01 placeholder until MENU-01; tapping one opens the item sheet.
struct StoreScreen: View {
    let storeId: String
    let name: String
    @StateObject private var model: StoreDetailModel
    @StateObject private var favorite: FavoriteStoreModel
    @State private var sheet: ItemSheetTarget?

    init(storeId: String, name: String) {
        self.storeId = storeId
        self.name = name
        _model = StateObject(wrappedValue: StoreDetailModel(storeId: storeId))
        _favorite = StateObject(wrappedValue: FavoriteStoreModel(storeId: storeId))
    }

    var body: some View {
        Group {
            if let store = model.state.store {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        StoreHeaderView(store: store)
                        LiveQueueCounterView(store: store)
                        if !store.isAcceptingOrders {
                            NotAcceptingOrdersBanner()
                        }
                        menu
                    }
                    .padding(16)
                }
            } else {
                ProgressView("Loading store…")
            }
        }
        .background(Palette.screen)
        .navigationTitle(name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { favorite.toggle() } label: {
                    Image(systemName: favorite.isFavorite ? "heart.fill" : "heart")
                }
                .accessibilityLabel(favorite.isFavorite ? "Remove from favorites" : "Add to favorites")
            }
        }
        .safeAreaInset(edge: .bottom) { viewCartButton }
        .sheet(item: $sheet) { ItemSheetView(target: $0) }
    }

    /// Shown once the cart has items from this store (CART-01).
    @ViewBuilder
    private var viewCartButton: some View {
        let state = model.state
        if state.cartItemCount > 0 {
            NavigationLink(value: DiscoverRoute.cart) {
                Label("View cart (\(state.cartItemCount)) · \(state.cartTotal.formatted())", systemImage: "bag")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 44)
            }
            .modifier(ProminentGlassButton())
            .padding(.horizontal, 16)
            .padding(.bottom, 8)
        }
    }

    @ViewBuilder
    private var menu: some View {
        if let menu = model.state.loadedMenu {
            ForEach(menu.categories, id: \.id) { category in
                Text(category.name)
                    .font(.title3.weight(.bold))
                    .padding(.top, 8)
                VStack(spacing: 0) {
                    let items = menu.itemsIn(categoryId: category.id)
                    ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                        // Items open the customize sheet (CART-01); sold-out items can't be added.
                        Button {
                            sheet = ItemSheetTarget(storeId: storeId, itemId: item.id, editingLineId: nil)
                        } label: {
                            MenuItemRowView(item: item)
                        }
                        .buttonStyle(.plain)
                        .disabled(item.isSoldOut)
                        .accessibilityHint(item.isSoldOut ? "" : "Opens options and Add to Cart")
                        if index < items.count - 1 {
                            Divider().padding(.leading, 16)
                        }
                    }
                }
                .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            }
        } else if let error = model.state.menuError {
            Text(error).foregroundStyle(.secondary)
        } else {
            ProgressView("Loading menu…").frame(maxWidth: .infinity)
        }
    }
}

/// Photo, name, category and walking time, then the status badge and opening hours.
private struct StoreHeaderView: View {
    let store: Store

    var body: some View {
        HStack(spacing: 16) {
            StoreArtView(store: store, cornerRadius: 18)
                .frame(width: 72, height: 72)
            VStack(alignment: .leading, spacing: 6) {
                Text(store.name)
                    .font(.title2.weight(.bold))
                    .lineLimit(2)
                Text(detail)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                HStack(spacing: 8) {
                    StatusBadgeView(badge: store.badge)
                    if store.isAcceptingOrders, let hours = store.openLabel, hours != store.badge.label {
                        Text(hours).font(.footnote).foregroundStyle(.secondary).lineLimit(1)
                    }
                }
            }
        }
        .accessibilityElement(children: .combine)
    }

    private var detail: String {
        var parts = [store.category, "\(store.walkMinutes) min walk"]
        if let modes = store.businessType.modesLabel { parts.append(modes) }
        return parts.joined(separator: " · ")
    }
}

/// The store's live queue as a card: headline, a Busy badge at the high-queue threshold, wait and Live dot.
/// Shows nothing when the store has no queue (paused). The cart reuses it.
struct LiveQueueCounterView: View {
    let store: Store

    var body: some View {
        if let queue = store.queue {
            HStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 8) {
                        Text(queue.headline()).font(.headline)
                        if queue.isHigh {
                            let busy = Palette.badge(.full)
                            Text("Busy")
                                .font(.caption.weight(.semibold))
                                .padding(.horizontal, 8)
                                .frame(minHeight: 22)
                                .foregroundStyle(busy.foreground)
                                .background(busy.background, in: Capsule())
                        }
                    }
                    Text(store.queueDetail())
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if store.isAcceptingOrders {
                    HStack(spacing: 6) {
                        Circle().fill(.green).frame(width: 8, height: 8)
                        Text("LIVE").font(.caption.weight(.bold)).foregroundStyle(Palette.badge(.openForLunch).foreground)
                    }
                }
            }
            .padding(14)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .accessibilityElement(children: .combine)
            .animation(.easeInOut, value: queue.ordersAhead)
        }
    }
}

private struct NotAcceptingOrdersBanner: View {
    var body: some View {
        let colors = Palette.badge(.paused)
        Text(LabelsKt.NOT_ACCEPTING_ORDERS_MESSAGE)
            .font(.subheadline.weight(.medium))
            .foregroundStyle(colors.foreground)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(colors.background, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}

/// A placeholder menu row: name, description, price and options, with a sold-out or mode badge.
private struct MenuItemRowView: View {
    let item: MenuItem

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(item.name).font(.body.weight(.semibold))
                Text(item.description_)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                Text(item.basePrice.formatted()).font(.subheadline.weight(.semibold))
                if let options = item.optionsSummary() {
                    Text(options).font(.caption).foregroundStyle(.secondary)
                }
            }
            .opacity(item.isSoldOut ? 0.45 : 1)
            .frame(maxWidth: .infinity, alignment: .leading)
            if let badge {
                Text(badge.text)
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 8)
                    .frame(minHeight: 22)
                    .foregroundStyle(badge.colors.foreground)
                    .background(badge.colors.background, in: Capsule())
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }

    private var badge: (text: String, colors: Palette.BadgeColors)? {
        if item.isSoldOut { return ("Sold out", Palette.badge(.paused)) }
        if item.availableModes == [FulfillmentMode.inStore] { return ("In-store only", Palette.badge(.inStoreOnly)) }
        if item.availableModes == [FulfillmentMode.pickup] { return ("Pickup only", Palette.badge(.pickupOnly)) }
        return nil
    }
}
