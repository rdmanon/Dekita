package tech.vauldex.dekita.ui.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dekita.sharedui.generated.resources.Res
import dekita.sharedui.generated.resources.ic_arrow_back
import org.jetbrains.compose.resources.painterResource
import tech.vauldex.dekita.ui.theme.Dimens

/** Stand-in for payment (PAY-01). Back returns to Cart Review with the cart as it was. */
@Composable
fun PaymentPlaceholderScreen(onBack: () -> Unit) {
    PlaceholderScaffold(title = "Payment", onBack = onBack) {
        Text("Payment comes in a later ticket (PAY-01).", style = MaterialTheme.typography.titleMedium)
        Text(
            "Your cart is kept. A failed or cancelled payment returns to Cart Review.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onBack) { Text("Back to cart") }
    }
}

/** Stand-in for check-in (CHECKIN-01), shown after Eat-In saves a DRAFT order. */
@Composable
fun CheckInPlaceholderScreen(orderNumber: Int, storeName: String, onBack: () -> Unit, onDone: () -> Unit) {
    PlaceholderScaffold(title = "Check in", onBack = onBack) {
        Text("Draft order #$orderNumber saved", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Head to $storeName. You pay and cooking starts when you check in with Start Cooking.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Check-in comes in a later ticket (CHECKIN-01). The draft is in the Orders tab.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onDone) { Text("Done") }
    }
}

@Composable
private fun PlaceholderScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(Dimens.ScreenPadding),
        ) { content() }
    }
}
