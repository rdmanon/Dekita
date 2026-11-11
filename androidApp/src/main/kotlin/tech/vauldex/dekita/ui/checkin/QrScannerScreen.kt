package tech.vauldex.dekita.ui.checkin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.vauldex.dekita.R
import tech.vauldex.dekita.presentation.checkin.QrScannerUiState
import tech.vauldex.dekita.presentation.checkin.QrScannerViewModel
import tech.vauldex.dekita.ui.debug.LocalOpenDebugPanel
import tech.vauldex.dekita.ui.theme.DekitaTheme
import tech.vauldex.dekita.ui.theme.Dimens

/** The camera area stays dark in both themes, as a live camera preview would be. */
private val CameraBackground = Color(0xFF141414)
private val CameraContent = Color(0xFFF2F2F2)

/**
 * The QR fallback. The mockup has no camera: the viewfinder is a placeholder, and codes come from
 * the debug panel. A store or table QR checks in; another store's code or a non-Passnize code
 * shows an error with Scan again.
 */
@Composable
fun QrScannerScreen(viewModel: QrScannerViewModel, onClose: () -> Unit, onContinue: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(CameraBackground)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = "Close scanner", tint = CameraContent)
            }
            Text(
                "Check in",
                style = MaterialTheme.typography.titleLarge,
                color = CameraContent,
                modifier = Modifier.semantics { heading() },
            )
        }

        Viewfinder(state, Modifier.weight(1f).fillMaxWidth())

        ScanResultCard(
            state = state,
            onScanAgain = viewModel::scanAgain,
            onContinue = onContinue,
        )
    }
}

@Composable
private fun Viewfinder(state: QrScannerUiState, modifier: Modifier = Modifier) {
    val frameColor = when {
        state.isCheckedIn -> DekitaTheme.colors.liveDot
        state.isError -> MaterialTheme.colorScheme.error
        else -> CameraContent
    }
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(Modifier.size(240.dp)) {
            val corner = 40.dp.toPx()
            val stroke = 5.dp.toPx()
            val w = size.width
            val h = size.height
            listOf(
                Offset(0f, 0f) to (Offset(corner, 0f) to Offset(0f, corner)),
                Offset(w, 0f) to (Offset(w - corner, 0f) to Offset(w, corner)),
                Offset(0f, h) to (Offset(corner, h) to Offset(0f, h - corner)),
                Offset(w, h) to (Offset(w - corner, h) to Offset(w, h - corner)),
            ).forEach { (at, ends) ->
                drawLine(frameColor, at, ends.first, stroke, StrokeCap.Round)
                drawLine(frameColor, at, ends.second, stroke, StrokeCap.Round)
            }
        }
        Text(
            if (state.isScanning) "Camera preview\n(not available in the mockup)" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = CameraContent.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ScanResultCard(state: QrScannerUiState, onScanAgain: () -> Unit, onContinue: () -> Unit) {
    val openDebugPanel = LocalOpenDebugPanel.current
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(24.dp),
        ) {
            // Announce each new result, since it arrives without a tap on this screen.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            ) {
                ResultIcon(state)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(state.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                state.isCheckedIn -> Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Continue", style = MaterialTheme.typography.titleMedium)
                }
                state.isError -> Button(onClick = onScanAgain, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Scan again", style = MaterialTheme.typography.titleMedium)
                }
                openDebugPanel != null -> OutlinedButton(
                    onClick = openDebugPanel,
                    modifier = Modifier.fillMaxWidth().height(Dimens.MinTouchTarget),
                ) { Text("Simulate a scan (debug panel)") }
            }
        }
    }
}

@Composable
private fun ResultIcon(state: QrScannerUiState) {
    val (icon, container, content) = when {
        state.isCheckedIn -> Triple(R.drawable.ic_check, DekitaTheme.colors.open.container, DekitaTheme.colors.open.content)
        state.isError -> Triple(R.drawable.ic_error, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        else -> return
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp).background(container, CircleShape)) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
    }
}
