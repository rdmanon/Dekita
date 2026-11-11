import SwiftUI
import SharedLogic

/// Check-in for an Eat-In draft, as the iOS Check-in artboard: the store on a map and a card with the order,
/// the queue and Start Cooking. SEAT-01 puts "Have you found a seat?" in front of cooking: Yes opens 1-tap
/// payment, and a full store or "Not yet" offers Switch to Takeout, Wait in line or Cancel / Exit.
/// Location is not checked in this mockup, so the customer is always shown as in range.
struct CheckInView: View {
    @EnvironmentObject private var flow: AuthFlow
    @StateObject private var model: CheckInModel
    @State private var showQrNote = false

    init(orderId: String) {
        _model = StateObject(wrappedValue: CheckInModel(orderId: orderId))
    }

    var body: some View {
        let state = model.state
        ZStack(alignment: .bottom) {
            CheckInMap(storeName: state.store?.name ?? state.order?.storeName ?? "")
            CheckInCard {
                if state.isLoading {
                    ProgressView("Loading your order…").frame(maxWidth: .infinity).padding(.vertical, 24)
                } else if let number = state.sentOrderNumber {
                    SentContent(
                        orderNumber: Int(truncating: number),
                        onViewOrders: { flow.showOrders() },
                        onDone: { flow.popToRoot() }
                    )
                } else if state.isUnavailable {
                    UnavailableContent(onDone: { flow.popToRoot() })
                } else {
                    ArrivedContent(
                        state: state,
                        onStartCooking: { model.viewModel.startCooking() },
                        onScanQr: { showQrNote = true }
                    )
                }
            }
        }
        .navigationTitle("Check in")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbar {
            if !state.isSent {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showQrNote = true } label: {
                        Label("Scan QR", systemImage: "qrcode.viewfinder").labelStyle(.titleAndIcon)
                    }
                }
            }
        }
        .alert(LabelsKt.SEAT_QUESTION, isPresented: seatQuestionPresented) {
            Button("Yes") { model.viewModel.answerSeat(hasSeat: true) }
            Button("Not yet") { model.viewModel.answerSeat(hasSeat: false) }
        } message: {
            Text(LabelsKt.SEAT_QUESTION_DETAIL)
        }
        .sheet(item: sheetPresented) { sheet in
            switch sheet {
            case .noSeat:
                NoSeatSheet(model: model)
                    .presentationDetents([.medium, .large])
            case .payment:
                PaymentSheet(model: model)
                    .presentationDetents([.medium, .large])
            }
        }
        .alert("QR check-in", isPresented: $showQrNote) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("QR check-in isn't built in this mockup yet.")
        }
        .onReceive(model.events) { event in
            if let switched = event as? CheckInEventSwitchedToTakeout {
                // Cart Review sits on the store's menu, so "Add more items" and Back lead there.
                let name = model.state.store?.name ?? model.state.order?.storeName ?? ""
                flow.replaceTop(with: .store(id: switched.storeId, name: name), .cart)
            } else if event is CheckInEventExited {
                // Cancel / Exit: the draft is kept in Orders and on the Discover banner.
                flow.popToRoot()
            }
        }
    }

    private var seatQuestionPresented: Binding<Bool> {
        Binding(
            get: { model.state.prompt == CheckInPrompt.seatQuestion },
            // Both buttons answer the question, so there is nothing to do when the alert closes.
            set: { _ in }
        )
    }

    private var sheetPresented: Binding<CheckInSheet?> {
        Binding(
            get: {
                switch model.state.prompt {
                case CheckInPrompt.noSeat: return .noSeat
                case CheckInPrompt.payment: return .payment
                default: return nil
                }
            },
            set: { if $0 == nil { model.viewModel.dismissPrompt() } }
        )
    }
}

private enum CheckInSheet: String, Identifiable {
    case noSeat, payment
    var id: String { rawValue }
}

