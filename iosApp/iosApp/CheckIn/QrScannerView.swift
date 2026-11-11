import SwiftUI
import SharedLogic

/// The QR fallback. The mockup has no camera: the viewfinder is a placeholder, and codes come from the
/// debug panel. A store or table QR checks in; another store's code or a non-Passnize code shows an
/// error with Scan again.
struct QrScannerView: View {
    @StateObject private var model: QrScannerModel
    let onContinue: () -> Void
    @State private var showDebugPanel = false

    /// The camera area stays dark in both modes, as a live camera preview would be.
    private let cameraBackground = Color(white: 0.08)

    init(orderId: String, onContinue: @escaping () -> Void) {
        _model = StateObject(wrappedValue: QrScannerModel(orderId: orderId))
        self.onContinue = onContinue
    }

    var body: some View {
        let state = model.state
        VStack(spacing: 0) {
            ZStack {
                ViewfinderCorners()
                    .stroke(frameColor(state), style: StrokeStyle(lineWidth: 5, lineCap: .round))
                    .frame(width: 240, height: 240)
                    .animation(.easeInOut, value: state.title)
                if state.isScanning {
                    Text("Camera preview\n(not available in the mockup)")
                        .font(.subheadline)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.white.opacity(0.7))
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .accessibilityHidden(true)

            ResultPanel(
                state: state,
                onContinue: onContinue,
                onScanAgain: { model.viewModel.scanAgain() },
                onSimulate: AppBuild.isDebug ? { showDebugPanel = true } : nil
            )
        }
        .background(cameraBackground.ignoresSafeArea())
        .navigationTitle("Check in")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .toolbar {
            if AppBuild.isDebug {
                ToolbarItem(placement: .topBarTrailing) { DebugPanelButton() }
            }
        }
        .sheet(isPresented: $showDebugPanel) { DebugPanelView() }
        // A result arrives without a tap on this screen, so say it.
        .onChange(of: state.title) { _, title in
            AccessibilityNotification.Announcement(title).post()
        }
    }

    private func frameColor(_ state: QrScannerUiState) -> Color {
        if state.isCheckedIn { return .green }
        if state.isError { return .red }
        return .white
    }
}

private struct ResultPanel: View {
    let state: QrScannerUiState
    let onContinue: () -> Void
    let onScanAgain: () -> Void
    let onSimulate: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top, spacing: 12) {
                if let icon {
                    Image(systemName: icon.name)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(icon.colors.foreground)
                        .frame(width: 40, height: 40)
                        .background(icon.colors.background, in: Circle())
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text(state.title)
                        .font(.title3.weight(.bold))
                        .accessibilityAddTraits(.isHeader)
                    Text(state.message)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            .accessibilityElement(children: .combine)

            if state.isCheckedIn {
                prominentButton("Continue", action: onContinue)
            } else if state.isError {
                prominentButton("Scan again", action: onScanAgain)
            } else if let onSimulate {
                Button(action: onSimulate) {
                    Text("Simulate a scan (debug panel)")
                        .font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.bordered)
                .buttonBorderShape(.capsule)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.card, in: UnevenRoundedRectangle(topLeadingRadius: 32, topTrailingRadius: 32, style: .continuous))
        .background(Palette.card.ignoresSafeArea(edges: .bottom))
    }

    private func prominentButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.headline)
                .frame(maxWidth: .infinity, minHeight: 50)
        }
        .buttonStyle(.borderedProminent)
        .buttonBorderShape(.capsule)
    }

    private var icon: (name: String, colors: Palette.BadgeColors)? {
        if state.isCheckedIn { return ("checkmark", Palette.badge(.openForLunch)) }
        if state.isError { return ("exclamationmark", Palette.badge(.full)) }
        return nil
    }
}

/// The four corner brackets of a scanner viewfinder.
private struct ViewfinderCorners: Shape {
    func path(in rect: CGRect) -> Path {
        let corner: CGFloat = 40
        var path = Path()
        for (point, dx, dy) in [
            (CGPoint(x: rect.minX, y: rect.minY), corner, corner),
            (CGPoint(x: rect.maxX, y: rect.minY), -corner, corner),
            (CGPoint(x: rect.minX, y: rect.maxY), corner, -corner),
            (CGPoint(x: rect.maxX, y: rect.maxY), -corner, -corner),
        ] {
            path.move(to: CGPoint(x: point.x + dx, y: point.y))
            path.addLine(to: point)
            path.addLine(to: CGPoint(x: point.x, y: point.y + dy))
        }
        return path
    }
}
