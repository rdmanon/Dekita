import SwiftUI
import SharedLogic

/// The store screen: header, live queue, the paused/full banner, then the menu.
/// SwiftUI draws the navigation bar, with a Liquid Glass favorite button on iOS 26.
/// Tapping an item opens its customize sheet; a floating cart button appears once the cart has items.
struct StoreScreen: View {
    let name: String
    @StateObject private var model: StoreDetailModel
    @StateObject private var favorite: FavoriteStoreModel
    @State private var toast: String?

    init(storeId: String, name: String) {
        self.name = name
        _model = StateObject(wrappedValue: StoreDetailModel(storeId: storeId))
        _favorite = StateObject(wrappedValue: FavoriteStoreModel(storeId: storeId))
    }

    var body: some View {
        Group {
            if let store = model.state.store {
                ScrollView {
                    VStack(spacing: 0) {
                        // The art runs under the transparent bar; the 34 pt overlap lets the sheet's corners show it.
                        StoreArtView(artKey: store.artKey, badge: store.badge, cornerRadius: 0)
                            .frame(height: 268 + Self.sheetRadius)
                        LazyVStack(alignment: .leading, spacing: 16) {
                            StoreHeaderView(store: store)
                            LiveQueueCounterView(store: store)
                            if !store.isAcceptingOrders {
                                NotAcceptingOrdersBanner()
                            }
                            menu(store)
                        }
                        .padding(.horizontal, 16)
                        .padding(.top, 22)
                        .padding(.bottom, 16)
                        .frame(maxWidth: .infinity)
                        .background(
                            Palette.screen,
                            in: UnevenRoundedRectangle(topLeadingRadius: Self.sheetRadius, topTrailingRadius: Self.sheetRadius, style: .continuous)
                        )
                        .padding(.top, -Self.sheetRadius)
                    }
                }
                // On the scroll view, not the art: inside it the content would still start below the bar.
                .ignoresSafeArea(edges: .top)
            } else {
                ProgressView("Loading store…")
            }
        }
        .background(Palette.screen)
        // The menu artboard's full-width cart capsule, once the cart has items.
        .safeAreaInset(edge: .bottom) {
            if let cart = model.state.cart {
                NavigationLink(value: DiscoverRoute.cart) {
                    HStack(spacing: 12) {
                        Text("\(cart.itemCount)")
                            .font(.subheadline.weight(.bold))
                            .frame(minWidth: 40, minHeight: 40)
                            .background(Color.black.opacity(0.2), in: Capsule())
                        Text("View Cart").frame(maxWidth: .infinity, alignment: .leading)
                        Text(cart.total.formatted())
                    }
                    .padding(.leading, 8)
                    .padding(.trailing, 22)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("View Cart, \(LabelsKt.itemCountLabel(count: cart.itemCount)), \(cart.total.formatted())")
                }
                .buttonStyle(CapsuleButtonStyle())
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
            }
        }
        .overlay(alignment: .top) { toastView }
        .sheet(isPresented: sheetPresented) {
            ItemSheet(model: model, store: model.state.store, onAdded: showToast)
                .presentationDetents([.medium, .large])
        }
        .navigationTitle(name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbar {
            // The title would sit on the photo; the store's name is in the content.
            ToolbarItem(placement: .principal) { Color.clear.accessibilityHidden(true) }
            if AppBuild.isDebug {
                ToolbarItem(placement: .topBarTrailing) { DebugPanelButton() }
            }
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { favorite.toggle() } label: {
                    Image(systemName: favorite.isFavorite ? "heart.fill" : "heart")
                        .foregroundStyle(favorite.isFavorite ? Palette.link : Color.primary)
                }
                .accessibilityLabel(favorite.isFavorite ? "Remove store from favorites" : "Add store to favorites")
                if let store = model.state.store {
                    ShareLink(item: store.shareText()) { Image(systemName: "square.and.arrow.up") }
                        .accessibilityLabel("Share store")
                }
            }
        }
    }

    private static let sheetRadius: CGFloat = 34

