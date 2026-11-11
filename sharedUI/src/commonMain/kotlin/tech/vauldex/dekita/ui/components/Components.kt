package tech.vauldex.dekita.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import tech.vauldex.dekita.domain.BusinessType
import tech.vauldex.dekita.domain.CompletionReason
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.domain.StoreBadge
import tech.vauldex.dekita.ui.theme.BadgeColors
import tech.vauldex.dekita.ui.theme.DekitaTheme

@Composable
fun StatusPill(text: String, colors: BadgeColors, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = colors.content,
        modifier = modifier
            .background(colors.container, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun BusinessTypeBadge(type: BusinessType, modifier: Modifier = Modifier) {
    val colors = DekitaTheme.colors
    when (type) {
        BusinessType.IN_STORE_ONLY -> StatusPill("In-store Only", colors.inStore, modifier)
        BusinessType.PICKUP_ONLY -> StatusPill("Pickup Only", colors.pickup, modifier)
        BusinessType.BOTH -> StatusPill("In-store & Pickup", colors.bothModes, modifier)
    }
}

/**
 * The one status badge on a store card: Open for Lunch, Pickup Only, In-store Only, Paused or Full.
 * Pass the live `store.badge`, so it changes as soon as the feed does.
 */
@Composable
fun StoreStatusBadge(badge: StoreBadge, modifier: Modifier = Modifier) {
    val colors = DekitaTheme.colors
    val badgeColors = when (badge) {
        StoreBadge.OPEN_FOR_LUNCH -> colors.open
        StoreBadge.PICKUP_ONLY -> colors.pickup
        StoreBadge.IN_STORE_ONLY -> colors.inStore
        StoreBadge.PAUSED -> colors.paused
        StoreBadge.FULL -> colors.full
    }
    StatusPill(badge.label, badgeColors, modifier)
}

val StoreBadge.label: String
    get() = when (this) {
        StoreBadge.OPEN_FOR_LUNCH -> "Open for Lunch"
        StoreBadge.PICKUP_ONLY -> "Pickup Only"
        StoreBadge.IN_STORE_ONLY -> "In-store Only"
        StoreBadge.PAUSED -> "Paused"
        StoreBadge.FULL -> "Full"
    }

/** Shows nothing for an open store. */
@Composable
fun AvailabilityBadge(availability: StoreAvailability, modifier: Modifier = Modifier) {
    val colors = DekitaTheme.colors
    when (availability) {
        StoreAvailability.OPEN -> Unit
        StoreAvailability.PAUSED -> StatusPill("Paused", colors.paused, modifier)
        StoreAvailability.FULL -> StatusPill("Full", colors.full, modifier)
    }
}

@Composable
fun LoadingIndicator(label: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier = modifier.fillMaxWidth().padding(32.dp),
    ) {
        CircularProgressIndicator()
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(top = 16.dp, bottom = 12.dp),
    )
}

/** Corner shape for a row in a grouped list: round outer corners, tight inner ones. */
fun groupedShape(index: Int, count: Int): Shape {
    val outer = 20.dp
    val inner = 4.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

fun Store.queueLabel(): String {
    val queue = queue
    return when {
        availability == StoreAvailability.PAUSED -> "Not accepting orders"
        queue == null -> ""
        availability == StoreAvailability.FULL -> "${queue.ordersAhead} ahead · not taking new orders"
        queue.ordersAhead == 0 -> "No wait"
        else -> "${queue.ordersAhead} ahead · ~${queue.estimatedWaitMinutes} min"
    }
}

val FulfillmentMode.label: String
    get() = when (this) {
        FulfillmentMode.IN_STORE -> "In-store"
        FulfillmentMode.PICKUP -> "Pickup"
    }

val OrderStatus.label: String
    get() = when (this) {
        OrderStatus.DRAFT -> "Draft"
        OrderStatus.PAID -> "Paid"
        OrderStatus.QUEUED -> "In queue"
        OrderStatus.PREPARING -> "Preparing"
        OrderStatus.READY_FOR_PICKUP -> "Ready for pickup"
        OrderStatus.SERVED -> "Served"
        OrderStatus.COMPLETED -> "Completed"
    }

val CompletionReason.label: String
    get() = when (this) {
        CompletionReason.CUSTOMER_CONFIRMED -> "Confirmed by you"
        CompletionReason.AUTO_COMPLETED -> "Auto-completed"
    }
