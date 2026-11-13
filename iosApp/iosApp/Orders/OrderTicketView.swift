import SwiftUI
import SharedLogic

extension EnvironmentValues {
    /// Opens an order's ticket. ContentView presents it over every tab.
    @Entry var openOrderTicket: (String) -> Void = { _ in }

    /// Opens Rate & Add to Favorites for a completed order, from an Orders inbox message (RCPT-01).
    @Entry var openRating: (String) -> Void = { _ in }
}

/// The order ticket (ORDER-01) over the tabs, then Rate & Add to Favorites (RCPT-01). After Confirm, leaving
/// Rate closes the whole flow. Opened later from a receipt, leaving Rate returns to the receipt.
struct OrderTicketFlow: View {
    let orderId: String
    @StateObject private var model: OrderTicketModel
    @State private var showRating = false
    @State private var ratingAfterConfirm = false
    @Environment(\.dismiss) private var dismiss

    init(orderId: String) {
        self.orderId = orderId
        _model = StateObject(wrappedValue: OrderTicketModel(orderId: orderId))
    }

    var body: some View {
        NavigationStack {
            OrderTicketView(
                model: model,
                onConfirmed: { openRating(afterConfirm: true) },
                onRate: { openRating(afterConfirm: false) }
            )
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { dismiss() } label: { Image(systemName: "xmark") }
                        .accessibilityLabel("Close")
                }
            }
            .navigationDestination(isPresented: $showRating) {
                RateOrderView(orderId: orderId) {
                    if ratingAfterConfirm { dismiss() } else { showRating = false }
                }
            }
        }
        .onAppear { OrderNotifier.shared.requestAuthorizationIfNeeded() }
        .onReceive(model.$openRatingRequested.filter { $0 }) { _ in
            model.openRatingRequested = false
            openRating(afterConfirm: false)
        }
    }

    private func openRating(afterConfirm: Bool) {
        ratingAfterConfirm = afterConfirm
        showRating = true
    }
}

/// Rate opened on its own, from an Orders inbox message (RCPT-01). Leaving it closes the cover.
struct RateOrderFlow: View {
    let orderId: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            RateOrderView(orderId: orderId) { dismiss() }
        }
    }
}

/// The status banner, the number to show at the counter, the live timeline, and Confirm Received. Status changes
/// arrive without a refresh.
struct OrderTicketView: View {
    @ObservedObject var model: OrderTicketModel
    let onConfirmed: () -> Void
    let onRate: () -> Void

    var body: some View {
        let state = model.state
        Group {
            if state.isLoading {
                ProgressView("Loading your order…")
            } else if let order = state.order {
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        StatusBanner(state: state)
                        OrderNumberCard(state: state)
                        StatusTimeline(steps: state.steps, cookingNote: state.cookingNote)
                        ItemsCard(order: order)
                        if state.isCompleted {
                            ReceiptCard(state: state, onRate: onRate)
                        }
                        if AppBuild.isDebug && !state.isCompleted {
                            DebugControls(viewModel: model.viewModel)
                        }
                    }
                    .padding(16)
                }
                .safeAreaInset(edge: .bottom) {
                    if !state.isCompleted {
                        VStack(spacing: 8) {
                            if let hint = state.confirmHint {
                                Text(hint)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                                    .multilineTextAlignment(.center)
                                    .padding(.horizontal, 16)
                            }
                            BottomAction(action: { if model.viewModel.confirm() { onConfirmed() } }) {
                                CapsuleLabel(title: Copy.confirmReceived)
                            }
                            .disabled(!state.canConfirm)
                        }
                        .padding(.top, 8)
                        .background(.bar)
                    }
                }
            } else {
                ContentUnavailableView("This order isn't available", systemImage: "receipt")
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Palette.screen)
        .navigationTitle("Order")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// The in-app copy of the notification, first on the ticket. VoiceOver reads each new title.
private struct StatusBanner: View {
    let state: OrderTicketUiState

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "bell")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Palette.link)
                .frame(width: 36, height: 36)
                .background(Palette.tileIcon, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(state.bannerTitle).font(.subheadline.weight(.semibold))
                if !state.bannerDetail.isEmpty {
                    Text(state.bannerDetail).font(.footnote).foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(14)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .accessibilityElement(children: .combine)
        .onChange(of: state.bannerTitle) { _, title in
            AccessibilityNotification.Announcement(title).post()
        }
    }
}

/// The number to show at the counter, on a card as in the artboard: dark on the card color, so it reads at arm's
/// length in light and dark. Under it the store, mode and table, how it was paid, and what to tell the staff.
private struct OrderNumberCard: View {
    let state: OrderTicketUiState

