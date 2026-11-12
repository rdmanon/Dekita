import SwiftUI
import SharedLogic

/// The payment sheet (PAY-01), laid out as the iOS Payment artboard: what is being paid for, the total, the
/// methods in one grouped card with Add Card, and Pay. Pay shows a spinner and turns off while charging, and the
/// sheet can't be swiped away then. PayPay, Rakuten Pay and Apple Pay open their mock approval screen over it.
/// The sheet is only as tall as the form, as on Android, and draws the system's own glass, and grows when an error shows; on a short screen or at
/// large text sizes the form scrolls instead.
/// A decline replaces Pay with Retry, Use Another Method and Cancel (PAY-02); swiping down or Close dismisses the sheet
/// onto the screen behind it, Cart Review or the seat check, with nothing changed.
/// The presenter handles `PaymentEventPaid` and `PaymentEventClosed` from `model.events` (closing the sheet on Closed),
/// and calls `reset()` when a sheet it keeps the model for is dismissed. (Not `onDisappear`: the full-screen mock
/// approval page would trigger it.)
struct PaymentSheetView: View {
    @ObservedObject var model: PaymentModel
    @Environment(\.dismiss) private var dismiss
    @State private var showAddCard = false
    /// The form's measured height. The first guess fits the design's four methods, so the sheet rarely jumps.
    @State private var formHeight: CGFloat = 560

    var body: some View {
        let state = model.state
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                header(canDismiss: state.canDismiss)
                if state.isLoading {
                    ProgressView("Loading payment methods…").frame(maxWidth: .infinity).padding(.vertical, 32)
                } else if !state.hasSomethingToPay {
                    Text("There's nothing to pay. Your cart may have been emptied.")
                        .padding(.horizontal, 4)
                        .padding(.bottom, 16)
                } else {
                    content(state)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 20)
            .padding(.bottom, 8)
            .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { formHeight = $0 }
        }
        .scrollBounceBehavior(.basedOnSize)
        .presentationDetents([.height(formHeight)])
        .presentationDragIndicator(.visible)
        .interactiveDismissDisabled(!state.canDismiss)
        .paymentApproval(model: model)
        .sheet(isPresented: $showAddCard) {
            AddTestCardView { number, expiry, cvc in
                model.viewModel.addTestCard(number: number, expiry: expiry, cvc: cvc)
            }
        }
    }

    /// "Payment" and the close button, inside the sheet as on the artboard rather than in a navigation bar.
    private func header(canDismiss: Bool) -> some View {
        HStack {
            Text("Payment")
                .font(.title2.weight(.bold))
                .accessibilityAddTraits(.isHeader)
            Spacer()
            Button { dismiss() } label: {
                Image(systemName: "xmark")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.primary)
                    .frame(width: 36, height: 36)
                    .glassControl(in: Circle())
                    .frame(width: 44, height: 44)
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close")
            .disabled(!canDismiss)
        }
        .padding(.leading, 4)
    }

    @ViewBuilder
    private func content(_ state: PaymentUiState) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(state.summary).font(.subheadline).foregroundStyle(.secondary)
            Text(state.total?.formatted() ?? "").font(.system(size: 34, weight: .bold))
            Text("Tax included").font(.footnote).foregroundStyle(.secondary)
        }
        .padding(.horizontal, 4)
        .accessibilityElement(children: .combine)

        VStack(spacing: 0) {
            ForEach(state.methods, id: \.id) { method in
                PaymentMethodRow(
                    method: method,
                    isSelected: method.id == state.selectedMethod?.id
                ) { model.viewModel.selectMethod(methodId: method.id) }
                Divider().padding(.leading, 60)
            }
            Button { showAddCard = true } label: {
                HStack(spacing: 12) {
                    Image(systemName: "plus")
                        .font(.subheadline.weight(.bold))
                        .frame(width: 32, height: 32)
                        .background(Palette.tileIcon, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                    Text("Add Card").font(.body.weight(.semibold))
                    Spacer()
                }
                .padding(.horizontal, 16)
                .frame(minHeight: 52)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .foregroundStyle(Palette.link)
        }
        .disabled(!state.canChangeMethod)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))

        if state.isDeclined, let error = state.errorMessage {
            DeclinedCard(
                message: error,
                onRetry: { model.viewModel.pay() },
                onUseAnotherMethod: { model.viewModel.useAnotherMethod() },
                onCancel: { model.viewModel.cancel() }
            )
        } else {
            if let error = state.errorMessage {
                ErrorCard(message: error)
            }
            Button { model.viewModel.pay() } label: {
                CapsuleLabel(title: state.payButtonLabel, loading: state.isProcessing)
            }
            .buttonStyle(CapsuleButtonStyle())
            .disabled(!state.canPay)
        }

        Text("Mockup: no real payment is made.")
            .font(.footnote)
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity)
    }
}

