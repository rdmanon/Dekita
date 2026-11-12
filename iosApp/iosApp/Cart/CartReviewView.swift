import SwiftUI
import SharedLogic

/// Cart Review (CART-01), laid out as the iOS Cart artboard: the store and its live queue, the order mode,
/// editable lines, totals and one checkout button. The button follows the store's business type: single-mode
/// stores pay now; stores with both offer Eat-In ("Head to store", a DRAFT order) or Takeout ("Pay now").
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
                            Text(store.name).font(.title3.weight(.bold)).padding(.horizontal, 4)
                            LiveQueueCounterView(store: store)
                        }
                        if let mode = state.mode {
                            CartModeSection(
                                mode: mode,
                                canChoose: state.canChooseMode,
                                action: state.action,
                                onSelect: { model.viewModel.selectMode(mode: $0) }
                            )
                        }
                        Text("Your order").font(.headline).padding(.horizontal, 4)
                        VStack(spacing: 0) {
                            ForEach(Array(state.lines.enumerated()), id: \.element.line.lineId) { index, lineUi in
                                CartLineRow(
                                    lineUi: lineUi,
                                    onEdit: { model.viewModel.editLine(lineId: lineUi.line.lineId) },
                                    onDecrease: { model.viewModel.decrease(lineId: lineUi.line.lineId) },
                                    onIncrease: { model.viewModel.increase(lineId: lineUi.line.lineId) },
                                    onRemove: { model.viewModel.remove(lineId: lineUi.line.lineId) }
                                )
                                if index < state.lines.count - 1 {
                                    Divider().padding(.leading, 16)
                                }
                            }
                        }
                        .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                        Button { dismiss() } label: {
                            Label("Add more items", systemImage: "plus")
                                .font(.body.weight(.semibold))
                                .frame(maxWidth: .infinity, minHeight: 44)
                        }
                        .tint(Palette.action)
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
                                title: state.action.label,
                                systemImage: state.action == CheckoutAction.headToStore ? "figure.walk" : "creditcard"
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
                    actionTitle: LabelsKt.updateButtonLabel(total: sheet.draft.totalPrice),
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
            CartPaymentSheet { orderId in
                paidOrderId = orderId
                showPayment = false
            }
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
private struct CartPaymentSheet: View {
    let onPaid: (String) -> Void
    @StateObject private var model = PaymentModel()

    var body: some View {
        PaymentSheetView(model: model)
            .onReceive(model.events) { event in
                if let paid = event as? PaymentEventPaid { onPaid(paid.orderId) }
            }
    }
}

/// Eat-In / Takeout as a segmented control for stores with both modes; a fixed pill otherwise.
/// The line under it says when the customer pays.
private struct CartModeSection: View {
    let mode: FulfillmentMode
    let canChoose: Bool
    let action: CheckoutAction
    let onSelect: (FulfillmentMode) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if canChoose {
                Picker("Order mode", selection: Binding(get: { mode }, set: { onSelect($0) })) {
                    Text(FulfillmentMode.inStore.choiceLabel).tag(FulfillmentMode.inStore)
                    Text(FulfillmentMode.pickup.choiceLabel).tag(FulfillmentMode.pickup)
                }
                .pickerStyle(.segmented)
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
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}

/// One line: name, choices, price, Edit and the quantity stepper. A line that can't be ordered right now
/// (sold out, or not offered in this mode) is marked and offers Remove instead of the stepper.
private struct CartLineRow: View {
    let lineUi: CartLineUi
    let onEdit: () -> Void
    let onDecrease: () -> Void
    let onIncrease: () -> Void
    let onRemove: () -> Void

    var body: some View {
        let line = lineUi.line
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(line.item.name).font(.body.weight(.semibold))
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
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Text(line.lineTotal.formatted()).font(.subheadline.weight(.semibold))
            }
            .accessibilityElement(children: .combine)
            HStack {
                Button("Edit", action: onEdit)
                    .font(.subheadline.weight(.semibold))
                    .tint(Palette.action)
                    .buttonStyle(.borderless)
                Spacer()
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
        HStack(spacing: 4) {
            Button(action: onDecrease) {
                Image(systemName: quantity <= 1 ? "trash" : "minus")
                    .frame(width: 36, height: 36)
            }
            .accessibilityLabel(quantity <= 1 ? "Remove \(itemName)" : "Decrease \(itemName)")
            Text("\(quantity)")
                .font(.body.weight(.semibold).monospacedDigit())
                .frame(minWidth: 24)
                .accessibilityLabel("Quantity \(quantity)")
            Button(action: onIncrease) {
                Image(systemName: "plus")
                    .frame(width: 36, height: 36)
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
                Text("Total").font(.headline)
                Text("tax incl.").font(.footnote).foregroundStyle(.secondary)
                Spacer()
                Text(cart.total.formatted()).font(.headline)
            }
        }
        .padding(16)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}
