import SwiftUI
import SharedLogic

/// Account tab. Not designed yet: guests get a sign-in card; signed-in users see their profile,
/// payment methods, and can sign out. The inbox is on the Orders tab (RCPT-01).
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
