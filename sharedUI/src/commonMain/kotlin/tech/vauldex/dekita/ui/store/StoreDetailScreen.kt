package tech.vauldex.dekita.ui.store

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dekita.sharedui.generated.resources.Res
import dekita.sharedui.generated.resources.ic_arrow_back
import dekita.sharedui.generated.resources.ic_favorite
import dekita.sharedui.generated.resources.ic_favorite_filled
import org.jetbrains.compose.resources.painterResource
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.MenuItem
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.presentation.store.StoreDetailViewModel
import tech.vauldex.dekita.ui.components.AvailabilityBadge
import tech.vauldex.dekita.ui.components.BusinessTypeBadge
import tech.vauldex.dekita.ui.components.LoadingIndicator
import tech.vauldex.dekita.ui.components.SectionTitle
import tech.vauldex.dekita.ui.components.StatusPill
import tech.vauldex.dekita.ui.theme.DekitaTheme
import tech.vauldex.dekita.ui.theme.Dimens

/**
 * Placeholder store screen that shows the seeded menu states. The real screen is a later ticket.
 * The iOS app passes [showTopBar] false: its SwiftUI navigation bar shows the title, back and favorite buttons.
 */
@Composable
fun StoreDetailScreen(viewModel: StoreDetailViewModel, onBack: () -> Unit, showTopBar: Boolean = true) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val store = state.store

    Scaffold(
        topBar = {
            if (showTopBar) TopAppBar(
                title = { Text(store?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                actions = {
                    if (store != null) {
                        IconToggleButton(checked = state.isFavorite, onCheckedChange = { viewModel.toggleFavorite() }) {
                            Icon(
                                painterResource(if (state.isFavorite) Res.drawable.ic_favorite_filled else Res.drawable.ic_favorite),
                                contentDescription = if (state.isFavorite) "Remove from favorites" else "Add to favorites",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (store == null) {
            LoadingIndicator("Loading store…", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = Dimens.ScreenPadding,
                end = Dimens.ScreenPadding,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Dimens.ScreenPadding,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { StoreHeader(store) }
            when (val menu = state.menu) {
                LoadState.Loading -> item(key = "menu-loading") { LoadingIndicator("Loading menu…") }
                is LoadState.Error -> item(key = "menu-error") { Text(menu.message) }
                is LoadState.Success -> menu.data.categories.forEach { category ->
                    item(key = "category-${category.id}") { SectionTitle(category.name) }
                    items(menu.data.itemsIn(category.id), key = { it.id }) { item -> MenuItemRow(item) }
                }
            }
        }
    }
}

@Composable
private fun StoreHeader(store: Store) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BusinessTypeBadge(store.businessType)
            AvailabilityBadge(store.availability)
            store.openLabel?.takeIf { store.availability == StoreAvailability.OPEN }?.let {
                StatusPill(it, DekitaTheme.colors.open)
            }
        }
        Text(
            "${store.category} · ${store.walkMinutes} min walk",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                val queue = store.queue
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    when {
                        store.availability == StoreAvailability.PAUSED -> {
                            Text("Not accepting orders", style = MaterialTheme.typography.titleMedium)
                            Text("This store has paused ordering for now.", style = MaterialTheme.typography.bodyMedium)
                        }
                        queue != null -> {
                            Text("${queue.ordersAhead} orders ahead", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (store.availability == StoreAvailability.FULL) {
                                    "Full right now · not taking new orders"
                                } else {
                                    "About ${queue.estimatedWaitMinutes} min wait · updates live"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
}

@Composable
private fun MenuItemRow(item: MenuItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f).alpha(if (item.isSoldOut) 0.45f else 1f),
        ) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Text(
                item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(item.basePrice.formatted(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            item.optionsSummary()?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when {
            item.isSoldOut -> StatusPill("Sold out", DekitaTheme.colors.paused)
            item.availableModes == setOf(FulfillmentMode.IN_STORE) -> StatusPill("In-store only", DekitaTheme.colors.inStore)
            item.availableModes == setOf(FulfillmentMode.PICKUP) -> StatusPill("Pickup only", DekitaTheme.colors.pickup)
        }
    }
}

private fun MenuItem.optionsSummary(): String? {
    if (modifierGroups.isEmpty()) return null
    return modifierGroups.joinToString(" · ") { group ->
        val rule = if (group.isRequired) "required" else "up to ${group.maxSelections}"
        val soldOut = group.options.filter { it.isSoldOut }.joinToString { it.name }
        if (soldOut.isEmpty()) "${group.name} ($rule)" else "${group.name} ($rule), $soldOut sold out"
    }
}
