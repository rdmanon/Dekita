package tech.vauldex.dekita.ui.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tech.vauldex.dekita.domain.MAX_QUANTITY
import tech.vauldex.dekita.domain.ModifierGroup
import tech.vauldex.dekita.presentation.addButtonLabel
import tech.vauldex.dekita.presentation.priceLabel
import tech.vauldex.dekita.presentation.ruleLabel
import tech.vauldex.dekita.presentation.store.ItemSheetState
import tech.vauldex.dekita.ui.debug.DebugPanelAction
import tech.vauldex.dekita.ui.theme.Dimens

/**
 * Customize one item: modifier groups, quantity and the live total. Add stays off until the choices are
 * complete; [onStopSelling] is the debug-build-only control that sells the item out while the sheet is open.
 * Cart Review reuses it to edit a line, with [actionLabel] "Update · ¥…".
 */
@Composable
internal fun ItemSheet(
    sheet: ItemSheetState,
    onToggle: (groupId: String, optionId: String) -> Unit,
    onQuantity: (Int) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
    onStopSelling: (() -> Unit)?,
    actionLabel: String = addButtonLabel(sheet.draft.totalPrice),
) {
    val draft = sheet.draft
    val item = draft.item
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = 24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    // Debug builds only: the panel opens over the sheet, so changes show here at once.
                    DebugPanelAction()
                }
                Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.basePrice.formatted(), style = MaterialTheme.typography.titleMedium)
            }

            item.modifierGroups.forEach { group ->
                ModifierGroupSection(group, sheet, onToggle)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Quantity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilledTonalIconButton(
                    onClick = { onQuantity(draft.quantity - 1) },
                    enabled = draft.quantity > 1,
                    modifier = Modifier.semantics { contentDescription = "Decrease quantity" },
                ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Text("${draft.quantity}", style = MaterialTheme.typography.titleMedium)
                FilledTonalIconButton(
                    onClick = { onQuantity(draft.quantity + 1) },
                    enabled = draft.quantity < MAX_QUANTITY,
                    modifier = Modifier.semantics { contentDescription = "Increase quantity" },
                ) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }

            sheet.blockedReason?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = onAdd, enabled = sheet.canAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(actionLabel)
            }
            if (onStopSelling != null && !item.isSoldOut) {
                TextButton(onClick = onStopSelling, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Stop selling this item (debug)")
                }
            }
        }
    }
}

@Composable
private fun ModifierGroupSection(group: ModifierGroup, sheet: ItemSheetState, onToggle: (String, String) -> Unit) {
    val missing = group in sheet.draft.missingRequiredGroups
    val single = group.maxSelections == 1
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(group.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                group.ruleLabel,
                style = MaterialTheme.typography.labelLarge,
                color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        group.options.forEach { option ->
            val selected = sheet.draft.isSelected(group, option)
            // A choice that sold out after it was picked stays on, in error color, so it can be removed.
            val enabled = sheet.draft.canToggle(group, option)
            val soldOutPick = selected && option.isSoldOut
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.MinTouchTarget)
                    .toggleable(
                        value = selected,
                        enabled = enabled,
                        role = if (single) Role.RadioButton else Role.Checkbox,
                        onValueChange = { onToggle(group.id, option.id) },
                    ),
            ) {
                if (single) {
                    RadioButton(selected = selected, onClick = null, enabled = enabled)
                } else {
                    Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
                }
                Text(option.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    if (option.isSoldOut) "Sold out" else option.priceLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (soldOutPick) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
