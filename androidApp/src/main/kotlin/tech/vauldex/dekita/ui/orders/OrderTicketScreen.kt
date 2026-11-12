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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import tech.vauldex.dekita.R
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStep
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
                title = { Text("Order") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Close")
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
                        PrimaryButton("Confirm received", onClick = { if (viewModel.confirm()) onConfirmed() }, enabled = state.canConfirm)
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
                StatusBanner(state)
                OrderNumberCard(state)
                StatusCard(state)
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

/** The in-app copy of the notification, first on the page. Read out when it changes. */
@Composable
private fun StatusBanner(state: OrderTicketUiState) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            ) {
                Icon(
                    painterResource(R.drawable.ic_notifications),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(state.bannerTitle, style = MaterialTheme.typography.titleMedium)
                Text(state.bannerDetail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** High contrast at arm's length: the theme's order-number pair is at least 4.5:1 in light and dark. */
@Composable
private fun OrderNumberCard(state: OrderTicketUiState) {
    val colors = DekitaTheme.colors.orderNumber
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.container, MaterialTheme.shapes.extraLarge)
            .padding(24.dp)
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(
                    "Order number ${state.order?.number}", state.storeLine, state.tableLabel, state.paidLine,
                ).joinToString(". ")
            },
    ) {
        Text("Order number", style = MaterialTheme.typography.labelLarge, color = colors.content)
        Text(state.numberLabel, style = DekitaTheme.textStyles.orderNumber, color = colors.content)
        Text(state.storeLine, style = MaterialTheme.typography.titleMedium, color = colors.content, textAlign = TextAlign.Center)
        state.tableLabel?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = colors.content) }
        Text(state.paidLine, style = MaterialTheme.typography.bodyMedium, color = colors.content, textAlign = TextAlign.Center)
        Text(
            "Show this number to staff if asked.",
            style = MaterialTheme.typography.labelMedium,
            color = colors.content,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * The status card: the current step as a headline, a wavy progress bar, then Received / Cooking / Ready or
 * Served as a three-column grid with the time each was reached.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusCard(state: OrderTicketUiState) {
    val steps = state.steps
    val current = steps.firstOrNull { it.state == StepState.CURRENT } ?: steps.lastOrNull()
    val currentIndex = steps.indexOf(current).coerceAtLeast(0)
    val progress = if (steps.isEmpty()) 0f else if (state.isCompleted) 1f else (currentIndex + 1f) / steps.size
    val note = state.cookingNote ?: if (current?.step == OrderStep.SERVED) "Enjoy your meal" else null
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    current?.label.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            // The grid below says the same in words, so the bar is hidden from screen readers.
            LinearWavyProgressIndicator(
                progress = { progress },
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                steps.forEachIndexed { index, step ->
                    StepColumn(
                        step,
                        position = index + 1,
                        count = steps.size,
                        align = when (index) {
                            0 -> Alignment.Start
                            steps.lastIndex -> Alignment.End
                            else -> Alignment.CenterHorizontally
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** One grid cell, read as one item, for example "Cooking, step 2 of 3, current, 12:06". */
@Composable
private fun StepColumn(step: TicketStep, position: Int, count: Int, align: Alignment.Horizontal, modifier: Modifier) {
    // The device's own time zone, like the receipt's paid-on time.
    val time = step.atEpochSeconds?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it * 1000)) }
        ?: if (step.state == StepState.TODO) "Soon" else if (step.state == StepState.CURRENT) "Now" else null
    Column(
        horizontalAlignment = align,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.clearAndSetSemantics {
            contentDescription = listOfNotNull(step.spokenDescription(position, count), time).joinToString(", ")
        },
    ) {
        StepMarker(step.state)
        Text(
            step.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (step.state == StepState.CURRENT) FontWeight.Bold else FontWeight.Medium,
            color = if (step.state == StepState.CURRENT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        time?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun StepMarker(state: StepState) {
    val primary = MaterialTheme.colorScheme.primary
    val modifier = Modifier.size(24.dp)
    when (state) {
        StepState.DONE -> Box(contentAlignment = Alignment.Center, modifier = modifier.background(DekitaTheme.colors.liveDot, CircleShape)) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        StepState.CURRENT -> Box(contentAlignment = Alignment.Center, modifier = modifier.border(BorderStroke(3.dp, primary), CircleShape)) {
            Box(Modifier.size(8.dp).background(primary, CircleShape))
        }
        StepState.TODO -> Box(modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), CircleShape))
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
