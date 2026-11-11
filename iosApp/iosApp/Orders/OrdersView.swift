import SwiftUI
import SharedLogic

/// Orders tab: active tickets and history from the seed data. Guests see a sign-in card instead.
/// The real Orders screen is a later ticket; this matches the content the Android tab shows.
struct OrdersView: View {
    @EnvironmentObject private var flow: AuthFlow
    @StateObject private var model = OrdersModel()

    var body: some View {
        let state = model.state
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if !state.isSignedIn {
                    SignInPromptCard(
                        title: "Sign in to see your orders",
                        message: "Your active orders and order history appear here once you sign in."
                    ) { flow.requestSignIn(from: .standard) }
                } else if state.isLoading {
                    ProgressView("Loading orders…").frame(maxWidth: .infinity).padding(.top, 32)
                } else {
                    Text("Active").font(.title3.weight(.bold))
                    if state.active.isEmpty {
                        Text("No active orders.").foregroundStyle(.secondary)
                    }
                    ForEach(state.active, id: \.id) { order in
                        ActiveOrderCard(order: order)
                    }
                    Text("History").font(.title3.weight(.bold)).padding(.top, 8)
                    VStack(spacing: 0) {
                        ForEach(Array(state.history.enumerated()), id: \.element.id) { index, order in
                            HistoryRow(order: order)
                            if index < state.history.count - 1 {
                                Divider().padding(.leading, 16)
                            }
                        }
                    }
                    .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                }
            }
            .padding(16)
        }
        .background(Palette.screen)
        .navigationTitle("Orders")
    }
}

/// The ticket's order number large enough to show at the counter, then store, mode, total and status.
private struct ActiveOrderCard: View {
    let order: Order

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            VStack(spacing: 2) {
                Text("ORDER NUMBER").font(.caption.weight(.semibold)).tracking(1)
                Text("#\(order.number)").font(.system(size: 52, weight: .bold, design: .rounded))
            }
            .foregroundStyle(Color.accentColor)
            .frame(maxWidth: .infinity)
            .padding(16)
            .background(Palette.tileIcon, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Order number \(order.number)")

            Text("\(order.storeName) · \(order.mode.label)").font(.headline)
            Text("\(order.itemCount) items · \(order.total.formatted())")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Text(order.status.label)
                .font(.caption.weight(.semibold))
                .padding(.horizontal, 8)
                .frame(minHeight: 22)
                .foregroundStyle(Color.accentColor)
                .background(Palette.tileIcon, in: Capsule())
        }
        .padding(16)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}

private struct HistoryRow: View {
    let order: Order

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("#\(order.number) · \(order.storeName)").font(.body.weight(.semibold))
            Text(detail).font(.footnote).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .accessibilityElement(children: .combine)
    }

    private var detail: String {
        var parts = [order.mode.label, order.total.formatted()]
        if let reason = order.completionReason?.label { parts.append(reason) }
        return parts.joined(separator: " · ")
    }
}
