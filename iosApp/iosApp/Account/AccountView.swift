import SwiftUI
import SharedLogic

/// Account tab. Not designed yet: guests get a sign-in card; signed-in users see their profile,
/// payment methods and inbox, and can sign out.
struct AccountView: View {
    @EnvironmentObject private var flow: AuthFlow
    @StateObject private var model = AccountModel()
    @State private var showDebugPanel = false

    var body: some View {
        let state = model.state
        List {
            if let profile = state.profile {
                Section {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(profile.displayName).font(.headline)
                        if let contact = profile.email ?? profile.phone {
                            Text(contact).font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 4)
                }
                if state.isLoading {
                    Section { ProgressView("Loading account…") }
                } else {
                    Section("Payment methods") {
                        ForEach(state.paymentMethods, id: \.id) { method in
                            HStack {
                                Text(method.label)
                                Spacer()
                                if method.isDefault {
                                    Text("Default").font(.footnote.weight(.semibold)).foregroundStyle(Color.accentColor)
                                }
                            }
                        }
                    }
                    Section("Inbox") {
                        ForEach(state.inbox, id: \.id) { message in
                            InboxRow(message: message) { model.viewModel.markRead(messageId: message.id) }
                        }
                    }
                }
                Section {
                    Button("Sign Out", role: .destructive) {
                        model.viewModel.signOut()
                        flow.signedOut()
                    }
                }
            } else {
                Section {
                    SignInPromptCard(
                        title: "Sign in or create an account",
                        message: "Save favorites, keep your order history and pay faster."
                    ) { flow.requestSignIn(from: .standard) }
                }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            }
            if AppBuild.isDebug {
                Section {
                    Button { showDebugPanel = true } label: {
                        Label("Open debug panel", systemImage: "ladybug")
                    }
                }
            }
            Section {
                Text("Mockup build. Every store, order and payment here is sample data.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Account")
        .sheet(isPresented: $showDebugPanel) { DebugPanelView() }
    }
}

private struct InboxRow: View {
    let message: InboxMessage
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            HStack(alignment: .top, spacing: 12) {
                Circle()
                    .fill(message.isRead ? Color.clear : Color.accentColor)
                    .frame(width: 8, height: 8)
                    .padding(.top, 7)
                VStack(alignment: .leading, spacing: 2) {
                    Text(message.title).font(.body.weight(.semibold)).foregroundStyle(.primary)
                    Text(message.body).font(.subheadline).foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        // A list tints button labels with the accent color; inbox text should read as normal text.
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityValue(message.isRead ? "" : "Unread")
    }
}
