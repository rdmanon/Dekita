package tech.vauldex.dekita.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.vauldex.dekita.data.mock.SeedAuth
import tech.vauldex.dekita.domain.AuthInput
import tech.vauldex.dekita.presentation.auth.AuthEvent
import tech.vauldex.dekita.presentation.auth.OtpViewModel
import tech.vauldex.dekita.ui.components.PrimaryButton
import tech.vauldex.dekita.ui.debug.LocalOpenDebugPanel

@Composable
fun OtpScreen(viewModel: OtpViewModel, onBack: () -> Unit, onSignedIn: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isDebug = LocalOpenDebugPanel.current != null
    val focus = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue(state.code, TextRange(state.code.length))) }
    CollectEvents(viewModel.events) { if (it == AuthEvent.SignedIn) onSignedIn() }

    // Follow the ViewModel when it clears the code after a wrong attempt, unless the IME is mid-word.
    LaunchedEffect(state.code) {
        if (field.composition == null && field.text != state.code) field = TextFieldValue(state.code, TextRange(state.code.length))
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(topBar = { AuthTopBar("Enter code", onBack) }) { padding ->
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
            Text("We sent a 6-digit code to ${state.phone}.", style = MaterialTheme.typography.bodyLarge)
            OtpField(
                value = field,
                onValueChange = { typed ->
                    field = typed.normalizedWhenCommitted(AuthInput::normalizeOtp)
                    // Full-width digits stay in the IME until committed; only committed text is checked.
                    if (field.composition == null) viewModel.onCodeChange(field.text)
                },
                isError = state.error != null,
                enabled = !state.isVerifying,
                modifier = Modifier.focusRequester(focus),
            )
            state.error?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            PrimaryButton("Verify", onClick = viewModel::verify, loading = state.isVerifying)
            TextButton(onClick = viewModel::resend, enabled = state.canResend) {
                Text(
                    if (state.resendSecondsLeft > 0) "Resend code in ${countdown(state.resendSecondsLeft)}" else "Resend code",
                )
            }
            TextButton(onClick = onBack) { Text("Change number") }
            if (isDebug) {
                Text(
                    "Debug build · test code: ${SeedAuth.TEST_OTP}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 42 becomes "0:42". */
private fun countdown(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
