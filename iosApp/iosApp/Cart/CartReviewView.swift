import SwiftUI
import SharedLogic

/// Cart Review (CART-01), laid out as the iOS Cart artboard: the store card with its live queue, the order mode,
/// editable lines, totals and one checkout button. The button follows the store's business type: single-mode
/// stores pay now; stores with both offer Eat-In ("Head to Store", a DRAFT order) or Takeout ("Pay Now").
/// A guest's checkout opens sign-in as a sheet over this screen, so finishing it lands back here unchanged.
/// "Pay now" opens the payment sheet (PAY-01); a fresh one each time, so every checkout gets its own id.
struct CartReviewView: View {
    @EnvironmentObject private var flow: AuthFlow
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openCheckIn) private var openCheckIn
    @Environment(\.openOrderTicket) private var openOrderTicket
    @StateObject private var model = CartReviewModel()
    @State private var showPayment = false
    @State private var paidOrderId: String?

    var body: some View {
        let state = model.state
        Group {
            if let cart = state.cart {
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        if let store = state.store {
                            CartStoreCard(store: store)
                        }
                        if let mode = state.mode {
                            CartModeSection(
                                mode: mode,
                                canChoose: state.canChooseMode,
                                action: state.action,
                                onSelect: { model.viewModel.selectMode(mode: $0) }
                            )
                        }
                        VStack(spacing: 0) {
                            ForEach(Array(state.lines.enumerated()), id: \.element.line.lineId) { index, lineUi in
                                CartLineRow(
                                    lineUi: lineUi,
                                    fallbackArtKey: state.store?.artKey ?? "",
                                    onEdit: { model.viewModel.editLine(lineId: lineUi.line.lineId) },
                                    onDecrease: { model.viewModel.decrease(lineId: lineUi.line.lineId) },
                                    onIncrease: { model.viewModel.increase(lineId: lineUi.line.lineId) },
                                    onRemove: { model.viewModel.remove(lineId: lineUi.line.lineId) }
                                )
                                // Starts after the art, as in the artboard.
                                Divider().padding(.leading, index < state.lines.count - 1 ? 80 : 16)
                            }
                            Button { dismiss() } label: {
                                Label("Add more items", systemImage: "plus")
                                    .font(.callout.weight(.semibold))
                                    .foregroundStyle(Palette.link)
                                    .frame(maxWidth: .infinity, minHeight: 50, alignment: .leading)
                                    .padding(.horizontal, 16)
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                        .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                        CartTotals(cart: cart)
                    }
                    .padding(16)
                }
                .safeAreaInset(edge: .bottom) {
                    VStack(spacing: 0) {
                        if let block = state.block {
                            Text(block.message(storeName: state.store?.name ?? "The store"))
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(Palette.error)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, 16)
                                .padding(.top, 10)
                        }
                        BottomAction(action: { model.viewModel.checkout() }) {
                            CapsuleLabel(
                                title: state.action == CheckoutAction.headToStore ? Copy.headToStore : Copy.payNow,
                                systemImage: state.action == CheckoutAction.headToStore ? "mappin.and.ellipse" : "creditcard"
                            )
                        }
                        .disabled(!state.canCheckout)
                    }
                }
            } else {
                ContentUnavailableView {
                    Label("Your cart is empty", systemImage: "cart")
                } description: {
                    Text("Add items from a store's menu.")
                } actions: {
                    Button("Browse menu") { dismiss() }
                }
            }
        }
        .background(Palette.screen)
        .navigationTitle("Cart")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if AppBuild.isDebug {
                ToolbarItem(placement: .topBarTrailing) { DebugPanelButton() }
            }
        }
        .sheet(isPresented: editPresented) {
            if let sheet = model.state.editSheet {
                ItemSheetContent(
                    sheet: sheet,
                    storeArtKey: state.store?.artKey ?? "",
                    actionTitle: Copy.update(total: sheet.draft.totalPrice),
                    onToggle: { model.viewModel.toggleEditOption(groupId: $0, optionId: $1) },
                    onQuantity: { model.viewModel.setEditQuantity(quantity: $0) },
                    onAction: { _ = model.viewModel.saveEdit() },
                    onClose: { model.viewModel.closeEdit() }
                )
                .presentationDetents([.medium, .large])
            }
        }
        .onReceive(model.events) { event in
            if event is CartReviewEventRequireSignIn {
                flow.requestSignIn(from: .checkout)
            } else if let saved = event as? CartReviewEventDraftSaved {
                // The cart is now a draft order: close it, then check in over the tabs (CHECKIN-01).
                flow.popTop()
                openCheckIn(saved.orderId)
            } else if event is CartReviewEventOpenPayment {
                showPayment = true
            }
        }
        .sheet(isPresented: $showPayment, onDismiss: openPaidTicket) {
            CartPaymentSheet(
                onPaid: { orderId in
                    paidOrderId = orderId
                    showPayment = false
                },
                onClosed: { showPayment = false }
            )
        }
    }

    /// Once the sheet is gone: the cart is paid and empty, so close it and show the ticket over the tabs (PAY-01).
    private func openPaidTicket() {
        guard let orderId = paidOrderId else { return }
        paidOrderId = nil
        flow.popTop()
        openOrderTicket(orderId)
    }

    private var editPresented: Binding<Bool> {
        Binding(
            get: { model.state.editSheet != nil },
            set: { if !$0 { model.viewModel.closeEdit() } }
        )
    }
}

