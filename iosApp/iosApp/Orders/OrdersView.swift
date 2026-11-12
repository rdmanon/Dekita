import SwiftUI
import SharedLogic

/// The Orders inbox (RCPT-01): active orders, then inbox messages, then receipts, each newest first.
/// Guests see a sign-in card instead. An active order or a receipt opens its ticket; a rate prompt opens
/// Rate. An in-store draft waiting for check-in can be resumed here, so leaving check-in never loses it.
struct OrdersView: View {
    @EnvironmentObject private var flow: AuthFlow
    @StateObject private var model = OrdersModel()
    @Environment(\.openCheckIn) private var openCheckIn
    @Environment(\.openOrderTicket) private var openOrderTicket
    @Environment(\.openRating) private var openRating

    var body: some View {
        let state = model.state
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if !state.isSignedIn {
                    SignInPromptCard(
                        title: "Sign in to see your orders",
                        message: "Your active orders, messages and receipts appear here once you sign in."
                    ) { flow.requestSignIn(from: .standard) }
                } else if state.isLoading {
                    ProgressView("Loading orders…").frame(maxWidth: .infinity).padding(.top, 32)
                } else if state.isEmpty {
                    ContentUnavailableView(
                        "No orders yet",
                        systemImage: "receipt",
                        description: Text("Your orders, receipts and messages will appear here.")
                    )
                    .padding(.top, 32)
                } else {
                    Text("Active").font(.title3.weight(.bold))
                    if state.active.isEmpty {
                        Text("No active orders.").foregroundStyle(.secondary)
                    }
                    ForEach(state.active, id: \.id) { order in
                        if order.awaitsCheckIn {
                            DraftCard(order: order) { openCheckIn(order.id) }
                        } else {
                            Button { openOrderTicket(order.id) } label: { ActiveOrderCard(order: order) }
                                .buttonStyle(.plain)
                                .accessibilityHint("Opens the order ticket")
                        }
                    }
                    if !state.inbox.isEmpty {
                        Text("Inbox").font(.title3.weight(.bold)).padding(.top, 8)
                        VStack(spacing: 0) {
                            ForEach(Array(state.inbox.enumerated()), id: \.element.message.id) { index, row in
                                InboxRowView(row: row) { open(row) }
                                if index < state.inbox.count - 1 {
                                    Divider().padding(.leading, 36)
                                }
                            }
                        }
                        .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                    }
                    if !state.receipts.isEmpty {
                        Text("Receipts").font(.title3.weight(.bold)).padding(.top, 8)
                        VStack(spacing: 0) {
                            ForEach(Array(state.receipts.enumerated()), id: \.element.id) { index, order in
                                Button { openOrderTicket(order.id) } label: { ReceiptRow(order: order) }
                                    .buttonStyle(.plain)
                                    .accessibilityHint("Opens the receipt")
                                if index < state.receipts.count - 1 {
                                    Divider().padding(.leading, 16)
                                }
                            }
                        }
                        .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                    }
                }
            }
            .padding(16)
        }
        .background(Palette.screen)
        .navigationTitle("Orders")
    }

    /// Marks the message read, then opens Rate for a rate prompt or the ticket for an order message.
    private func open(_ row: InboxRow) {
        model.viewModel.openMessage(messageId: row.message.id)
        guard let orderId = row.message.orderId else { return }
        if row.isRatePrompt { openRating(orderId) } else { openOrderTicket(orderId) }
    }
}

/// An in-app message. Unread ones have a dot; a rate prompt shows "Rated" once its order is rated.
private struct InboxRowView: View {
    let row: InboxRow
    let onOpen: () -> Void

    var body: some View {
        let message = row.message
        Button(action: onOpen) {
            HStack(alignment: .top, spacing: 12) {
                Circle()
                    .fill(message.isRead ? Color.clear : Color.accentColor)
                    .frame(width: 8, height: 8)
                    .padding(.top, 7)
                VStack(alignment: .leading, spacing: 2) {
                    Text(message.title).font(.body.weight(.semibold)).foregroundStyle(.primary)
                    Text(message.body).font(.subheadline).foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if row.isDone {
                    let green = Palette.badge(.openForLunch)
                    Text("Rated")
                        .font(.caption.weight(.semibold))
                        .padding(.horizontal, 8)
                        .frame(minHeight: 22)
                        .foregroundStyle(green.foreground)
                        .background(green.background, in: Capsule())
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityValue(message.isRead ? "" : "Unread")
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
            Text(order.currentStep?.label ?? order.status.label)
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

/// An in-store order built on the way. It has no ticket yet: the kitchen starts after check-in.
private struct DraftCard: View {
    let order: Order
    let onResume: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Waiting for check-in")
                .font(.caption.weight(.semibold))
                .padding(.horizontal, 8)
                .frame(minHeight: 22)
                .foregroundStyle(Color.accentColor)
                .background(Palette.tileIcon, in: Capsule())
            Text("\(order.storeName) · Eat-In").font(.headline)
            Text("\(order.itemCount) items · \(order.total.formatted())")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            if let checkIn = order.checkIn {
                Text(checkIn.label)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Button(action: onResume) {
                Text("Resume check-in")
                    .font(.headline)
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.capsule)
            .padding(.top, 4)
        }
        .padding(16)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}

private struct ReceiptRow: View {
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
        if let detail = order.rating?.label ?? order.completionReason?.label { parts.append(detail) }
        return parts.joined(separator: " · ")
    }
}
