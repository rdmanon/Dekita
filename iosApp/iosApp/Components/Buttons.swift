import SwiftUI

/// The iOS artboards' call to action: a full-width capsule, 56 pt high.
/// `.primary` is the filled #C2410C button; `.secondary` is the tinted one used for "Add Card".
struct CapsuleButtonStyle: ButtonStyle {
    enum Kind { case primary, secondary }

    var kind: Kind = .primary

    func makeBody(configuration: Configuration) -> some View {
        CapsuleButtonBody(configuration: configuration, kind: kind)
    }
}

private struct CapsuleButtonBody: View {
    let configuration: ButtonStyle.Configuration
    let kind: CapsuleButtonStyle.Kind
    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        configuration.label
            .font(.body.weight(.semibold))
            .foregroundStyle(kind == .primary ? Color.white : Color.accentColor)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(kind == .primary ? Palette.action : Palette.tileIcon, in: Capsule())
            .contentShape(Capsule())
            .opacity(isEnabled ? (configuration.isPressed ? 0.8 : 1) : 0.45)
    }
}

/// A button label with an optional SF Symbol. While `loading`, a spinner replaces it; the label stays
/// in place, invisible, so the button keeps its size and VoiceOver still reads it.
struct CapsuleLabel: View {
    let title: String
    var systemImage: String? = nil
    var loading = false
    var spinnerTint: Color = .white

    var body: some View {
        ZStack {
            HStack(spacing: 8) {
                if let systemImage { Image(systemName: systemImage) }
                Text(title)
            }
            .opacity(loading ? 0 : 1)
            if loading {
                ProgressView().tint(spinnerTint)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(title)
        .accessibilityValue(loading ? "Loading" : "")
    }
}

/// A screen's main action pinned above the home indicator and the keyboard.
struct BottomAction<Label: View>: View {
    let action: () -> Void
    @ViewBuilder let label: Label

    var body: some View {
        Button(action: action) { label }
            .buttonStyle(CapsuleButtonStyle())
            .shadow(color: Palette.action.opacity(0.3), radius: 12, y: 6)
            .padding(.horizontal, 16)
            .padding(.bottom, 8)
    }
}

/// A white field box, as the artboards' grouped cards, outlined in red when its value is wrong.
struct FieldBox<Content: View>: View {
    var isError = false
    @ViewBuilder let content: Content

    var body: some View {
        content
            .padding(.horizontal, 16)
            .frame(minHeight: 52)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(isError ? Palette.error : .clear, lineWidth: 1.5)
            }
    }
}

/// An inline error under a field, or a hint when there is no error.
struct FieldMessage: View {
    let error: String?
    var hint: String? = nil

    var body: some View {
        if let error {
            Text(error).font(.footnote).foregroundStyle(Palette.error).padding(.horizontal, 4)
        } else if let hint {
            Text(hint).font(.footnote).foregroundStyle(.secondary).padding(.horizontal, 4)
        }
    }
}

/// A red card with an optional action, such as "Try another way". VoiceOver announces it when it appears.
struct ErrorCard: View {
    let message: String
    var actionTitle: String? = nil
    var action: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(message, systemImage: "exclamationmark.triangle.fill")
                .font(.subheadline)
            if let actionTitle {
                Button(actionTitle, action: action)
                    .font(.subheadline.weight(.semibold))
                    .frame(minHeight: 44)
            }
        }
        .foregroundStyle(Palette.error)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Palette.badge(.full).background, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .onAppear { UIAccessibility.post(notification: .announcement, argument: message) }
    }
}

/// Shown to guests where an account is needed.
struct SignInPromptCard: View {
    let title: String
    let message: String
    let onSignIn: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title).font(.headline)
            Text(message).font(.subheadline).foregroundStyle(.secondary)
            Button(action: onSignIn) { CapsuleLabel(title: "Sign In") }
                .buttonStyle(CapsuleButtonStyle())
        }
        .padding(16)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}
