import SwiftUI

/// Stand-in for check-in (CHECKIN-01), shown after Eat-In saves a DRAFT order.
struct CheckInPlaceholderView: View {
    @EnvironmentObject private var flow: AuthFlow
    let orderNumber: Int
    let storeName: String

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Draft order #\(orderNumber) saved").font(.title2.weight(.bold))
                Text("Head to \(storeName). You pay and cooking starts when you check in with Start Cooking.")
                    .font(.body)
                Text("Check-in comes in a later ticket (CHECKIN-01). The draft is in the Orders tab.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
        }
        .background(Palette.screen)
        .safeAreaInset(edge: .bottom) {
            BottomAction(action: { flow.popToRoot() }) {
                CapsuleLabel(title: "Done")
            }
        }
        .navigationTitle("Check in")
        .navigationBarTitleDisplayMode(.inline)
    }
}
