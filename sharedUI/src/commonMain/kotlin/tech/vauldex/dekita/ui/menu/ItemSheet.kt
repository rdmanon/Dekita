package tech.vauldex.dekita.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dekita.sharedui.generated.resources.Res
import dekita.sharedui.generated.resources.ic_close
import org.jetbrains.compose.resources.painterResource
import tech.vauldex.dekita.domain.ItemSelection
import tech.vauldex.dekita.domain.ModifierGroup
import tech.vauldex.dekita.domain.ModifierOption
import tech.vauldex.dekita.presentation.menu.ItemSheetViewModel
import tech.vauldex.dekita.presentation.menu.SaveResult
import tech.vauldex.dekita.ui.components.LoadingIndicator
import tech.vauldex.dekita.ui.components.QuantityStepper
import tech.vauldex.dekita.ui.components.StatusPill
import tech.vauldex.dekita.ui.components.StoreArt
import tech.vauldex.dekita.ui.theme.DekitaTheme

/**
 * The customize-item sheet: required and optional choices, quantity, and Add (or Update when editing
 * a cart line). Sold-out choices are shown but can't be picked.
 */
@Composable
fun ItemSheet(viewModel: ItemSheetViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var askNewCart by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        val selection = state.selection
        when {
            selection != null -> SheetContent(
                selection = selection,
                store = state.store,
                isEditing = state.isEditing,
                onToggle = viewModel::toggle,
                onQuantity = viewModel::setQuantity,
                onClose = onDismiss,
                onSave = {
                    when (viewModel.save()) {
                        SaveResult.SAVED -> onDismiss()
                        SaveResult.CART_FROM_OTHER_STORE -> askNewCart = true
                        SaveResult.NOT_SAVED -> Unit
                    }
                },
            )
            state.isLoading -> LoadingIndicator("Loading item…")
            else -> Text(
                "This item is no longer on the menu.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(24.dp),
            )
        }
    }

    if (askNewCart) {
        AlertDialog(
            onDismissRequest = { askNewCart = false },
            title = { Text("Start a new cart?") },
            text = {
                Text("Your cart has items from ${state.otherCartStoreName ?: "another store"}. A cart holds one store's items, so starting a new one removes them.")
            },
            confirmButton = {
                TextButton(onClick = {
                    askNewCart = false
                    if (viewModel.startNewCartAndSave() == SaveResult.SAVED) onDismiss()
                }) { Text("Start new cart") }
            },
            dismissButton = { TextButton(onClick = { askNewCart = false }) { Text("Keep my cart") } },
        )
    }
}

@Composable
private fun SheetContent(
    selection: ItemSelection,
    store: tech.vauldex.dekita.domain.Store?,
    isEditing: Boolean,
    onToggle: (groupId: String, optionId: String) -> Unit,
    onQuantity: (Int) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
) {
    val item = selection.item
    Column(Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            if (store != null) {
                StoreArt(store, Modifier.fillMaxWidth().height(120.dp), shape = RoundedCornerShape(24.dp))
            }
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.name, style = MaterialTheme.typography.headlineSmall)
                    Text(item.basePrice.formatted(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(
                        item.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = onClose) {
                    Icon(painterResource(Res.drawable.ic_close), contentDescription = "Close")
                }
            }
            if (item.isSoldOut) {
                StatusPill("Sold out right now", DekitaTheme.colors.full)
            }
            item.modifierGroups.forEach { group ->
                ModifierGroupSection(group, selection.selected[group.id].orEmpty()) { optionId -> onToggle(group.id, optionId) }
            }
            Spacer(Modifier.height(8.dp))
        }

        val missing = selection.missingRequired.firstOrNull()
        if (missing != null) {
            Text(
                "Choose ${missing.name.lowercase()} to continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        ) {
            QuantityStepper(
                quantity = selection.quantity,
                itemName = item.name,
                onDecrease = { onQuantity(selection.quantity - 1) },
                onIncrease = { onQuantity(selection.quantity + 1) },
                height = 56.dp,
                canDecrease = selection.quantity > 1,
                canIncrease = selection.quantity < ItemSelection.MAX_QUANTITY,
            )
            Button(
                onClick = onSave,
                enabled = selection.canSave,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) {
                Text("${if (isEditing) "Update" else "Add"} · ${selection.total.formatted()}")
            }
        }
    }
}

@Composable
private fun ModifierGroupSection(group: ModifierGroup, selected: Set<String>, onToggle: (String) -> Unit) {
    val single = group.maxSelections == 1
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(group.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (group.isRequired) {
                StatusPill("Required", DekitaTheme.colors.bothModes)
            } else {
                Text(
                    if (single) "Optional" else "Choose up to ${group.maxSelections}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(if (single) Modifier.selectableGroup() else Modifier) {
            group.options.forEach { option ->
                OptionRow(option, isSelected = option.id in selected, single = single, onClick = { onToggle(option.id) })
            }
        }
    }
}

@Composable
private fun OptionRow(option: ModifierOption, isSelected: Boolean, single: Boolean, onClick: () -> Unit) {
    // A sold-out option can't be picked, but one picked before it sold out can still be unpicked.
    val enabled = !option.isSoldOut || isSelected
    val rowModifier = if (single) {
        Modifier.selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    } else {
        Modifier.toggleable(value = isSelected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(rowModifier)
            .alpha(if (option.isSoldOut && !isSelected) 0.45f else 1f),
    ) {
        if (single) {
            RadioButton(selected = isSelected, onClick = null, enabled = enabled)
        } else {
            Checkbox(checked = isSelected, onCheckedChange = null, enabled = enabled)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            option.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        when {
            option.isSoldOut -> Text("Sold out", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            option.priceDelta.amount > 0 -> Text(
                "+${option.priceDelta.formatted()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
