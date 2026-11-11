import SwiftUI
import SharedLogic

/// Mobile number with a +81 default. The field keeps what was typed; the shared view model turns
/// full-width digits into half-width when it checks the number, so a Japanese IME isn't interrupted.
struct PhoneEntryView: View {
    let onCodeSent: (_ phoneE164: String) -> Void
    @StateObject private var model = PhoneEntryModel()
    @State private var number = ""
    @FocusState private var focused: Bool

    var body: some View {
        let state = model.state
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("We'll text you a 6-digit code to sign in or create your account.")
                    .foregroundStyle(.secondary)
                    .padding(.bottom, 4)
                FieldBox(isError: state.error != nil) {
                    HStack(spacing: 12) {
                        Menu {
                            ForEach(model.dialCodes, id: \.self) { dialCode in
                                Button("\(dialCode.country) \(dialCode.prefix)") {
                                    model.viewModel.onDialCodeChange(dialCode: dialCode)
                                }
                            }
                        } label: {
                            HStack(spacing: 4) {
                                Text(state.dialCode.prefix)
                                Image(systemName: "chevron.down").font(.caption.weight(.semibold))
                            }
                            .frame(minHeight: 44)
                        }
                        .accessibilityLabel("Country code, \(state.dialCode.country) \(state.dialCode.prefix)")
                        Divider().frame(height: 28)
                        TextField("Mobile number", text: $number, prompt: Text("90-1234-5678"))
                            .keyboardType(.phonePad)
                            .textContentType(.telephoneNumber)
                            .focused($focused)
                            .disabled(state.isSending)
                            .onChange(of: number) { _, typed in model.viewModel.onNumberChange(number: typed) }
                    }
                }
                FieldMessage(error: state.error)
            }
            .padding(16)
        }
        .background(Palette.screen)
        .navigationTitle("Phone Number")
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            BottomAction(action: { model.viewModel.sendCode() }) {
                CapsuleLabel(title: "Send Verification Code", loading: state.isSending)
            }
        }
        .onReceive(model.events) { event in
            if let sent = event as? AuthEventCodeSent { onCodeSent(sent.phone.e164) }
        }
        .onAppear { focused = true }
    }
}
