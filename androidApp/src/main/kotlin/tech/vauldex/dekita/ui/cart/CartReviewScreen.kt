package tech.vauldex.dekita.ui.cart

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.vauldex.dekita.R
import tech.vauldex.dekita.domain.Cart
import tech.vauldex.dekita.domain.CheckoutAction
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.MAX_QUANTITY
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.presentation.cart.CartLineUi
import tech.vauldex.dekita.presentation.cart.CartReviewEvent
import tech.vauldex.dekita.presentation.cart.CartReviewUiState
import tech.vauldex.dekita.presentation.cart.CartReviewViewModel
import tech.vauldex.dekita.presentation.cart.CheckoutBlock
import tech.vauldex.dekita.presentation.checkoutInfo
import tech.vauldex.dekita.presentation.choiceLabel
import tech.vauldex.dekita.presentation.label
import tech.vauldex.dekita.presentation.message
import tech.vauldex.dekita.presentation.optionsLabel
import tech.vauldex.dekita.presentation.updateButtonLabel
import tech.vauldex.dekita.ui.components.BottomActionBar
import tech.vauldex.dekita.ui.components.LiveQueueCounter
import tech.vauldex.dekita.ui.components.PrimaryButton
import tech.vauldex.dekita.ui.components.QuantityStepper
import tech.vauldex.dekita.ui.components.StatusPill
import tech.vauldex.dekita.ui.components.StoreArt
import tech.vauldex.dekita.ui.components.groupedShape
import tech.vauldex.dekita.ui.debug.DebugPanelAction
import tech.vauldex.dekita.ui.store.ItemSheet
import tech.vauldex.dekita.ui.theme.DekitaTheme
import tech.vauldex.dekita.ui.theme.Dimens

/**
 * Cart Review, laid out as the Android Cart artboard: the store and its live queue, Eat-In / Takeout for
 * stores with both modes, lines with Edit, quantity and remove, totals, and one button whose action depends
 * on the store and mode. A guest's tap goes to sign-in, which returns here with the cart unchanged.
 */
@Composable
fun CartReviewScreen(
    viewModel: CartReviewViewModel,
    onBack: () -> Unit,
    onBrowseMenu: () -> Unit,
    onSignInRequired: () -> Unit,
    onDraftSaved: (orderId: String) -> Unit,
    onOrderPlaced: (orderId: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSignInRequired by rememberUpdatedState(onSignInRequired)
    val currentOnDraftSaved by rememberUpdatedState(onDraftSaved)
    val currentOnOrderPlaced by rememberUpdatedState(onOrderPlaced)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CartReviewEvent.RequireSignIn -> currentOnSignInRequired()
                is CartReviewEvent.OrderPlaced -> currentOnOrderPlaced(event.orderId)
                is CartReviewEvent.DraftSaved -> currentOnDraftSaved(event.orderId)
            }
        }
    }
    val cart = state.cart

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cart") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                actions = { DebugPanelAction() },
            )
        },
        bottomBar = {
            if (cart != null) CheckoutBar(state, onCheckout = viewModel::checkout)
        },
    ) { padding ->
        if (cart == null) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().padding(padding).padding(Dimens.ScreenPadding),
            ) {
                Text("Your cart is empty", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Add items from a store's menu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = onBrowseMenu, modifier = Modifier.padding(top = 8.dp)) { Text("Browse menu") }
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = Dimens.ScreenPadding,
                end = Dimens.ScreenPadding,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + Dimens.ScreenPadding,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            state.store?.let { store ->
                item(key = "store") { StoreCard(store, Modifier.padding(bottom = 8.dp)) }
                item(key = "queue") { LiveQueueCounter(store, Modifier.padding(bottom = 16.dp)) }
            }
            state.mode?.let { mode ->
                item(key = "mode") { ModeSection(state, mode, viewModel::selectMode, Modifier.padding(bottom = 16.dp)) }
            }
            val rowCount = state.lines.size + 1
            itemsIndexed(state.lines, key = { _, lineUi -> lineUi.line.lineId }) { index, lineUi ->
                LineRow(
                    lineUi = lineUi,
                    store = state.store,
                    shape = groupedShape(index, rowCount),
                    onEdit = { viewModel.editLine(lineUi.line.lineId) },
                    onDecrease = { viewModel.decrease(lineUi.line.lineId) },
                    onIncrease = { viewModel.increase(lineUi.line.lineId) },
                    onRemove = { viewModel.remove(lineUi.line.lineId) },
                )
            }
            item(key = "add-more") { AddMoreRow(groupedShape(rowCount - 1, rowCount), onBrowseMenu) }
            item(key = "totals") { Totals(cart) }
        }
    }

    state.editSheet?.let { sheet ->
        ItemSheet(
            sheet = sheet,
            onToggle = viewModel::toggleEditOption,
            onQuantity = viewModel::setEditQuantity,
            onAdd = { viewModel.saveEdit() },
            onDismiss = viewModel::closeEdit,
            onStopSelling = null,
            actionLabel = updateButtonLabel(sheet.draft.totalPrice),
        )
    }
}

