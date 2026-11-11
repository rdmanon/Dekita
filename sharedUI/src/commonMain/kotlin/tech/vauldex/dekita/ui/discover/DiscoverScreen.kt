package tech.vauldex.dekita.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.presentation.discover.DiscoverViewModel
import tech.vauldex.dekita.ui.components.AvailabilityBadge
import tech.vauldex.dekita.ui.components.BusinessTypeBadge
import tech.vauldex.dekita.ui.components.LoadingIndicator
import tech.vauldex.dekita.ui.components.SectionTitle
import tech.vauldex.dekita.ui.components.StoreArt
import tech.vauldex.dekita.ui.components.groupedShape
import tech.vauldex.dekita.ui.components.queueLabel
import tech.vauldex.dekita.ui.theme.Dimens

/** Placeholder Discover screen: enough to show the seed data. The real screen is a later ticket. */
@Composable
fun DiscoverScreen(viewModel: DiscoverViewModel, onStoreClick: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Kept above the loading branch so the saved scroll position is still here when the list comes back.
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Lunchtime near you",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("Discover")
                    }
                },
            )
        },
    ) { padding ->
        val error = state.error
        when {
            state.isLoading -> LoadingIndicator("Finding stores near you…", Modifier.padding(padding))
            error != null -> Text(error, Modifier.padding(padding).padding(Dimens.ScreenPadding))
            else -> {
                val favorites = state.stores.filter { it.id in state.favoriteStoreIds }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = Dimens.ScreenPadding,
                        end = Dimens.ScreenPadding,
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + Dimens.ScreenPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "favorites-title") { SectionTitle("Favorites · ${state.favoritesCount}") }
                    itemsIndexed(favorites, key = { _, store -> "favorite-${store.id}" }) { index, store ->
                        StoreRow(store, groupedShape(index, favorites.size)) { onStoreClick(store.id) }
                    }
                    item(key = "nearby-title") { SectionTitle("Nearby") }
                    itemsIndexed(state.stores, key = { _, store -> "nearby-${store.id}" }) { index, store ->
                        StoreRow(store, groupedShape(index, state.stores.size)) { onStoreClick(store.id) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoreRow(store: Store, shape: Shape, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            StoreArt(store, 56.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(store.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${store.category} · ${store.walkMinutes} min walk",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BusinessTypeBadge(store.businessType)
                    AvailabilityBadge(store.availability)
                    Text(
                        store.queueLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }
    }
}
