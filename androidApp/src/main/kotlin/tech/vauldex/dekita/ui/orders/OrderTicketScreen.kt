package tech.vauldex.dekita.ui.orders

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import tech.vauldex.dekita.R
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.presentation.optionsLabel
import tech.vauldex.dekita.presentation.orders.OrderTicketEvent
import tech.vauldex.dekita.presentation.orders.OrderTicketUiState
import tech.vauldex.dekita.presentation.orders.OrderTicketViewModel
import tech.vauldex.dekita.presentation.orders.StepState
import tech.vauldex.dekita.presentation.orders.TicketStep
import tech.vauldex.dekita.ui.components.BottomActionBar
import tech.vauldex.dekita.ui.components.LoadingIndicator
import tech.vauldex.dekita.ui.components.PrimaryButton
import tech.vauldex.dekita.ui.components.SectionTitle
import tech.vauldex.dekita.ui.debug.LocalOpenDebugPanel
import tech.vauldex.dekita.ui.theme.DekitaTheme
import tech.vauldex.dekita.ui.theme.Dimens

/**
 * The order ticket (ORDER-01): the number to show at the counter, the live stepper, and [Confirm].
 * Status changes arrive without a refresh. Confirm closes the order and calls [onConfirmed].
 * A completed order shows as a receipt (RCPT-01) and opens Rate with [onOpenRating].
 */
@Composable
fun OrderTicketScreen(
    viewModel: OrderTicketViewModel,
    onBack: () -> Unit,
    onConfirmed: () -> Unit,
    onOpenRating: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isDebug = LocalOpenDebugPanel.current != null
    val currentOnOpenRating by rememberUpdatedState(onOpenRating)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                OrderTicketEvent.OpenRating -> currentOnOpenRating()
            }
        }
    }
    RequestNotificationPermission()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order ticket") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            if (state.order != null && !state.isCompleted) {
                BottomActionBar {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.confirmHint?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        PrimaryButton("Confirm", onClick = { if (viewModel.confirm()) onConfirmed() }, enabled = state.canConfirm)
                    }
                }
            }
        },
    ) { padding ->
        val order = state.order
        when {
            state.isLoading -> LoadingIndicator("Loading your order…", Modifier.padding(padding))
            order == null -> Box(Modifier.fillMaxSize().padding(padding).padding(Dimens.ScreenPadding)) {
                Text(
                    "This order isn't available.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.ScreenPadding),
            ) {
                OrderNumberCard(state)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.storeLine, style = MaterialTheme.typography.titleMedium)
                    state.tableLabel?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                }
                Stepper(state.steps)
                StatusMessage(state)
                ItemsCard(order)
                if (state.isCompleted) Receipt(state, onRate = onOpenRating)
                if (isDebug && !state.isCompleted) {
                    DebugControls(onAdvance = viewModel::debugAdvance, onAutoComplete = viewModel::debugAutoComplete)
                }
            }
        }
    }
}

/** Asks once per ticket visit on Android 13+, where posting notifications needs the runtime permission. */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** High contrast at arm's length: the theme's order-number pair is at least 4.5:1 in light and dark. */
@Composable
private fun OrderNumberCard(state: OrderTicketUiState) {
    val colors = DekitaTheme.colors.orderNumber
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.container, MaterialTheme.shapes.extraLarge)
            .padding(vertical = 20.dp, horizontal = 16.dp)
            .clearAndSetSemantics { contentDescription = "Order number ${state.order?.number}" },
    ) {
        Text("Order number", style = MaterialTheme.typography.labelLarge, color = colors.content)
        Text(state.numberLabel, style = DekitaTheme.textStyles.orderNumber, color = colors.content)
    }
}

/** Received → Cooking → Ready / Served. Each step reads as one item, for example "Cooking, step 2 of 3, current". */
@Composable
private fun Stepper(steps: List<TicketStep>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = step.spokenDescription(index + 1, steps.size) },
            ) {
                StepDot(step.state, index + 1)
                Text(
                    step.label,
                    style = if (step.state == StepState.CURRENT) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                    color = if (step.state == StepState.TODO) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun StepDot(state: StepState, number: Int) {
    val primary = MaterialTheme.colorScheme.primary
    val modifier = Modifier.size(32.dp)
    when (state) {
        StepState.DONE -> Box(contentAlignment = Alignment.Center, modifier = modifier.background(primary, CircleShape)) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
        }
        StepState.CURRENT -> Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.border(BorderStroke(3.dp, primary), CircleShape),
        ) {
            Text("$number", style = MaterialTheme.typography.labelLarge, color = primary)
        }
        StepState.TODO -> Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape),
        ) {
            Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The in-app copy of the notification. Green once the order is ready or served. Read out when it changes. */
@Composable
private fun StatusMessage(state: OrderTicketUiState) {
    val green = state.canConfirm
    val colors = DekitaTheme.colors.open
    Surface(
        color = if (green) colors.container else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (green) colors.content else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                painterResource(if (green || state.isCompleted) R.drawable.ic_check_circle else R.drawable.ic_info),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(state.message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ItemsCard(order: Order) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Items")
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                order.lines.forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("${line.quantity} × ${line.item.name}", style = MaterialTheme.typography.bodyLarge)
                            if (line.optionsLabel.isNotEmpty()) {
                                Text(
                                    line.optionsLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(line.lineTotal.formatted(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                HorizontalDivider()
                Row(Modifier.semantics(mergeDescendants = true) {}) {
                    Text("Total (tax incl.)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(order.total.formatted(), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** RCPT-01: how the order was paid, when, and the rating, or a way to rate it. */
@Composable
private fun Receipt(state: OrderTicketUiState, onRate: () -> Unit) {
    // The device's own time zone and locale format. DateFormat works on every supported API level.
    val paidAt = state.paidAtEpochSeconds?.let {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it * 1000))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Receipt")
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                ReceiptRow("Paid with", state.paymentLabel ?: "—")
                ReceiptRow("Paid on", paidAt ?: "—")
                state.ratingLabel?.let { ReceiptRow("Your rating", it) }
            }
        }
        if (state.canRate) {
            OutlinedButton(onClick = onRate, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Rate this order") }
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}

/** Debug builds only: stand-ins for the store's own app. */
@Composable
private fun DebugControls(onAdvance: () -> Unit, onAutoComplete: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Debug", style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
        OutlinedButton(onClick = onAdvance, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Advance status") }
        OutlinedButton(onClick = onAutoComplete, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Trigger auto-complete") }
    }
}
