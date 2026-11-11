import SwiftUI
import SharedLogic

/// Steps pushed inside the sign-in flow.
enum AuthRoute: Hashable {
    case phone
    case otp(phoneE164: String)
    case email(signUp: Bool)
    case forgotPassword
}

/// The sign-in flow in its own navigation stack: a sheet, or full screen after sign-out (`isRoot`).
struct SignInFlowView: View {
    let isRoot: Bool
    @State private var path = NavigationPath()

    var body: some View {
        NavigationStack(path: $path) {
            SignInView(isRoot: isRoot)
                .navigationDestination(for: AuthRoute.self) { route in
                    switch route {
                    case .phone: PhoneEntryView { path.append(AuthRoute.otp(phoneE164: $0)) }
                    case let .otp(phoneE164): OtpView(phoneE164: phoneE164)
                    case let .email(signUp): EmailAuthView(signUp: signUp)
                    case .forgotPassword: ForgotPasswordView()
                    }
                }
        }
        .interactiveDismissDisabled(isRoot)
    }
}

/// The Sign-Up / Login screen: phone, email, Google and Apple. Apple is always offered, as the App Store
/// requires next to other third-party sign-in. Google and Apple are simulated: no SDK runs.
struct SignInView: View {
    let isRoot: Bool
    @EnvironmentObject private var flow: AuthFlow
    @Environment(\.dismiss) private var dismiss
    @StateObject private var model = SignInModel()
    @AccessibilityFocusState private var phoneFocused: Bool

    private let phoneAnchor = "phone"

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
                            withAnimation { proxy.scrollTo(phoneAnchor, anchor: .top) }
                            phoneFocused = true
                        }
                    }

                    NavigationLink(value: AuthRoute.phone) {
                        CapsuleLabel(title: "Sign in with Phone", systemImage: "phone.fill")
                    }
                    .buttonStyle(CapsuleButtonStyle())
                    .accessibilityFocused($phoneFocused)
                    .id(phoneAnchor)

                    NavigationLink(value: AuthRoute.email(signUp: true)) {
                        CapsuleLabel(title: "Sign in with Email", systemImage: "envelope.fill")
                    }
                    .buttonStyle(CapsuleButtonStyle(kind: .secondary))

                    OrDivider()

                    SsoButton(provider: .google, loading: state.ssoInProgress == .google) {
                        model.viewModel.continueWith(provider: .google)
                    }
                    SsoButton(provider: .apple, loading: state.ssoInProgress == .apple) {
                        model.viewModel.continueWith(provider: .apple)
                    }

                    if isRoot {
                        Button("Browse as guest") { flow.browseAsGuest() }
                            .frame(maxWidth: .infinity, minHeight: 44)
                    }
                    Text("Mockup: no real account is created and nothing leaves this device.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .padding(.top, 8)
                }
                .disabled(state.isBusy)
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
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
            if event is AuthEventSignedIn { flow.finishSignIn() }
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
