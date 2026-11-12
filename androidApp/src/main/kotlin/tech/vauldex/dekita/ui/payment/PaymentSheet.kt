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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
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
 * The caller collects [PaymentEvent.Paid] with [CollectPaymentEvents], so it can navigate even with the sheet closed.
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
                )
            }
        }
    }

    PaymentApprovalHost(state, onApprove = viewModel::approve, onCancel = viewModel::cancelApproval)
    if (showAddCard) {
        AddTestCardDialog(onSave = viewModel::addTestCard, onDismiss = { showAddCard = false })
    }
}

/** Runs [onPaid] once the charge goes through, with the paid order's id. */
@Composable
internal fun CollectPaymentEvents(viewModel: PaymentViewModel, onPaid: (orderId: String) -> Unit) {
    CollectEvents(viewModel.events) { event ->
        when (event) {
            is PaymentEvent.Paid -> onPaid(event.orderId)
        }
    }
}

@Composable
private fun ColumnScope.PaymentSheetContent(
    state: PaymentUiState,
    onSelect: (String) -> Unit,
    onAddCard: () -> Unit,
    onPay: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(state.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            state.total?.formatted().orEmpty(),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 44.sp),
            fontWeight = FontWeight.Bold,
        )
        Text("Tax included", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    state.errorMessage?.let { ErrorBanner(it) }
    PrimaryButton(
        text = state.payButtonLabel,
        onClick = onPay,
        enabled = state.canPay,
        loading = state.isProcessing,
    )
    Text(
        "Mockup: no real payment is made.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
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
