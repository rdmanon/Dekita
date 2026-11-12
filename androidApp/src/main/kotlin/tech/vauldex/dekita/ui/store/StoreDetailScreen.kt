package tech.vauldex.dekita.ui.store

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import tech.vauldex.dekita.presentation.modesLabel
import tech.vauldex.dekita.presentation.shareText
import tech.vauldex.dekita.presentation.store.MenuTab
import tech.vauldex.dekita.ui.components.StoreStatusBadge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tech.vauldex.dekita.R
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.MenuItem
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.presentation.NOT_ACCEPTING_ORDERS_MESSAGE
import tech.vauldex.dekita.presentation.optionsSummary
import tech.vauldex.dekita.presentation.store.AddResult
import tech.vauldex.dekita.presentation.store.StoreDetailViewModel
import tech.vauldex.dekita.ui.components.LiveQueueCounter
import tech.vauldex.dekita.ui.components.StatusPill
import tech.vauldex.dekita.ui.components.LoadingIndicator
import tech.vauldex.dekita.ui.components.StoreArt
import tech.vauldex.dekita.ui.debug.DebugPanelAction
import tech.vauldex.dekita.ui.debug.LocalOpenDebugPanel
import tech.vauldex.dekita.ui.theme.DekitaTheme
import tech.vauldex.dekita.ui.theme.Dimens

/**
 * The store screen, as the Android Menu artboard: a hero picture with floating Back / Favorite / Share buttons,
 * the store header and live queue, an underline tab row (Popular first, then the categories) and one list of
 * the tab's items. Tapping an item opens its customize sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StoreDetailScreen(viewModel: StoreDetailViewModel, onBack: () -> Unit, onViewCart: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val store = state.store
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isDebug = LocalOpenDebugPanel.current != null
    fun announceAdded(name: String) {
        scope.launch { snackbarHostState.showSnackbar("Added $name") }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // The hero runs under the status bar; the floating buttons add that inset themselves.
        contentWindowInsets = WindowInsets.navigationBars,
        floatingActionButton = {
            // As on the Android menu artboard: an extended FAB once the cart has items.
            state.cart?.let { cart ->
                ExtendedFloatingActionButton(
                    onClick = onViewCart,
                    icon = { Icon(painterResource(R.drawable.ic_shopping_bag), contentDescription = null) },
                    text = { Text("View cart (${cart.itemCount}) · ${cart.total.formatted()}") },
                )
            }
        },
    ) { padding ->
        if (store == null) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                LoadingIndicator("Loading store…", Modifier.align(Alignment.Center))
                FloatingButtons(Modifier.align(Alignment.TopStart).fillMaxWidth(), onBack = onBack, favorite = null, onShare = null)
            }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(
                    // Room under the last row for the cart button.
                    bottom = padding.calculateBottomPadding() + Dimens.ScreenPadding + if (state.cart != null) 88.dp else 0.dp,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "hero") { StoreHero(store) }
                item(key = "header") { StoreHeader(store, Modifier.padding(horizontal = Dimens.ScreenPadding)) }
                when (val menu = state.menu) {
                    LoadState.Loading -> item(key = "menu-loading") { LoadingIndicator("Loading menu…") }
                    is LoadState.Error -> item(key = "menu-error") { Text(menu.message, Modifier.padding(Dimens.ScreenPadding)) }
                    is LoadState.Success -> {
                        if (state.tabs.isNotEmpty()) stickyHeader(key = "tabs") {
                            MenuTabs(state.tabs, state.currentTab?.id, onSelect = viewModel::selectTab)
                        }
                        items(state.visibleItems, key = { it.id }) { item ->
                            MenuItemRow(
                                item,
                                artKey = item.artKey ?: store.artKey,
                                canOrder = state.canOrder,
                                modifier = Modifier.padding(horizontal = Dimens.ScreenPadding),
                            ) { viewModel.openItem(item.id) }
                        }
                        state.emptyTabMessage?.let { message ->
                            item(key = "empty-tab") {
                                Text(
                                    message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
            FloatingButtons(
                Modifier.align(Alignment.TopStart).fillMaxWidth(),
                onBack = onBack,
                favorite = FavoriteState(state.isFavorite, viewModel::toggleFavorite),
                onShare = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, store.shareText())
                    }
                    context.startActivity(Intent.createChooser(send, null))
                },
            )
        }
    }

    state.sheet?.let { sheet ->
        ItemSheet(
            sheet = sheet,
            storeArtKey = store?.artKey,
            onToggle = viewModel::toggleOption,
            onQuantity = viewModel::setQuantity,
            onAdd = {
                val name = sheet.draft.item.name
                if (viewModel.addToCart() == AddResult.ADDED) announceAdded(name)
            },
            onDismiss = viewModel::closeItem,
            onStopSelling = if (isDebug) viewModel::stopSellingOpenItem else null,
        )
    }
    state.pendingReplaceStoreName?.let { otherStore ->
        AlertDialog(
            onDismissRequest = viewModel::dismissReplace,
            title = { Text("Start a new cart?") },
            text = { Text("Your cart has items from $otherStore. Start a new cart with ${store?.name.orEmpty()}?") },
            confirmButton = {
                TextButton(onClick = {
                    val name = state.sheet?.draft?.item?.name.orEmpty()
                    viewModel.confirmReplace()
                    announceAdded(name)
                }) { Text("Start new cart") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissReplace) { Text("Cancel") } },
        )
    }
}

private class FavoriteState(val isFavorite: Boolean, val onToggle: () -> Unit)

/** The 248 dp picture; the page's rounded top edge overlaps its bottom 28 dp. */
@Composable
private fun StoreHero(store: Store) {
    Box(Modifier.fillMaxWidth().height(276.dp)) {
        StoreArt(store, Modifier.fillMaxSize(), shape = RectangleShape)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(28.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        )
    }
}

