package tech.vauldex.dekita.ui.account

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.vauldex.dekita.presentation.account.AccountViewModel
import tech.vauldex.dekita.ui.components.LoadingIndicator
import tech.vauldex.dekita.ui.components.SectionTitle
import tech.vauldex.dekita.ui.components.groupedShape
import tech.vauldex.dekita.ui.debug.LocalOpenDebugPanel
import tech.vauldex.dekita.ui.theme.Dimens

/** Placeholder Account tab. Account is not designed yet; this shows the seeded account data. */
@Composable
fun AccountScreen(viewModel: AccountViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openDebugPanel = LocalOpenDebugPanel.current

    Scaffold(topBar = { TopAppBar(title = { Text("Account") }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = Dimens.ScreenPadding,
                end = Dimens.ScreenPadding,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Dimens.ScreenPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "profile") {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(state.profile.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            state.profile.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (state.isLoading) {
                item(key = "loading") { LoadingIndicator("Loading account…") }
            } else {
                item(key = "payments-title") { SectionTitle("Payment methods") }
                itemsIndexed(state.paymentMethods, key = { _, method -> method.id }) { index, method ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = groupedShape(index, state.paymentMethods.size),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Text(method.label, style = MaterialTheme.typography.bodyLarge)
                            if (method.isDefault) {
                                Text("Default", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                item(key = "inbox-title") { SectionTitle("Inbox") }
                itemsIndexed(state.inbox, key = { _, message -> message.id }) { index, message ->
                    Surface(
                        onClick = { viewModel.markRead(message.id) },
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = groupedShape(index, state.inbox.size),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Box(
                                Modifier.size(8.dp).background(
                                    if (message.isRead) Color.Transparent else MaterialTheme.colorScheme.primary,
                                    CircleShape,
                                ),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(message.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    message.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            if (openDebugPanel != null) {
                item(key = "debug") {
                    Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = openDebugPanel) { Text("Open debug panel") }
                        Text(
                            "Or hold two fingers still anywhere for a second.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "footer") {
                Text(
                    "Mockup build. Every store, order and payment here is sample data.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }
}