/// The payment sheet for the cart. It owns its model, so closing the sheet ends that checkout.
/// Leaving without paying (PAY-02) closes it back onto Cart Review with the cart as it was. Never Home.
private struct CartPaymentSheet: View {
    let onPaid: (String) -> Void
    let onClosed: () -> Void
    @StateObject private var model = PaymentModel()

    var body: some View {
        PaymentSheetView(model: model)
            .onReceive(model.events) { event in
                if let paid = event as? PaymentEventPaid {
                    onPaid(paid.orderId)
                } else if event is PaymentEventClosed {
                    onClosed()
                }
            }
    }
}

/// "How are you eating?" and the Eat-In / Takeout control for stores with both modes; a fixed pill otherwise.
/// The line under it says when the customer pays.
private struct CartModeSection: View {
    let mode: FulfillmentMode
    let canChoose: Bool
    let action: CheckoutAction
    let onSelect: (FulfillmentMode) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if canChoose {
                Text("How are you eating?")
                    .font(.headline)
                    .padding(.horizontal, 4)
                    .padding(.top, 4)
                    .accessibilityAddTraits(.isHeader)
                SegmentedCapsule(
                    label: "Order mode",
                    options: [
                        (FulfillmentMode.inStore.choiceLabel, FulfillmentMode.inStore),
                        (FulfillmentMode.pickup.choiceLabel, FulfillmentMode.pickup),
                    ],
                    selection: mode,
                    onSelect: onSelect
                )
            } else {
                let colors = Palette.badge(mode == FulfillmentMode.pickup ? .pickupOnly : .inStoreOnly)
                Text(mode.choiceLabel)
                    .font(.subheadline.weight(.semibold))
                    .padding(.horizontal, 12)
                    .frame(minHeight: 28)
                    .foregroundStyle(colors.foreground)
                    .background(colors.background, in: Capsule())
            }
            Label(LabelsKt.checkoutInfo(action: action, mode: mode), systemImage: "info.circle")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .padding(.horizontal, 4)
        }
    }
}

/// The store at the top of the cart: art, name, the long queue line and the Live mark. The queue line
/// comes from the shared label, so it reads like the Android card.
private struct CartStoreCard: View {
    let store: Store

