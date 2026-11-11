package tech.vauldex.dekita.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.vauldex.dekita.R
import tech.vauldex.dekita.domain.AuthInput
import tech.vauldex.dekita.presentation.auth.ForgotPasswordViewModel
import tech.vauldex.dekita.ui.components.BottomActionBar
import tech.vauldex.dekita.ui.components.PrimaryButton

/** Password recovery. The confirmation is the same whether or not the email has an account. */
@Composable
fun ForgotPasswordScreen(viewModel: ForgotPasswordViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var field by remember { mutableStateOf(TextFieldValue(state.email)) }

    Scaffold(
        topBar = { AuthTopBar("Reset password", onBack) },
        bottomBar = {
            BottomActionBar {
                if (state.isSent) {
                    PrimaryButton("Back to log in", onClick = onBack)
                } else {
                    PrimaryButton("Send reset link", onClick = viewModel::send, loading = state.isSending)
                }
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
            if (state.isSent) {
                Icon(
                    painterResource(R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Text("Check your email", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "If an account exists for that email, we've sent a link to reset your password.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(
                    "Mockup: no email is sent.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Enter the email you use for your account. We'll send a link to reset your password.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = field,
                    onValueChange = { typed ->
                        field = typed.normalizedWhenCommitted(AuthInput::toHalfWidth)
                        viewModel.onEmailChange(field.text)
                    },
                    label = { Text("Email") },
                    isError = state.emailError != null,
                    supportingText = state.emailError?.let { error -> { Text(error) } },
                    singleLine = true,
                    enabled = !state.isSending,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Send,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onSend = { viewModel.send() }),
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
                )
            }
        }
    }
}
