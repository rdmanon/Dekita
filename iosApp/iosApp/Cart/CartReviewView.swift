import SwiftUI
import SharedLogic

/// Read-only Cart Review, laid out as the iOS Cart artboard. A guest's "Pay now" opens sign-in as a sheet
/// over this screen, so finishing sign-in lands back here with the items unchanged.
/// Editing lines and paying are later tickets.
struct CartReviewView: View {
    @EnvironmentObject private var flow: AuthFlow
    @StateObject private var model = CartReviewModel()
    @State private var showPaymentNote = false

    var body: some View {
        let state = model.state
        Group {
            if let cart = state.cart {
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        if let store = state.store {
                            CartStoreCard(store: store)
                        }
                        Text("Your order").font(.headline).padding(.horizontal, 4)
                        VStack(spacing: 0) {
                            ForEach(Array(cart.lines.enumerated()), id: \.element.lineId) { index, line in
                                CartLineRow(line: line)
                                if index < cart.lines.count - 1 {
                                    Divider().padding(.leading, 16)
                                }
                            }
                        }
                        .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                        CartTotals(cart: cart)
                    }
                    .padding(16)
                }
                .safeAreaInset(edge: .bottom) {
                    BottomAction(action: { model.viewModel.payNow() }) {
                        CapsuleLabel(title: "Pay now", systemImage: "creditcard")
                    }
                }
            } else {
                ContentUnavailableView(
                    "Your cart is empty",
                    systemImage: "cart",
                    description: Text("Add items from a store's menu.")
                )
            }
        }
        .background(Palette.screen)
        .navigationTitle("Cart")
        .navigationBarTitleDisplayMode(.inline)
        .onReceive(model.events) { event in
            if event is CartReviewEventRequireSignIn {
                flow.requestSignIn(from: .checkout)
            } else {
                showPaymentNote = true
            }
        }
        .alert("You're signed in", isPresented: $showPaymentNote) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Payment isn't built in this mockup yet.")
        }
    }
}

/// The store, its live queue line and the order mode, as the top card on the artboard.
private struct CartStoreCard: View {
    let store: Store

    var body: some View {
        HStack(spacing: 12) {
            StoreArtView(store: store, cornerRadius: 12)
                .frame(width: 48, height: 48)
            VStack(alignment: .leading, spacing: 2) {
                Text(store.name).font(.headline)
                Text(store.queueDetail()).font(.footnote).foregroundStyle(.secondary)
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
    }
}

private struct CartLineRow: View {
    let line: CartLine

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(line.item.name).font(.body.weight(.semibold))
                if !line.selectedOptions.isEmpty {
                    Text(line.selectedOptions.map(\.name).joined(separator: " · "))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Text("Qty \(line.quantity)").font(.footnote).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Text(line.lineTotal.formatted()).font(.subheadline.weight(.semibold))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .accessibilityElement(children: .combine)
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
