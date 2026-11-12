import SwiftUI
import SharedLogic

/// Rate & Add to Favorites (RCPT-01). A rating is recorded once. Favorites save on each tap. Leaving any way
/// other than Submit (Skip or Close) is a skip, which leaves an in-app inbox message. No push is sent.
struct RateOrderView: View {
    @StateObject private var model: RateOrderModel
    let onDone: () -> Void

    init(orderId: String, onDone: @escaping () -> Void) {
        _model = StateObject(wrappedValue: RateOrderModel(orderId: orderId))
        self.onDone = onDone
    }

    var body: some View {
        let state = model.state
        Group {
            if state.isLoading {
                ProgressView("Loading your order…")
            } else if state.order != nil {
                ScrollView {
                    VStack(alignment: .leading, spacing: 22) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(state.alreadyRated ? "Thanks for rating!" : "How was it?")
                                .font(.title2.weight(.bold))
                                .accessibilityAddTraits(.isHeader)
                            Text(state.heading).foregroundStyle(.secondary)
                        }
                        StarPicker(state: state) { model.viewModel.setStars(stars: Int32($0)) }
                        TagPicker(state: state) { model.viewModel.toggleTag(tag: $0) }
                        FavoriteItems(items: state.items) { model.viewModel.toggleFavorite(itemId: $0) }
                        if !state.alreadyRated {
                            Text("Skip and we'll leave a reminder in your Orders inbox. No notifications.")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(16)
                }
                .safeAreaInset(edge: .bottom) { bottomBar(state) }
            } else {
                ContentUnavailableView("This order can't be rated", systemImage: "star")
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Palette.screen)
        .navigationTitle("Rate & Add to Favorites")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden()
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(action: leave) { Image(systemName: "xmark") }
                    .accessibilityLabel("Close")
            }
        }
    }

    @ViewBuilder
    private func bottomBar(_ state: RateOrderUiState) -> some View {
        if state.order != nil {
            VStack(spacing: 4) {
                if state.alreadyRated {
                    BottomAction(action: onDone) { CapsuleLabel(title: "Done") }
                } else {
                    BottomAction(action: { if model.viewModel.submit() { onDone() } }) { CapsuleLabel(title: "Submit") }
                        .disabled(!state.canSubmit)
                    Button("Skip for now", action: leave)
                        .font(.subheadline.weight(.semibold))
                        .tint(Palette.action)
                        .padding(.bottom, 4)
                }
            }
            .padding(.top, 8)
            .background(.bar)
        }
    }

    /// Skip or Close: leaves the inbox reminder unless the order is already rated.
    private func leave() {
        model.viewModel.skip()
        onDone()
    }
}

/// Five stars. VoiceOver reads each as "3 of 5 stars" and marks the chosen one as selected.
private struct StarPicker: View {
    let state: RateOrderUiState
    let onStars: (Int) -> Void

    var body: some View {
        HStack(spacing: 8) {
            ForEach(1...5, id: \.self) { star in
                Button { onStars(star) } label: {
                    Image(systemName: star <= Int(state.stars) ? "star.fill" : "star")
                        .font(.system(size: 34))
                        .foregroundStyle(star <= Int(state.stars) ? Palette.action : Color.secondary)
                        .frame(width: 48, height: 48)
                }
                .buttonStyle(.plain)
                .disabled(state.alreadyRated)
                .accessibilityLabel("\(star) of 5 stars")
                .accessibilityAddTraits(star == Int(state.stars) ? .isSelected : [])
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Rating")
    }
}

private struct TagPicker: View {
    let state: RateOrderUiState
    let onTag: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Anything stand out? (optional)").font(.headline)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(state.tagOptions, id: \.self) { tag in
                        let selected = state.tags.contains(tag)
                        Button { onTag(tag) } label: {
                            Text(tag)
                                .font(.subheadline.weight(.semibold))
                                .padding(.horizontal, 14)
                                .frame(minHeight: 36)
                                .foregroundStyle(selected ? Color.white : Color.primary)
                                .background(selected ? Palette.action : Palette.card, in: Capsule())
                        }
                        .buttonStyle(.plain)
                        .disabled(state.alreadyRated)
                        .accessibilityAddTraits(selected ? .isSelected : [])
                    }
                }
            }
        }
    }
}

/// One heart per ordered item. Each tap saves at once; favorites appear in Discover → Favorites.
private struct FavoriteItems: View {
    let items: [RateItem]
    let onToggle: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Add to your favorites").font(.headline)
            VStack(spacing: 0) {
                ForEach(Array(items.enumerated()), id: \.element.item.itemId) { index, entry in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(entry.item.itemName)
                            Text(entry.item.price.formatted()).font(.footnote).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Button { onToggle(entry.item.itemId) } label: {
                            Image(systemName: entry.isFavorite ? "heart.fill" : "heart")
                                .font(.title3)
                                .foregroundStyle(entry.isFavorite ? Palette.action : Color.secondary)
                                .frame(width: 44, height: 44)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Favorite \(entry.item.itemName)")
                        .accessibilityAddTraits(entry.isFavorite ? .isSelected : [])
                    }
                    .padding(.leading, 16)
                    .padding(.trailing, 6)
                    .padding(.vertical, 4)
                    if index < items.count - 1 { Divider().padding(.leading, 16) }
                }
            }
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        }
    }
}
