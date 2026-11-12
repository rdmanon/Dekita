import SwiftUI
import SharedLogic

/// Customize one item: modifier groups, quantity and the live total. Add stays off until the choices are
/// complete. Debug builds also get "Stop selling this item", to check the sold-out-while-open state.
struct ItemSheet: View {
    @ObservedObject var model: StoreDetailModel
    let onAdded: (String) -> Void

    var body: some View {
        if let sheet = model.state.sheet {
            content(sheet)
                .confirmationDialog(
                    "Start a new cart?",
                    isPresented: replacePresented,
                    titleVisibility: .visible
                ) {
                    Button("Start new cart") {
                        let name = sheet.draft.item.name
                        model.viewModel.confirmReplace()
                        onAdded("Added \(name)")
                    }
                    Button("Cancel", role: .cancel) { model.viewModel.dismissReplace() }
                } message: {
                    Text("Your cart has items from \(model.state.pendingReplaceStoreName ?? "another store"). Start a new cart with \(model.state.store?.name ?? "this store")?")
                }
        }
    }

    private func content(_ sheet: ItemSheetState) -> some View {
        ItemSheetContent(
            sheet: sheet,
            actionTitle: LabelsKt.addButtonLabel(total: sheet.draft.totalPrice),
            onToggle: { model.viewModel.toggleOption(groupId: $0, optionId: $1) },
            onQuantity: { model.viewModel.setQuantity(quantity: $0) },
            onAction: {
                let name = sheet.draft.item.name
                if model.viewModel.addToCart() == AddResult.added {
                    onAdded("Added \(name)")
                }
            },
            onClose: { model.viewModel.closeItem() },
            onStopSelling: { model.viewModel.stopSellingOpenItem() }
        )
    }

    private var replacePresented: Binding<Bool> {
        Binding(
            get: { model.state.pendingReplaceStoreName != nil },
            set: { if !$0 { model.viewModel.dismissReplace() } }
        )
    }
}

/// The item sheet's content, shared by Add on the store screen and Update on Cart Review (CART-01).
/// The caller owns the draft and decides what the bottom button does.
struct ItemSheetContent: View {
    let sheet: ItemSheetState
    let actionTitle: String
    let onToggle: (String, String) -> Void
    let onQuantity: (Int32) -> Void
    let onAction: () -> Void
    let onClose: () -> Void
    /// Debug builds show "Stop selling this item" when this is set.
    var onStopSelling: (() -> Void)? = nil

    var body: some View {
        let draft = sheet.draft
        let item = draft.item
        let missing = Set(draft.missingRequiredGroups.map(\.id))
        return NavigationStack {
            List {
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(item.name).font(.title2.weight(.bold))
                        Text(item.description_).font(.subheadline).foregroundStyle(.secondary)
                        Text(item.basePrice.formatted()).font(.headline)
                    }
                    .accessibilityElement(children: .combine)
                }

                // Debug builds only. Kept away from the Add button so it can't be hit by mistake.
                if AppBuild.isDebug && !item.isSoldOut, let onStopSelling {
                    Section {
                        Button("Stop selling this item (debug)", action: onStopSelling)
                    }
                }

                ForEach(item.modifierGroups, id: \.id) { group in
                    Section {
                        ForEach(group.options, id: \.id) { option in
                            optionRow(group: group, option: option, selected: draft.isSelected(group: group, option: option))
                        }
                    } header: {
                        HStack {
                            Text(group.name)
                            Spacer()
                            Text(group.ruleLabel)
                                .foregroundStyle(missing.contains(group.id) ? Color.red : Color.secondary)
                        }
                    }
                }

                Section {
                    Stepper(
                        "Quantity: \(draft.quantity)",
                        value: Binding(
                            get: { Int(draft.quantity) },
                            set: { onQuantity(Int32($0)) }
                        ),
                        in: 1...Int(ItemDraftKt.MAX_QUANTITY)
                    )
                    if let reason = sheet.blockedReason {
                        Text(reason).font(.subheadline).foregroundStyle(.red)
                    }
                }

            }
            .safeAreaInset(edge: .bottom) {
                BottomAction(action: onAction) {
                    CapsuleLabel(title: actionTitle)
                }
                .disabled(!sheet.canAdd)
            }
            .toolbar {
                // Debug builds only: the panel opens over the sheet, so changes show here at once.
                if AppBuild.isDebug {
                    ToolbarItem(placement: .topBarLeading) { DebugPanelButton() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onClose) { Image(systemName: "xmark") }
                        .accessibilityLabel("Close")
                }
            }
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    /// A checkmark row for single-choice groups, a circle for multi-choice ones. Sold-out options are disabled,
    /// except one picked before it sold out: it stays on, marked in red, so it can be removed.
    private func optionRow(group: ModifierGroup, option: ModifierOption, selected: Bool) -> some View {
        let single = group.maxSelections == 1
        let soldOutPick = selected && option.isSoldOut
        return Button {
            onToggle(group.id, option.id)
        } label: {
            HStack {
                if !single {
                    Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                        .foregroundStyle(selected ? Color.accentColor : Color.secondary)
                }
                Text(option.name).foregroundStyle(.primary)
                Spacer()
                Text(option.isSoldOut ? "Sold out" : option.priceLabel)
                    .foregroundStyle(soldOutPick ? AnyShapeStyle(Palette.error) : AnyShapeStyle(.secondary))
                if single && selected {
                    Image(systemName: "checkmark").foregroundStyle(Color.accentColor).fontWeight(.semibold)
                }
            }
            .contentShape(Rectangle())
        }
        // Plain style: the row text stays in the label color instead of the button tint.
        .buttonStyle(.plain)
        .disabled(!sheet.draft.canToggle(group: group, option: option))
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

}
