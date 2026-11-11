package tech.vauldex.dekita.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.vauldex.dekita.R
import tech.vauldex.dekita.domain.AuthInput
import tech.vauldex.dekita.presentation.auth.AuthEvent
import tech.vauldex.dekita.presentation.auth.EmailAuthMode
import tech.vauldex.dekita.presentation.auth.EmailAuthViewModel
import tech.vauldex.dekita.ui.components.BottomActionBar
import tech.vauldex.dekita.ui.components.PrimaryButton

/** Email sign-up and log-in on one screen; a link switches between them. */
@Composable
fun EmailAuthScreen(
    viewModel: EmailAuthViewModel,
    onBack: () -> Unit,
    onForgotPassword: () -> Unit,
    onSignedIn: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signUp = state.mode == EmailAuthMode.SIGN_UP
    // Plain remember, not rememberSaveable, so neither value lands in saved state. Keyed on the mode
    // because switching mode clears the password in the ViewModel.
    var emailField by remember { mutableStateOf(TextFieldValue(state.email)) }
    var passwordField by remember(state.mode) { mutableStateOf(TextFieldValue(state.password)) }
    var showPassword by remember { mutableStateOf(false) }
    CollectEvents(viewModel.events) { if (it == AuthEvent.SignedIn) onSignedIn() }

    val action = if (signUp) "Create account" else "Log in"
    Scaffold(
        topBar = { AuthTopBar(action, onBack) },
        bottomBar = {
            BottomActionBar { PrimaryButton(action, onClick = viewModel::submit, loading = state.isSubmitting) }
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            state.formError?.let { ErrorBanner(it, Modifier.padding(bottom = 8.dp)) }

            OutlinedTextField(
                value = emailField,
                onValueChange = { typed ->
                    emailField = typed.normalizedWhenCommitted(AuthInput::toHalfWidth)
                    viewModel.onEmailChange(emailField.text)
                },
                label = { Text("Email") },
                isError = state.emailError != null,
                supportingText = state.emailError?.let { error -> { Text(error) } },
                singleLine = true,
                enabled = !state.isSubmitting,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    autoCorrectEnabled = false,
                ),
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
            )
            OutlinedTextField(
                value = passwordField,
                onValueChange = { typed ->
                    // Never rewritten: the password is checked exactly as typed.
                    passwordField = typed
                    viewModel.onPasswordChange(typed.text)
                },
                label = { Text("Password") },
                isError = state.passwordError != null,
                supportingText = {
                    val hint = if (signUp) "At least 8 characters, with a number and a letter." else null
                    (state.passwordError ?: hint)?.let { Text(it) }
                },
                singleLine = true,
                enabled = !state.isSubmitting,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            painterResource(if (showPassword) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = if (signUp) ContentType.NewPassword else ContentType.Password },
            )

            if (!signUp) {
                TextButton(onClick = onForgotPassword) { Text("Forgot password?") }
            }
            TextButton(onClick = viewModel::switchMode) {
                Text(if (signUp) "Already have an account? Log in" else "New here? Create an account")
            }
            if (signUp) {
                Text(
                    "Mockup: the account lives in memory only and is gone when the app closes.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
