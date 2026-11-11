package tech.vauldex.dekita.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.vauldex.dekita.R
import tech.vauldex.dekita.domain.AuthInput
import tech.vauldex.dekita.domain.DialCode
import tech.vauldex.dekita.domain.PhoneNumber
import tech.vauldex.dekita.presentation.auth.AuthEvent
import tech.vauldex.dekita.presentation.auth.PhoneEntryViewModel
import tech.vauldex.dekita.ui.components.BottomActionBar
import tech.vauldex.dekita.ui.components.PrimaryButton

@Composable
fun PhoneEntryScreen(viewModel: PhoneEntryViewModel, onBack: () -> Unit, onCodeSent: (PhoneNumber) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Plain remember, not rememberSaveable: the number stays out of saved state. The ViewModel keeps it across rotation.
    var field by remember { mutableStateOf(TextFieldValue(state.number)) }
    CollectEvents(viewModel.events) { if (it is AuthEvent.CodeSent) onCodeSent(it.phone) }

    Scaffold(
        topBar = { AuthTopBar("Phone number", onBack) },
        bottomBar = {
            BottomActionBar {
                PrimaryButton("Send Verification Code", onClick = viewModel::sendCode, loading = state.isSending)
            }
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                "We'll text you a 6-digit code to sign in or create your account.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                DialCodePicker(state.dialCode, onSelect = viewModel::onDialCodeChange, enabled = !state.isSending)
                OutlinedTextField(
                    value = field,
                    onValueChange = { typed ->
                        field = typed.normalizedWhenCommitted(AuthInput::toHalfWidth)
                        viewModel.onNumberChange(field.text)
                    },
                    label = { Text("Mobile number") },
                    placeholder = { Text("90-1234-5678") },
                    isError = state.error != null,
                    supportingText = state.error?.let { error -> { Text(error) } },
                    singleLine = true,
                    enabled = !state.isSending,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { viewModel.sendCode() }),
                    modifier = Modifier.weight(1f).semantics { contentType = ContentType.PhoneNumberNational },
                )
            }
        }
    }
}

/** "+81" by default. Lined up with the text field's outline, below the space its floating label uses. */
@Composable
private fun DialCodePicker(selected: DialCode, onSelect: (DialCode) -> Unit, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(top = 8.dp)) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier
                .heightIn(min = 56.dp)
                .semantics { contentDescription = "Country code, ${selected.country} ${selected.prefix}" },
        ) {
            Text(selected.prefix, style = MaterialTheme.typography.bodyLarge)
            Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DialCode.entries.forEach { dialCode ->
                DropdownMenuItem(
                    text = { Text("${dialCode.country} ${dialCode.prefix}") },
                    onClick = {
                        onSelect(dialCode)
                        expanded = false
                    },
                )
            }
        }
    }
}
