import SwiftUI
import SharedLogic

/// The debug panel, as a native sheet. Only debug builds open it (`AppBuild.isDebug`).
/// The same controls as the Android panel: build info, mock data, live store changes and sign-in.
struct DebugPanelView: View {
    @StateObject private var model = DebugModel()

    var body: some View {
        let state = model.state
        let bridge = model.bridge
        NavigationStack {
            Form {
                Section {
                    LabeledContent("Platform", value: "iOS \(UIDevice.current.systemVersion)")
                    LabeledContent("Build", value: "debug")
                    LabeledContent("Data", value: "in-memory mock data, no network")
                } header: {
                    Text("Build")
                } footer: {
                    Text("Debug builds only. Release builds never show this panel.")
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
                    LabeledContent("Session", value: state.session)
                    Toggle("Force SSO failure", isOn: Binding(
                        get: { state.forceSsoFailure },
                        set: { bridge.setForceSsoFailure(on: $0) }
                    ))
                    LabeledContent("Test code", value: bridge.testCode)
                    LabeledContent("Test account", value: "\(bridge.testEmail) / \(bridge.testPassword)")
                    Button("Fill sample cart") { bridge.fillSampleCart() }
                } header: {
                    Text("Auth")
                } footer: {
                    Text("Fixed, obviously fake values from the seed data. Nothing is sent anywhere.")
                }
            }
            .navigationTitle("Debug Panel")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }
}
