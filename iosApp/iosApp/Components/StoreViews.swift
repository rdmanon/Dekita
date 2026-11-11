import SwiftUI
import SharedLogic

/// The store's photo slot. The mockup loads no remote images, so each store shows a local illustration
/// that fills the frame the caller gives it. Paused stores are dimmed. Hidden from VoiceOver.
struct StoreArtView: View {
    let store: Store
    var cornerRadius: CGFloat = 14

    var body: some View {
        Color(.tertiarySystemFill)
            .overlay {
                Image("art-\(store.artKey)")
                    .resizable()
                    .scaledToFill()
            }
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .opacity(store.badge == StoreBadge.paused ? 0.55 : 1)
            .accessibilityHidden(true)
    }
}

/// Open for Lunch, Pickup Only, In-store Only, Paused or Full. Fades when the live feed changes it.
struct StatusBadgeView: View {
    let badge: StoreBadge

    var body: some View {
        let colors = Palette.badge(badge)
        Text(badge.label)
            .font(.caption.weight(.semibold))
            .lineLimit(1)
            .padding(.horizontal, 8)
            .frame(minHeight: 22)
            .foregroundStyle(colors.foreground)
            .background(colors.background, in: Capsule())
            .animation(.easeInOut, value: badge)
    }
}

/// A row in the inset store list: photo, name, category, walking time, queue, status badge.
/// Long names, Japanese ones included, stay on one line and end in an ellipsis.
struct StoreRowView: View {
    let store: Store
    var matchedItem: String? = nil
    /// In a List, the row draws no chevron or padding of its own: the List adds both.
    var insideList = false

    var body: some View {
        HStack(spacing: 12) {
            StoreArtView(store: store)
                .frame(width: 56, height: 56)
            VStack(alignment: .leading, spacing: 3) {
                Text(store.name)
                    .font(.body.weight(.semibold))
                    .lineLimit(1)
                    .truncationMode(.tail)
                Text(detail)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                if let matchedItem {
                    Text("Has \(matchedItem)")
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(.tint)
                        .lineLimit(1)
                }
                StatusBadgeView(badge: store.badge)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if !insideList {
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.tertiary)
                    .accessibilityHidden(true)
            }
        }
        .padding(.horizontal, insideList ? 0 : 14)
        .padding(.vertical, insideList ? 4 : 12)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }

    private var detail: String {
        let queue = store.queueLabel()
        let walk = "\(store.category) · \(store.walkMinutes) min walk"
        return queue.isEmpty ? walk : "\(walk) · \(queue)"
    }
}

/// The design's grouped white card of store rows, with inset dividers. Each row opens its store.
struct StoreListCard: View {
    let matches: [StoreMatch]

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(matches.enumerated()), id: \.element.store.id) { index, match in
                NavigationLink(value: DiscoverRoute.store(id: match.store.id, name: match.store.name)) {
                    StoreRowView(store: match.store, matchedItem: match.matchedItem)
                }
                .buttonStyle(.plain)
                if index < matches.count - 1 {
                    Divider().padding(.leading, 82)
                }
            }
        }
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}

/// A recommended store: photo with a live queue pill, then name, badge and details.
struct RecommendedCard: View {
    let store: Store

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            StoreArtView(store: store, cornerRadius: 0)
                .frame(height: 130)
                .overlay(alignment: .topLeading) { queuePill }
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text(store.name)
                        .font(.headline)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    StatusBadgeView(badge: store.badge)
                }
                Text(detail)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            .padding(.horizontal, 12)
            .padding(.top, 10)
            .padding(.bottom, 12)
        }
        .frame(width: 250)
        .background(Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .contentShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var queuePill: some View {
        let queue = store.queueLabel()
        if !queue.isEmpty {
            HStack(spacing: 6) {
                Circle()
                    .fill(store.isAcceptingOrders ? Color.green : Color.gray)
                    .frame(width: 7, height: 7)
                Text(queue)
                    .font(.caption.weight(.semibold))
                    .lineLimit(1)
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 10)
            .frame(height: 26)
            // A dark scrim in both modes, like text over a photo.
            .background(Color.black.opacity(0.72), in: Capsule())
            .padding(10)
        }
    }

    private var detail: String {
        var parts = [store.category, "\(store.walkMinutes) min walk"]
        if let modes = store.businessType.modesLabel { parts.append(modes) }
        return parts.joined(separator: " · ")
    }
}
