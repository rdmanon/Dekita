import SwiftUI
import SharedLogic

extension EnvironmentValues {
    /// Opens check-in for an in-store draft. ContentView presents it over every tab.
    @Entry var openCheckIn: (String) -> Void = { _ in }
}

/// Check-in from "On your way" to the seat check, with the QR scanner as the fallback.
/// Closing it at any point keeps the draft, which Orders and the Discover banner both resume.
/// Paying at the seat check swaps this flow for the order's ticket.
struct CheckInFlow: View {
    let orderId: String
    @StateObject private var model: CheckInModel
    @State private var path: [Step] = []
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openOrderTicket) private var openOrderTicket

    private enum Step: Hashable {
        case scanner, seatCheck
    }

    init(orderId: String) {
        self.orderId = orderId
        _model = StateObject(wrappedValue: CheckInModel(orderId: orderId))
    }

    var body: some View {
        NavigationStack(path: $path) {
            CheckInView(
                model: model,
                onBack: { dismiss() },
                onScanQr: { path.append(.scanner) },
                onCheckedIn: { path.append(.seatCheck) }
            )
            .navigationDestination(for: Step.self) { step in
                switch step {
                case .scanner:
                    // The scanner is done once it checks in, so Back from the seat check returns to check-in.
                    QrScannerView(orderId: orderId) { path = [.seatCheck] }
                case .seatCheck:
                    SeatCheckView(model: model) {
                        if let paidOrderId = model.viewModel.payAtCheckIn() { openOrderTicket(paidOrderId) }
                    }
                }
            }
        }
    }
}