@Composable
private fun StoreCard(store: Store, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        StoreArt(store, Modifier.size(48.dp))
        Text(store.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Stores with both modes get the Eat-In / Takeout toggle; single-mode stores show their one mode,
 * fixed. Either way a line says when the customer pays.
 */
@Composable
private fun ModeSection(
    state: CartReviewUiState,
    mode: FulfillmentMode,
    onSelect: (FulfillmentMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
        if (state.canChooseMode) {
            Text("How are you eating?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth().selectableGroup()) {
                ModeButton(FulfillmentMode.IN_STORE, mode == FulfillmentMode.IN_STORE, isFirst = true, onSelect, Modifier.weight(1f))
                ModeButton(FulfillmentMode.PICKUP, mode == FulfillmentMode.PICKUP, isFirst = false, onSelect, Modifier.weight(1f))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("How you'll be served", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusPill(mode.label, if (mode == FulfillmentMode.PICKUP) DekitaTheme.colors.pickup else DekitaTheme.colors.inStore)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                painterResource(R.drawable.ic_info),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(checkoutInfo(state.action, mode), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One half of the connected Eat-In / Takeout group: the selected half fills and rounds fully. */
@Composable
private fun ModeButton(
    mode: FulfillmentMode,
    isSelected: Boolean,
    isFirst: Boolean,
    onSelect: (FulfillmentMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outer = 24.dp
    val inner = 8.dp
    val start by animateDpAsState(if (isSelected || isFirst) outer else inner, label = "mode-start")
    val end by animateDpAsState(if (isSelected || !isFirst) outer else inner, label = "mode-end")
    val colors = MaterialTheme.colorScheme
    val content = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end))
            .background(if (isSelected) colors.primary else colors.surfaceContainerHighest)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(mode) }),
    ) {
        if (isSelected) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        }
        Text(mode.choiceLabel, style = MaterialTheme.typography.titleMedium, color = content)
    }
}

@Composable
private fun LineRow(
    lineUi: CartLineUi,
    store: Store?,
    shape: Shape,
    onEdit: () -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onRemove: () -> Unit,
) {
    val line = lineUi.line
    val reason = lineUi.unavailableReason
    Surface(
        color = if (reason != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (store != null) StoreArt(store, Modifier.size(56.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(line.item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val options = line.optionsLabel
                if (options.isNotEmpty()) {
                    Text(options, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (reason != null) StatusPill(reason, DekitaTheme.colors.full, Modifier.padding(vertical = 4.dp))
                Row {
                    TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Edit") }
                    if (reason != null) {
                        TextButton(onClick = onRemove, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Remove") }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(line.lineTotal.formatted(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                QuantityStepper(
                    quantity = line.quantity,
                    itemName = line.item.name,
                    onDecrease = onDecrease,
                    onIncrease = onIncrease,
                    canIncrease = line.quantity < MAX_QUANTITY,
                )
            }
        }
    }
}

@Composable
private fun AddMoreRow(shape: Shape, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp),
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text("Add more items", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Totals(cart: Cart) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Subtotal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(cart.subtotal.formatted(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("tax incl.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(cart.total.formatted(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** The one checkout button, with the reason when it is off. */
@Composable
private fun CheckoutBar(state: CartReviewUiState, onCheckout: () -> Unit) {
    BottomActionBar {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.block?.let { block ->
                Text(
                    block.message(state.store?.name ?: "This store"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (block == CheckoutBlock.CHECKING) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            PrimaryButton(
                text = state.action.label,
                onClick = onCheckout,
                enabled = state.canCheckout,
                icon = if (state.action == CheckoutAction.HEAD_TO_STORE) R.drawable.ic_place else R.drawable.ic_cart,
            )
        }
    }
}
