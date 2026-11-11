package tech.vauldex.dekita.ui.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tech.vauldex.dekita.R
import tech.vauldex.dekita.ui.components.PrimaryButton
import tech.vauldex.dekita.ui.theme.DekitaTheme
import tech.vauldex.dekita.ui.theme.Dimens

/**
 * Placeholder for Rate & Add to Favorites, which opens after [Confirm] closes an order.
 * RCPT-01 replaces this with the rating, the per-item Favorite toggles and Skip.
 */
@Composable
fun RateOrderPlaceholderScreen(onDone: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Rate & Add to Favorites") }) }) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(Dimens.ScreenPadding),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(top = 24.dp).size(64.dp).background(DekitaTheme.colors.open.container, CircleShape),
            ) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = DekitaTheme.colors.open.content,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text("Thanks! Your order is complete.", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                Text(
                    "Next: rate your order and add items to your favorites. " +
                        "This step is a placeholder until Rate & Add to Favorites (RCPT-01) is built.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Box(Modifier.weight(1f))
            PrimaryButton("Done", onClick = onDone)
        }
    }
}
