package tech.vauldex.dekita.ui.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.vauldex.dekita.R
import tech.vauldex.dekita.presentation.checkin.CheckInViewModel
import tech.vauldex.dekita.presentation.label
import tech.vauldex.dekita.ui.components.LoadingIndicator
import tech.vauldex.dekita.ui.theme.DekitaTheme
import tech.vauldex.dekita.ui.theme.Dimens

/**
 * Placeholder for seat verification, which opens after a successful check-in. SEAT-01 replaces this
 * with the real prompt (seat available, switch to Pickup, wait, or cancel) before the kitchen starts.
 * Its button pays with a mock payment (PAY-01 replaces it) and calls [onPaid] with the order to show.
 */
@Composable
fun SeatCheckScreen(viewModel: CheckInViewModel, onBack: () -> Unit, onPaid: (orderId: String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Seat check") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val draft = state.draft
        val checkIn = state.checkIn
        when {
            state.isLoading -> LoadingIndicator("Loading your order…", Modifier.padding(padding))
            draft == null || checkIn == null -> Box(Modifier.fillMaxSize().padding(padding)) {
                NoDraftMessage(onBack, Modifier.align(Alignment.Center))
            }
            else -> Column(
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
                Text(checkIn.label, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(state.storeLine, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                    Text(
                        "Next: confirm there's a seat for you before the kitchen starts cooking. " +
                            "This step is a placeholder until seat verification (SEAT-01) is built, " +
                            "and payment is simulated until PAY-01.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                Box(Modifier.weight(1f))
                Button(
                    onClick = { viewModel.payAtCheckIn()?.let(onPaid) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text("Pay now · ${draft.total.formatted()}", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
