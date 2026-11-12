package tech.vauldex.dekita.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tech.vauldex.dekita.R
import tech.vauldex.dekita.domain.BusinessType
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.domain.StoreBadge
import tech.vauldex.dekita.presentation.headline
import tech.vauldex.dekita.presentation.label
import tech.vauldex.dekita.presentation.modesLabel
import tech.vauldex.dekita.presentation.queueDetail
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
        BusinessType.BOTH -> StatusPill(type.modesLabel.orEmpty(), colors.bothModes, modifier)
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
fun groupedShape(index: Int, count: Int, outer: Dp = 20.dp): Shape {
    val inner = 4.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

/** The store's live queue as a card. Shows nothing when the store has no queue (paused). Reused by the cart. */
@Composable
fun LiveQueueCounter(store: Store, modifier: Modifier = Modifier) {
    val queue = store.queue ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            ) {
                Icon(
                    painterResource(R.drawable.ic_group),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(queue.headline(), style = MaterialTheme.typography.titleMedium)
                    if (queue.isHigh) StatusPill("Busy", DekitaTheme.colors.full)
                }
                Text(
                    store.queueDetail(),
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
