package tech.vauldex.dekita.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.vauldex.dekita.R
import tech.vauldex.dekita.domain.SsoProvider
import tech.vauldex.dekita.presentation.auth.AuthEvent
import tech.vauldex.dekita.presentation.auth.SignInViewModel
import tech.vauldex.dekita.ui.components.ButtonContent
import tech.vauldex.dekita.ui.components.PrimaryButton

/**
 * The Sign-Up / Login screen: phone, email, Google and Apple. Apple is offered on both platforms, which also
 * meets the App Store rule for apps with third-party sign-in. As the root screen after sign-out it offers
 * "Browse as guest" instead of a close button.
 */
@Composable
fun SignInScreen(
    viewModel: SignInViewModel,
    isRoot: Boolean,
    onClose: () -> Unit,
    onPhone: () -> Unit,
    onEmail: () -> Unit,
    onSignedIn: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val phoneFocus = remember { FocusRequester() }
    CollectEvents(viewModel.events) { if (it == AuthEvent.SignedIn) onSignedIn() }

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
                        phoneFocus.requestFocus()
                    },
                )
            }

            PrimaryButton(
                text = "Sign in with Phone",
                onClick = onPhone,
                enabled = !state.isBusy,
                icon = R.drawable.ic_phone,
                modifier = Modifier.focusRequester(phoneFocus),
            )
            FilledTonalButton(
                onClick = onEmail,
                enabled = !state.isBusy,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                ButtonContent("Sign in with Email", loading = false) {
                    Icon(painterResource(R.drawable.ic_mail), contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }

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
