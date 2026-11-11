package tech.vauldex.dekita.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.vauldex.dekita.presentation.discover.DiscoverUiState
import tech.vauldex.dekita.presentation.discover.DiscoverViewModel
import tech.vauldex.dekita.ui.components.groupedShape
import tech.vauldex.dekita.ui.theme.Dimens

/**
 * Discover, the start of every order. From the top: search, the In-store / Pickup selector,
 * the decided customer's Favorites and History, then the undecided customer's categories,
 * Recommended and Nearby. Any search or filter swaps the home sections for one result list.
 */
@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel,
    onStoreClick: (String) -> Unit,
    onFavoritesClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onResumeDraft: (orderId: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Kept above the loading branch so the saved scroll position is still here when the list comes back.
    val listState = rememberLazyListState()
    var showFilters by rememberSaveable { mutableStateOf(false) }

    // The field keeps its own text so typing never waits on the view model. It only follows the
    // view model when the query is cleared from elsewhere, such as Clear filters.
    var searchText by rememberSaveable { mutableStateOf(state.filters.query) }
    LaunchedEffect(state.filters.query) {
        if (state.filters.query.isEmpty()) searchText = ""
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = statusBarTop + 8.dp, bottom = Dimens.ScreenPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "search") {
                DiscoverSearchField(
                    query = searchText,
                    onQueryChange = {
                        searchText = it
                        viewModel.setQuery(it)
                    },
                    filterCount = state.filters.sheetFilterCount,
                    onOpenFilters = { showFilters = true },
                    modifier = Modifier.screenPadding(),
                )
            }
            state.draft?.let { draft ->
                item(key = "draft") {
                    DraftBanner(
                        order = draft,
                        onResume = { onResumeDraft(draft.id) },
                        modifier = Modifier.screenPadding().padding(top = 12.dp),
                    )
                }
            }
            item(key = "mode") {
                ModeSelector(
                    selected = state.filters.mode,
                    onSelect = viewModel::selectMode,
                    modifier = Modifier.screenPadding().padding(top = 12.dp),
                )
            }
            item(key = "shortcuts") {
                DecidedShortcuts(
                    favoriteItemCount = state.favoriteItemCount,
                    lastOrderStoreName = state.lastOrderStoreName,
                    onFavoritesClick = onFavoritesClick,
                    onHistoryClick = onHistoryClick,
                    modifier = Modifier.screenPadding().padding(top = 8.dp),
                )
            }
            storeSections(state, viewModel, onStoreClick)
        }
    }

    if (showFilters) {
        FilterSheet(
            filters = state.filters,
            categories = state.categories,
            resultCount = state.stores.size,
            onWalkLimit = viewModel::selectWalkLimit,
            onToggleCategory = viewModel::toggleCategory,
            onToggleStatus = viewModel::toggleStatus,
            onClearAll = viewModel::clearFilters,
            onDismiss = { showFilters = false },
        )
    }
}

private fun LazyListScope.storeSections(
    state: DiscoverUiState,
    viewModel: DiscoverViewModel,
    onStoreClick: (String) -> Unit,
) {
    val error = state.error
    when {
        state.isLoading -> item(key = "placeholder") {
            DiscoverPlaceholder(Modifier.screenPadding().padding(top = 24.dp))
        }

        error != null -> item(key = "error") {
            LoadErrorState(error, onRetry = viewModel::retry, modifier = Modifier.screenPadding().padding(top = 24.dp))
        }

        else -> {
            item(key = "categories") {
                Column {
                    SectionHeading("Browse by category")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding),
                    ) {
                        items(state.categories, key = { it }) { category ->
                            SelectChip(category, category in state.filters.categories) {
                                viewModel.toggleCategory(category)
                            }
                        }
                    }
                }
            }

            if (state.showsResults) {
                // With no results, the empty state below says so and has its own Clear filters.
                if (state.stores.isNotEmpty()) item(key = "results-heading") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.screenPadding().padding(top = 16.dp, bottom = 4.dp),
                    ) {
                        Text(
                            if (state.stores.size == 1) "1 store" else "${state.stores.size} stores",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f).semantics { heading() },
                        )
                        TextButton(onClick = viewModel::clearFilters) { Text("Clear filters") }
                    }
                }
            } else if (state.recommended.isNotEmpty()) {
                item(key = "recommended") {
                    Column {
                        SectionHeading("Recommended")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding),
                        ) {
                            items(state.recommended, key = { it.store.id }) { match ->
                                RecommendedCard(match.store, onClick = { onStoreClick(match.store.id) })
                            }
                        }
                    }
                }
            }

            if (!state.showsResults && state.stores.isNotEmpty()) {
                item(key = "nearby-heading") { SectionHeading("Nearby") }
            }

            if (state.hasNoMatches) {
                item(key = "no-matches") {
                    NoMatchesState(
                        onClearFilters = viewModel::clearFilters,
                        modifier = Modifier.screenPadding().padding(top = 16.dp),
                    )
                }
            }

            itemsIndexed(state.stores, key = { _, match -> "store-${match.store.id}" }) { index, match ->
                StoreRow(
                    store = match.store,
                    shape = groupedShape(index, state.stores.size),
                    onClick = { onStoreClick(match.store.id) },
                    matchedItem = match.matchedItem,
                    modifier = Modifier.screenPadding().padding(bottom = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .fillMaxWidth()
            .screenPadding()
            .padding(top = 24.dp, bottom = 12.dp)
            .semantics { heading() },
    )
}

private fun Modifier.screenPadding(): Modifier = padding(horizontal = Dimens.ScreenPadding)
