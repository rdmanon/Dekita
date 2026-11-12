package tech.vauldex.dekita.ui.store

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tech.vauldex.dekita.R
import tech.vauldex.dekita.ui.components.StoreArt
import tech.vauldex.dekita.ui.debug.LocalOpenDebugPanel
import tech.vauldex.dekita.domain.MAX_QUANTITY
import tech.vauldex.dekita.domain.ModifierGroup
import tech.vauldex.dekita.presentation.addButtonLabel
import tech.vauldex.dekita.presentation.priceLabel
import tech.vauldex.dekita.presentation.ruleLabel
import tech.vauldex.dekita.presentation.store.ItemSheetState
import tech.vauldex.dekita.ui.debug.DebugPanelAction
import tech.vauldex.dekita.ui.theme.Dimens

/**
 * Customize one item, as the Android Item artboard: art, name, price, modifier groups, and a bar pinned at the
 * bottom with the quantity pill beside the button. Add stays off until the choices are complete;
 * [onStopSelling] is the debug-build-only control that sells the item out while the sheet is open.
 * Cart Review reuses it to edit a line, with [actionLabel] "Update · ¥…". [storeArtKey] is the picture for an
 * item that has none of its own.
 */
@Composable
internal fun ItemSheet(
    sheet: ItemSheetState,
    storeArtKey: String?,
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
        Column {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                StoreArt(item.artKey ?: storeArtKey, Modifier.fillMaxWidth().height(120.dp), shape = RoundedCornerShape(24.dp))

                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.headlineSmall)
                        Text(item.basePrice.formatted(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalIconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close", modifier = Modifier.size(22.dp)) }
                }

                item.modifierGroups.forEach { group ->
                    ModifierGroupSection(group, sheet, onToggle)
                }

                sheet.blockedReason?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                // Debug builds only: the panel opens over the sheet, so changes show here at once.
                if (LocalOpenDebugPanel.current != null) {
                    Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        if (onStopSelling != null && !item.isSoldOut) {
                            TextButton(onClick = onStopSelling) { Text("Stop selling this item (debug)") }
                        }
                        DebugPanelAction()
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
            ) {
                QuantityPill(draft.quantity, onQuantity)
                Button(onClick = onAdd, enabled = sheet.canAdd, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Text(actionLabel, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** The outlined minus / count / plus pill from the artboard. */
@Composable
private fun QuantityPill(quantity: Int, onQuantity: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(56.dp).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
    ) {
        IconButton(onClick = { onQuantity(quantity - 1) }, enabled = quantity > 1, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_remove), contentDescription = "Decrease quantity", modifier = Modifier.size(20.dp))
        }
        Text(
            "$quantity",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 20.dp).semantics { contentDescription = "Quantity $quantity" },
        )
        IconButton(onClick = { onQuantity(quantity + 1) }, enabled = quantity < MAX_QUANTITY, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = "Increase quantity", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ModifierGroupSection(group: ModifierGroup, sheet: ItemSheetState, onToggle: (String, String) -> Unit) {
    val missing = group in sheet.draft.missingRequiredGroups
    val single = group.maxSelections == 1
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(group.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (group.isRequired && single) {
                // The artboard's "Required" chip; it turns to the error colors while nothing is picked.
                val chip = if (missing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                val chipText = if (missing) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                Text(
                    group.ruleLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = chipText,
                    modifier = Modifier
                        .heightIn(min = 24.dp)
                        .background(chip, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            } else {
                Text(
                    group.ruleLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        group.options.forEach { option ->
            val selected = sheet.draft.isSelected(group, option)
            // A choice that sold out after it was picked stays on, in error color, so it can be removed.
            val enabled = sheet.draft.canToggle(group, option)
            val soldOutPick = selected && option.isSoldOut
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.MinTouchTarget)
                    .alpha(if (enabled) 1f else 0.45f)
                    .toggleable(
                        value = selected,
                        enabled = enabled,
                        role = if (single) Role.RadioButton else Role.Checkbox,
                        onValueChange = { onToggle(group.id, option.id) },
                    ),
            ) {
                if (single) RadioMark(selected) else CheckMark(selected)
                Text(
                    option.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (option.isSoldOut) "Sold out" else option.priceLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (option.isSoldOut) FontWeight.Medium else FontWeight.Normal,
                    color = when {
                        soldOutPick -> MaterialTheme.colorScheme.error
                        option.isSoldOut -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

// The marks are decoration: the row's toggleable carries the role and state for screen readers.

@Composable
private fun RadioMark(selected: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(20.dp)
            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
    ) {
        if (selected) Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
    }
}

@Composable
private fun CheckMark(selected: Boolean) {
    val shape = RoundedCornerShape(2.dp)
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .size(18.dp)
            .background(if (selected) color else Color.Transparent, shape)
            .border(2.dp, color, shape),
    ) {
        if (selected) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
        }
    }
}
