import SwiftUI
import SharedLogic

// Stand-ins for the providers' own screens (PAY-01). No SDK, app switch or network: each only approves or cancels.
// They are labelled as mocks and use no provider logos, so nobody mistakes them for the real thing.

/// The PayPay or Rakuten Pay page the customer is sent to, full screen.
struct MockRedirectView: View {
    let method: PaymentMethod
    let storeName: String
    let amount: String
    let onApprove: () -> Void
    let onCancel: () -> Void

    var body: some View {
        let provider = method.type.providerName
        NavigationStack {
            VStack(spacing: 12) {
                Text(String(provider.prefix(1)))
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(Palette.action)
                    .frame(width: 72, height: 72)
                    .background(Palette.tileIcon, in: Circle())
                    .padding(.top, 32)
                    .accessibilityHidden(true)
                Text("\(storeName) is asking for a payment")
                Text(amount).font(.system(size: 40, weight: .bold))
                Text("From \(method.label)").font(.subheadline).foregroundStyle(.secondary)
                Spacer()
                Text("This stands in for the \(provider) app. No money moves.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                Button(action: onApprove) { CapsuleLabel(title: "Approve") }
                    .buttonStyle(CapsuleButtonStyle())
                Button(action: onCancel) { CapsuleLabel(title: "Cancel") }
                    .buttonStyle(CapsuleButtonStyle(kind: .secondary))
            }
            .padding(16)
            .frame(maxWidth: .infinity)
            .background(Palette.screen)
            .navigationTitle("\(provider) (mock)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: onCancel)
                }
            }
        }
    }
}

/// The Apple Pay sheet, as a half-height sheet over the payment sheet. Swiping it away cancels.
struct MockWalletSheet: View {
    let method: PaymentMethod
    let storeName: String
    let amount: String
    let onApprove: () -> Void
    let onCancel: () -> Void

    var body: some View {
        let provider = method.type.providerName
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("\(provider) (mock)").font(.title3.weight(.bold)).accessibilityAddTraits(.isHeader)
                Spacer()
                Button("Cancel", action: onCancel)
            }
            HStack(spacing: 12) {
                PaymentMethodIcon(type: method.type)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Test account").font(.body.weight(.semibold))
                    Text("This stands in for \(provider). No money moves.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            HStack(alignment: .firstTextBaseline) {
                Text("Pay \(storeName)").foregroundStyle(.secondary)
                Spacer()
                Text(amount).font(.title2.weight(.bold))
            }
            .accessibilityElement(children: .combine)
            Spacer(minLength: 0)
            Button(action: onApprove) { CapsuleLabel(title: "Pay \(amount)") }
                .buttonStyle(CapsuleButtonStyle())
        }
        .padding(20)
        .background(Palette.screen)
    }
}

/// The demo add-card form (PAY-01). Demo only, and must not become the production card form: a real build uses
/// the payment provider's SDK or hosted fields. What is typed lives in this view's `@State` only, is never stored
/// or logged, and is dropped when the sheet closes. `onSave` returns the problem, or nil once the card is added.
struct AddTestCardView: View {
    let onSave: (_ number: String, _ expiry: String, _ cvc: String) -> CardFormError?
    @Environment(\.dismiss) private var dismiss
    @State private var number = ""
    @State private var expiry = ""
    @State private var cvc = ""
    @State private var error: CardFormError?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Card number", text: $number)
                        .keyboardType(.numberPad)
                        .onChange(of: number) { _, value in
                            let kept = String(value.filter { $0.isNumber || $0 == " " }.prefix(23))
                            if kept != value { number = kept }
                        }
                    HStack {
                        TextField("MM/YY", text: $expiry)
                            .keyboardType(.numbersAndPunctuation)
                            .onChange(of: expiry) { _, value in
                                let kept = String(value.filter { $0.isNumber || $0 == "/" }.prefix(5))
                                if kept != value { expiry = kept }
                            }
                        SecureField("CVC", text: $cvc)
                            .keyboardType(.numberPad)
                            .onChange(of: cvc) { _, value in
                                let kept = String(value.filter { $0.isNumber }.prefix(4))
                                if kept != value { cvc = kept }
                            }
                    }
                } footer: {
                    if let error {
                        Text(error.message).foregroundStyle(Palette.error)
                    } else {
                        Text("Demo only. Use a published test number such as 4242 4242 4242 4242. Nothing you type is saved or sent.")
                    }
                }
            }
            .autocorrectionDisabled()
            .navigationTitle("Add a Test Card")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") {
                        error = onSave(number, expiry, cvc)
                        if error == nil { dismiss() }
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