    var body: some View {
        HStack(spacing: 12) {
            StoreArtView(store: store, cornerRadius: 12)
                .frame(width: 48, height: 48)
            VStack(alignment: .leading, spacing: 2) {
                Text(store.name).font(.headline)
                let queue = store.queueDetailLabel()
                if !queue.isEmpty {
                    Text(queue).font(.footnote).foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if store.isAcceptingOrders, store.queue != nil {
                HStack(spacing: 6) {
                    Circle().fill(.green).frame(width: 8, height: 8)
                    Text("LIVE").font(.caption.weight(.bold)).foregroundStyle(Palette.badge(.openForLunch).foreground)
                }
            }
        }
        .padding(14)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

/// One line: art, name, choices and Edit, with the price over the quantity stepper. A line that can't be
/// ordered right now (sold out, or not offered in this mode) is marked and offers Remove instead of the stepper.
/// At accessibility text sizes the price and stepper drop under the name, so nothing squeezes.
private struct CartLineRow: View {
    let lineUi: CartLineUi
    let fallbackArtKey: String
    let onEdit: () -> Void
    let onDecrease: () -> Void
    let onIncrease: () -> Void
    let onRemove: () -> Void
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let line = lineUi.line
        let layout = typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading, spacing: 10)) : AnyLayout(HStackLayout(spacing: 12))
        layout {
            HStack(spacing: 12) {
                StoreArtView(artKey: line.item.artKey ?? fallbackArtKey, cornerRadius: 14)
                    .frame(width: 52, height: 52)
                VStack(alignment: .leading, spacing: 2) {
                    Text(line.item.name).font(.callout.weight(.semibold))
                    if !line.optionsLabel.isEmpty {
                        Text(line.optionsLabel).font(.footnote).foregroundStyle(.secondary)
                    }
                    if let reason = lineUi.unavailableReason {
                        Text(reason)
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 8)
                            .frame(minHeight: 22)
                            .foregroundStyle(Palette.badge(.full).foreground)
                            .background(Palette.badge(.full).background, in: Capsule())
                    }
                    Button("Edit", action: onEdit)
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Palette.link)
                        .buttonStyle(.borderless)
                        .frame(minHeight: 28)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            VStack(alignment: typeSize.isAccessibilitySize ? .leading : .trailing, spacing: 8) {
                Text(line.lineTotal.formatted()).font(.subheadline.weight(.semibold))
                if lineUi.isAvailable {
                    QuantityStepperView(
                        quantity: Int(line.quantity),
                        itemName: line.item.name,
                        onDecrease: onDecrease,
                        onIncrease: onIncrease
                    )
                } else {
                    Button("Remove", role: .destructive, action: onRemove)
                        .font(.subheadline.weight(.semibold))
                        .buttonStyle(.borderless)
                        .frame(minHeight: 32)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(lineUi.isAvailable ? Color.clear : Palette.badge(.full).background.opacity(0.4))
    }
}

/// − n + with a bin in place of − at one, so the last step down removes the line.
private struct QuantityStepperView: View {
    let quantity: Int
    let itemName: String
    let onDecrease: () -> Void
    let onIncrease: () -> Void

    var body: some View {
        let canIncrease = quantity < Int(ItemDraftKt.MAX_QUANTITY)
        HStack(spacing: 0) {
            Button(action: onDecrease) {
                Image(systemName: quantity <= 1 ? "trash" : "minus")
                    .frame(width: 34, height: 32)
            }
            .accessibilityLabel(quantity <= 1 ? "Remove \(itemName)" : "Decrease \(itemName)")
            Text("\(quantity)")
                .font(.subheadline.weight(.semibold).monospacedDigit())
                .frame(minWidth: 16)
                .accessibilityLabel("Quantity \(quantity)")
            Button(action: onIncrease) {
                Image(systemName: "plus")
                    .frame(width: 34, height: 32)
            }
            .disabled(!canIncrease)
            .accessibilityLabel("Increase \(itemName)")
        }
        .buttonStyle(.borderless)
        .tint(.primary)
        .background(Palette.screen, in: Capsule())
    }
}

private struct CartTotals: View {
    let cart: Cart

    var body: some View {
        VStack(spacing: 8) {
            HStack {
                Text("Subtotal").foregroundStyle(.secondary)
                Spacer()
                Text(cart.subtotal.formatted())
            }
            .font(.subheadline)
            HStack(alignment: .firstTextBaseline) {
                Text("Total").font(.headline.weight(.bold))
                Text("tax incl.").font(.footnote).foregroundStyle(.secondary)
                Spacer()
                Text(cart.total.formatted()).font(.headline.weight(.bold))
            }
        }
        .padding(16)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}