/// The artboard's floating card at the bottom of the map.
private struct CheckInCard<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 14) { content }
            .padding(.horizontal, 20)
            .padding(.top, 22)
            .padding(.bottom, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 40, style: .continuous))
            .shadow(color: .black.opacity(0.2), radius: 30, y: 10)
            .padding(8)
    }
}

private struct ArrivedContent: View {
    let state: CheckInUiState
    let onStartCooking: () -> Void
    let onScanQr: () -> Void

    var body: some View {
        if let order = state.order {
            let storeName = state.store?.name ?? order.storeName
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text("You've arrived").font(.title2.weight(.bold)).accessibilityAddTraits(.isHeader)
                    Spacer()
                    InRangeBadge()
                }
                Text("\(storeName) · \(FulfillmentMode.inStore.choiceLabel)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            VStack(spacing: 0) {
                InfoRow(label: "Your order", value: order.itemsLabel)
                Divider().padding(.leading, 16)
                InfoRow(label: "Queue", value: queueText)
            }
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))

            if let notice = notice(storeName: storeName) {
                let colors = Palette.badge(state.isPaused || state.isStoreFull ? .full : .pickupOnly)
                Text(notice)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(colors.foreground)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(14)
                    .background(colors.background, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }

            Button(action: onStartCooking) {
                Text(state.isWaitingForSeat ? LabelsKt.FOUND_SEAT_BUTTON : "Start Cooking")
            }
            .buttonStyle(CapsuleButtonStyle())
            .disabled(!state.canStartCooking)

            Text("You pay when you tap Start Cooking.")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity)
            Button(action: onScanQr) {
                Label("Can't check in? Scan the store QR", systemImage: "qrcode")
                    .font(.subheadline.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .tint(Palette.action)
        }
    }

    private var queueText: String {
        if state.isPaused { return "Paused" }
        if state.store?.seatsFull == true { return "Seats full" }
        return state.store?.queue?.summary() ?? "—"
    }

    private func notice(storeName: String) -> String? {
        if state.isPaused { return LabelsKt.checkInPausedMessage(storeName: storeName) }
        if state.isWaitingForSeat { return LabelsKt.WAITING_FOR_SEAT_MESSAGE }
        if state.isStoreFull { return "\(LabelsKt.noSeatTitle(storeName: storeName, storeIsFull: true)). Start Cooking shows your options." }
        return nil
    }
}

private struct InRangeBadge: View {
    var body: some View {
        let colors = Palette.badge(.openForLunch)
        Label("In range", systemImage: "checkmark")
            .font(.footnote.weight(.semibold))
            .padding(.horizontal, 10)
            .frame(minHeight: 26)
            .foregroundStyle(colors.foreground)
            .background(colors.background, in: Capsule())
    }
}

private struct InfoRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label).foregroundStyle(.secondary)
            Spacer()
            Text(value).fontWeight(.semibold)
        }
        .font(.subheadline)
        .padding(.horizontal, 16)
        .frame(minHeight: 46)
        .accessibilityElement(children: .combine)
    }
}

/// The store is full, or the customer has no seat yet. None of the choices loses the order.
private struct NoSeatSheet: View {
    @ObservedObject var model: CheckInModel

