import SwiftUI
import SharedUI

/// Cart Review, the confirm-or-change loop before checkout: the store's live queue, Eat-In / Takeout for
/// stores with both modes, lines with Edit, quantity and remove, totals, and one button whose action
/// depends on the store and mode. Failed or cancelled payments come back here.
struct CartReviewView: View {
    @StateObject private var model = CartReviewModel()
    @EnvironmentObject private var navigation: NavigationModel
    @Environment(\.dismiss) private var dismiss
    @State private var sheet: ItemSheetTarget?

    var body: some View {
        let state = model.state
        Group {
            if state.isEmpty {
                ContentUnavailableView {
                    Label("Your cart is empty", systemImage: "bag")
                } description: {
                    Text("Add something from the menu and it will show up here.")
                } actions: {
                    // The cart is opened from its store's screen, so the menu is one step back.
                    Button("Browse menu") { dismiss() }
                        .buttonStyle(.bordered)
                }
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        if let store = state.store { QueueCard(store: store) }
                        if let mode = state.mode { ModeSection(state: state, mode: mode, viewModel: model.viewModel) }
                        linesCard(state)
                        TotalsCard(subtotal: state.subtotal.formatted(), total: state.total.formatted())
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                }
                .safeAreaInset(edge: .bottom) { checkoutBar(state) }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Palette.screen)
        .navigationTitle("Cart")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $sheet) { ItemSheetView(target: $0) }
    }

    private func linesCard(_ state: CartReviewUiState) -> some View {
        VStack(spacing: 0) {
            ForEach(state.lines, id: \.line.lineId) { lineUi in
                CartLineRow(
                    lineUi: lineUi,
                    store: state.store,
                    maxQuantity: model.maxQuantity,
                    onEdit: {
                        sheet = ItemSheetTarget(
                            storeId: lineUi.line.item.storeId,
                            itemId: lineUi.line.item.id,
                            editingLineId: lineUi.line.lineId
                        )
                    },
                    onDecrease: { model.viewModel.decrease(lineId: lineUi.line.lineId) },
                    onIncrease: { model.viewModel.increase(lineId: lineUi.line.lineId) },
                    onRemove: { model.viewModel.remove(lineId: lineUi.line.lineId) }
                )
                Divider().padding(.leading, 80)
            }
            Button { dismiss() } label: {
                Label("Add more items", systemImage: "plus")
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 50, alignment: .leading)
                    .padding(.horizontal, 16)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .foregroundStyle(.tint)
        }
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }

    private func checkoutBar(_ state: CartReviewUiState) -> some View {
        VStack(spacing: 8) {
            if let block = state.block {
                Text(block.message(storeName: state.store?.name ?? "This store"))
                    .font(.footnote)
                    .foregroundStyle(block == CheckoutBlock.checking ? Color.secondary : Color.red)
                    .multilineTextAlignment(.center)
            }
            Button { checkout(state) } label: {
                Label(
                    state.action.label,
                    systemImage: state.action == CheckoutAction.headToStore ? "mappin.and.ellipse" : "creditcard"
                )
                .font(.headline)
                .frame(maxWidth: .infinity)
                .frame(minHeight: 40)
            }
            .modifier(ProminentGlassButton())
            .disabled(!state.canCheckout)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }

    private func checkout(_ state: CartReviewUiState) {
        if state.action == CheckoutAction.headToStore {
            guard let draft = model.viewModel.headToStore() else { return }
            // The cart became a draft order, so Back from check-in skips the empty cart.
            navigation.replaceTop(with: .checkIn(orderNumber: Int(draft.number), storeName: draft.storeName))
        } else if model.viewModel.canStartPayment() {
            navigation.path.append(DiscoverRoute.payment)
        }
    }
}

/// The store and its live queue, as on the store screen.
private struct QueueCard: View {
    let store: Store

