import SwiftUI
import SharedLogic

extension EnvironmentValues {
    /// Opens an order's ticket. ContentView presents it over every tab.
    @Entry var openOrderTicket: (String) -> Void = { _ in }
}

/// The order ticket (ORDER-01) over the tabs: the ticket, then Rate & Add to Favorites after Confirm.
struct OrderTicketFlow: View {
    @StateObject private var model: OrderTicketModel
    @State private var showRating = false
    @Environment(\.dismiss) private var dismiss

    init(orderId: String) {
        _model = StateObject(wrappedValue: OrderTicketModel(orderId: orderId))
    }

    var body: some View {
        NavigationStack {
            OrderTicketView(model: model) { showRating = true }
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button { dismiss() } label: { Image(systemName: "xmark") }
                            .accessibilityLabel("Close")
                    }
                }
                .navigationDestination(isPresented: $showRating) {
                    RateOrderPlaceholderView { dismiss() }
                }
        }
        .onAppear { OrderNotifier.shared.requestAuthorizationIfNeeded() }
    }
}

/// The number to show at the counter, the live stepper, and [Confirm]. Status changes arrive without a refresh.
struct OrderTicketView: View {
    @ObservedObject var model: OrderTicketModel
    let onConfirmed: () -> Void

    var body: some View {
        let state = model.state
        Group {
            if state.isLoading {
                ProgressView("Loading your order…")
            } else if let order = state.order {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        OrderNumberCard(state: state)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(state.storeLine).font(.headline)
                            if let table = state.tableLabel { Text(table).font(.headline) }
                        }
                        StepperRow(steps: state.steps)
                        StatusMessage(state: state)
                        ItemsCard(order: order)
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
                                CapsuleLabel(title: "Confirm")
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
        .navigationTitle("Order ticket")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// High contrast at arm's length: at least 4.5:1 in light and dark.
private struct OrderNumberCard: View {
    let state: OrderTicketUiState

    var body: some View {
        VStack(spacing: 2) {
            Text("ORDER NUMBER").font(.caption.weight(.semibold)).tracking(1)
            Text(state.numberLabel)
                .font(.system(size: 88, weight: .bold, design: .rounded))
                .monospacedDigit()
                .minimumScaleFactor(0.5)
                .lineLimit(1)
        }
        .foregroundStyle(Palette.orderNumber.foreground)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 20)
        .padding(.horizontal, 16)
        .background(Palette.orderNumber.background, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Order number \(state.order?.number ?? 0)")
    }
}

/// Received → Cooking → Ready / Served. Each step reads as one item, for example "Cooking, step 2 of 3, current".
private struct StepperRow: View {
    let steps: [TicketStep]

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            ForEach(Array(steps.enumerated()), id: \.offset) { index, step in
                VStack(spacing: 6) {
                    StepDot(state: step.state, number: index + 1)
                    Text(step.label)
                        .font(step.state == .current ? .subheadline.weight(.semibold) : .subheadline)
                        .foregroundStyle(step.state == .todo ? .secondary : .primary)
                }
                .frame(maxWidth: .infinity)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(step.spokenDescription(position: Int32(index + 1), count: Int32(steps.count)))
            }
        }
    }
}

private struct StepDot: View {
    let state: StepState
    let number: Int

    var body: some View {
        ZStack {
            switch state {
            case .done:
                Circle().fill(Palette.action)
                Image(systemName: "checkmark").font(.system(size: 14, weight: .bold)).foregroundStyle(.white)
            case .current:
                Circle().strokeBorder(Palette.action, lineWidth: 3)
                Text("\(number)").font(.subheadline.weight(.bold)).foregroundStyle(Palette.action)
            default:
                Circle().strokeBorder(Color.secondary.opacity(0.6), lineWidth: 1)
                Text("\(number)").font(.subheadline).foregroundStyle(.secondary)
            }
        }
        .frame(width: 32, height: 32)
    }
}

/// The in-app copy of the notification. Green once the order is ready or served; VoiceOver reads changes.
private struct StatusMessage: View {
    let state: OrderTicketUiState

    var body: some View {
        let green = Palette.badge(.openForLunch)
        HStack(spacing: 12) {
            Image(systemName: state.canConfirm || state.isCompleted ? "checkmark.circle.fill" : "info.circle")
                .font(.title3)
                .accessibilityHidden(true)
            Text(state.message).font(.body)
        }
        .foregroundStyle(state.canConfirm ? green.foreground : Color.primary)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(state.canConfirm ? green.background : Palette.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .onChange(of: state.message) { _, message in
            AccessibilityNotification.Announcement(message).post()
        }
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

/// Placeholder for Rate & Add to Favorites, which opens after [Confirm] closes an order.
/// RCPT-01 replaces this with the rating, the per-item Favorite toggles and Skip.
struct RateOrderPlaceholderView: View {
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            let colors = Palette.badge(.openForLunch)
            Image(systemName: "checkmark")
                .font(.system(size: 28, weight: .bold))
                .foregroundStyle(colors.foreground)
                .frame(width: 64, height: 64)
                .background(colors.background, in: Circle())
                .padding(.top, 24)
                .accessibilityHidden(true)
            Text("Thanks! Your order is complete.")
                .font(.title2.weight(.bold))
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Text("Next: rate your order and add items to your favorites. This step is a placeholder until Rate & Add to Favorites (RCPT-01) is built.")
                .font(.subheadline)
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Palette.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            Spacer()
            BottomAction(action: onDone) { CapsuleLabel(title: "Done") }
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Palette.screen)
        .navigationTitle("Rate & Add to Favorites")
        .navigationBarTitleDisplayMode(.inline)
        // The order is closed, so going back to its ticket isn't useful.
        .navigationBarBackButtonHidden()
    }
}