    var body: some View {
        let state = model.state
        let storeName = state.store?.name ?? state.order?.storeName ?? ""
        NavigationStack {
            List {
                Section {
                    option(
                        title: LabelsKt.SWITCH_TO_TAKEOUT,
                        detail: state.takeoutUnavailableReason ?? LabelsKt.SWITCH_TO_TAKEOUT_DETAIL,
                        systemImage: "bag",
                        enabled: state.canSwitchToTakeout
                    ) { model.viewModel.switchToTakeout() }
                    option(
                        title: LabelsKt.WAIT_IN_LINE,
                        detail: LabelsKt.WAIT_IN_LINE_DETAIL,
                        systemImage: "clock",
                        enabled: true
                    ) { model.viewModel.waitInLine() }
                    option(
                        title: LabelsKt.CANCEL_EXIT,
                        detail: LabelsKt.CANCEL_EXIT_DETAIL,
                        systemImage: "xmark.circle",
                        enabled: true
                    ) { model.viewModel.exit() }
                } header: {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(LabelsKt.noSeatTitle(storeName: storeName, storeIsFull: state.isStoreFull))
                            .font(.title2.weight(.bold))
                            .foregroundStyle(.primary)
                            .textCase(nil)
                            .accessibilityAddTraits(.isHeader)
                        Text(LabelsKt.NO_SEAT_DETAIL).font(.subheadline).foregroundStyle(.secondary).textCase(nil)
                    }
                    .padding(.bottom, 8)
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { model.viewModel.dismissPrompt() } label: { Image(systemName: "xmark") }
                        .accessibilityLabel("Close")
                }
            }
            .navigationBarTitleDisplayMode(.inline)
        }
        .confirmationDialog("Replace your cart?", isPresented: replacePresented, titleVisibility: .visible) {
            Button("Replace cart") { model.viewModel.confirmReplaceCart() }
            Button("Keep cart", role: .cancel) { model.viewModel.dismissReplaceCart() }
        } message: {
            Text("Your cart has items from \(state.pendingReplaceCartStoreName ?? "another store"). Switching to Takeout puts this order in the cart instead.")
        }
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
            }
            .padding(.vertical, 6)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.5)
    }

    private var replacePresented: Binding<Bool> {
        Binding(
            get: { model.state.pendingReplaceCartStoreName != nil },
            set: { if !$0 { model.viewModel.dismissReplaceCart() } }
        )
    }
}

/// 1-tap payment, as the iOS Payment artboard. Apple Pay is already picked, so one tap on Pay sends the order.
private struct PaymentSheet: View {
    @ObservedObject var model: CheckInModel

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
                    Button { model.viewModel.dismissPrompt() } label: { Image(systemName: "xmark") }
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
            .font(.system(size: 40))
            .foregroundStyle(Palette.badge(.openForLunch).foreground)
            .accessibilityHidden(true)
        Text(LabelsKt.sentToKitchenTitle(orderNumber: Int32(orderNumber)))
            .font(.title2.weight(.bold))
            .accessibilityAddTraits(.isHeader)
        Text(LabelsKt.SENT_TO_KITCHEN_DETAIL).font(.subheadline).foregroundStyle(.secondary)
        Button("View order", action: onViewOrders).buttonStyle(CapsuleButtonStyle())
        Button("Back to Discover", action: onDone)
            .font(.subheadline.weight(.semibold))
            .tint(Palette.action)
            .frame(maxWidth: .infinity, minHeight: 44)
    }
}

private struct UnavailableContent: View {
    let onDone: () -> Void

    var body: some View {
        Text("This order can't be checked in").font(.title2.weight(.bold)).accessibilityAddTraits(.isHeader)
        Text("It's no longer a draft. It may already be in the kitchen or back in your cart.")
            .font(.subheadline)
            .foregroundStyle(.secondary)
        Button("Back to Discover", action: onDone).buttonStyle(CapsuleButtonStyle(kind: .secondary))
    }
}

/// The artboard's illustrated map, drawn in its own 393 x 852 space and scaled to cover the screen.
/// A fixed picture, not a real map: the mockup has no location or map SDK.
private struct CheckInMap: View {
    let storeName: String

    private static let size = CGSize(width: 393, height: 852)
    private static let store = CGPoint(x: 214, y: 318)
    private static let you = CGPoint(x: 196, y: 376)
    private static let ground = Color(light: 0xECE6DC, dark: 0xECE6DC)
    private static let block = Color(light: 0xE2DACD, dark: 0xE2DACD)
    private static let park = Color(light: 0xD5E6C9, dark: 0xD5E6C9)
    private static let brand = Color(light: 0xC2410C, dark: 0xC2410C)
    private static let blue = Color(light: 0x0A84FF, dark: 0x0A84FF)

