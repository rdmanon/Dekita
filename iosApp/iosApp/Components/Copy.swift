import SharedLogic

/// Button text the iOS artboards write in title case. The shared labels are sentence case, which is
/// what the Android design uses, so the two platforms differ only here.
enum Copy {
    static func addToCart(total: Money) -> String { "Add to Cart · \(total.formatted())" }
    static func update(total: Money) -> String { LabelsKt.updateButtonLabel(total: total) }
    static let headToStore = "Head to Store"
    static let payNow = "Pay Now"
    static let startCooking = "Start Cooking"
    static let confirmReceived = "Confirm Received"

    /// Shared hints say "Start cooking" (Android's spelling). The button on iOS says "Start Cooking",
    /// so the hint next to it has to match.
    static func titleCased(_ hint: String) -> String {
        hint.replacingOccurrences(of: "Start cooking", with: startCooking)
    }
}
