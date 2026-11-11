package tech.vauldex.dekita.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import tech.vauldex.dekita.R
import tech.vauldex.dekita.domain.AuthInput
import tech.vauldex.dekita.domain.SsoProvider
import tech.vauldex.dekita.ui.components.ButtonContent

@Composable
internal fun AuthTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
            }
        },
    )
}

/** Runs [onEvent] for each one-off ViewModel event while the screen is shown. */
@Composable
internal fun <T> CollectEvents(events: Flow<T>, onEvent: (T) -> Unit) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(events) { events.collect { currentOnEvent(it) } }
}

/**
 * Applies [normalize] once the IME has committed the text. Rewriting while a Japanese IME is still composing
 * breaks the input, so composing text passes through untouched.
 */
internal fun TextFieldValue.normalizedWhenCommitted(normalize: (String) -> String): TextFieldValue {
    if (composition != null) return this
    val normalized = normalize(text)
    if (normalized == text) return this
    return copy(text = normalized, selection = if (normalized.length == text.length) selection else TextRange(normalized.length))
}

/** "Continue with Google" or "Continue with Apple", styled as each provider's guidelines ask. Simulated: no SDK. */
@Composable
internal fun SsoButton(
    provider: SsoProvider,
    onClick: () -> Unit,
    enabled: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val text = "Continue with ${provider.label}"
    val onTap = { if (!loading) onClick() }
    val buttonModifier = modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .semantics { if (loading) stateDescription = "Loading" }
    when (provider) {
        // Google's branding: white fill, grey outline, near-black text; the dark variant in dark mode.
        SsoProvider.GOOGLE -> OutlinedButton(
            onClick = onTap,
            enabled = enabled,
            shape = CircleShape,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (dark) Color(0xFF131314) else Color.White,
                contentColor = if (dark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F),
            ),
            border = BorderStroke(1.dp, if (dark) Color(0xFF8E918F) else Color(0xFF747775)),
            modifier = buttonModifier,
        ) {
            ButtonContent(text, loading) {
                Image(painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        // Apple's HIG: a black button on a light background, a white one on a dark background.
        SsoProvider.APPLE -> Button(
            onClick = onTap,
            enabled = enabled,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (dark) Color.White else Color.Black,
                contentColor = if (dark) Color.Black else Color.White,
            ),
            modifier = buttonModifier,
        ) {
            ButtonContent(text, loading) {
                Icon(painterResource(R.drawable.ic_apple), contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Six cells drawn over one invisible text field, so paste and SMS autofill fill every cell at once. */
@Composable
internal fun OtpField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    isError: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                contentType = ContentType.SmsOtpCode
                contentDescription = "6-digit code"
            },
        decorationBox = { innerTextField ->
            Box {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                ) {
                    repeat(AuthInput.OTP_LENGTH) { index ->
                        val active = focused && index == value.text.length.coerceAtMost(AuthInput.OTP_LENGTH - 1)
                        val borderColor = when {
                            isError -> colors.error
                            active -> colors.primary
                            else -> colors.outline
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp)
                                .border(if (active || isError) 2.dp else 1.dp, borderColor, MaterialTheme.shapes.medium),
                        ) {
                            Text(value.text.getOrNull(index)?.toString().orEmpty(), style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
                Box(Modifier.matchParentSize().alpha(0f)) { innerTextField() }
            }
        },
    )
}

/** Error card that screen readers announce. [actionLabel] adds a button such as "Try another way". */
@Composable
internal fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = if (actionLabel == null) 12.dp else 4.dp)) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (actionLabel != null) {
                TextButton(
                    onClick = onAction,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
internal fun OrDivider() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text("or", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f))
    }
}
