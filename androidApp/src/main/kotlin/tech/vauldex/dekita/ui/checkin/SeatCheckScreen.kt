package tech.vauldex.dekita.ui.checkin

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.vauldex.dekita.R
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.presentation.CANCEL_EXIT
import tech.vauldex.dekita.presentation.CANCEL_EXIT_DETAIL
import tech.vauldex.dekita.presentation.NO_SEAT_DETAIL
import tech.vauldex.dekita.presentation.SEAT_QUESTION
import tech.vauldex.dekita.presentation.SEAT_QUESTION_DETAIL
import tech.vauldex.dekita.presentation.SWITCH_TO_TAKEOUT
import tech.vauldex.dekita.presentation.SWITCH_TO_TAKEOUT_DETAIL
import tech.vauldex.dekita.presentation.WAIT_IN_LINE
import tech.vauldex.dekita.presentation.WAIT_IN_LINE_DETAIL
import tech.vauldex.dekita.presentation.checkin.SeatCheckEvent
import tech.vauldex.dekita.presentation.payment.PaymentViewModel
import tech.vauldex.dekita.presentation.checkin.SeatCheckStep
import tech.vauldex.dekita.presentation.checkin.SeatCheckUiState
import tech.vauldex.dekita.presentation.checkin.SeatCheckViewModel
import tech.vauldex.dekita.presentation.choiceLabel
import tech.vauldex.dekita.presentation.itemsLabel
import tech.vauldex.dekita.presentation.label
import tech.vauldex.dekita.presentation.noSeatTitle
import tech.vauldex.dekita.ui.components.LoadingIndicator
import tech.vauldex.dekita.ui.components.PrimaryButton
import tech.vauldex.dekita.ui.components.groupedShape
import tech.vauldex.dekita.ui.theme.DekitaTheme
import tech.vauldex.dekita.ui.payment.CollectPaymentEvents
import tech.vauldex.dekita.ui.payment.PaymentSheet
import tech.vauldex.dekita.ui.theme.Dimens

/**
 * Seat verification (SEAT-01), opened by Start Cooking after check-in. "Have you found a seat?" comes before
 * the kitchen starts: Yes opens the payment sheet (PAY-01) for the draft, whose payment opens the order's ticket, and a
 * full store or "Not yet" offers Switch to Takeout, Wait in line or Cancel / Exit. None of them loses the order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeatCheckScreen(
    viewModel: SeatCheckViewModel,
    payment: PaymentViewModel,
    onBack: () -> Unit,
    onSwitchedToTakeout: (storeId: String) -> Unit,
    onWaitInLine: () -> Unit,
    onExit: () -> Unit,
    onPaid: (orderId: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSwitched by rememberUpdatedState(onSwitchedToTakeout)
    val currentOnWait by rememberUpdatedState(onWaitInLine)
    val currentOnExit by rememberUpdatedState(onExit)
    CollectPaymentEvents(payment) { orderId ->
        viewModel.markPaid(orderId)
        onPaid(orderId)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SeatCheckEvent.SwitchedToTakeout -> currentOnSwitched(event.storeId)
                SeatCheckEvent.WaitInLine -> currentOnWait()
                SeatCheckEvent.Exited -> currentOnExit()
            }
        }
    }

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
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.ScreenPadding),
        ) {
            when {
                state.isLoading -> LoadingIndicator("Loading your order…")
                // Paid: the ticket opens next.
                state.isPaid -> LoadingIndicator("Opening your order…")
                state.isUnavailable -> UnavailableContent(onDone = onBack)
                else -> {
                    OrderHeader(state)
                    if (state.step == SeatCheckStep.NO_SEAT) {
                        NoSeatContent(
                            state = state,
                            onSwitchToTakeout = viewModel::switchToTakeout,
                            onWait = viewModel::waitInLine,
                            onExit = viewModel::exit,
                        )
                    } else {
                        QuestionContent(onYes = { viewModel.answerSeat(true) }, onNotYet = { viewModel.answerSeat(false) })
                    }
                }
            }
        }
    }

    // Closing the sheet without paying goes back to the question; the check-in is kept.
    if (state.showsPayment) PaymentSheet(payment, onDismiss = viewModel::closePayment)

    state.pendingReplaceCartStoreName?.let { otherStore ->
        AlertDialog(
            onDismissRequest = viewModel::dismissReplaceCart,
            title = { Text("Replace your cart?") },
            text = { Text("Your cart has items from $otherStore. Switching to Takeout puts this order in the cart instead.") },
            confirmButton = { TextButton(onClick = viewModel::confirmReplaceCart) { Text("Replace cart") } },
            dismissButton = { TextButton(onClick = viewModel::dismissReplaceCart) { Text("Keep cart") } },
        )
    }
}

/** How the customer checked in, and the order. */
@Composable
private fun OrderHeader(state: SeatCheckUiState) {
    val order = state.order ?: return
    val colors = DekitaTheme.colors.open
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp).background(colors.container, CircleShape)) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.content, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(order.checkIn?.label.orEmpty(), style = MaterialTheme.typography.titleMedium)
            Text(
                "${state.store?.name ?: order.storeName} · ${FulfillmentMode.IN_STORE.choiceLabel} · ${order.itemsLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuestionContent(onYes: () -> Unit, onNotYet: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        ) {
            Icon(
                painterResource(R.drawable.ic_restaurant),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            SEAT_QUESTION,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            SEAT_QUESTION_DETAIL,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    PrimaryButton(text = "Yes, I have a seat", onClick = onYes, modifier = Modifier.padding(top = 16.dp))
    OutlinedButton(onClick = onNotYet, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text("Not yet", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun NoSeatContent(state: SeatCheckUiState, onSwitchToTakeout: () -> Unit, onWait: () -> Unit, onExit: () -> Unit) {
    val storeName = state.store?.name ?: state.order?.storeName.orEmpty()
    Text(
        noSeatTitle(storeName, state.isStoreFull),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
    Text(NO_SEAT_DETAIL, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FallbackOption(
            icon = R.drawable.ic_storefront,
            title = SWITCH_TO_TAKEOUT,
            detail = state.takeoutUnavailableReason ?: SWITCH_TO_TAKEOUT_DETAIL,
            enabled = state.canSwitchToTakeout,
            shape = groupedShape(0, 3),
            onClick = onSwitchToTakeout,
        )
        FallbackOption(
            icon = R.drawable.ic_history,
            title = WAIT_IN_LINE,
            detail = WAIT_IN_LINE_DETAIL,
            shape = groupedShape(1, 3),
            onClick = onWait,
        )
        FallbackOption(
            icon = R.drawable.ic_close,
            title = CANCEL_EXIT,
            detail = CANCEL_EXIT_DETAIL,
            shape = groupedShape(2, 3),
            onClick = onExit,
        )
    }
}

@Composable
private fun FallbackOption(
    @DrawableRes icon: Int,
    title: String,
    detail: String,
    shape: Shape,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val alpha = if (enabled) 1f else 0.5f
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun UnavailableContent(onDone: () -> Unit) {
    Text(
        "Nothing to seat-check",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 24.dp).semantics { heading() },
    )
    Text(
        "This order isn't a checked-in draft any more. It may already be in the kitchen or back in your cart.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FilledTonalButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back") }
}
