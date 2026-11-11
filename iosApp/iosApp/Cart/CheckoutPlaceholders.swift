import SwiftUI

/// Stand-in for payment (PAY-01). Back returns to Cart Review with the cart as it was.
struct PaymentPlaceholderView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ContentUnavailableView {
            Label("Payment comes later", systemImage: "creditcard")
        } description: {
            Text("Payment is a later ticket (PAY-01). Your cart is kept, and a failed or cancelled payment returns to Cart Review.")
        } actions: {
            Button("Back to cart") { dismiss() }
                .buttonStyle(.bordered)
        }
        .navigationTitle("Payment")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Stand-in for check-in (CHECKIN-01), shown after Eat-In saves a DRAFT order.
struct CheckInPlaceholderView: View {
    let orderNumber: Int
    let storeName: String
    @EnvironmentObject private var navigation: NavigationModel

    var body: some View {
        ContentUnavailableView {
            Label("Draft order #\(orderNumber) saved", systemImage: "mappin.and.ellipse")
        } description: {
            Text("Head to \(storeName). You pay and cooking starts when you check in with Start Cooking. Check-in is a later ticket (CHECKIN-01); the draft is in the Orders tab.")
        } actions: {
            Button("Done") { navigation.popToRoot() }
                .modifier(ProminentGlassButton())
        }
        .navigationTitle("Check in")
        .navigationBarTitleDisplayMode(.inline)
    }
}
