import SwiftUI
import SharedLogic

/// Placeholder for seat verification, which opens after a successful check-in. SEAT-01 replaces this
/// with the real prompt (seat available, switch to Pickup, wait, or cancel) before the kitchen starts.
/// Its button pays with a mock payment, which PAY-01 replaces.
struct SeatCheckView: View {
    @ObservedObject var model: CheckInModel
    let onPay: () -> Void

    var body: some View {
        let state = model.state
        Group {
            if let checkIn = state.checkIn {
                VStack(spacing: 16) {
                    let colors = Palette.badge(.openForLunch)
                    Image(systemName: "checkmark")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundStyle(colors.foreground)
                        .frame(width: 64, height: 64)
                        .background(colors.background, in: Circle())
                        .padding(.top, 24)
                        .accessibilityHidden(true)
                    Text(checkIn.label)
                        .font(.title2.weight(.bold))
                        .accessibilityAddTraits(.isHeader)
                    Text(state.storeLine)
                        .foregroundStyle(.secondary)
                    Text("Next: confirm there's a seat for you before the kitchen starts cooking. This step is a placeholder until seat verification (SEAT-01) is built, and payment is simulated until PAY-01.")
                        .font(.subheadline)
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Palette.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                    Spacer()
                    Button(action: onPay) {
                        Text("Pay now · \(state.draft?.total.formatted() ?? "")")
                            .font(.headline)
                            .frame(maxWidth: .infinity, minHeight: 50)
                    }
                    .buttonStyle(.borderedProminent)
                    .buttonBorderShape(.capsule)
                }
                .padding(16)
            } else {
                ContentUnavailableView(
                    "Nothing to check in",
                    systemImage: "fork.knife",
                    description: Text("This order isn't waiting for check-in any more.")
                )
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Palette.screen)
        .navigationTitle("Seat check")
        .navigationBarTitleDisplayMode(.inline)
    }
}
