import SwiftUI

/// Opens the debug panel as a sheet over the current screen, which keeps updating underneath.
/// Callers show it only in debug builds (`AppBuild.isDebug`).
struct DebugPanelButton: View {
    @State private var showDebugPanel = false

    var body: some View {
        Button { showDebugPanel = true } label: {
            Label("Debug panel", systemImage: "ladybug")
        }
        .sheet(isPresented: $showDebugPanel) { DebugPanelView() }
    }
}