/// A decline, inline on the sheet (PAY-02): what happened in plain words, then Retry (the selected method, which can be
/// changed above first), Use Another Method, and Cancel back to Cart Review or the seat check. VoiceOver announces it.
private struct DeclinedCard: View {
    let message: String
    let onRetry: () -> Void
    let onUseAnotherMethod: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label(message, systemImage: "exclamationmark.triangle.fill")
                .font(.subheadline)
                .foregroundStyle(Palette.error)
            Button(action: onRetry) { CapsuleLabel(title: "Retry") }
                .buttonStyle(CapsuleButtonStyle())
            HStack {
                Button("Use Another Method", action: onUseAnotherMethod)
                Spacer()
                Button("Cancel", action: onCancel)
            }
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(Palette.action)
            .buttonStyle(.borderless)
            .frame(minHeight: 44)
        }
        .padding(14)
        .background(Palette.badge(.full).background, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .onAppear { UIAccessibility.post(notification: .announcement, argument: message) }
    }
}

/// One method: its icon tile, label and a checkmark when selected.
private struct PaymentMethodRow: View {
    let method: PaymentMethod
    let isSelected: Bool
    let onSelect: () -> Void

    var body: some View {
        Button(action: onSelect) {
            HStack(spacing: 12) {
                PaymentMethodIcon(type: method.type)
                Text(method.label)
                    .font(.body.weight(isSelected ? .semibold : .regular))
                    .foregroundStyle(.primary)
                Spacer()
                Image(systemName: "checkmark")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Palette.link)
                    .opacity(isSelected ? 1 : 0)
            }
            .padding(.horizontal, 16)
            .frame(minHeight: 52)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }
}

/// A card or a wallet, in a 32 pt tile. Apple Pay's tile is dark, as on the artboard. No provider logos.
struct PaymentMethodIcon: View {
    let type: PaymentType

    var body: some View {
        let isApplePay = type == PaymentType.applePay
        Image(systemName: type == PaymentType.card ? "creditcard" : "wallet.pass")
            .font(.subheadline)
            .foregroundStyle(isApplePay ? Color.white : Color.primary)
            .frame(width: 32, height: 32)
            .background(isApplePay ? Color.black : Palette.screen, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
            .accessibilityHidden(true)
    }
}

extension View {
    /// Presents the mock approval screen for the method being approved: the PayPay or Rakuten Pay page full screen,
    /// or the Apple Pay sheet. Dismissing either cancels. Pass `isActive: false` while another view presents them.
    func paymentApproval(model: PaymentModel, isActive: Bool = true) -> some View {
        modifier(PaymentApprovalModifier(model: model, isActive: isActive))
    }
}

private struct PaymentApprovalModifier: ViewModifier {
    @ObservedObject var model: PaymentModel
    let isActive: Bool

    func body(content: Content) -> some View {
        content
            .fullScreenCover(isPresented: binding(for: PaymentApproval.redirect)) {
                if let method = model.state.approvingMethod {
                    MockRedirectView(
                        method: method,
                        storeName: model.state.storeName,
                        amount: model.state.total?.formatted() ?? "",
                        onApprove: { model.viewModel.approve() },
                        onCancel: { model.viewModel.cancelApproval() }
                    )
                }
            }
            .sheet(isPresented: binding(for: PaymentApproval.walletSheet)) {
                if let method = model.state.approvingMethod {
                    MockWalletSheet(
                        method: method,
                        storeName: model.state.storeName,
                        amount: model.state.total?.formatted() ?? "",
                        onApprove: { model.viewModel.approve() },
                        onCancel: { model.viewModel.cancelApproval() }
                    )
                    .presentationDetents([.medium])
                }
            }
    }

    private func binding(for approval: PaymentApproval) -> Binding<Bool> {
        Binding(
            get: { isActive && model.state.approvingMethod?.type.approval == approval },
            // Swiping the screen away cancels. The view model ignores this once the charge has started.
            set: { if !$0 { model.viewModel.cancelApproval() } }
        )
    }
}
