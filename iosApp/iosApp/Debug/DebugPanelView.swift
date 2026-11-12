import SwiftUI
import SharedLogic

/// The debug panel, as a native sheet. Only debug builds open it (`AppBuild.isDebug`).
/// The same controls as the Android panel: live availability, build info, mock data, Discover, sign-in and check-in.
struct DebugPanelView: View {
    @StateObject private var model = DebugModel()
    @State private var tableNumber = ""

    var body: some View {
        let state = model.state
        let bridge = model.bridge
        NavigationStack {
            Form {
                // First, so it shows at half height over the screen being checked.
                availabilitySections(state: state, bridge: bridge)

                Section {
                    LabeledContent("Platform", value: "iOS \(UIDevice.current.systemVersion)")
                    LabeledContent("Build", value: "debug")
                    LabeledContent("Data", value: "in-memory mock data")
                } header: {
                    Text("Build")
                } footer: {
                    Text("Debug builds only. Release builds never show this panel. Only the check-in map loads from the network.")
                }

                Section("Mock data") {
                    Picker("Loading delay", selection: Binding(
                        get: { state.loadingDelayMillis },
                        set: { bridge.setLoadingDelayMillis(millis: $0) }
                    )) {
                        ForEach(bridge.loadingDelayOptionsMillis.map(\.int64Value), id: \.self) { millis in
                            Text(millis == 0 ? "0s" : millis < 1000 ? "\(millis)ms" : "\(millis / 1000)s").tag(millis)
                        }
                    }
                    .pickerStyle(.segmented)
                    Toggle("Live queue updates", isOn: Binding(get: { state.liveQueue }, set: { bridge.setLiveQueue(on: $0) }))
                    Toggle("Live order status", isOn: Binding(get: { state.liveOrders }, set: { bridge.setLiveOrders(on: $0) }))
                    Button("Reset mock data") { bridge.resetMockData() }
                }

                Section {
                    Button("Simulate store feed error") { bridge.failNextStoreLoad() }
                    Button("Simulate new user (no favorites or history)") { bridge.simulateNewUser() }
                } header: {
                    Text("Discover")
                }

                Section {
                    Button("Fill sample cart") { bridge.fillSampleCart() }
                } header: {
                    Text("Cart")
                } footer: {
                    Text("Live availability starts on the cart's store.")
                }

                Section {
                    LabeledContent("Session", value: state.session)
                    Toggle("Force SSO failure", isOn: Binding(
                        get: { state.forceSsoFailure },
                        set: { bridge.setForceSsoFailure(on: $0) }
                    ))
                    LabeledContent("Test code", value: bridge.testCode)
                    LabeledContent("Test account", value: "\(bridge.testEmail) / \(bridge.testPassword)")
                } header: {
                    Text("Auth")
                } footer: {
                    Text("Fixed, obviously fake values from the seed data. Nothing is sent anywhere.")
                }

                checkInSection(state: state, bridge: bridge)
                seatSection(state: state, bridge: bridge)

                Section {
                    Toggle("Decline every payment", isOn: Binding(
                        get: { state.forcePaymentDecline },
                        set: { bridge.setForcePaymentDecline(on: $0) }
                    ))
                } header: {
                    Text("Payments")
                } footer: {
                    Text("Without this, \"Test Visa •••• 0002\" always declines and the rest succeed. Add Card takes published test numbers only, such as 4242 4242 4242 4242.")
                }
            }
            .navigationTitle("Debug Panel")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
        .onAppear { if tableNumber.isEmpty { tableNumber = bridge.defaultTableNumber } }
    }

    /// AVAIL-01: what a store's own app can change at any moment. Every change reaches the screens that are
    /// already open (Discover, the store, the item sheet, Cart Review) with no refresh.
    @ViewBuilder
    private func availabilitySections(state: DebugState, bridge: DebugBridge) -> some View {
        Section {
            if let store = state.availabilityStore, let stores = state.stores {
                Picker("Store", selection: Binding(
                    get: { store.id },
                    set: { bridge.selectAvailabilityStore(storeId: $0) }
                )) {
                    ForEach(stores, id: \.id) { Text($0.name).tag($0.id) }
                }
                .pickerStyle(.menu)
                Text(state.availabilityStatus).foregroundStyle(.secondary)

                let accepting = store.availability != .paused
                Button(accepting ? "Stop Accepting Orders" : "Resume Accepting Orders") {
                    bridge.setAcceptingOrders(accepting: !accepting)
                }

                Picker("Service mode", selection: Binding(
                    get: { store.businessType },
                    set: { bridge.setBusinessType(type: $0) }
                )) {
                    ForEach(bridge.businessTypes, id: \.self) { Text(bridge.businessTypeLabel(type: $0)).tag($0) }
                }
                .pickerStyle(.segmented)

                if let queue = store.queue {
                    HStack {
                        Text("Queue: \(queue.ordersAhead) ahead")
                        Spacer()
                        Button("−1") { bridge.adjustQueue(delta: -1) }.buttonStyle(.borderless)
                        Button("+1") { bridge.adjustQueue(delta: 1) }.buttonStyle(.borderless)
                        Button("+5") { bridge.adjustQueue(delta: 5) }.buttonStyle(.borderless)
                    }
                } else {
                    Text("No queue while the store isn't accepting orders.").foregroundStyle(.secondary)
                }
            } else {
                Text("The store feed is not loaded.")
            }
        } header: {
            Text("Live availability")
        } footer: {
            Text("Changes reach open screens at once, with no refresh. Open the panel over a screen to watch it change.")
        }

        if state.availabilityStore != nil {
            Section("Menu items") {
                if state.availabilityItems.isEmpty {
                    Text("Loading the menu…").foregroundStyle(.secondary)
                }
                ForEach(state.availabilityItems, id: \.id) { item in
                    sellingRow(name: item.name, soldOut: item.isSoldOut) {
                        bridge.setItemSoldOut(item: item, soldOut: $0)
                    }
                }
            }
            if !state.availabilityOptions.isEmpty {
                Section("Options") {
                    ForEach(state.availabilityOptions, id: \.id) { option in
                        sellingRow(name: option.name, soldOut: option.isSoldOut) {
                            bridge.setOptionSoldOut(option: option, soldOut: $0)
                        }
                    }
                }
            }
        }
    }

    /// An item or option with its Stop Selling / Resume Selling switch.
    private func sellingRow(name: String, soldOut: Bool, onSet: @escaping (Bool) -> Void) -> some View {
        HStack {
            Text(soldOut ? "\(name) (sold out)" : name)
                .foregroundStyle(soldOut ? .secondary : .primary)
                .lineLimit(1)
            Spacer()
            Button(soldOut ? "Resume Selling" : "Stop Selling") { onSet(!soldOut) }
                .buttonStyle(.borderless)
                .font(.footnote)
        }
    }

    /// SEAT-01: "seats full" per Eat-In store, to try the seat check's fallbacks.
    private func seatSection(state: DebugState, bridge: DebugBridge) -> some View {
        Section {
            if let stores = state.stores {
                ForEach(stores.filter { $0.supports(mode: .inStore) }, id: \.id) { store in
                    HStack {
                        Text(store.name).lineLimit(1)
                        Spacer()
                        Button(store.seatsFull ? "Free seats" : "Seats full") { bridge.toggleSeatsFull(store: store) }
                            .buttonStyle(.borderless)
                            .font(.footnote)
                    }
                }
            } else {
                Text("The store feed is not loaded.")
            }
        } header: {
            Text("Seat check")
        } footer: {
            Text("A store with full seats skips \"Have you found a seat?\" and offers Takeout, waiting or leaving.")
        }
    }

    /// CHECKIN-01: arrival and QR scans, in place of GPS and the camera. Each acts on the in-store draft.
    @ViewBuilder
    private func checkInSection(state: DebugState, bridge: DebugBridge) -> some View {
        Section {
            if let status = state.checkInStatus {
                Text(status)
                Button("Simulate arrival") { bridge.simulateArrival() }
                Button("Simulate leaving") { bridge.simulateLeaving() }
                Button("Simulate store QR") { bridge.scanStoreQr() }
                HStack {
                    TextField("Table", text: $tableNumber)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .onChange(of: tableNumber) { _, value in
                            if value.count > 4 { tableNumber = String(value.prefix(4)) }
                        }
                    Button("Simulate table QR") { bridge.scanTableQr(tableNumber: tableNumber) }
                        .buttonStyle(.borderless)
                        .disabled(!bridge.isValidTableNumber(tableNumber: tableNumber))
                }
                Button("Simulate other-store QR") { bridge.scanOtherStoreQr() }
                Button("Simulate invalid QR") { bridge.scanInvalidQr() }
            } else {
                Text("No in-store draft to check in. Reset mock data to get the seed draft back.")
            }
        } header: {
            Text("Check-in")
        } footer: {
            Text(bridge.isValidTableNumber(tableNumber: tableNumber) || state.checkInStatus == nil
                ? "QR scans reach the scanner only while it's open. A result pauses scanning until Scan again."
                : "Table numbers are 1–4 letters or digits.")
        }
    }
}
