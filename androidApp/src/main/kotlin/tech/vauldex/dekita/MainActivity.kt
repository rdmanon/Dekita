package tech.vauldex.dekita

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.flow.MutableStateFlow
import tech.vauldex.dekita.ui.checkin.LocalMapStyleUrl

class MainActivity : ComponentActivity() {

    /** The order a tapped notification asked to open. Untrusted: AppShell checks it before opening. */
    private val ticketRequest = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // After a configuration change the same intent comes back, and its ticket is already open.
        if (savedInstanceState == null) ticketRequest.value = intent.orderIdExtra()

        setContent {
            CompositionLocalProvider(LocalMapStyleUrl provides BuildConfig.MAP_STYLE_URL) {
                App(
                    isDebug = BuildConfig.DEBUG,
                    ticketRequest = ticketRequest,
                    onTicketRequestHandled = { ticketRequest.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.orderIdExtra()?.let { ticketRequest.value = it }
    }

    private fun Intent.orderIdExtra(): String? = getStringExtra(EXTRA_ORDER_ID)?.takeIf { it.isNotBlank() }

    companion object {
        /** Set by an order notification's tap. */
        const val EXTRA_ORDER_ID = "tech.vauldex.dekita.extra.ORDER_ID"
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(isDebug = true)
}
