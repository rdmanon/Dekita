package tech.vauldex.dekita.ui.discover

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.ui.components.StoreArt
import tech.vauldex.dekita.ui.components.StoreStatusBadge
import tech.vauldex.dekita.presentation.label
import tech.vauldex.dekita.presentation.queueLabel

/**
 * A store card in a grouped list: photo, name, category and walking time, status badge and queue.
 * Long names, Japanese ones included, stay on one line and end in an ellipsis.
 */
@Composable
internal fun StoreRow(
    store: Store,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Set when a search matched one of the store's items rather than the store itself. */
    matchedItem: String? = null,
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            StoreArt(store, Modifier.size(56.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(
                    store.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${store.category} · ${store.walkMinutes} min walk",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (matchedItem != null) {
                    Text(
                        "Has $matchedItem",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StoreStatusLine(store)
            }
        }
    }
}

/** Badge plus queue text. The badge cross-fades when the store's state changes in the live feed. */
@Composable
internal fun StoreStatusLine(store: Store, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = store.badge,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "store-badge",
        ) { badge -> StoreStatusBadge(badge) }
        Text(
            store.queueLabel(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** A large recommended-store card: the photo with the name and live status over it. */
@Composable
internal fun RecommendedCard(store: Store, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.width(236.dp).height(200.dp),
    ) {
        Box {
            StoreArt(store, Modifier.fillMaxSize(), shape = RectangleShape)
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .fillMaxWidth()
                    // A dark scrim that stays dark in both themes, like text over a photo.
                    .background(Color(0xCC231A16), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    store.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    recommendedSummary(store),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun recommendedSummary(store: Store): String {
    val detail = store.queueLabel().ifEmpty { "${store.walkMinutes} min walk" }
    return "${store.badge.label} · $detail"
}
