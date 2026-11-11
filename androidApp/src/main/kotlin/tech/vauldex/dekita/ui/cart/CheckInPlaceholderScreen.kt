package tech.vauldex.dekita.ui.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import tech.vauldex.dekita.R
import tech.vauldex.dekita.ui.components.PrimaryButton
import tech.vauldex.dekita.ui.theme.Dimens

/** Stand-in for check-in (CHECKIN-01), shown after Eat-In saves a DRAFT order. */
@Composable
fun CheckInPlaceholderScreen(orderNumber: Int, storeName: String, onBack: () -> Unit, onDone: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Check in") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(Dimens.ScreenPadding),
        ) {
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
            PrimaryButton("Done", onClick = onDone)
        }
    }
}
