import SwiftUI

/// The artboards' segmented control: 34 pt pills on a tinted track, the selected one white and bold.
/// Drawn by hand because the system segmented control can't set the selected weight or the height.
struct SegmentedCapsule<Value: Hashable>: View {
    let label: String
    let options: [(title: String, value: Value)]
    let selection: Value
    let onSelect: (Value) -> Void

    @Namespace private var thumb

    var body: some View {
        HStack(spacing: 2) {
            ForEach(options, id: \.value) { option in
                segment(option.title, selected: option.value == selection, value: option.value)
            }
        }
        .padding(3)
        .background(Color(.tertiarySystemFill), in: Capsule())
        .accessibilityElement(children: .contain)
        .accessibilityLabel(label)
    }

    private func segment(_ title: String, selected: Bool, value: Value) -> some View {
        Button { withAnimation(.snappy(duration: 0.2)) { onSelect(value) } } label: {
            Text(title)
                .font(.subheadline.weight(selected ? .semibold : .regular))
                .foregroundStyle(.primary)
                .frame(maxWidth: .infinity, minHeight: 34)
                .background {
                    if selected {
                        Capsule()
                            .fill(Color(.systemBackground))
                            .shadow(color: .black.opacity(0.12), radius: 3, y: 1)
                            .matchedGeometryEffect(id: "thumb", in: thumb)
                    }
                }
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