    var body: some View {
        GeometryReader { geo in
            let scale = max(geo.size.width / Self.size.width, geo.size.height / Self.size.height)
            let dx = (geo.size.width - Self.size.width * scale) / 2
            let dy = (geo.size.height - Self.size.height * scale) / 2
            let pin = CGPoint(x: dx + Self.store.x * scale, y: dy + Self.store.y * scale)
            ZStack(alignment: .topLeading) {
                Canvas { context, _ in
                    context.translateBy(x: dx, y: dy)
                    context.scaleBy(x: scale, y: scale)
                    context.fill(Path(CGRect(origin: .zero, size: Self.size)), with: .color(Self.ground))
                    let blocks: [(CGRect, Color)] = [
                        (CGRect(x: 18, y: 40, width: 120, height: 140), Self.block),
                        (CGRect(x: 186, y: 30, width: 190, height: 130), Self.block),
                        (CGRect(x: 20, y: 226, width: 116, height: 196), Self.park),
                        (CGRect(x: 292, y: 210, width: 90, height: 210), Self.block),
                        (CGRect(x: 20, y: 472, width: 140, height: 150), Self.block),
                        (CGRect(x: 206, y: 474, width: 176, height: 160), Self.block),
                        (CGRect(x: 20, y: 676, width: 140, height: 160), Self.block),
                        (CGRect(x: 206, y: 676, width: 176, height: 160), Self.park),
                    ]
                    for (rect, color) in blocks {
                        context.fill(Path(roundedRect: rect, cornerRadius: 10), with: .color(color))
                    }
                    let roads: [(CGPoint, CGPoint, CGFloat)] = [
                        (CGPoint(x: -20, y: 200), CGPoint(x: 420, y: 182), 22),
                        (CGPoint(x: -20, y: 448), CGPoint(x: 420, y: 436), 22),
                        (CGPoint(x: 158, y: -20), CGPoint(x: 176, y: 880), 22),
                        (CGPoint(x: 270, y: -20), CGPoint(x: 256, y: 880), 22),
                        (CGPoint(x: -20, y: 654), CGPoint(x: 420, y: 646), 10),
                        (CGPoint(x: 64, y: -20), CGPoint(x: 82, y: 880), 10),
                    ]
                    for (from, to, width) in roads {
                        var road = Path()
                        road.move(to: from)
                        road.addLine(to: to)
                        context.stroke(road, with: .color(.white), lineWidth: width)
                    }
                    // The check-in range around the store, and the customer inside it.
                    let range = Path(ellipseIn: CGRect(x: Self.store.x - 92, y: Self.store.y - 92, width: 184, height: 184))
                    context.fill(range, with: .color(Self.brand.opacity(0.08)))
                    context.stroke(range, with: .color(Self.brand.opacity(0.45)), style: StrokeStyle(lineWidth: 2, dash: [6, 6]))
                    context.fill(Path(ellipseIn: CGRect(x: Self.you.x - 24, y: Self.you.y - 24, width: 48, height: 48)), with: .color(Self.blue.opacity(0.16)))
                    context.fill(Path(ellipseIn: CGRect(x: Self.you.x - 11, y: Self.you.y - 11, width: 22, height: 22)), with: .color(.white))
                    context.fill(Path(ellipseIn: CGRect(x: Self.you.x - 8, y: Self.you.y - 8, width: 16, height: 16)), with: .color(Self.blue))
                }
                Text(storeName)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color(light: 0x1C1C1E, dark: 0x1C1C1E))
                    .lineLimit(1)
                    .padding(.horizontal, 10)
                    .frame(height: 26)
                    .background(Color.white, in: Capsule())
                    .shadow(color: .black.opacity(0.15), radius: 4, y: 2)
                    .position(x: pin.x, y: pin.y - 43)
                Image(systemName: "fork.knife")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 48, height: 48)
                    .background(Self.brand, in: Circle())
                    .overlay(Circle().stroke(Color.white, lineWidth: 3))
                    .shadow(color: .black.opacity(0.25), radius: 8, y: 6)
                    .position(pin)
            }
        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }
}
