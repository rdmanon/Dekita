import SwiftUI
import SharedUI

/// The customize-item sheet: required and optional choices, quantity, and Add to Cart (or Update when
/// editing a cart line, which saves over the same line). Sold-out choices show but can't be picked.
struct ItemSheetView: View {
    @StateObject private var model: ItemSheetModel
    @Environment(\.dismiss) private var dismiss
    @State private var askNewCart = false

    init(target: ItemSheetTarget) {
        _model = StateObject(wrappedValue: ItemSheetModel(target: target))
    }

    var body: some View {
        let state = model.state
        Group {
            if let selection = state.selection {
                content(selection, state: state)
            } else if state.isLoading {
                ProgressView("Loading item…")
            } else {
                ContentUnavailableView(
                    "No longer on the menu",
                    systemImage: "fork.knife",
                    description: Text("This item was taken off the menu.")
                )
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .alert("Start a new cart?", isPresented: $askNewCart) {
            Button("Start new cart", role: .destructive) {
                if model.viewModel.startNewCartAndSave() == SaveResult.saved { dismiss() }
            }
            Button("Keep my cart", role: .cancel) {}
        } message: {
            Text("Your cart has items from \(state.otherCartStoreName ?? "another store"). A cart holds one store's items, so starting a new one removes them.")
        }
    }

    private func content(_ selection: ItemSelection, state: ItemSheetUiState) -> some View {
        let item = selection.item
        return List {
            Section {
                HStack(alignment: .top, spacing: 14) {
                    if let store = state.store {
                        StoreArtView(store: store, cornerRadius: 18)
                            .frame(width: 72, height: 72)
                    }
                    VStack(alignment: .leading, spacing: 3) {
                        Text(item.name).font(.title2.weight(.bold))
                        Text(item.basePrice.formatted()).font(.headline)
                        Text(item.description_)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .font(.footnote.weight(.bold))
                            .frame(width: 36, height: 36)
                    }
                    .modifier(GlassCircleButton())
                    .accessibilityLabel("Close")
                }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))

                if item.isSoldOut {
                    Label("Sold out right now", systemImage: "exclamationmark.circle")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Palette.badge(StoreBadge.full).foreground)
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets())
                }
            }

            ForEach(item.modifierGroups, id: \.id) { group in
                Section {
                    ForEach(group.options, id: \.id) { option in
                        OptionRow(
                            option: option,
                            isSelected: selection.selected[group.id]?.contains(option.id) ?? false,
                            isSingleChoice: group.maxSelections == 1
                        ) {
                            model.viewModel.toggle(groupId: group.id, optionId: option.id)
                        }
                    }
                } header: {
                    GroupHeader(group: group)
                }
            }
        }
        .listStyle(.insetGrouped)
        .safeAreaInset(edge: .bottom) {
            bottomBar(selection, isEditing: state.isEditing)
        }
    }

    private func bottomBar(_ selection: ItemSelection, isEditing: Bool) -> some View {
        VStack(spacing: 8) {
            if let missing = selection.missingRequired.first {
                Text("Choose \(missing.name.lowercased()) to continue.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            HStack(spacing: 10) {
                QuantityStepperView(
                    quantity: Int(selection.quantity),
                    itemName: selection.item.name,
                    canDecrease: selection.quantity > 1,
                    canIncrease: Int(selection.quantity) < model.maxQuantity,
                    onDecrease: { model.viewModel.setQuantity(quantity: selection.quantity - 1) },
                    onIncrease: { model.viewModel.setQuantity(quantity: selection.quantity + 1) }
                )
                .modifier(GlassCapsule())
                Button {
                    switch model.viewModel.save() {
                    case .saved: dismiss()
                    case .cartFromOtherStore: askNewCart = true
                    default: break
                    }
                } label: {
                    Text("\(isEditing ? "Update" : "Add to Cart") · \(selection.total.formatted())")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 36)
                }
                .modifier(ProminentGlassButton())
                .disabled(!selection.canSave)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }
}

private struct GroupHeader: View {
    let group: ModifierGroup

    var body: some View {
        HStack {
            Text(group.name)
                .font(.headline)
                .foregroundStyle(.primary)
            Spacer()
            if group.isRequired {
                Text("Required")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.tint)
                    .padding(.horizontal, 8)
                    .frame(minHeight: 22)
                    .background(Palette.tileIcon, in: Capsule())
            } else {
                Text(group.maxSelections == 1 ? "Optional" : "Choose up to \(group.maxSelections)")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .textCase(nil)
    }
}

/// One choice: a checkmark for one-choice groups, a round check box for multi-choice groups.
private struct OptionRow: View {
    let option: ModifierOption
    let isSelected: Bool
    let isSingleChoice: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                if !isSingleChoice {
                    Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                        .font(.title3)
                        .foregroundStyle(isSelected ? AnyShapeStyle(.tint) : AnyShapeStyle(.tertiary))
                }
                Text(option.name)
                    .fontWeight(isSelected ? .semibold : .regular)
                    .foregroundStyle(.primary)
                Spacer()
                if option.isSoldOut {
                    Text("Sold out").font(.footnote.weight(.semibold)).foregroundStyle(.secondary)
                } else if option.priceDelta.amount > 0 {
                    Text("+\(option.priceDelta.formatted())").font(.subheadline).foregroundStyle(.secondary)
                }
                if isSingleChoice && isSelected {
                    Image(systemName: "checkmark").fontWeight(.semibold).foregroundStyle(.tint)
                }
            }
            .contentShape(Rectangle())
        }
        // A sold-out option can't be picked, but one picked before it sold out can still be unpicked.
        .disabled(option.isSoldOut && !isSelected)
        .opacity(option.isSoldOut && !isSelected ? 0.45 : 1)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// Minus, count, plus. With `removesAtOne`, the minus becomes a bin at one, so it removes the line.
struct QuantityStepperView: View {
    let quantity: Int
    let itemName: String
    var canDecrease = true
    var canIncrease = true
    var removesAtOne = false
    let onDecrease: () -> Void
    let onIncrease: () -> Void

    var body: some View {
        let removes = removesAtOne && quantity <= 1
        HStack(spacing: 0) {
            Button(action: onDecrease) {
                Image(systemName: removes ? "trash" : "minus")
                    .font(.subheadline.weight(.semibold))
                    .frame(width: 40, height: 40)
            }
            .disabled(!(removes || canDecrease))
            .accessibilityLabel(removes ? "Remove \(itemName)" : "Decrease \(itemName) quantity")
            Text("\(quantity)")
                .font(.headline.monospacedDigit())
                .frame(minWidth: 18)
                .accessibilityLabel("Quantity \(quantity)")
            Button(action: onIncrease) {
                Image(systemName: "plus")
                    .font(.subheadline.weight(.semibold))
                    .frame(width: 40, height: 40)
            }
            .disabled(!canIncrease)
            .accessibilityLabel("Increase \(itemName) quantity")
        }
        .buttonStyle(.plain)
        .foregroundStyle(.primary)
    }
}

/// A Liquid Glass capsule on iOS 26, a material capsule before.
struct GlassCapsule: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.padding(.horizontal, 4).glassEffect(.regular.interactive(), in: .capsule)
        } else {
            content.padding(.horizontal, 4).background(.regularMaterial, in: Capsule())
        }
    }
}

/// A round Liquid Glass button on iOS 26, a bordered circle before.
struct GlassCircleButton: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.buttonStyle(.glass).buttonBorderShape(.circle)
        } else {
            content.buttonStyle(.bordered).buttonBorderShape(.circle)
        }
    }
}