    var body: some View {
        HStack(spacing: 12) {
            StoreArtView(store: store, cornerRadius: 12)
                .frame(width: 48, height: 48)
            VStack(alignment: .leading, spacing: 2) {
                Text(store.name).font(.headline).lineLimit(1)
                Text(store.queueSummary()).font(.footnote).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if store.isAcceptingOrders {
                HStack(spacing: 6) {
                    Circle().fill(Color.green).frame(width: 8, height: 8)
                    Text("LIVE").font(.caption.weight(.bold)).tracking(0.5)
                }
                .foregroundStyle(Palette.badge(StoreBadge.openForLunch).foreground)
                .accessibilityLabel("Updates live")
            }
        }
        .padding(14)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

/// Stores with both modes get the Eat-In / Takeout control; single-mode stores show their mode, fixed.
private struct ModeSection: View {
    let state: CartReviewUiState
    let mode: FulfillmentMode
    let viewModel: CartReviewViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if state.canChooseMode {
                Text("How are you eating?")
                    .font(.headline)
                    .padding(.horizontal, 4)
                    .accessibilityAddTraits(.isHeader)
                Picker("How are you eating?", selection: Binding(
                    get: { mode == FulfillmentMode.inStore },
                    set: { viewModel.selectMode(mode: $0 ? FulfillmentMode.inStore : FulfillmentMode.pickup) }
                )) {
                    Text(FulfillmentMode.inStore.choiceLabel).tag(true)
                    Text(FulfillmentMode.pickup.choiceLabel).tag(false)
                }
                .pickerStyle(.segmented)
            } else {
                let colors = Palette.badge(mode == FulfillmentMode.pickup ? StoreBadge.pickupOnly : StoreBadge.inStoreOnly)
                HStack {
                    Text("How you'll be served").font(.headline)
                    Spacer()
                    Text(mode.label)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(colors.foreground)
                        .padding(.horizontal, 10)
                        .frame(minHeight: 26)
                        .background(colors.background, in: Capsule())
                }
                .padding(.horizontal, 4)
            }
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "info.circle").foregroundStyle(.secondary)
                Text(LabelsKt.checkoutInfo(action: state.action, mode: mode))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal, 4)
        }
    }
}

private struct CartLineRow: View {
    let lineUi: CartLineUi
    let store: Store?
    let maxQuantity: Int
    let onEdit: () -> Void
    let onDecrease: () -> Void
    let onIncrease: () -> Void
    let onRemove: () -> Void

    var body: some View {
        let line = lineUi.line
        HStack(alignment: .center, spacing: 12) {
            if let store {
                StoreArtView(store: store, cornerRadius: 14)
                    .frame(width: 52, height: 52)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(line.item.name).font(.body.weight(.semibold)).lineLimit(2)
                if !line.optionsLabel.isEmpty {
                    Text(line.optionsLabel).font(.footnote).foregroundStyle(.secondary)
                }
                if lineUi.isSoldOut {
                    let colors = Palette.badge(StoreBadge.full)
                    Text("Sold out")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(colors.foreground)
                        .padding(.horizontal, 8)
                        .frame(minHeight: 22)
                        .background(colors.background, in: Capsule())
                        .padding(.vertical, 2)
                }
                HStack(spacing: 16) {
                    Button("Edit", action: onEdit)
                    if lineUi.isSoldOut {
                        Button("Remove", role: .destructive, action: onRemove)
                    }
                }
                .font(.footnote.weight(.semibold))
                .buttonStyle(.borderless)
                .frame(minHeight: 32)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            VStack(alignment: .trailing, spacing: 8) {
                Text(line.lineTotal.formatted()).font(.subheadline.weight(.semibold))
                QuantityStepperView(
                    quantity: Int(line.quantity),
                    itemName: line.item.name,
                    canIncrease: Int(line.quantity) < maxQuantity,
                    removesAtOne: true,
                    onDecrease: onDecrease,
                    onIncrease: onIncrease
                )
                .background(Color(.tertiarySystemFill), in: Capsule())
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(lineUi.isSoldOut ? Palette.badge(StoreBadge.full).background.opacity(0.4) : Color.clear)
    }
}

private struct TotalsCard: View {
    let subtotal: String
    let total: String

    var body: some View {
        VStack(spacing: 8) {
            HStack {
                Text("Subtotal").foregroundStyle(.secondary)
                Spacer()
                Text(subtotal)
            }
            .font(.subheadline)
            HStack(alignment: .firstTextBaseline) {
                Text("Total").font(.headline)
                Text("tax incl.").font(.footnote).foregroundStyle(.secondary)
                Spacer()
                Text(total).font(.headline)
            }
        }
        .padding(16)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}
