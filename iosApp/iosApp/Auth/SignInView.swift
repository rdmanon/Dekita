import SwiftUI
import SharedLogic

/// Steps pushed inside the sign-in flow.
enum AuthRoute: Hashable {
    /// The code step. `contactKey` is `AuthContact.key`: an email address, or a phone number in E.164 form.
    case otp(contactKey: String)
}

/// The sign-in flow in its own navigation stack: a sheet, or full screen after sign-out (`isRoot`).
struct SignInFlowView: View {
    let isRoot: Bool
    @State private var path = NavigationPath()

    var body: some View {
        NavigationStack(path: $path) {
            SignInView(isRoot: isRoot) { path.append(AuthRoute.otp(contactKey: $0)) }
                .navigationDestination(for: AuthRoute.self) { route in
                    switch route {
                    case let .otp(contactKey): OtpView(contactKey: contactKey)
                    }
                }
        }
        .interactiveDismissDisabled(isRoot)
    }
}

/// The Sign-In screen: one field for a phone number or an email, then Google and Apple. "Send Code" goes to the
/// code step for either. Apple is always offered, as the App Store requires next to other third-party sign-in.
/// Google and Apple are simulated: no SDK runs.
struct SignInView: View {
    let isRoot: Bool
    /// Called with `AuthContact.key` once the code is sent.
    let onCodeSent: (_ contactKey: String) -> Void
    @EnvironmentObject private var flow: AuthFlow
    @Environment(\.dismiss) private var dismiss
    @StateObject private var model = SignInModel()
    /// What the field holds. The shared view model reads it, and turns full-width digits into half-width
    /// when it checks them, so a Japanese IME isn't interrupted.
    @State private var input = ""
    @FocusState private var fieldFocused: Bool

    private let fieldAnchor = "field"

    var body: some View {
        let state = model.state
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Create an account or sign in to save favorites, keep your order history and pay faster.")
                        .font(.body)
                        .foregroundStyle(.secondary)
                        .padding(.bottom, 8)

                    if let provider = state.ssoError {
                        ErrorCard(
                            message: "Couldn't sign in with \(provider.label). Try again, or sign in another way.",
                            actionTitle: "Try another way"
                        ) {
                            model.viewModel.dismissError()
                            withAnimation { proxy.scrollTo(fieldAnchor, anchor: .top) }
                            fieldFocused = true
                        }
                    }

                    contactField(state)
                        .id(fieldAnchor)

                    Button { model.viewModel.sendCode() } label: {
                        CapsuleLabel(title: "Send Code", systemImage: "paperplane", loading: state.isSending)
                    }
                    .buttonStyle(CapsuleButtonStyle())
                    // Stays lit while the code is on its way, so the spinner isn't dimmed. The view model
                    // ignores a second tap.
                    .disabled(!state.canSend && !state.isSending)

                    OrDivider()

                    SsoButton(provider: .google, loading: state.ssoInProgress == .google) {
                        model.viewModel.continueWith(provider: .google)
                    }
                    .disabled(state.isBusy)
                    SsoButton(provider: .apple, loading: state.ssoInProgress == .apple) {
                        model.viewModel.continueWith(provider: .apple)
                    }
                    .disabled(state.isBusy)

                    if isRoot {
                        Button("Browse as guest") { flow.browseAsGuest() }
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .disabled(state.isBusy)
                    }
                    Text("Mockup: no real account is created and nothing leaves this device.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .padding(.top, 8)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(Palette.screen)
        .navigationTitle("Sign In")
        .toolbar {
            if !isRoot {
                ToolbarItem(placement: .topBarLeading) {
                    Button { dismiss() } label: { Image(systemName: "xmark") }
                        .accessibilityLabel("Close")
                }
            }
        }
        .onReceive(model.events) { event in
            if event is AuthEventSignedIn {
                flow.finishSignIn()
            } else if let sent = event as? AuthEventCodeSent {
                onCodeSent(sent.contact.key)
            }
        }
    }

    /// The label, the field and the line under it: what happens next, or what is wrong. The line is the
    /// shared copy, so it reads like the Android one.
    private func contactField(_ state: SignInUiState) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Phone number or email")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.secondary)
                .padding(.horizontal, 4)
            FieldBox(isError: state.hasInputError) {
                HStack(spacing: 10) {
                    Image(systemName: "person")
                        .foregroundStyle(.secondary)
                        .accessibilityHidden(true)
                    TextField("Phone number or email", text: $input, prompt: Text("Email or Phone number"))
                        .keyboardType(.emailAddress)
                        .textContentType(.username)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .submitLabel(.send)
                        .focused($fieldFocused)
                        .disabled(state.isBusy)
                        .onChange(of: input) { _, typed in model.viewModel.onInputChange(raw: typed) }
                        .onSubmit { model.viewModel.sendCode() }
                }
            }
            FieldMessage(
                error: state.hasInputError ? state.hint : nil,
                hint: state.hasInputError ? nil : state.hint
            )
        }
    }
}

/// "Continue with Google" or "Continue with Apple", drawn to each provider's button guidelines.
private struct SsoButton: View {
    let provider: SsoProvider
    let loading: Bool
    let action: () -> Void
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let colors = self.colors
        Button { if !loading { action() } } label: {
            ZStack {
                HStack(spacing: 10) {
                    icon
                    Text("Continue with \(provider.label)")
                }
                .opacity(loading ? 0 : 1)
                if loading { ProgressView().tint(colors.text) }
            }
            .font(.body.weight(.semibold))
            .foregroundStyle(colors.text)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(colors.fill, in: Capsule())
            .overlay { Capsule().stroke(colors.stroke, lineWidth: 1) }
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Continue with \(provider.label)")
        .accessibilityValue(loading ? "Loading" : "")
    }

    @ViewBuilder
    private var icon: some View {
        if provider == .google {
            Image("GoogleG").resizable().frame(width: 20, height: 20)
        } else {
            Image(systemName: "apple.logo")
        }
    }

    // Google: white fill, grey outline, near-black text, or its dark variant. Apple: black on light, white on dark.
    private var colors: (fill: Color, stroke: Color, text: Color) {
        let dark = colorScheme == .dark
        if provider == .google {
            return dark
                ? (Color(light: 0x131314, dark: 0x131314), Color(light: 0x8E918F, dark: 0x8E918F), Color(light: 0xE3E3E3, dark: 0xE3E3E3))
                : (.white, Color(light: 0x747775, dark: 0x747775), Color(light: 0x1F1F1F, dark: 0x1F1F1F))
        }
        return dark ? (.white, .clear, .black) : (.black, .clear, .white)
    }
}

private struct OrDivider: View {
    var body: some View {
        HStack(spacing: 12) {
            Rectangle().fill(.separator).frame(height: 0.5)
            Text("or").font(.footnote).foregroundStyle(.secondary)
            Rectangle().fill(.separator).frame(height: 0.5)
        }
        .padding(.vertical, 4)
        .accessibilityHidden(true)
    }
}
