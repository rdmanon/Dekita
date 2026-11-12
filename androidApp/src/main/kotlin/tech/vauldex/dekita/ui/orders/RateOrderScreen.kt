package tech.vauldex.dekita.ui.orders

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.vauldex.dekita.R
import tech.vauldex.dekita.presentation.orders.RateItem
import tech.vauldex.dekita.presentation.orders.RateOrderUiState
import tech.vauldex.dekita.presentation.orders.RateOrderViewModel
import tech.vauldex.dekita.ui.components.BottomActionBar
import tech.vauldex.dekita.ui.components.LoadingIndicator
import tech.vauldex.dekita.ui.components.PrimaryButton
import tech.vauldex.dekita.ui.components.SectionTitle
import tech.vauldex.dekita.ui.theme.Dimens

/**
 * Rate & Add to Favorites (RCPT-01). A rating is recorded once. Favorites save on each tap. Leaving any
 * way other than Submit (Skip, Close, system Back) is a skip, which leaves an in-app inbox message.
 */
@Composable
fun RateOrderScreen(viewModel: RateOrderViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)
    val leave = {
        viewModel.skip()
        currentOnDone()
    }
    BackHandler(onBack = leave)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rate & Add to Favorites") },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Close")
                    }
                },
            )
        },
        bottomBar = {
            if (state.order != null) {
                BottomActionBar {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state.alreadyRated) {
                            PrimaryButton("Done", onClick = { currentOnDone() })
                        } else {
                            PrimaryButton(
                                "Submit",
                                onClick = { if (viewModel.submit()) currentOnDone() },
                                enabled = state.canSubmit,
                            )
                            TextButton(onClick = leave, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator("Loading your order…", Modifier.padding(padding))
            state.order == null -> Text(
                "This order can't be rated.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(padding).padding(Dimens.ScreenPadding),
            )
            else -> Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.ScreenPadding),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (state.alreadyRated) "Thanks for rating!" else "How was it?",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(state.heading, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StarRow(state, enabled = !state.alreadyRated, onStars = viewModel::setStars)
                TagChips(state, enabled = !state.alreadyRated, onTag = viewModel::toggleTag)
                ItemFavorites(state.items, onToggle = viewModel::toggleFavorite)
                if (!state.alreadyRated) {
                    Text(
                        "Skip and we'll leave a reminder in your Orders inbox. No notifications.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Five stars. TalkBack reads the row as one control, for example "Rating, 3 of 5 stars". */
@Composable
private fun StarRow(state: RateOrderUiState, enabled: Boolean, onStars: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .selectableGroup()
            .semantics { contentDescription = "Rating"; stateDescription = state.starsDescription },
    ) {
        (1..5).forEach { star ->
            val filled = star <= state.stars
            IconButton(
                onClick = { onStars(star) },
                enabled = enabled,
                modifier = Modifier.size(56.dp).semantics {
                    role = Role.RadioButton
                    selected = star == state.stars
                    contentDescription = "$star of 5 stars"
                },
            ) {
                Icon(
                    painterResource(if (filled) R.drawable.ic_star_filled else R.drawable.ic_star),
                    contentDescription = null,
                    tint = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}

@Composable
private fun TagChips(state: RateOrderUiState, enabled: Boolean, onTag: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Anything stand out? (optional)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.tagOptions.forEach { tag ->
                FilterChip(
                    selected = tag in state.tags,
                    onClick = { onTag(tag) },
                    enabled = enabled,
                    label = { Text(tag) },
                )
            }
        }
    }
}

/** One heart per ordered item. Each tap saves at once; favorites appear in Discover → Favorites. */
@Composable
private fun ItemFavorites(items: List<RateItem>, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Add to your favorites")
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                items.forEach { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.item.itemName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                entry.item.price.formatted(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconToggleButton(
                            checked = entry.isFavorite,
                            onCheckedChange = { onToggle(entry.item.itemId) },
                            modifier = Modifier.semantics { contentDescription = "Favorite ${entry.item.itemName}" },
                        ) {
                            Icon(
                                painterResource(if (entry.isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
                                contentDescription = null,
                                tint = if (entry.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
