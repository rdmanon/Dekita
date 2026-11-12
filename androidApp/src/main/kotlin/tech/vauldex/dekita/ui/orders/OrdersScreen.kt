package tech.vauldex.dekita.ui.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.currentStep
import tech.vauldex.dekita.presentation.orders.InboxRow
import tech.vauldex.dekita.presentation.orders.OrdersViewModel
import tech.vauldex.dekita.ui.components.LoadingIndicator
import tech.vauldex.dekita.ui.components.SectionTitle
import tech.vauldex.dekita.ui.components.SignInPrompt
import tech.vauldex.dekita.ui.components.StatusPill
import tech.vauldex.dekita.ui.components.groupedShape
import tech.vauldex.dekita.presentation.itemsLabel
import tech.vauldex.dekita.presentation.label
import tech.vauldex.dekita.ui.theme.DekitaTheme
import tech.vauldex.dekita.ui.theme.Dimens

/**
 * The Orders inbox (RCPT-01): active orders, then inbox messages, then receipts, each newest first.
 * Statuses update live. An active order or a receipt opens its ticket with [onOpenTicket]; a rate prompt
 * opens Rate with [onOpenRating]. An in-store draft waiting for check-in shows here with [onCheckIn],
 * so leaving check-in never loses it.
 */
@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel,
    onSignIn: () -> Unit,
    onCheckIn: (orderId: String) -> Unit,
    onOpenTicket: (orderId: String) -> Unit,
    onOpenRating: (orderId: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Orders") }) }) { padding ->
        if (!state.isSignedIn) {
            SignInPrompt(
                title = "Sign in to see your orders",
                body = "Your active orders, messages and receipts appear here once you sign in.",
                onSignIn = onSignIn,
                modifier = Modifier.padding(padding).padding(Dimens.ScreenPadding),
            )
            return@Scaffold
        }
        if (state.isLoading) {
            LoadingIndicator("Loading orders…", Modifier.padding(padding))
            return@Scaffold
        }
        if (state.isEmpty) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(padding).padding(Dimens.ScreenPadding),
            ) {
                Text("No orders yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Your orders, receipts and messages will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = Dimens.ScreenPadding,
                end = Dimens.ScreenPadding,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Dimens.ScreenPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "active-title") { SectionTitle("Active") }
            if (state.active.isEmpty()) {
                item(key = "active-empty") { Text("No active orders.", style = MaterialTheme.typography.bodyMedium) }
            }
            itemsIndexed(state.active, key = { _, order -> order.id }) { index, order ->
                val shape = groupedShape(index, state.active.size)
                if (order.awaitsCheckIn) {
                    DraftRow(order, shape) { onCheckIn(order.id) }
                } else {
                    ActiveOrderRow(order, shape) { onOpenTicket(order.id) }
                }
            }
            if (state.inbox.isNotEmpty()) {
                item(key = "inbox-title") { SectionTitle("Inbox") }
                itemsIndexed(state.inbox, key = { _, row -> row.message.id }) { index, row ->
                    InboxRowItem(row, groupedShape(index, state.inbox.size)) {
                        viewModel.openMessage(row.message.id)
                        val orderId = row.message.orderId ?: return@InboxRowItem
                        if (row.isRatePrompt) onOpenRating(orderId) else onOpenTicket(orderId)
                    }
                }
            }
            if (state.receipts.isNotEmpty()) {
                item(key = "receipts-title") { SectionTitle("Receipts") }
                itemsIndexed(state.receipts, key = { _, order -> order.id }) { index, order ->
                    ReceiptRow(order, groupedShape(index, state.receipts.size)) { onOpenTicket(order.id) }
                }
            }
        }
    }
}

/** UI-01: one compact row per active order. The big number stays on the ticket. */
@Composable
private fun ActiveOrderRow(order: Order, shape: Shape, onClick: () -> Unit) {
    val numberColors = DekitaTheme.colors.orderNumber
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainer, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // Same colors as the ticket and the Discover banner, so the number looks the same everywhere.
            Text(
                "#${order.number}",
                style = MaterialTheme.typography.titleLarge,
                color = numberColors.content,
                modifier = Modifier
                    .background(numberColors.container, MaterialTheme.shapes.medium)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .semantics { contentDescription = "Order number ${order.number}" },
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text("${order.storeName} · ${order.mode.label}", style = MaterialTheme.typography.titleMedium)
                val table = order.checkIn?.tableNumber?.let { " · Table $it" }.orEmpty()
                Text(
                    "${order.itemsLabel}$table",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                order.currentStep?.let { StatusPill(it.label, DekitaTheme.colors.bothModes) }
            }
        }
    }
}

/** An in-store order built on the way. It has no ticket yet: tapping the row resumes check-in. */
@Composable
private fun DraftRow(order: Order, shape: Shape, onCheckIn: () -> Unit) {
    Surface(onClick = onCheckIn, color = MaterialTheme.colorScheme.surfaceContainer, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                StatusPill("Waiting for check-in", DekitaTheme.colors.bothModes)
                Text("${order.storeName} · Eat-In", style = MaterialTheme.typography.titleMedium)
                val checkedIn = order.checkIn?.let { " · ${it.label}" }.orEmpty()
                Text(
                    "${order.itemsLabel}$checkedIn",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Resume", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** A completed order. Opens as a receipt; shows the rating once there is one. */
@Composable
private fun ReceiptRow(order: Order, shape: Shape, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainer, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("#${order.number} · ${order.storeName}", style = MaterialTheme.typography.titleMedium)
            val detail = order.rating?.label ?: order.completionReason?.label
            Text(
                listOfNotNull(order.mode.label, order.total.formatted(), detail).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** An in-app message. Unread ones have a dot; a rate prompt shows "Rated" once its order is rated. */
@Composable
private fun InboxRowItem(row: InboxRow, shape: Shape, onClick: () -> Unit) {
    val message = row.message
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainer, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { if (!message.isRead) stateDescription = "Unread" },
        ) {
            Box(
                Modifier.size(8.dp).background(
                    if (message.isRead) Color.Transparent else MaterialTheme.colorScheme.primary,
                    CircleShape,
                ),
            )
            Column(Modifier.weight(1f)) {
                Text(message.title, style = MaterialTheme.typography.titleMedium)
                Text(message.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (row.isDone) StatusPill("Rated", DekitaTheme.colors.open)
        }
    }
}
