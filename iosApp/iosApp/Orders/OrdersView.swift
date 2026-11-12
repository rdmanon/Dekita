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
                    if !state.active.isEmpty {
                        VStack(spacing: 0) {
                            ForEach(Array(state.active.enumerated()), id: \.element.id) { index, order in
                                if order.awaitsCheckIn {
                                    Button { openCheckIn(order.id) } label: { DraftRow(order: order) }
                                        .buttonStyle(.plain)
                                        .accessibilityHint("Resumes check-in")
                                } else {
                                    Button { openOrderTicket(order.id) } label: { ActiveOrderRow(order: order) }
                                        .buttonStyle(.plain)
                                        .accessibilityHint("Opens the order ticket")
                                }
                                if index < state.active.count - 1 {
                                    Divider().padding(.leading, 16)
                                }
                            }
                        }
                        .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
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

/// UI-01: one compact row per active order. The big number stays on the ticket.
private struct ActiveOrderRow: View {
    let order: Order
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let layout = rowLayout(typeSize)
        layout {
            // Neutral, like the ticket's number card; the peach stays on the Discover banner.
            Text("#\(order.number)")
                .font(.title2.weight(.bold))
                .monospacedDigit()
                .foregroundStyle(.primary)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Palette.screen, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .accessibilityLabel("Order number \(order.number)")
            VStack(alignment: .leading, spacing: 4) {
                Text("\(order.storeName) · \(order.mode.label)").font(.headline)
                Text(detail).font(.subheadline).foregroundStyle(.secondary)
                StepCapsule(text: order.currentStep?.label ?? order.status.label)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }

    private var detail: String {
        var parts = [order.itemsLabel]
        if let table = order.checkIn?.tableNumber { parts.append("Table \(table)") }
        return parts.joined(separator: " · ")
    }
}

/// An in-store order built on the way. It has no ticket yet: tapping the row resumes check-in.
private struct DraftRow: View {
    let order: Order
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let layout = rowLayout(typeSize)
        layout {
            VStack(alignment: .leading, spacing: 4) {
                StepCapsule(text: "Waiting for check-in")
                Text("\(order.storeName) · Eat-In").font(.headline)
                Text(detail).font(.subheadline).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Text("Resume").font(.subheadline.weight(.semibold)).foregroundStyle(Color.accentColor)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }

    private var detail: String {
        var parts = [order.itemsLabel]
        if let checkIn = order.checkIn { parts.append(checkIn.label) }
        return parts.joined(separator: " · ")
    }
}


/// The small status pill both rows share.
private struct StepCapsule: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 8)
            .frame(minHeight: 22)
            .foregroundStyle(Color.accentColor)
            // A capsule at one line; stays a pill instead of an oval when large text wraps it.
            .background(Palette.tileIcon, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
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
