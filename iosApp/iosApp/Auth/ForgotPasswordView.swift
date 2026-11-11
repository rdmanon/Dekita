import SwiftUI
import SharedLogic

/// Password recovery. The confirmation is the same whether or not the email has an account.
struct ForgotPasswordView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var model = ForgotPasswordModel()
    @State private var email = ""

    var body: some View {
        let state = model.state
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if state.isSent {
                    Image(systemName: "checkmark.circle")
                        .font(.system(size: 44))
                        .foregroundStyle(Color.accentColor)
                        .accessibilityHidden(true)
                    Text("Check your email").font(.title2.weight(.bold))
                    Text("If an account exists for that email, we've sent a link to reset your password.")
                    Text("Mockup: no email is sent.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else {
                    Text("Enter the email you use for your account. We'll send a link to reset your password.")
                        .foregroundStyle(.secondary)
                        .padding(.bottom, 4)
                    FieldBox(isError: state.emailError != nil) {
                        TextField("Email", text: $email)
                            .keyboardType(.emailAddress)
                            .textContentType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .onSubmit { model.viewModel.send() }
                            .onChange(of: email) { _, typed in model.viewModel.onEmailChange(email: typed) }
                    }
                    FieldMessage(error: state.emailError)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
        }
        .background(Palette.screen)
        .navigationTitle("Reset Password")
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            if state.isSent {
                BottomAction(action: { dismiss() }) { CapsuleLabel(title: "Back to Log In") }
            } else {
                BottomAction(action: { model.viewModel.send() }) {
                    CapsuleLabel(title: "Send Reset Link", loading: state.isSending)
                }
            }
        }
        .onChange(of: state.isSent) { _, sent in
            if sent {
                UIAccessibility.post(
                    notification: .announcement,
                    argument: "If an account exists for that email, we've sent a link to reset your password."
                )
            }
        }
    }
}
