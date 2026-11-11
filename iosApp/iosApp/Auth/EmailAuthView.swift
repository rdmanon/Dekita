import SwiftUI
import SharedLogic

/// Email sign-up and log-in on one screen; a link switches between them. Errors appear on submit.
/// The password lives in plain `@State` and the view model only, never in saved state.
struct EmailAuthView: View {
    @EnvironmentObject private var flow: AuthFlow
    @StateObject private var model: EmailAuthModel
    @State private var email = ""
    @State private var password = ""
    @State private var showPassword = false

    init(signUp: Bool) {
        _model = StateObject(wrappedValue: EmailAuthModel(signUp: signUp))
    }

    var body: some View {
        let state = model.state
        let signUp = state.mode == .signUp
        let action = signUp ? "Create Account" : "Log In"
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                if let formError = state.formError {
                    ErrorCard(message: formError).padding(.bottom, 8)
                }
                FieldBox(isError: state.emailError != nil) {
                    TextField("Email", text: $email)
                        .keyboardType(.emailAddress)
                        .textContentType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .onChange(of: email) { _, typed in model.viewModel.onEmailChange(email: typed) }
                }
                FieldMessage(error: state.emailError)

                FieldBox(isError: state.passwordError != nil) {
                    HStack {
                        Group {
                            if showPassword {
                                TextField("Password", text: $password)
                            } else {
                                SecureField("Password", text: $password)
                            }
                        }
                        .textContentType(signUp ? .newPassword : .password)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .onSubmit { model.viewModel.submit() }
                        Button { showPassword.toggle() } label: {
                            Image(systemName: showPassword ? "eye.slash" : "eye").foregroundStyle(.secondary)
                        }
                        .frame(minWidth: 44, minHeight: 44)
                        .accessibilityLabel(showPassword ? "Hide password" : "Show password")
                    }
                    .onChange(of: password) { _, typed in model.viewModel.onPasswordChange(password: typed) }
                }
                FieldMessage(
                    error: state.passwordError,
                    hint: signUp ? "At least 8 characters, with a number and a letter." : nil
                )

                if !signUp {
                    NavigationLink("Forgot password?", value: AuthRoute.forgotPassword)
                        .frame(minHeight: 44)
                }
                Button(signUp ? "Already have an account? Log In" : "New here? Create an Account") {
                    model.viewModel.switchMode()
                }
                .frame(minHeight: 44)
                if signUp {
                    Text("Mockup: the account lives in memory only and is gone when the app closes.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(16)
        }
        .background(Palette.screen)
        .navigationTitle(action)
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            BottomAction(action: { model.viewModel.submit() }) {
                CapsuleLabel(title: action, loading: state.isSubmitting)
            }
        }
        // Switching mode clears the password in the view model; clear the field with it.
        .onChange(of: state.mode) { _, _ in password = "" }
        .onReceive(model.events) { event in
            if event is AuthEventSignedIn { flow.finishSignIn() }
        }
    }
}
