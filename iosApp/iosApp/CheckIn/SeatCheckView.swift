import SwiftUI
import SharedLogic

/// Seat verification (SEAT-01), opened by Start Cooking after check-in. "Have you found a seat?" comes before
/// the kitchen starts: Yes opens the payment sheet (PAY-01) for the draft, whose payment opens the order's ticket, and
/// a full store or "Not yet" offers Switch to Takeout, Wait in line or Cancel / Exit. None of them loses the order.
struct SeatCheckView: View {
    @StateObject private var model: SeatCheckModel
    @StateObject private var payment: PaymentModel
    /// Set when the sheet's payment went through, so the ticket opens once the sheet has closed.
    @State private var paidFromSheet: String?
    let onWaitInLine: () -> Void
    let onSwitchedToTakeout: (_ storeId: String, _ storeName: String) -> Void
    let onExit: () -> Void
    let onPaid: (_ orderId: String) -> Void

    init(
        orderId: String,
        onWaitInLine: @escaping () -> Void,
        onSwitchedToTakeout: @escaping (_ storeId: String, _ storeName: String) -> Void,
        onExit: @escaping () -> Void,
        onPaid: @escaping (_ orderId: String) -> Void
    ) {
        _model = StateObject(wrappedValue: SeatCheckModel(orderId: orderId))
        _payment = StateObject(wrappedValue: PaymentModel(draftOrderId: orderId))
        self.onWaitInLine = onWaitInLine
        self.onSwitchedToTakeout = onSwitchedToTakeout
        self.onExit = onExit
        self.onPaid = onPaid
    }

    var body: some View {
        let state = model.state
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if state.isLoading {
                    ProgressView("Loading your order…").frame(maxWidth: .infinity).padding(.top, 32)
                } else if state.isPaid {
                    // Paid: the ticket opens next.
                    ProgressView("Opening your order…").frame(maxWidth: .infinity).padding(.top, 32)
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
        .sheet(isPresented: paymentPresented, onDismiss: afterPaymentSheet) {
            PaymentSheetView(model: payment)
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
        .onReceive(payment.events) { event in
            if let paid = event as? PaymentEventPaid {
                // Marking it paid closes the sheet; the ticket opens once it has.
                paidFromSheet = paid.orderId
                model.viewModel.markPaid(paidOrderId: paid.orderId)
            } else if event is PaymentEventClosed {
                // Left without paying (PAY-02): back to the question, still checked in.
                model.viewModel.closePayment()
            }
        }
    }

    private func afterPaymentSheet() {
        if let orderId = paidFromSheet {
            paidFromSheet = nil
            onPaid(orderId)
        } else {
            // Closed without paying: back to the question, with the check-in kept.
            payment.viewModel.reset()
        }
    }

    private var paymentPresented: Binding<Bool> {
        Binding(
            get: { model.state.showsPayment },
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
            option(title: LabelsKt.WAIT_IN_LINE, detail: Copy.titleCased(LabelsKt.WAIT_IN_LINE_DETAIL), systemImage: "clock", enabled: true) {
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
