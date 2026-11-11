package tech.vauldex.dekita.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dekita.sharedui.generated.resources.Res
import dekita.sharedui.generated.resources.ic_add
import dekita.sharedui.generated.resources.ic_delete
import dekita.sharedui.generated.resources.ic_remove
import org.jetbrains.compose.resources.painterResource

/**
 * An outlined minus / count / plus pill. With [removesAtOne], the minus becomes a bin at a quantity
 * of one, so the same button removes the line.
 */
@Composable
fun QuantityStepper(
    quantity: Int,
    itemName: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    canIncrease: Boolean = true,
    canDecrease: Boolean = true,
    removesAtOne: Boolean = false,
) {
    val removes = removesAtOne && quantity <= 1
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(height)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
    ) {
        IconButton(onClick = onDecrease, enabled = removes || canDecrease) {
            Icon(
                painterResource(if (removes) Res.drawable.ic_delete else Res.drawable.ic_remove),
                contentDescription = if (removes) "Remove $itemName" else "Decrease $itemName quantity",
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            "$quantity",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = 16.dp)
                .semantics { contentDescription = "Quantity $quantity" },
        )
        IconButton(onClick = onIncrease, enabled = canIncrease) {
            Icon(
                painterResource(Res.drawable.ic_add),
                contentDescription = "Increase $itemName quantity",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
