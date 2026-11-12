import SwiftUI
import SharedLogic

/// The debug panel, as a native sheet. Only debug builds open it (`AppBuild.isDebug`).
/// The same controls as the Android panel: build info, mock data, live store changes, sign-in and check-in.
struct DebugPanelView: View {
    @StateObject private var model = DebugModel()
    @State private var tableNumber = ""

    var body: some View {
        let state = model.state
        let bridge = model.bridge
        NavigationStack {
            Form {
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
                    Button("Reset mock data") { bridge.resetMockData() }
                }

                Section {
                    if let stores = state.stores {
                        ForEach(stores, id: \.id) { store in
                            HStack {
                                Text(store.name).lineLimit(1)
                                Spacer()
                                Button(store.availability == .paused ? "Resume orders" : "Stop Accepting Orders") {
                                    bridge.togglePaused(store: store)
                                }
                                .buttonStyle(.borderless)
                                .font(.footnote)
                            }
                        }
                    } else {
                        Text("The store feed is not loaded.")
                    }
                    Button("Simulate store feed error") { bridge.failNextStoreLoad() }
                    Button("Simulate new user (no favorites or history)") { bridge.simulateNewUser() }
                } header: {
                    Text("Discover")
                } footer: {
                    Text("Changes reach Discover at once, with no refresh.")
                }

                Section {
                    Button("Fill sample cart") { bridge.fillSampleCart() }
                    HStack {
                        Text("Queue at the cart's store")
                        Spacer()
                        Button("−1") { bridge.adjustCartQueue(delta: -1) }.buttonStyle(.borderless)
                        Button("+1") { bridge.adjustCartQueue(delta: 1) }.buttonStyle(.borderless)
                    }
                    ForEach(state.cartItems, id: \.id) { item in
                        HStack {
                            Text(item.name).lineLimit(1)
                            Spacer()
                            Button(item.isSoldOut ? "Resume" : "Stop Selling") { bridge.toggleSoldOut(item: item) }
                                .buttonStyle(.borderless)
                                .font(.footnote)
                        }
                    }
                } header: {
                    Text("Cart")
                } footer: {
                    Text("Changes reach Cart Review at once: the live queue, and sold-out lines that block checkout.")
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
            }
            .navigationTitle("Debug Panel")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
        .onAppear { if tableNumber.isEmpty { tableNumber = bridge.defaultTableNumber } }
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