    var body: some View {
        VStack(spacing: 6) {
            Text("ORDER NUMBER").font(.caption.weight(.semibold)).tracking(1).foregroundStyle(.secondary)
            Text(state.numberLabel)
                .font(.system(size: 96, weight: .heavy))
                .tracking(-2)
                .monospacedDigit()
                .minimumScaleFactor(0.5)
                .lineLimit(1)
                .foregroundStyle(.primary)
                .accessibilityLabel("\(state.order?.number ?? 0)")
            Text(state.storeLine).font(.headline)
            if let table = state.tableLabel {
                Text(table).font(.headline)
            }
            Text(state.paidLine).font(.footnote).foregroundStyle(.secondary)
            Line()
                .stroke(Color(light: 0xD1D1D6, dark: 0x48484A), style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
                .frame(height: 1.5)
                .padding(.top, 10)
                .padding(.bottom, 4)
            Text("Show this number to staff if asked.").font(.footnote).foregroundStyle(.secondary)
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity)
        .padding(.top, 22)
        .padding(.bottom, 18)
        .padding(.horizontal, 20)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

private struct Line: Shape {
    func path(in rect: CGRect) -> Path {
        Path { $0.move(to: CGPoint(x: rect.minX, y: rect.midY)); $0.addLine(to: CGPoint(x: rect.maxX, y: rect.midY)) }
    }
}

/// Received → Cooking → Ready / Served, top to bottom: green checks with the time each was reached, the current
/// step as a ring with the usual wait, the next ones grey. Each step reads as one item, for example
/// "Cooking, step 2 of 3, current".
private struct StatusTimeline: View {
    let steps: [TicketStep]
    let cookingNote: String?
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(steps.enumerated()), id: \.offset) { index, step in
                let isLast = index == steps.count - 1
                HStack(alignment: .top, spacing: 14) {
                    VStack(spacing: 0) {
                        StepDot(state: step.state)
                        if !isLast {
                            Rectangle()
                                .fill(step.state == .done ? green : Color(light: 0xE5E5EA, dark: 0x3A3A3C))
                                .frame(width: 2, height: 28)
                        }
                    }
                    let layout = typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading, spacing: 2)) : AnyLayout(HStackLayout(alignment: .firstTextBaseline))
                    layout {
                        Text(step.label)
                            .font(.callout.weight(step.state == .current ? .bold : (step.state == .done ? .semibold : .regular)))
                            .foregroundStyle(step.state == .current ? Palette.link : (step.state == .todo ? Color.secondary : Color.primary))
                        if typeSize.isAccessibilitySize == false { Spacer(minLength: 8) }
                        if let note = note(for: step) {
                            Text(note).font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, 1)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(spoken(step, position: index + 1))
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }

    /// The time a finished step was reached, in the device's time zone; the usual wait for the current one.
    private func note(for step: TicketStep) -> String? {
        if step.state == .current, let cookingNote { return cookingNote }
        guard let seconds = step.atEpochSeconds?.int64Value else { return nil }
        return Date(timeIntervalSince1970: TimeInterval(seconds)).formatted(date: .omitted, time: .shortened)
    }

    private func spoken(_ step: TicketStep, position: Int) -> String {
        let base = step.spokenDescription(position: Int32(position), count: Int32(steps.count))
        guard let note = note(for: step) else { return base }
        return "\(base), \(note)"
    }
}

private let green = Color(light: 0x248A3D, dark: 0x30D158)

private struct StepDot: View {
    let state: StepState

    var body: some View {
        ZStack {
            switch state {
            case .done:
                Circle().fill(green)
                Image(systemName: "checkmark").font(.system(size: 12, weight: .bold)).foregroundStyle(.white)
            case .current:
                Circle().strokeBorder(Palette.action, lineWidth: 3)
                    .background(Circle().fill(Palette.action.opacity(0.14)).padding(-5))
                Circle().fill(Palette.action).frame(width: 8, height: 8)
            default:
                Circle().strokeBorder(Color(light: 0xAEAEB2, dark: 0x636366), lineWidth: 2)
            }
        }
        .frame(width: 24, height: 24)
    }
}

private struct ItemsCard: View {
    let order: Order

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Items").font(.title3.weight(.bold))
            VStack(alignment: .leading, spacing: 12) {
                ForEach(order.lines, id: \.lineId) { line in
                    HStack(alignment: .top, spacing: 12) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(line.quantity) × \(line.item.name)").font(.body)
                            if !line.optionsLabel.isEmpty {
                                Text(line.optionsLabel).font(.footnote).foregroundStyle(.secondary)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        Text(line.lineTotal.formatted()).font(.body)
                    }
                    .accessibilityElement(children: .combine)
                }
                Divider()
                HStack {
                    Text("Total (tax incl.)").font(.headline)
                    Spacer()
                    Text(order.total.formatted()).font(.headline)
                }
                .accessibilityElement(children: .combine)
            }
            .padding(16)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        }
    }
}

/// RCPT-01: how the order was paid, when (in the device's time zone), and the rating, or a way to rate it.
private struct ReceiptCard: View {
    let state: OrderTicketUiState
    let onRate: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Receipt").font(.title3.weight(.bold))
            VStack(alignment: .leading, spacing: 10) {
                row("Paid with", state.paymentLabel ?? "—")
                row("Paid on", paidOn ?? "—")
                if let rating = state.ratingLabel { row("Your rating", rating) }
            }
            .padding(16)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            if state.canRate {
                Button("Rate this order", action: onRate)
                    .buttonStyle(CapsuleButtonStyle(kind: .secondary))
            }
        }
    }

    private var paidOn: String? {
        guard let seconds = state.paidAtEpochSeconds?.int64Value else { return nil }
        return Date(timeIntervalSince1970: TimeInterval(seconds)).formatted(date: .abbreviated, time: .shortened)
    }

    private func row(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top) {
            Text(label).foregroundStyle(.secondary)
            Spacer(minLength: 12)
            Text(value).multilineTextAlignment(.trailing)
        }
        .font(.subheadline)
        .accessibilityElement(children: .combine)
    }
}

/// Debug builds only: stand-ins for the store's own app.
private struct DebugControls: View {
    let viewModel: OrderTicketViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Debug").font(.subheadline.weight(.semibold)).accessibilityAddTraits(.isHeader)
            Button("Advance status") { viewModel.debugAdvance() }
                .buttonStyle(CapsuleButtonStyle(kind: .secondary))
            Button("Trigger auto-complete") { viewModel.debugAutoComplete() }
                .buttonStyle(CapsuleButtonStyle(kind: .secondary))
        }
    }
}
