package tech.vauldex.dekita.ui.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tech.vauldex.dekita.R
import tech.vauldex.dekita.domain.CardFormError
import tech.vauldex.dekita.domain.PaymentApproval
import tech.vauldex.dekita.domain.PaymentMethod
import tech.vauldex.dekita.domain.approval
import tech.vauldex.dekita.presentation.message
import tech.vauldex.dekita.presentation.payment.PaymentUiState
import tech.vauldex.dekita.presentation.providerName
import tech.vauldex.dekita.ui.components.PrimaryButton
import tech.vauldex.dekita.ui.theme.Dimens

// Stand-ins for the providers' own screens (PAY-01). No SDK, app switch or network: each only approves or cancels.
// They are labelled as mocks and use no provider logos, so nobody mistakes them for the real thing.

/** Shows the open method's mock approval screen, if any: the PayPay or Rakuten Pay page, or the Google Pay sheet. */
@Composable
internal fun PaymentApprovalHost(state: PaymentUiState, onApprove: () -> Unit, onCancel: () -> Unit) {
    val method = state.approvingMethod ?: return
    val amount = state.total?.formatted().orEmpty()
    when (method.type.approval) {
        PaymentApproval.REDIRECT -> MockRedirectPage(method, state.storeName, amount, onApprove, onCancel)
        PaymentApproval.WALLET_SHEET -> MockWalletSheet(method, state.storeName, amount, onApprove, onCancel)
        PaymentApproval.NONE -> Unit
    }
}

/** The PayPay or Rakuten Pay page the customer is sent to, full screen. Back cancels. */
@Composable
private fun MockRedirectPage(
    method: PaymentMethod,
    storeName: String,
    amount: String,
    onApprove: () -> Unit,
    onCancel: () -> Unit,
) {
    val provider = method.type.providerName
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(Dimens.ScreenPadding),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onCancel) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Cancel and go back")
                    }
                    Text("$provider (mock)", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                }
                Spacer(Modifier.heightIn(min = 24.dp))
                ProviderBadge(provider)
                Text("$storeName is asking for a payment", style = MaterialTheme.typography.bodyLarge)
                Text(amount, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(
                    "From ${method.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "This stands in for the $provider app. No money moves.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrimaryButton(text = "Approve", onClick = onApprove)
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("Cancel", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** The Google Pay sheet, as a bottom sheet over the payment sheet. Dismissing it cancels. */
@Composable
private fun MockWalletSheet(
    method: PaymentMethod,
    storeName: String,
    amount: String,
    onApprove: () -> Unit,
    onCancel: () -> Unit,
) {
    val provider = method.type.providerName
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text("$provider (mock)", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text("Pay $storeName", style = MaterialTheme.typography.bodyLarge)
            Text(amount, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Test account · This stands in for $provider. No money moves.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(text = "Pay $amount", onClick = onApprove)
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
        }
    }
}

/** The provider's initial in a circle, in place of a logo. */
@Composable
private fun ProviderBadge(provider: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
    ) {
        Text(
            provider.take(1),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * The demo add-card form (PAY-01). Demo only, and must not become the production card form: a real build uses the
 * payment provider's SDK or hosted fields. What is typed lives in this dialog's memory only. It uses `remember`, not
 * `rememberSaveable`, so it never reaches saved instance state, and it is never logged. [onSave] returns the problem,
 * or null once the card is added.
 */
@Composable
internal fun AddTestCardDialog(onSave: (number: String, expiry: String, cvc: String) -> CardFormError?, onDismiss: () -> Unit) {
    var number by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var cvc by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<CardFormError?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a test card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Demo only. Use a published test number such as 4242 4242 4242 4242. Nothing you type is saved or sent.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = number,
                    onValueChange = { value -> number = value.filter { it.isDigit() || it == ' ' }.take(MAX_NUMBER_INPUT) },
                    label = { Text("Card number") },
                    isError = error == CardFormError.NUMBER || error == CardFormError.NOT_A_TEST_CARD,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = expiry,
                        onValueChange = { value -> expiry = value.filter { it.isDigit() || it == '/' }.take(5) },
                        label = { Text("MM/YY") },
                        isError = error == CardFormError.EXPIRY,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = cvc,
                        onValueChange = { value -> cvc = value.filter { it.isDigit() }.take(4) },
                        label = { Text("CVC") },
                        isError = error == CardFormError.CVC,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrectEnabled = false),
                        modifier = Modifier.weight(1f),
                    )
                }
                error?.let { Text(it.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = onSave(number, expiry, cvc)
                if (error == null) onDismiss()
            }) { Text("Add card") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** 19 digits plus spaces between groups of four. */
private const val MAX_NUMBER_INPUT = 23
