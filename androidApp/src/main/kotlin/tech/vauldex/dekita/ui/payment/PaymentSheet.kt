package tech.vauldex.dekita.ui.payment

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.vauldex.dekita.R
import tech.vauldex.dekita.domain.PaymentMethod
import tech.vauldex.dekita.domain.PaymentType
import tech.vauldex.dekita.presentation.payment.PaymentEvent
import tech.vauldex.dekita.presentation.payment.PaymentUiState
import tech.vauldex.dekita.presentation.payment.PaymentViewModel
import tech.vauldex.dekita.ui.auth.CollectEvents
import tech.vauldex.dekita.ui.auth.ErrorBanner
import tech.vauldex.dekita.ui.components.LoadingIndicator
import tech.vauldex.dekita.ui.components.PrimaryButton
import tech.vauldex.dekita.ui.components.groupedShape
import tech.vauldex.dekita.ui.theme.Dimens

/**
 * The payment sheet (PAY-01), laid out as the Android Payment artboard: what is being paid for, the total, the
 * methods as a connected group, Add card, and Pay. Pay shows a spinner and turns off while charging, and the sheet
 * can't be closed then. PayPay, Rakuten Pay and Google Pay open their mock approval screen over it.
 * A decline replaces Pay with Retry, Use another method and Cancel (PAY-02). Back, swiping down and Close all dismiss
 * the sheet onto the screen behind it, Cart Review or the seat check, with nothing changed.
 * The caller collects [PaymentEvent.Paid] and [PaymentEvent.Closed] with [CollectPaymentEvents]: it navigates on Paid,
 * and closes the sheet on Closed (Cancel after a decline, or Cancel on the PayPay or Rakuten Pay page).
 */
@Composable
internal fun PaymentSheet(viewModel: PaymentViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val latest by rememberUpdatedState(state)
    var showAddCard by remember { mutableStateOf(false) }
    val close = {
        if (latest.canDismiss) {
            viewModel.reset()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            // Swiping down is refused while charging, so the result is never missed.
            confirmValueChange = { it != SheetValue.Hidden || latest.canDismiss },
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Payment",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = close, enabled = state.canDismiss) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Close")
                }
            }
            when {
                state.isLoading -> LoadingIndicator("Loading payment methods…")
                !state.hasSomethingToPay -> Text(
                    "There's nothing to pay. Your cart may have been emptied.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                else -> PaymentSheetContent(
                    state = state,
                    onSelect = viewModel::selectMethod,
                    onAddCard = { showAddCard = true },
                    onPay = viewModel::pay,
                    onUseAnotherMethod = viewModel::useAnotherMethod,
                    onCancel = viewModel::cancel,
                )
            }
        }
    }

    PaymentApprovalHost(state, onApprove = viewModel::approve, onCancel = viewModel::cancelApproval)
    if (showAddCard) {
        AddTestCardDialog(onSave = viewModel::addTestCard, onDismiss = { showAddCard = false })
    }
}

/**
 * Runs [onPaid] once the charge goes through, with the paid order's id, and [onClosed] when the customer leaves
 * without paying: close the sheet then, so they are back on Cart Review or the seat check with nothing changed.
 */
@Composable
internal fun CollectPaymentEvents(
    viewModel: PaymentViewModel,
    onPaid: (orderId: String) -> Unit,
    onClosed: () -> Unit,
) {
    CollectEvents(viewModel.events) { event ->
        when (event) {
            is PaymentEvent.Paid -> onPaid(event.orderId)
            PaymentEvent.Closed -> onClosed()
        }
    }
}

@Composable
private fun ColumnScope.PaymentSheetContent(
    state: PaymentUiState,
    onSelect: (String) -> Unit,
    onAddCard: () -> Unit,
    onPay: () -> Unit,
    onUseAnotherMethod: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(state.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            state.total?.formatted().orEmpty(),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 44.sp),
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Tax included",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.selectableGroup()) {
        state.methods.forEachIndexed { index, method ->
            MethodRow(
                method = method,
                isSelected = method.id == state.selectedMethod?.id,
                enabled = state.canChangeMethod,
                index = index,
                count = state.methods.size,
                onSelect = { onSelect(method.id) },
            )
        }
    }
    TextButton(onClick = onAddCard, enabled = state.canChangeMethod) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
        Text("Add card", modifier = Modifier.padding(start = 8.dp))
    }

    val error = state.errorMessage
    if (state.isDeclined && error != null) {
        DeclinedCard(error, onRetry = onPay, onUseAnotherMethod = onUseAnotherMethod, onCancel = onCancel)
    } else {
        error?.let { ErrorBanner(it) }
        PrimaryButton(
            text = state.payButtonLabel,
            onClick = onPay,
            enabled = state.canPay,
            loading = state.isProcessing,
        )
    }
    Text(
        "Mockup: no real payment is made.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}

/**
 * A decline, inline on the sheet (PAY-02): what happened in plain words, then Retry (the selected method, which can be
 * changed above first), Use another method, and Cancel back to Cart Review or the seat check. Screen readers announce
 * it.
 */
@Composable
private fun DeclinedCard(message: String, onRetry: () -> Unit, onUseAnotherMethod: () -> Unit, onCancel: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_error), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            PrimaryButton(text = "Retry", onClick = onRetry)
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onUseAnotherMethod,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                ) { Text("Use another method") }
                TextButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                ) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun MethodRow(
    method: PaymentMethod,
    isSelected: Boolean,
    enabled: Boolean,
    index: Int,
    count: Int,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(groupedShape(index, count))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = Dimens.ScreenPadding),
    ) {
        Icon(
            painterResource(method.type.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            method.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        RadioButton(selected = isSelected, onClick = null, enabled = enabled)
    }
}

@get:DrawableRes
internal val PaymentType.icon: Int
    get() = if (this == PaymentType.CARD) R.drawable.ic_credit_card else R.drawable.ic_wallet
