import SwiftUI
import SharedLogic

/// The place Discover measures from, above the title. Tapping it opens the location sheet.
struct LocationChip: View {
    let placeName: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: "mappin.and.ellipse")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.tint)
                Text(placeName)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                Image(systemName: "chevron.down")
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(.secondary)
            }
            .padding(.leading, 10)
            .padding(.trailing, 12)
            .frame(height: 34)
            .background(Palette.card, in: Capsule())
            // Keeps the touch target at 44 pt while the chip stays 34 pt tall.
            .padding(.vertical, 5)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(LabelsKt.PLACE_PICKER_TITLE): \(placeName)")
        .accessibilityHint("Tap to change")
    }
}

/// The location sheet: the saved places, narrowed by the search field. The mockup has no address lookup, so the
/// field only filters the list. Picking a place makes Nearby, the walk times and the queues measure from it.
/// The presenter calls `clearQuery()` when the sheet goes away, however it was closed.
struct PlaceSheetView: View {
    @ObservedObject var model: PlacePickerModel
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var detent = PresentationDetent.medium
    @FocusState private var searchFocused: Bool

    var body: some View {
        let state = model.state
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                header
                Text(LabelsKt.PLACE_PICKER_DETAIL)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 4)
                searchField
                placeList(state)
            }
            .padding(.horizontal, 16)
            .padding(.top, 20)
            .padding(.bottom, 8)
        }
        .scrollBounceBehavior(.basedOnSize)
        .presentationDetents([.medium, .large], selection: $detent)
        .presentationDragIndicator(.visible)
        // The grouped background, as the Filters sheet has, so the white cards stand out at both heights.
        .presentationBackground(Palette.screen)
        // The list needs the room once the keyboard is up.
        .onChange(of: searchFocused) { _, focused in if focused { detent = .large } }
        .onChange(of: query) { _, text in model.viewModel.setQuery(text: text) }
        // A sheet opened again while it is still closing can come back with its old text. Start empty either way.
        .onAppear {
            query = ""
            model.viewModel.clearQuery()
        }
    }

    /// The title and the close button, inside the sheet as in the Payment sheet.
    private var header: some View {
        HStack {
            Text(LabelsKt.PLACE_PICKER_TITLE)
                .font(.title2.weight(.bold))
                .accessibilityAddTraits(.isHeader)
            Spacer()
            Button { dismiss() } label: {
                Image(systemName: "xmark")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.primary)
                    .frame(width: 36, height: 36)
                    .glassControl(in: Circle())
                    .frame(width: 44, height: 44)
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close")
        }
        .padding(.leading, 4)
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(.secondary)
                .accessibilityHidden(true)
            TextField(LabelsKt.PLACE_SEARCH_HINT, text: $query)
                .autocorrectionDisabled()
                .submitLabel(.search)
                .focused($searchFocused)
            if !query.isEmpty {
                Button { query = "" } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(.secondary)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Clear search")
            }
        }
        .padding(.leading, 12)
        .padding(.trailing, query.isEmpty ? 12 : 0)
        .frame(minHeight: 46)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    @ViewBuilder
    private func placeList(_ state: PlacePickerUiState) -> some View {
        if state.hasNoMatches {
            Text(LabelsKt.NO_PLACES_FOUND)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
                .background(Palette.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        } else {
            VStack(spacing: 0) {
                ForEach(Array(state.places.enumerated()), id: \.element.id) { index, place in
                    if index > 0 { Divider().padding(.leading, 60) }
                    PlaceRow(place: place, isSelected: place.id == state.selected.id) {
                        model.viewModel.select(placeId: place.id)
                        dismiss()
                    }
                }
            }
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
    }
}

/// A place in a 32 pt icon tile, its name and second line, and a checkmark on the one in use.
private struct PlaceRow: View {
    let place: SavedPlace
    let isSelected: Bool
    let onSelect: () -> Void

    var body: some View {
        Button(action: onSelect) {
            HStack(spacing: 12) {
                Image(systemName: place.kind.symbol)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.tint)
                    .frame(width: 32, height: 32)
                    .background(Palette.tileIcon, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(place.name)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.primary)
                    Text(place.subtitle)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "checkmark")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Palette.link)
                    .opacity(isSelected ? 1 : 0)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, 16)
            .frame(minHeight: 60)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

private extension PlaceKind {
    /// The design's icons: the navigation arrow, a train, a house and a briefcase.
    var symbol: String {
        switch self {
        case .current: return "location.fill"
        case .home: return "house"
        case .office: return "briefcase"
        default: return "tram.fill" // .recent: the mockup's recent place is a station
        }
    }
}
