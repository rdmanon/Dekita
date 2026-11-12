package tech.vauldex.dekita.ui.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.vauldex.dekita.R
import tech.vauldex.dekita.presentation.checkin.CheckInUiState
import tech.vauldex.dekita.presentation.checkin.CheckInViewModel
import tech.vauldex.dekita.presentation.label
import tech.vauldex.dekita.ui.components.LoadingIndicator
import tech.vauldex.dekita.ui.components.StatusPill
import tech.vauldex.dekita.ui.components.groupedShape
import tech.vauldex.dekita.ui.debug.LocalOpenDebugPanel
import tech.vauldex.dekita.ui.theme.DekitaTheme
import tech.vauldex.dekita.ui.theme.Dimens

/**
 * "On your way": the in-store draft, how far the store is, its live queue and [Start Cooking], which
 * stays off until the customer is in range. The QR link is the fallback when GPS fails. Going back
 * keeps the draft; Orders and the Discover banner both lead back here.
 */
@Composable
fun CheckInScreen(
    viewModel: CheckInViewModel,
    onBack: () -> Unit,
    onScanQr: () -> Unit,
    onCheckedIn: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openDebugPanel = LocalOpenDebugPanel.current

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        when {
            state.isLoading -> LoadingIndicator("Loading your order…", Modifier.align(Alignment.Center))
            state.draft == null -> NoDraftMessage(onBack, Modifier.align(Alignment.Center))
            else -> {
                state.store?.let { store ->
                    ArrivalMap(store.name, store.location, state.customerLocation, Modifier.fillMaxSize())
                }
                CheckInSheet(
                    state = state,
                    onStartCooking = { if (viewModel.startCooking()) onCheckedIn() },
                    onScanQr = onScanQr,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Dimens.ScreenPadding, vertical = 8.dp),
        ) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
                modifier = Modifier.size(Dimens.MinTouchTarget),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.draft != null) ScanQrButton(onScanQr)
                // Debug builds only: arrival and QR scans are simulated from the panel.
                openDebugPanel?.let { DebugPanelButton(it) }
            }
        }
    }
}

/** 40 dp pill as drawn, inside a 48 dp touch target. */
@Composable
private fun ScanQrButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.heightIn(min = Dimens.MinTouchTarget).clickable(role = Role.Button, onClick = onClick),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            shadowElevation = 3.dp,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(40.dp).padding(start = 12.dp, end = 16.dp),
            ) {
                Icon(painterResource(R.drawable.ic_qr_code), contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Scan QR", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DebugPanelButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        modifier = Modifier.size(Dimens.MinTouchTarget),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_bug), contentDescription = "Debug panel", modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun CheckInSheet(
    state: CheckInUiState,
    onStartCooking: () -> Unit,
    onScanQr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 22.dp, bottom = 2.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        state.headline,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    ArrivalChip(state)
                }
                Text(state.storeLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val rows = buildList {
                add("Your order" to state.orderSummary)
                add("Distance" to state.distanceLabel)
                add("Queue" to state.queueLabel)
                state.checkIn?.let { add("Check-in" to it.label) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                rows.forEachIndexed { index, (label, value) ->
                    SummaryRow(label, value, groupedShape(index, rows.size, outer = 16.dp))
                }
            }

            // Gaps from the artboard: 10 dp under the button, 8 dp under the hint.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onStartCooking,
                    enabled = state.canStartCooking,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text("Start cooking", style = MaterialTheme.typography.titleMedium) }
                Text(
                    state.startCookingHint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                )
                TextButton(onClick = onScanQr, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.MinTouchTarget)) {
                    Icon(painterResource(R.drawable.ic_qr_code), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Can't check in? Scan the store QR", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ArrivalChip(state: CheckInUiState) {
    val colors = DekitaTheme.colors
    when {
        state.checkIn != null -> StatusPill("Checked in", colors.open)
        state.isInRange -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .heightIn(min = 32.dp)
                .background(colors.open.container, MaterialTheme.shapes.small)
                .padding(start = 8.dp, end = 12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.open.content, modifier = Modifier.size(18.dp))
            Text("In range", style = MaterialTheme.typography.labelLarge, color = colors.open.content)
        }
        else -> StatusPill("Not in range", colors.paused)
    }
}

@Composable
private fun SummaryRow(label: String, value: String, shape: Shape) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .padding(horizontal = 16.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
internal fun NoDraftMessage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.padding(32.dp),
    ) {
        Text("Nothing to check in", style = MaterialTheme.typography.titleLarge)
        Text(
            "This order isn't waiting for check-in any more.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onBack) { Text("Go back") }
    }
}
