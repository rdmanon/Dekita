package tech.vauldex.dekita.ui.discover

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tech.vauldex.dekita.R
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.presentation.discover.StoreFilters
import tech.vauldex.dekita.presentation.discover.WalkLimit
import tech.vauldex.dekita.presentation.filterLabel
import tech.vauldex.dekita.presentation.label
import tech.vauldex.dekita.ui.components.groupedShape

/** No results: says so and offers the one action that always helps. */
@Composable
internal fun NoMatchesState(onClearFilters: () -> Unit, modifier: Modifier = Modifier) {
    MessageCard(
        icon = R.drawable.ic_storefront,
        title = "No stores match",
        body = "Try another search, or turn off some filters.",
        modifier = modifier,
    ) {
        FilledTonalButton(onClick = onClearFilters) { Text("Clear filters") }
    }
}

/** A failed load, with a working Retry. */
@Composable
internal fun LoadErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    MessageCard(
        icon = R.drawable.ic_error,
        title = "Couldn't load stores",
        body = message,
        modifier = modifier,
    ) {
        Button(onClick = onRetry) { Text("Retry") }
    }
}

/** An empty list with its own message, such as no favorites yet. */
@Composable
internal fun EmptyListState(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MessageCard(icon = icon, title = title, body = body, modifier = modifier) {
        FilledTonalButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun MessageCard(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Box(Modifier.padding(top = 8.dp)) { action() }
        }
    }
}

/** Grey blocks in the shape of the content, gently pulsing, while the store feed loads. */
@Composable
internal fun DiscoverPlaceholder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "placeholder")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "placeholder-alpha",
    )
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .alpha(pulse)
            .semantics { contentDescription = "Loading stores" },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { Box(Modifier.width(72.dp).height(32.dp).background(block, RoundedCornerShape(8.dp))) }
        }
        Box(Modifier.fillMaxWidth().height(200.dp).background(block, MaterialTheme.shapes.extraLarge))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(4) { index ->
                Box(Modifier.fillMaxWidth().height(80.dp).background(block, groupedShape(index, 4)))
            }
        }
    }
}

/** Walking distance, category and status filters. They apply as you tap; the button just closes the sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilterSheet(
    filters: StoreFilters,
    categories: List<String>,
    resultCount: Int,
    onWalkLimit: (WalkLimit) -> Unit,
    onToggleCategory: (String) -> Unit,
    onToggleStatus: (StoreAvailability) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Text("Filters", style = MaterialTheme.typography.headlineSmall)

            FilterGroupTitle("Walking distance")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WalkLimit.entries.forEach { limit ->
                    SelectChip(limit.label, filters.walkLimit == limit) { onWalkLimit(limit) }
                }
            }

            FilterGroupTitle("Category")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    SelectChip(category, category in filters.categories) { onToggleCategory(category) }
                }
            }

            FilterGroupTitle("Status")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StoreAvailability.entries.forEach { status ->
                    SelectChip(status.filterLabel, status in filters.statuses) { onToggleStatus(status) }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                TextButton(onClick = onClearAll) { Text("Clear all") }
                Button(onClick = onDismiss) {
                    Text(if (resultCount == 1) "Show 1 store" else "Show $resultCount stores")
                }
            }
        }
    }
}

@Composable
private fun FilterGroupTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}

/** A filter chip with the M3 check mark when selected. */
@Composable
internal fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
    )
}
