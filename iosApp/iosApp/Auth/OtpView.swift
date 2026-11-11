import SwiftUI
import SharedLogic

/// The 6-digit code step: six cells over one hidden field, so SMS autofill and paste fill them all.
/// The cells show the view model's normalized code, so full-width digits appear half-width.
struct OtpView: View {
    @EnvironmentObject private var flow: AuthFlow
    @Environment(\.dismiss) private var dismiss
    @StateObject private var model: OtpModel
    @State private var typed = ""
    @FocusState private var focused: Bool

    init(phoneE164: String) {
        _model = StateObject(wrappedValue: OtpModel(phoneE164: phoneE164))
    }

    var body: some View {
        let state = model.state
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("We sent a 6-digit code to \(state.phone).")
                ZStack {
                    TextField("", text: $typed)
                        .keyboardType(.numberPad)
                        .textContentType(.oneTimeCode)
                        .focused($focused)
                        .foregroundStyle(.clear)
                        .tint(.clear)
                        .accessibilityLabel("6-digit code")
                        .accessibilityValue(state.code)
                        .onChange(of: typed) { _, raw in model.viewModel.onCodeChange(raw: raw) }
                    HStack(spacing: 8) {
                        ForEach(0..<6, id: \.self) { index in
                            cell(index, code: Array(state.code), hasError: state.error != nil)
                        }
                    }
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
                }
                .contentShape(Rectangle())
                .onTapGesture { focused = true }

                if let error = state.error {
                    Text(error).font(.footnote).foregroundStyle(Palette.error)
                }
                Button { model.viewModel.verify() } label: {
                    CapsuleLabel(title: "Verify", loading: state.isVerifying)
                }
                .buttonStyle(CapsuleButtonStyle())

                Button(state.resendSecondsLeft > 0 ? "Resend code in \(countdown(Int(state.resendSecondsLeft)))" : "Resend code") {
                    model.viewModel.resend()
                }
                .disabled(!state.canResend)
                .frame(minHeight: 44)
                Button("Change number") { dismiss() }
                    .frame(minHeight: 44)

                if AppBuild.isDebug {
                    Text("Debug build · test code: \(SeedAuth.shared.TEST_OTP)")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(16)
        }
        .background(Palette.screen)
        .navigationTitle("Enter Code")
        .navigationBarTitleDisplayMode(.inline)
        // A wrong code clears the view model's code; clear the field with it.
        .onChange(of: state.code) { _, code in if code.isEmpty { typed = "" } }
        .onReceive(model.events) { event in
            if event is AuthEventSignedIn { flow.finishSignIn() }
        }
        .onAppear { focused = true }
    }

    private func cell(_ index: Int, code: [Character], hasError: Bool) -> some View {
        let active = focused && index == min(code.count, 5)
        return Text(index < code.count ? String(code[index]) : "")
            .font(.title2.weight(.semibold))
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(hasError ? Palette.error : (active ? Color.accentColor : .clear), lineWidth: 2)
            }
    }

    /// 42 becomes "0:42".
    private func countdown(_ seconds: Int) -> String {
        String(format: "%d:%02d", seconds / 60, seconds % 60)
    }
}
