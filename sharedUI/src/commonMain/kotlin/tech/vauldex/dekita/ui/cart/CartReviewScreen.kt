package tech.vauldex.dekita.ui.cart

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dekita.sharedui.generated.resources.Res
import dekita.sharedui.generated.resources.ic_add
import dekita.sharedui.generated.resources.ic_arrow_back
import dekita.sharedui.generated.resources.ic_bag
import dekita.sharedui.generated.resources.ic_card
import dekita.sharedui.generated.resources.ic_check
import dekita.sharedui.generated.resources.ic_info
import dekita.sharedui.generated.resources.ic_place
import org.jetbrains.compose.resources.painterResource
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.CheckoutAction
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.ItemSelection
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.presentation.cart.CartLineUi
import tech.vauldex.dekita.presentation.cart.CartReviewUiState
import tech.vauldex.dekita.presentation.cart.CartReviewViewModel
import tech.vauldex.dekita.presentation.cart.CheckoutBlock
import tech.vauldex.dekita.presentation.checkoutInfo
import tech.vauldex.dekita.presentation.choiceLabel
import tech.vauldex.dekita.presentation.label
import tech.vauldex.dekita.presentation.message
import tech.vauldex.dekita.presentation.optionsLabel
import tech.vauldex.dekita.presentation.queueSummary
import tech.vauldex.dekita.ui.components.QuantityStepper
import tech.vauldex.dekita.ui.components.StatusPill
import tech.vauldex.dekita.ui.components.StoreArt
import tech.vauldex.dekita.ui.components.groupedShape
import tech.vauldex.dekita.ui.discover.EmptyListState
import tech.vauldex.dekita.ui.theme.DekitaTheme
import tech.vauldex.dekita.ui.theme.Dimens

/**
 * Cart Review, the confirm-or-change loop before checkout: lines with quantity, edit and remove,
 * the store's live queue, Eat-In / Takeout for stores with both modes, totals, and one button
 * whose action depends on the store and mode. Failed or cancelled payments come back here.
 */
@Composable
fun CartReviewScreen(
    viewModel: CartReviewViewModel,
    onBack: () -> Unit,
    onBrowseMenu: () -> Unit,
    onEditLine: (CartLine) -> Unit,
    onPayNow: () -> Unit,
    onDraftSaved: (orderNumber: Int, storeName: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cart") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            if (!state.isEmpty) {
                CheckoutBar(
                    state = state,
                    onClick = {
                        if (state.action == CheckoutAction.HEAD_TO_STORE) {
                            val draft = viewModel.headToStore()
                            if (draft != null) onDraftSaved(draft.number, draft.storeName)
                        } else if (viewModel.canStartPayment()) {
                            onPayNow()
                        }
                    },
                )
            }
        },
    ) { padding ->
        if (state.isEmpty) {
            EmptyListState(
                icon = Res.drawable.ic_bag,
                title = "Your cart is empty",
                body = "Add something from the menu and it will show up here.",
                actionLabel = "Browse menu",
                onAction = onBrowseMenu,
                modifier = Modifier.padding(padding).padding(Dimens.ScreenPadding),
            )
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
                item(key = "queue") { QueueCard(store, Modifier.padding(bottom = 16.dp)) }
            }
            val mode = state.mode
            if (mode != null) {
                item(key = "mode") {
                    ModeSection(
                        state = state,
                        mode = mode,
                        onSelect = viewModel::selectMode,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }
            val rowCount = state.lines.size + 1
            itemsIndexed(state.lines, key = { _, lineUi -> lineUi.line.lineId }) { index, lineUi ->
                CartLineRow(
                    lineUi = lineUi,
                    store = state.store,
                    shape = groupedShape(index, rowCount),
                    onEdit = { onEditLine(lineUi.line) },
                    onDecrease = { viewModel.decrease(lineUi.line.lineId) },
                    onIncrease = { viewModel.increase(lineUi.line.lineId) },
                    onRemove = { viewModel.remove(lineUi.line.lineId) },
                )
            }
            item(key = "add-more") {
                AddMoreRow(shape = groupedShape(rowCount - 1, rowCount), onClick = onBrowseMenu)
            }
            item(key = "totals") { Totals(state, Modifier.padding(top = 16.dp)) }
        }
    }
}

@Composable
private fun QueueCard(store: Store, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            StoreArt(store, Modifier.size(48.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(store.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    store.queueSummary(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (store.isAcceptingOrders) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).background(DekitaTheme.colors.liveDot, CircleShape))
                    Text("Live", style = MaterialTheme.typography.labelMedium, color = DekitaTheme.colors.open.content)
                }
            }
        }
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("How you'll be served", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusPill(
                    mode.label,
                    if (mode == FulfillmentMode.PICKUP) DekitaTheme.colors.pickup else DekitaTheme.colors.inStore,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                painterResource(Res.drawable.ic_info),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                checkoutInfo(state.action, mode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
            Icon(painterResource(Res.drawable.ic_check), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        }
        Text(mode.choiceLabel, style = MaterialTheme.typography.titleMedium, color = content)
    }
}

@Composable
private fun CartLineRow(
    lineUi: CartLineUi,
    store: Store?,
    shape: Shape,
    onEdit: () -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onRemove: () -> Unit,
) {
    val line = lineUi.line
    Surface(
        color = if (lineUi.isSoldOut) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
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
                if (lineUi.isSoldOut) {
                    StatusPill("Sold out", DekitaTheme.colors.full, Modifier.padding(vertical = 4.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Edit") }
                    if (lineUi.isSoldOut) {
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
                    canIncrease = line.quantity < ItemSelection.MAX_QUANTITY,
                    removesAtOne = true,
                )
            }
        }
    }
}

@Composable
private fun AddMoreRow(shape: Shape, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onClick)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp),
        ) {
            Icon(painterResource(Res.drawable.ic_add), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text("Add more items", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Totals(state: CartReviewUiState, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.padding(horizontal = 4.dp)) {
        Row {
            Text("Subtotal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(state.subtotal.formatted(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text(
                "tax incl.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(bottom = 2.dp),
            )
            Text(state.total.formatted(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** The one checkout button, with the reason when it is off. */
@Composable
private fun CheckoutBar(state: CartReviewUiState, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        ) {
            val block = state.block
            if (block != null) {
                Text(
                    block.message(state.store?.name ?: "This store"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (block == CheckoutBlock.CHECKING) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onClick,
                enabled = state.canCheckout,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(
                    painterResource(if (state.action == CheckoutAction.HEAD_TO_STORE) Res.drawable.ic_place else Res.drawable.ic_card),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(state.action.label, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
