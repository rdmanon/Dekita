package tech.vauldex.dekita.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dekita.sharedui.generated.resources.Res
import dekita.sharedui.generated.resources.ic_arrow_back
import dekita.sharedui.generated.resources.ic_history
import dekita.sharedui.generated.resources.ic_star
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.compose.resources.painterResource
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.presentation.discover.FavoriteItemEntry
import tech.vauldex.dekita.presentation.discover.FavoritesViewModel
import tech.vauldex.dekita.presentation.discover.HistoryOrderEntry
import tech.vauldex.dekita.presentation.discover.HistoryViewModel
import tech.vauldex.dekita.ui.components.LoadingIndicator
import tech.vauldex.dekita.ui.components.SectionTitle
import tech.vauldex.dekita.ui.components.StoreArt
import tech.vauldex.dekita.ui.components.groupedShape
import tech.vauldex.dekita.ui.components.label
import tech.vauldex.dekita.ui.theme.Dimens

/** Favorited items, each with its store, then favorited stores. Every row opens its store. */
@Composable
fun FavoritesScreen(viewModel: FavoritesViewModel, onStoreClick: (String) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DecidedScaffold(title = "Favorites", onBack = onBack) { padding ->
        val error = state.error
        when {
            state.isLoading -> LoadingIndicator("Loading favorites…", Modifier.padding(padding))
            error != null -> LoadErrorState(error, viewModel::retry, Modifier.padding(padding).padding(Dimens.ScreenPadding))
            state.isEmpty -> EmptyListState(
                icon = Res.drawable.ic_star,
                title = "No favorites yet",
                body = "Save the stores and dishes you order often, and they'll show up here for a quick reorder.",
                actionLabel = "Browse stores",
                onAction = onBack,
                modifier = Modifier.padding(padding).padding(Dimens.ScreenPadding),
            )
            else -> LazyColumn(
                contentPadding = listPadding(padding),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.items.isNotEmpty()) {
                    item(key = "items-title") { SectionTitle("Items") }
                    itemsIndexed(state.items, key = { _, entry -> "item-${entry.item.storeId}-${entry.item.itemId}" }) { index, entry ->
                        FavoriteItemRow(entry, groupedShape(index, state.items.size)) { onStoreClick(entry.store.id) }
                    }
                }
                if (state.stores.isNotEmpty()) {
                    item(key = "stores-title") { SectionTitle("Stores") }
                    itemsIndexed(state.stores, key = { _, store -> "store-${store.id}" }) { index, store ->
                        StoreRow(store, groupedShape(index, state.stores.size), onClick = { onStoreClick(store.id) }, modifier = Modifier.padding(bottom = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteItemRow(entry: FavoriteItemEntry, shape: Shape, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            StoreArt(entry.store, Modifier.size(56.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(entry.item.itemName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${entry.store.name} · ${entry.item.price.formatted()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StoreStatusLine(entry.store)
            }
        }
    }
}

/** Where the customer ordered before: the stores, then each order. Every row opens its store. */
@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onStoreClick: (String) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DecidedScaffold(title = "History", onBack = onBack) { padding ->
        val error = state.error
        when {
            state.isLoading -> LoadingIndicator("Loading history…", Modifier.padding(padding))
            error != null -> LoadErrorState(error, viewModel::retry, Modifier.padding(padding).padding(Dimens.ScreenPadding))
            state.isEmpty -> EmptyListState(
                icon = Res.drawable.ic_history,
                title = "No orders yet",
                body = "Stores you order from will show up here, so you can go back in one tap.",
                actionLabel = "Browse stores",
                onAction = onBack,
                modifier = Modifier.padding(padding).padding(Dimens.ScreenPadding),
            )
            else -> LazyColumn(contentPadding = listPadding(padding), modifier = Modifier.fillMaxSize()) {
                historyStores(state.recentStores, onStoreClick)
                item(key = "orders-title") { SectionTitle("Recent orders") }
                itemsIndexed(state.recentOrders, key = { _, entry -> "order-${entry.order.id}" }) { index, entry ->
                    HistoryOrderRow(entry, groupedShape(index, state.recentOrders.size)) { onStoreClick(entry.order.storeId) }
                }
            }
        }
    }
}

private fun LazyListScope.historyStores(stores: List<Store>, onStoreClick: (String) -> Unit) {
    if (stores.isEmpty()) return
    item(key = "stores-title") { SectionTitle("Recent stores") }
    itemsIndexed(stores, key = { _, store -> "store-${store.id}" }) { index, store ->
        StoreRow(store, groupedShape(index, stores.size), onClick = { onStoreClick(store.id) }, modifier = Modifier.padding(bottom = 2.dp))
    }
}

@Composable
private fun HistoryOrderRow(entry: HistoryOrderEntry, shape: Shape, onClick: () -> Unit) {
    val order = entry.order
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                "#${order.number} · ${entry.store?.name ?: order.storeName}",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val first = order.lines.firstOrNull()?.item?.name.orEmpty()
            val more = if (order.lines.size > 1) " +${order.lines.size - 1} more" else ""
            Text(
                "$first$more · ${order.mode.label} · ${order.total.formatted()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${order.status.label} · ${relativeDay(order.createdAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun relativeDay(at: Instant, now: Instant = Clock.System.now()): String =
    when (val days = (now - at).inWholeDays) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> "$days days ago"
    }

@Composable
private fun DecidedScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
        content = content,
    )
}

private fun listPadding(padding: PaddingValues) = PaddingValues(
    start = Dimens.ScreenPadding,
    end = Dimens.ScreenPadding,
    top = padding.calculateTopPadding(),
    bottom = padding.calculateBottomPadding() + Dimens.ScreenPadding,
)
