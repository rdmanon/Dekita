package tech.vauldex.dekita.ui.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.currentStep
import tech.vauldex.dekita.presentation.orders.OrdersViewModel
import tech.vauldex.dekita.ui.components.LoadingIndicator
import tech.vauldex.dekita.ui.components.SectionTitle
import tech.vauldex.dekita.ui.components.SignInPrompt
import tech.vauldex.dekita.ui.components.StatusPill
import tech.vauldex.dekita.ui.components.groupedShape
import tech.vauldex.dekita.presentation.label
import tech.vauldex.dekita.ui.theme.DekitaTheme
import tech.vauldex.dekita.ui.theme.Dimens

/**
 * Orders tab: active tickets, then history. The full Orders inbox is RCPT-01. Statuses update live.
 * An active order opens its ticket with [onOpenTicket]. An in-store draft waiting for check-in shows here
 * with [onCheckIn], so leaving check-in never loses it.
 */
@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel,
    onSignIn: () -> Unit,
    onCheckIn: (orderId: String) -> Unit,
    onOpenTicket: (orderId: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Orders") }) }) { padding ->
        if (!state.isSignedIn) {
            SignInPrompt(
                title = "Sign in to see your orders",
                body = "Your active orders and order history appear here once you sign in.",
                onSignIn = onSignIn,
                modifier = Modifier.padding(padding).padding(Dimens.ScreenPadding),
            )
            return@Scaffold
        }
        if (state.isLoading) {
            LoadingIndicator("Loading orders…", Modifier.padding(padding))
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
            items(state.active, key = { it.id }) { order ->
                if (order.awaitsCheckIn) {
                    DraftCard(order, onCheckIn = { onCheckIn(order.id) }, Modifier.padding(bottom = 12.dp))
                } else {
                    ActiveOrderCard(order, onClick = { onOpenTicket(order.id) }, Modifier.padding(bottom = 12.dp))
                }
            }
            item(key = "history-title") { SectionTitle("History") }
            itemsIndexed(state.history, key = { _, order -> order.id }) { index, order ->
                HistoryRow(order, groupedShape(index, state.history.size))
            }
        }
    }
}

@Composable
private fun ActiveOrderCard(order: Order, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val numberColors = DekitaTheme.colors.orderNumber
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(numberColors.container, MaterialTheme.shapes.large)
                    .padding(16.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "Order number ${order.number}" },
            ) {
                Text("Order number", style = MaterialTheme.typography.labelLarge, color = numberColors.content)
                Text("#${order.number}", style = DekitaTheme.textStyles.orderNumber, color = numberColors.content)
            }
            Text("${order.storeName} · ${order.mode.label}", style = MaterialTheme.typography.titleMedium)
            Text(
                "${order.itemCount} items · ${order.total.formatted()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            order.checkIn?.tableNumber?.let { Text("Table $it", style = MaterialTheme.typography.titleMedium) }
            order.currentStep?.let { StatusPill(it.label, DekitaTheme.colors.bothModes) }
        }
    }
}

/** An in-store order built on the way. It has no ticket yet: the kitchen starts after check-in. */
@Composable
private fun DraftCard(order: Order, onCheckIn: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            StatusPill("Waiting for check-in", DekitaTheme.colors.bothModes)
            Text("${order.storeName} · Eat-In", style = MaterialTheme.typography.titleMedium)
            Text(
                "${order.itemCount} items · ${order.total.formatted()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            order.checkIn?.let {
                Text(it.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onCheckIn, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("Resume check-in") }
        }
    }
}

@Composable
private fun HistoryRow(order: Order, shape: Shape) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("#${order.number} · ${order.storeName}", style = MaterialTheme.typography.titleMedium)
            val reason = order.completionReason?.label?.let { " · $it" }.orEmpty()
            Text(
                "${order.mode.label} · ${order.total.formatted()}$reason",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
