package tech.vauldex.dekita.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.vauldex.dekita.R
import tech.vauldex.dekita.domain.AuthInput
import tech.vauldex.dekita.domain.SsoProvider
import tech.vauldex.dekita.presentation.auth.AuthEvent
import tech.vauldex.dekita.presentation.auth.SignInViewModel
import tech.vauldex.dekita.ui.components.PrimaryButton

/**
 * The Sign-in / Create-account screen: one field for a phone number or an email, then "Send code", plus Google and
 * Apple. Apple is offered on both platforms, which also meets the App Store rule for apps with third-party sign-in.
 * As the root screen after sign-out it offers "Browse as guest" instead of a close button. [onCodeSent] gets
 * [tech.vauldex.dekita.domain.AuthContact.key] of where the code went, for the code screen.
 */
@Composable
fun SignInScreen(
    viewModel: SignInViewModel,
    isRoot: Boolean,
    onClose: () -> Unit,
    onCodeSent: (contactKey: String) -> Unit,
    onSignedIn: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val inputFocus = remember { FocusRequester() }
    // Plain remember, not rememberSaveable: the number or address stays out of saved state. The ViewModel keeps it across rotation.
    var field by remember { mutableStateOf(TextFieldValue(state.input)) }
    CollectEvents(viewModel.events) {
        when (it) {
            AuthEvent.SignedIn -> onSignedIn()
            is AuthEvent.CodeSent -> onCodeSent(it.contact.key)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    if (!isRoot) {
                        IconButton(onClick = onClose) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = "Close")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text("Sign in or create an account", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Save favorites, keep your order history and pay faster.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))

            state.ssoError?.let { provider ->
                ErrorBanner(
                    message = "Couldn't sign in with ${provider.label}. Try again, or sign in another way.",
                    actionLabel = "Try another way",
                    onAction = {
                        viewModel.dismissError()
                        inputFocus.requestFocus()
                    },
                )
            }

            OutlinedTextField(
                value = field,
                onValueChange = { typed ->
                    field = typed.normalizedWhenCommitted(AuthInput::toHalfWidth)
                    viewModel.onInputChange(field.text)
                },
                label = { Text("Phone number or email") },
                placeholder = { Text("Email or Phone number") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_account), contentDescription = null) },
                // The hint is the helper line: what will happen for this input, or in the error colour what is wrong with it.
                supportingText = { Text(state.hint) },
                isError = state.hasInputError,
                singleLine = true,
                enabled = !state.isSending,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { if (state.canSend) viewModel.sendCode() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(inputFocus)
                    .semantics { contentType = ContentType.Username },
            )
            PrimaryButton(
                text = "Send code",
                onClick = viewModel::sendCode,
                // Stays enabled while sending, so the spinner shows on a live button; taps are ignored then.
                enabled = state.canSend || state.isSending,
                loading = state.isSending,
                icon = R.drawable.ic_send,
            )

            OrDivider()

            SsoProvider.entries.forEach { provider ->
                SsoButton(
                    provider = provider,
                    onClick = { viewModel.continueWith(provider) },
                    enabled = !state.isBusy || state.ssoInProgress == provider,
                    loading = state.ssoInProgress == provider,
                )
            }

            if (isRoot) {
                TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Browse as guest")
                }
            }
            Text(
                "Mockup: no real account is created and nothing leaves this device.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}
