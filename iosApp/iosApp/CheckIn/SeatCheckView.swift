import SwiftUI
import SharedLogic

/// Seat verification (SEAT-01), opened by Start Cooking after check-in. "Have you found a seat?" comes before
/// the kitchen starts: Yes opens 1-tap payment, and a full store or "Not yet" offers Switch to Takeout, Wait in
/// line or Cancel / Exit. None of them loses the order.
struct SeatCheckView: View {
    @StateObject private var model: SeatCheckModel
    let onWaitInLine: () -> Void
    let onSwitchedToTakeout: (_ storeId: String, _ storeName: String) -> Void
    let onExit: () -> Void
    let onViewOrders: () -> Void

    init(
        orderId: String,
        onWaitInLine: @escaping () -> Void,
        onSwitchedToTakeout: @escaping (_ storeId: String, _ storeName: String) -> Void,
        onExit: @escaping () -> Void,
        onViewOrders: @escaping () -> Void
    ) {
        _model = StateObject(wrappedValue: SeatCheckModel(orderId: orderId))
        self.onWaitInLine = onWaitInLine
        self.onSwitchedToTakeout = onSwitchedToTakeout
        self.onExit = onExit
        self.onViewOrders = onViewOrders
    }

    var body: some View {
        let state = model.state
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if state.isLoading {
                    ProgressView("Loading your order…").frame(maxWidth: .infinity).padding(.top, 32)
                } else if let number = state.sentOrderNumber {
                    SentContent(orderNumber: Int(truncating: number), onViewOrders: onViewOrders, onDone: onExit)
                } else if state.isUnavailable {
                    ContentUnavailableView(
                        "Nothing to seat-check",
                        systemImage: "fork.knife",
                        description: Text("This order isn't a checked-in draft any more. It may already be in the kitchen or back in your cart.")
                    )
                } else {
                    OrderHeader(state: state)
                    if state.step == SeatCheckStep.noSeat {
                        NoSeatContent(model: model)
                    } else {
                        QuestionContent(
                            onYes: { model.viewModel.answerSeat(hasSeat: true) },
                            onNotYet: { model.viewModel.answerSeat(hasSeat: false) }
                        )
                    }
                }
            }
            .padding(16)
        }
        .background(Palette.screen)
        .navigationTitle("Seat check")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: paymentPresented) {
            PaymentSheet(model: model)
                .presentationDetents([.medium, .large])
        }
        .confirmationDialog("Replace your cart?", isPresented: replacePresented, titleVisibility: .visible) {
            Button("Replace cart") { model.viewModel.confirmReplaceCart() }
            Button("Keep cart", role: .cancel) { model.viewModel.dismissReplaceCart() }
        } message: {
            Text("Your cart has items from \(state.pendingReplaceCartStoreName ?? "another store"). Switching to Takeout puts this order in the cart instead.")
        }
        .onReceive(model.events) { event in
            if let switched = event as? SeatCheckEventSwitchedToTakeout {
                let name = model.state.store?.name ?? model.state.order?.storeName ?? ""
                onSwitchedToTakeout(switched.storeId, name)
            } else if event is SeatCheckEventWaitInLine {
                onWaitInLine()
            } else if event is SeatCheckEventExited {
                onExit()
            }
        }
    }

    private var paymentPresented: Binding<Bool> {
        Binding(
            get: {
                let state = model.state
                return state.step == SeatCheckStep.payment && !state.isUnavailable && !state.isSent
            },
            set: { if !$0 { model.viewModel.closePayment() } }
        )
    }

    private var replacePresented: Binding<Bool> {
        Binding(
            get: { model.state.pendingReplaceCartStoreName != nil },
            set: { if !$0 { model.viewModel.dismissReplaceCart() } }
        )
    }
}

/// How the customer checked in, and the order.
private struct OrderHeader: View {
    let state: SeatCheckUiState

    var body: some View {
        if let order = state.order {
            let colors = Palette.badge(.openForLunch)
            HStack(spacing: 12) {
                Image(systemName: "checkmark")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(colors.foreground)
                    .frame(width: 40, height: 40)
                    .background(colors.background, in: Circle())
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(order.checkIn?.label ?? "").font(.headline)
                    Text("\(state.store?.name ?? order.storeName) · \(FulfillmentMode.inStore.choiceLabel) · \(order.itemsLabel)")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            .accessibilityElement(children: .combine)
        }
    }
}

private struct QuestionContent: View {
    let onYes: () -> Void
    let onNotYet: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "fork.knife")
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(Palette.action)
                .frame(width: 72, height: 72)
                .background(Palette.tileIcon, in: Circle())
                .accessibilityHidden(true)
            Text(LabelsKt.SEAT_QUESTION)
                .font(.title2.weight(.bold))
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Text(LabelsKt.SEAT_QUESTION_DETAIL)
                .font(.body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 24)
        .padding(.bottom, 8)

        Button("Yes, I have a seat", action: onYes).buttonStyle(CapsuleButtonStyle())
        Button("Not yet", action: onNotYet).buttonStyle(CapsuleButtonStyle(kind: .secondary))
    }
}

/// The store is full, or the customer has no seat yet.
private struct NoSeatContent: View {
    @ObservedObject var model: SeatCheckModel