    @ViewBuilder
    private func menu(_ store: Store) -> some View {
        if model.state.loadedMenu != nil {
            CategoryChips(
                categories: model.state.tabs.map(\.name),
                selected: Set([model.state.currentTab?.name].compactMap { $0 }),
                horizontalPadding: 16
            ) { name in
                if let tab = model.state.tabs.first(where: { $0.name == name }) {
                    model.viewModel.selectTab(tabId: tab.id)
                }
            }
            VStack(spacing: 0) {
                if let empty = model.state.emptyTabMessage {
                    Text(empty)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16)
                }
                let items = model.state.visibleItems
                ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                    Button { model.viewModel.openItem(itemId: item.id) } label: {
                        MenuItemRowView(item: item, fallbackArtKey: store.artKey, canOrder: model.state.canOrder)
                    }
                    .buttonStyle(.plain)
                    .disabled(item.isSoldOut)
                    if index < items.count - 1 {
                        Divider().padding(.leading, 16)
                    }
                }
            }
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        } else if let error = model.state.menuError {
            Text(error).foregroundStyle(.secondary)
        } else {
            ProgressView("Loading menu…").frame(maxWidth: .infinity)
        }
    }

    private var sheetPresented: Binding<Bool> {
        Binding(
            get: { model.state.sheet != nil },
            set: { if !$0 { model.viewModel.closeItem() } }
        )
    }

    @ViewBuilder
    private var toastView: some View {
        if let toast {
            Text(toast)
                .font(.subheadline.weight(.semibold))
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(.thickMaterial, in: Capsule())
                .shadow(color: .black.opacity(0.15), radius: 8, y: 2)
                .padding(.top, 8)
                .transition(.move(edge: .top).combined(with: .opacity))
                .accessibilityAddTraits(.isStaticText)
        }
    }

    private func showToast(_ text: String) {
        withAnimation { toast = text }
        UIAccessibility.post(notification: .announcement, argument: text)
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            withAnimation { if toast == text { toast = nil } }
        }
    }
}

/// Name with the status pill beside it, then category, walking time and modes, then the opening hours.
private struct StoreHeaderView: View {
    let store: Store
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let layout = rowLayout(typeSize)
        VStack(alignment: .leading, spacing: 6) {
            layout {
                Text(store.name)
                    .font(.title.weight(.bold))
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                StatusBadgeView(badge: store.badge, minHeight: 24)
            }
            Text(detail)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            if store.isAcceptingOrders, let hours = store.openLabel, hours != store.badge.label {
                Text(hours).font(.footnote).foregroundStyle(.secondary)
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
/// Shows nothing when the store has no queue (paused).
struct LiveQueueCounterView: View {
    let store: Store
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        if let queue = store.queue {
            let layout = rowLayout(typeSize)
            layout {
                Image(systemName: "person.2")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Palette.link)
                    .frame(width: 40, height: 40)
                    .background(Palette.tileIcon, in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                    .accessibilityHidden(true)
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

/// A menu row, as the iOS-Menu artboard: name, description and price, then the item's art with the add
/// button on its corner. Sold-out items fade and carry a "Sold out" pill instead. The add button is dimmed while
/// the store isn't accepting orders. It is decorative: the row opens the sheet, which says why Add is off.
private struct MenuItemRowView: View {
    let item: MenuItem
    let fallbackArtKey: String
    let canOrder: Bool

    var body: some View {
        HStack(spacing: 14) {
            VStack(alignment: .leading, spacing: 3) {
                Text(item.name).font(.callout.weight(.semibold))
                Text(item.description_)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                Text(item.basePrice.formatted())
                    .font(.subheadline.weight(.semibold))
                    .padding(.top, 2)
                if let options = item.optionsSummary() {
                    Text(options).font(.caption).foregroundStyle(.secondary)
                }
                if let badge {
                    Text(badge.text)
                        .font(.caption.weight(.semibold))
                        .padding(.horizontal, 8)
                        .frame(minHeight: 22)
                        .foregroundStyle(badge.colors.foreground)
                        .background(badge.colors.background, in: Capsule())
                }
            }
            .opacity(item.isSoldOut ? 0.45 : 1)
            .frame(maxWidth: .infinity, alignment: .leading)
            StoreArtView(artKey: item.artKey ?? fallbackArtKey, cornerRadius: 16)
                .frame(width: 64, height: 64)
                .opacity(item.isSoldOut ? 0.45 : 1)
                .overlay(alignment: .bottomTrailing) {
                    if !item.isSoldOut {
                        Image(systemName: "plus")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Palette.link)
                            .frame(width: 28, height: 28)
                            .background(Palette.card, in: Circle())
                            .shadow(color: .black.opacity(0.18), radius: 4, y: 2)
                            .opacity(canOrder ? 1 : 0.35)
                            .offset(x: 6, y: 6)
                            .accessibilityHidden(true)
                    }
                }
                .overlay(alignment: .bottom) {
                    if item.isSoldOut {
                        Text("Sold out")
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 8)
                            .frame(minHeight: 22)
                            // Dark in both modes, like text over a photo.
                            .background(Color(light: 0x3A3A3C, dark: 0x3A3A3C), in: Capsule())
                            .offset(y: 8)
                    }
                }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .accessibilityElement(children: .combine)
    }

    /// Sold out is the pill on the art, so only the mode limits are badged here.
    private var badge: (text: String, colors: Palette.BadgeColors)? {
        if item.availableModes == [FulfillmentMode.inStore] { return ("In-store only", Palette.badge(.inStoreOnly)) }
        if item.availableModes == [FulfillmentMode.pickup] { return ("Pickup only", Palette.badge(.pickupOnly)) }
        return nil
    }
}
