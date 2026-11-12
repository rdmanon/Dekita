package tech.vauldex.dekita.ui.discover

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tech.vauldex.dekita.R
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.ui.debug.DebugPanelAction

/** The search bar at the top of Discover. Search covers store names and item names. */
@Composable
internal fun DiscoverSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    filterCount: Int,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 16.dp, end = 4.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            "Search stores or dishes",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear search")
            }
        }
        IconButton(onClick = onOpenFilters) {
            BadgedBox(badge = { if (filterCount > 0) Badge { Text("$filterCount") } }) {
                Icon(
                    painterResource(R.drawable.ic_tune),
                    contentDescription = if (filterCount > 0) "Filters, $filterCount on" else "Filters",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Debug builds only, as on iOS Discover: the panel opens over the list, which changes live.
        DebugPanelAction()
    }
}

/**
 * The In-store / Pickup selector, shown before any store, as an M3 Expressive connected button group.
 * "All" clears the selection. The selected segment fills with the brand color and rounds fully.
 */
@Composable
internal fun ModeSelector(
    selected: FulfillmentMode?,
    onSelect: (FulfillmentMode?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf<Pair<FulfillmentMode?, String>>(
        null to "All",
        FulfillmentMode.IN_STORE to "In-store",
        FulfillmentMode.PICKUP to "Pickup",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = modifier.fillMaxWidth().selectableGroup()) {
        options.forEachIndexed { index, (mode, label) ->
            ModeSegment(
                label = label,
                isSelected = selected == mode,
                isFirst = index == 0,
                isLast = index == options.lastIndex,
                onClick = { onSelect(mode) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ModeSegment(
    label: String,
    isSelected: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val full = 20.dp
    val inner = 8.dp
    val start by animateDpAsState(if (isSelected || isFirst) full else inner, label = "segment-start")
    val end by animateDpAsState(if (isSelected || isLast) full else inner, label = "segment-end")
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(40.dp)
            .clip(RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end))
            .background(if (isSelected) colors.primary else colors.surfaceContainerHighest)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        val content = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
        if (isSelected) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

/** The decided customer's shortcuts. Search is the bar above them. */
@Composable
internal fun DecidedShortcuts(
    favoriteItemCount: Int,
    lastOrderStoreName: String?,
    onFavoritesClick: () -> Unit,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        ShortcutTile(
            icon = R.drawable.ic_star,
            title = "Favorites",
            subtitle = when (favoriteItemCount) {
                0 -> "None yet"
                1 -> "1 item"
                else -> "$favoriteItemCount items"
            },
            onClick = onFavoritesClick,
            modifier = Modifier.weight(1f),
        )
        ShortcutTile(
            icon = R.drawable.ic_history,
            title = "History",
            subtitle = lastOrderStoreName?.let { "Last: $it" } ?: "No orders yet",
            onClick = onHistoryClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ShortcutTile(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** An in-store draft waiting for check-in. Tapping it goes back to the "On your way" screen. */
@Composable
internal fun DraftBanner(draft: Order, onResume: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onResume,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(painterResource(R.drawable.ic_restaurant), contentDescription = null, modifier = Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text("Your Eat-In order is waiting", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    "${draft.storeName} · ${draft.itemCount} items · ${draft.total.formatted()}",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("Resume", style = MaterialTheme.typography.labelLarge)
        }
    }
}