/** Back, then Favorite and Share, as 48 dp circles over the picture. Favorite and Share are null while the store loads. */
@Composable
private fun FloatingButtons(modifier: Modifier, onBack: () -> Unit, favorite: FavoriteState?, onShare: (() -> Unit)?) {
    val container = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    val circle = Modifier.size(48.dp).background(container, CircleShape)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp),
    ) {
        IconButton(onClick = onBack, modifier = circle) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
        }
        Spacer(Modifier.weight(1f))
        if (LocalOpenDebugPanel.current != null) Box(circle) { DebugPanelAction() }
        if (favorite != null) {
            IconToggleButton(checked = favorite.isFavorite, onCheckedChange = { favorite.onToggle() }, modifier = circle) {
                Icon(
                    painterResource(if (favorite.isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
                    contentDescription = if (favorite.isFavorite) "Remove store from favorites" else "Add store to favorites",
                    tint = if (favorite.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (onShare != null) {
            IconButton(onClick = onShare, modifier = circle) {
                Icon(painterResource(R.drawable.ic_share), contentDescription = "Share store", modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun StoreHeader(store: Store, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = modifier.padding(bottom = 8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(store.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                StoreStatusBadge(store.badge)
            }
            Text(
                listOfNotNull("${store.category} · ${store.walkMinutes} min walk", store.businessType.modesLabel).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LiveQueueCounter(store)
        if (!store.isAcceptingOrders) {
            Surface(
                color = DekitaTheme.colors.paused.container,
                contentColor = DekitaTheme.colors.paused.content,
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    NOT_ACCEPTING_ORDERS_MESSAGE,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }
}

/** Underline tabs: the selected one is primary with a 3 dp indicator the width of its label. */
@Composable
private fun MenuTabs(tabs: List<MenuTab>, selectedId: String?, onSelect: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .selectableGroup()
                .padding(horizontal = 4.dp),
        ) {
            tabs.forEach { tab ->
                val selected = tab.id == selectedId
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab.id) })
                        .padding(horizontal = 16.dp),
                ) {
                    Text(
                        tab.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    if (selected) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .wrapContentHeight(Alignment.Bottom)
                                .height(3.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                        )
                    }
                }
            }
        }
        HorizontalDivider(Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * A menu row. Orderable items end with the Android-Menu artboard's tonal add button, which turns off with the
 * rest of the store's ordering while it isn't accepting orders. The row still opens the sheet so the menu
 * can be read.
 */
@Composable
private fun MenuItemRow(item: MenuItem, artKey: String?, canOrder: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !item.isSoldOut, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        StoreArt(artKey, Modifier.size(72.dp).alpha(if (item.isSoldOut) 0.45f else 1f), shape = RoundedCornerShape(16.dp))
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
        if (!item.isSoldOut) AddAffordance(enabled = canOrder)
    }
}

/** Decorative: tapping the row opens the sheet. Dimmed like a disabled control while the store is paused. */
@Composable
private fun AddAffordance(enabled: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
    ) {
        Icon(
            painterResource(R.drawable.ic_add),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(22.dp),
        )
    }
}
