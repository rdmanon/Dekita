import SwiftUI
import SharedLogic

/// Customize one item: modifier groups, quantity and the live total. Add stays off until the choices are
/// complete. Debug builds also get "Stop selling this item", to check the sold-out-while-open state.
struct ItemSheet: View {
    @ObservedObject var model: StoreDetailModel
    let store: Store?
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
            storeArtKey: store?.artKey ?? "",
            actionTitle: Copy.addToCart(total: sheet.draft.totalPrice),
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
    /// The art shown when the item has none of its own.
    let storeArtKey: String
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
                    HStack(alignment: .top, spacing: 14) {
                        StoreArtView(artKey: item.artKey ?? storeArtKey, cornerRadius: 18)
                            .frame(width: 72, height: 72)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(item.name).font(.title2.weight(.bold))
                            Text(item.basePrice.formatted()).font(.headline)
                            Text(item.description_).font(.footnote).foregroundStyle(.secondary)
                        }
                        .accessibilityElement(children: .combine)
                    }
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
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
                        groupHeader(group, missing: missing.contains(group.id))
                    }
                }

                if let reason = sheet.blockedReason {
                    Section {
                        Text(reason).font(.subheadline).foregroundStyle(Palette.error)
                    }
                }
            }
            // So the sheet's own glass shows through instead of the list's opaque grey.
            .scrollContentBackground(.hidden)
            .safeAreaInset(edge: .bottom) {
                HStack(spacing: 10) {
                    quantityControl(draft.quantity)
                    Button(action: onAction) { CapsuleLabel(title: actionTitle) }
                        .buttonStyle(CapsuleButtonStyle())
                        .frame(height: 52)
                        .disabled(!sheet.canAdd)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
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

    /// Group name on the left; "Required" as a pill, any other rule as plain text. Red while a required group is empty.
    private func groupHeader(_ group: ModifierGroup, missing: Bool) -> some View {
        HStack {
            Text(group.name)
                .font(.headline)
                .foregroundStyle(.primary)
                .textCase(nil)
            Spacer()
            if group.isRequired {
                Text(group.ruleLabel)
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 8)
                    .frame(minHeight: 22)
                    .foregroundStyle(missing ? Palette.error : Palette.link)
                    .background(missing ? Palette.badge(.full).background : Palette.tileIcon, in: Capsule())
            } else {
                Text(group.ruleLabel).font(.footnote).foregroundStyle(.secondary).textCase(nil)
            }
        }
        .padding(.horizontal, -4)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }

    /// The artboard's quantity capsule beside the Add button, 1 to the shared maximum.
    private func quantityControl(_ quantity: Int32) -> some View {
        HStack(spacing: 0) {
            Button { onQuantity(quantity - 1) } label: {
                Image(systemName: "minus").frame(width: 44, height: 52)
            }
            .disabled(quantity <= 1)
            .accessibilityLabel("Decrease quantity")
            Text("\(quantity)")
                .font(.headline.monospacedDigit())
                .frame(minWidth: 22)
                .accessibilityLabel("Quantity \(quantity)")
            Button { onQuantity(quantity + 1) } label: {
                Image(systemName: "plus").frame(width: 44, height: 52)
            }
            .disabled(quantity >= ItemDraftKt.MAX_QUANTITY)
            .accessibilityLabel("Increase quantity")
        }
        .buttonStyle(.plain)
        .foregroundStyle(.primary)
        .glassControl(in: Capsule())
    }

    /// A checkmark row for single-choice groups, a ring for multi-choice ones. Sold-out options are disabled and
    /// faded, except one picked before it sold out: it stays on, marked in red, so it can be removed.
    private func optionRow(group: ModifierGroup, option: ModifierOption, selected: Bool) -> some View {
        let single = group.maxSelections == 1
        let soldOutPick = selected && option.isSoldOut
        let faded = option.isSoldOut && !soldOutPick
        return Button {
            onToggle(group.id, option.id)
        } label: {
            HStack(spacing: 12) {
                if !single {
                    ZStack {
                        Circle().strokeBorder(selected ? Palette.action : Color(light: 0x8E8E93, dark: 0x8E8E93), lineWidth: 1.5)
                        if selected {
                            Circle().fill(Palette.action)
                            Image(systemName: "checkmark").font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
                        }
                    }
                    .frame(width: 22, height: 22)
                }
                Text(option.name)
                    .font(.callout.weight(selected ? .semibold : .regular))
                    .foregroundStyle(.primary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if option.isSoldOut {
                    Text("Sold out")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(soldOutPick ? Palette.error : Color(light: 0x3A3A3C, dark: 0xD1D1D6))
                } else {
                    Text(option.priceLabel).font(.subheadline).foregroundStyle(.secondary)
                }
                if single && selected {
                    Image(systemName: "checkmark").foregroundStyle(Palette.link).fontWeight(.semibold)
                }
            }
            .frame(minHeight: 46)
            .opacity(faded ? 0.45 : 1)
            .contentShape(Rectangle())
        }
        // Plain style: the row text stays in the label color instead of the button tint.
        .buttonStyle(.plain)
        .disabled(!sheet.draft.canToggle(group: group, option: option))
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