    var body: some View {
        let state = model.state
        let storeName = state.store?.name ?? state.order?.storeName ?? ""
        VStack(alignment: .leading, spacing: 6) {
            Text(LabelsKt.noSeatTitle(storeName: storeName, storeIsFull: state.isStoreFull))
                .font(.title2.weight(.bold))
                .accessibilityAddTraits(.isHeader)
            Text(LabelsKt.NO_SEAT_DETAIL).font(.subheadline).foregroundStyle(.secondary)
        }
        .padding(.top, 8)

        VStack(spacing: 0) {
            option(
                title: LabelsKt.SWITCH_TO_TAKEOUT,
                detail: state.takeoutUnavailableReason ?? LabelsKt.SWITCH_TO_TAKEOUT_DETAIL,
                systemImage: "bag",
                enabled: state.canSwitchToTakeout
            ) { model.viewModel.switchToTakeout() }
            Divider().padding(.leading, 62)
            option(title: LabelsKt.WAIT_IN_LINE, detail: LabelsKt.WAIT_IN_LINE_DETAIL, systemImage: "clock", enabled: true) {
                model.viewModel.waitInLine()
            }
            Divider().padding(.leading, 62)
            option(title: LabelsKt.CANCEL_EXIT, detail: LabelsKt.CANCEL_EXIT_DETAIL, systemImage: "xmark.circle", enabled: true) {
                model.viewModel.exit()
            }
        }
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }

    private func option(title: String, detail: String, systemImage: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: systemImage)
                    .font(.title3)
                    .foregroundStyle(Palette.action)
                    .frame(width: 32)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.body.weight(.semibold)).foregroundStyle(.primary)
                    Text(detail).font(.footnote).foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.5)
    }
}

/// 1-tap payment, as the iOS Payment artboard. Apple Pay is already picked, so one tap on Pay sends the order.
private struct PaymentSheet: View {
    @ObservedObject var model: SeatCheckModel

    var body: some View {
        let state = model.state
        NavigationStack {
            List {
                if let order = state.order {
                    Section {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(state.store?.name ?? order.storeName) · \(FulfillmentMode.inStore.choiceLabel) · \(order.itemCount == 1 ? "1 item" : "\(order.itemCount) items")")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            Text(order.total.formatted()).font(.largeTitle.weight(.bold))
                            Text("Tax included").font(.footnote).foregroundStyle(.secondary)
                        }
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets(top: 0, leading: 4, bottom: 0, trailing: 4))
                        .accessibilityElement(children: .combine)
                    }
                }
                Section {
                    if state.paymentMethods.isEmpty {
                        ProgressView("Loading payment methods…")
                    }
                    ForEach(state.paymentMethods, id: \.id) { method in
                        let selected = method.id == state.selectedMethodId
                        Button { model.viewModel.selectPaymentMethod(methodId: method.id) } label: {
                            HStack(spacing: 12) {
                                Image(systemName: method.type == PaymentType.card ? "creditcard" : "wallet.pass")
                                    .frame(width: 32, height: 32)
                                    .foregroundStyle(method.type == PaymentType.applePay ? Color.white : Color.primary)
                                    .background(
                                        method.type == PaymentType.applePay ? Color.primary : Palette.screen,
                                        in: RoundedRectangle(cornerRadius: 9, style: .continuous)
                                    )
                                Text(method.label)
                                    .fontWeight(selected ? .semibold : .regular)
                                    .foregroundStyle(.primary)
                                Spacer()
                                if selected {
                                    Image(systemName: "checkmark").foregroundStyle(Palette.action).fontWeight(.semibold)
                                }
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(selected ? .isSelected : [])
                    }
                }
            }
            .safeAreaInset(edge: .bottom) {
                if let order = state.order {
                    BottomAction(action: { model.viewModel.pay() }) {
                        CapsuleLabel(title: LabelsKt.payButtonLabel(total: order.total))
                    }
                    .disabled(!state.canPay)
                }
            }
            .navigationTitle("Payment")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { model.viewModel.closePayment() } label: { Image(systemName: "xmark") }
                        .accessibilityLabel("Close")
                }
            }
        }
    }
}

private struct SentContent: View {
    let orderNumber: Int
    let onViewOrders: () -> Void
    let onDone: () -> Void

    var body: some View {
        Image(systemName: "checkmark.circle.fill")
            .font(.system(size: 48))
            .foregroundStyle(Palette.badge(.openForLunch).foreground)
            .padding(.top, 24)
            .accessibilityHidden(true)
        Text(LabelsKt.sentToKitchenTitle(orderNumber: Int32(orderNumber)))
            .font(.title2.weight(.bold))
            .accessibilityAddTraits(.isHeader)
        Text(LabelsKt.SENT_TO_KITCHEN_DETAIL).foregroundStyle(.secondary)
        Button("View order", action: onViewOrders).buttonStyle(CapsuleButtonStyle()).padding(.top, 16)
        Button("Back to Discover", action: onDone)
            .font(.subheadline.weight(.semibold))
            .tint(Palette.action)
            .frame(maxWidth: .infinity, minHeight: 44)
    }
}
