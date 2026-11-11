package tech.vauldex.dekita.ui.checkin

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tech.vauldex.dekita.R
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.PaymentMethod
import tech.vauldex.dekita.domain.PaymentType
import tech.vauldex.dekita.presentation.CANCEL_EXIT
import tech.vauldex.dekita.presentation.CANCEL_EXIT_DETAIL
import tech.vauldex.dekita.presentation.FOUND_SEAT_BUTTON
import tech.vauldex.dekita.presentation.NO_SEAT_DETAIL
import tech.vauldex.dekita.presentation.SEAT_QUESTION
import tech.vauldex.dekita.presentation.SEAT_QUESTION_DETAIL
import tech.vauldex.dekita.presentation.SENT_TO_KITCHEN_DETAIL
import tech.vauldex.dekita.presentation.SWITCH_TO_TAKEOUT
import tech.vauldex.dekita.presentation.SWITCH_TO_TAKEOUT_DETAIL
import tech.vauldex.dekita.presentation.WAITING_FOR_SEAT_MESSAGE
import tech.vauldex.dekita.presentation.WAIT_IN_LINE
import tech.vauldex.dekita.presentation.WAIT_IN_LINE_DETAIL
import tech.vauldex.dekita.presentation.checkInPausedMessage
import tech.vauldex.dekita.presentation.checkin.CheckInEvent
import tech.vauldex.dekita.presentation.checkin.CheckInPrompt
import tech.vauldex.dekita.presentation.checkin.CheckInUiState
import tech.vauldex.dekita.presentation.checkin.CheckInViewModel
import tech.vauldex.dekita.presentation.choiceLabel
import tech.vauldex.dekita.presentation.itemsLabel
import tech.vauldex.dekita.presentation.noSeatTitle
import tech.vauldex.dekita.presentation.payButtonLabel
import tech.vauldex.dekita.presentation.sentToKitchenTitle
import tech.vauldex.dekita.presentation.summary
import tech.vauldex.dekita.ui.components.LoadingIndicator
import tech.vauldex.dekita.ui.components.PrimaryButton
import tech.vauldex.dekita.ui.components.groupedShape
import tech.vauldex.dekita.ui.theme.DekitaTheme

private const val QR_NOT_BUILT = "QR check-in isn't built in this mockup yet."

/**
 * Check-in for an Eat-In draft, as the Android Check-in artboard: the store on a map and a sheet with the
 * order, the queue and Start cooking. SEAT-01 puts "Have you found a seat?" in front of cooking: Yes opens
 * 1-tap payment, and a full store or "Not yet" offers Switch to Takeout, Wait in line or Cancel / Exit.
 * Location is not checked in this mockup, so the customer is always shown as in range.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(
    viewModel: CheckInViewModel,
    onBack: () -> Unit,
    onSwitchedToTakeout: (storeId: String) -> Unit,
    onExit: () -> Unit,
    onViewOrders: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showQrNote: () -> Unit = { scope.launch { snackbarHostState.showSnackbar(QR_NOT_BUILT) } }
    val currentOnSwitched by rememberUpdatedState(onSwitchedToTakeout)
    val currentOnExit by rememberUpdatedState(onExit)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CheckInEvent.SwitchedToTakeout -> currentOnSwitched(event.storeId)
                CheckInEvent.Exited -> currentOnExit()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        CheckInMap(storeName = state.store?.name ?: state.order?.storeName.orEmpty(), modifier = Modifier.fillMaxSize())

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                }
            }
            if (!state.isSent) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
                    TextButton(onClick = showQrNote) {
                        Icon(painterResource(R.drawable.ic_qr_code), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Scan QR", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
            ) {
                when {
                    state.isLoading -> LoadingIndicator("Loading your order…")
                    state.isSent -> SentContent(state, onViewOrders = onViewOrders, onDone = onExit)
                    state.isUnavailable -> UnavailableContent(onDone = onExit)
                    else -> ArrivedContent(state, onStartCooking = viewModel::startCooking, onScanQr = showQrNote)
                }
            }
        }

        SnackbarHost(snackbarHostState, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp))
    }

    when (state.prompt) {
        CheckInPrompt.SEAT_QUESTION -> AlertDialog(
            onDismissRequest = viewModel::dismissPrompt,
            icon = { Icon(painterResource(R.drawable.ic_restaurant), contentDescription = null) },
            title = { Text(SEAT_QUESTION) },
            text = { Text(SEAT_QUESTION_DETAIL) },
            confirmButton = { TextButton(onClick = { viewModel.answerSeat(true) }) { Text("Yes") } },
            dismissButton = { TextButton(onClick = { viewModel.answerSeat(false) }) { Text("Not yet") } },
        )

        CheckInPrompt.NO_SEAT -> ModalBottomSheet(
            onDismissRequest = viewModel::dismissPrompt,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            NoSeatSheet(
                state = state,
                onSwitchToTakeout = viewModel::switchToTakeout,
                onWait = viewModel::waitInLine,
                onExit = viewModel::exit,
            )
        }

        CheckInPrompt.PAYMENT -> ModalBottomSheet(
            onDismissRequest = viewModel::dismissPrompt,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            PaymentSheet(
                state = state,
                onSelect = viewModel::selectPaymentMethod,
                onPay = viewModel::pay,
                onClose = viewModel::dismissPrompt,
            )
        }

        null -> Unit
    }

    state.pendingReplaceCartStoreName?.let { otherStore ->
        AlertDialog(
            onDismissRequest = viewModel::dismissReplaceCart,
            title = { Text("Replace your cart?") },
            text = { Text("Your cart has items from $otherStore. Switching to Takeout puts this order in the cart instead.") },
            confirmButton = { TextButton(onClick = viewModel::confirmReplaceCart) { Text("Replace cart") } },
            dismissButton = { TextButton(onClick = viewModel::dismissReplaceCart) { Text("Keep cart") } },
        )
    }
}

@Composable
private fun ArrivedContent(state: CheckInUiState, onStartCooking: () -> Unit, onScanQr: () -> Unit) {
    val order = state.order ?: return
    val storeName = state.store?.name ?: order.storeName
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "You've arrived",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            InRangePill()
        }
        Text(
            "$storeName · ${FulfillmentMode.IN_STORE.choiceLabel}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        InfoRow("Your order", order.itemsLabel, groupedShape(0, 2))
        val queue = when {
            state.isPaused -> "Paused"
            state.store?.seatsFull == true -> "Seats full"
            else -> state.store?.queue?.summary() ?: "—"
        }
        InfoRow("Queue", queue, groupedShape(1, 2))
    }

    val notice = when {
        state.isPaused -> checkInPausedMessage(storeName)
        state.isWaitingForSeat -> WAITING_FOR_SEAT_MESSAGE
        state.isStoreFull -> "${noSeatTitle(storeName, storeIsFull = true)}. Start cooking shows your options."
        else -> null
    }
    if (notice != null) {
        val colors = if (state.isPaused || state.isStoreFull) DekitaTheme.colors.full else DekitaTheme.colors.bothModes
        Surface(color = colors.container, contentColor = colors.content, shape = MaterialTheme.shapes.large) {
            Text(notice, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(16.dp))
        }
    }

    PrimaryButton(
        text = if (state.isWaitingForSeat) FOUND_SEAT_BUTTON else "Start cooking",
        onClick = onStartCooking,
        enabled = state.canStartCooking,
    )
    Text(
        "You pay when you tap Start cooking.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(onClick = onScanQr, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(painterResource(R.drawable.ic_qr_code), contentDescription = null, modifier = Modifier.size(18.dp))
        Text("Can't check in? Scan the store QR", modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun InRangePill() {
    val colors = DekitaTheme.colors.open
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(colors.container, MaterialTheme.shapes.small)
            .heightIn(min = 32.dp)
            .padding(start = 8.dp, end = 12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.content, modifier = Modifier.size(18.dp))
        Text("In range", style = MaterialTheme.typography.labelLarge, color = colors.content)
    }
}

@Composable
private fun InfoRow(label: String, value: String, shape: Shape) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = shape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun NoSeatSheet(state: CheckInUiState, onSwitchToTakeout: () -> Unit, onWait: () -> Unit, onExit: () -> Unit) {
    val storeName = state.store?.name ?: state.order?.storeName.orEmpty()
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
    ) {
        Text(
            noSeatTitle(storeName, state.isStoreFull),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(NO_SEAT_DETAIL, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FallbackOption(
                icon = R.drawable.ic_storefront,
                title = SWITCH_TO_TAKEOUT,
                detail = state.takeoutUnavailableReason ?: SWITCH_TO_TAKEOUT_DETAIL,
                enabled = state.canSwitchToTakeout,
                shape = groupedShape(0, 3),
                onClick = onSwitchToTakeout,
            )
            FallbackOption(
                icon = R.drawable.ic_history,
                title = WAIT_IN_LINE,
                detail = WAIT_IN_LINE_DETAIL,
                shape = groupedShape(1, 3),
                onClick = onWait,
            )
            FallbackOption(
                icon = R.drawable.ic_close,
                title = CANCEL_EXIT,
                detail = CANCEL_EXIT_DETAIL,
                shape = groupedShape(2, 3),
                onClick = onExit,
            )
        }
    }
}

@Composable
private fun FallbackOption(
    @DrawableRes icon: Int,
    title: String,
    detail: String,
    shape: Shape,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val alpha = if (enabled) 1f else 0.5f
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 1-tap payment, as the Android Payment artboard. The wallet is already picked, so one tap on Pay sends the order. */
@Composable
private fun PaymentSheet(state: CheckInUiState, onSelect: (String) -> Unit, onPay: () -> Unit, onClose: () -> Unit) {
    val order = state.order ?: return
    val storeName = state.store?.name ?: order.storeName
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Payment",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = "Close")
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "$storeName · ${FulfillmentMode.IN_STORE.choiceLabel} · ${if (order.itemCount == 1) "1 item" else "${order.itemCount} items"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(order.total.formatted(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("Tax included", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.paymentMethods.isEmpty()) {
            LoadingIndicator("Loading payment methods…")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.selectableGroup()) {
                state.paymentMethods.forEachIndexed { index, method ->
                    PaymentMethodRow(
                        method = method,
                        selected = method.id == state.selectedMethodId,
                        shape = groupedShape(index, state.paymentMethods.size),
                        onSelect = { onSelect(method.id) },
                    )
                }
            }
        }
        PrimaryButton(text = payButtonLabel(order.total), onClick = onPay, enabled = state.canPay)
    }
}

@Composable
private fun PaymentMethodRow(method: PaymentMethod, selected: Boolean, shape: Shape, onSelect: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = shape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp),
        ) {
            Icon(
                painterResource(if (method.type == PaymentType.CARD) R.drawable.ic_credit_card else R.drawable.ic_wallet),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                method.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            // The row handles the tap, so the radio itself is only a picture of the state.
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun SentContent(state: CheckInUiState, onViewOrders: () -> Unit, onDone: () -> Unit) {
    val number = state.sentOrderNumber ?: return
    Icon(
        painterResource(R.drawable.ic_check_circle),
        contentDescription = null,
        tint = DekitaTheme.colors.open.content,
        modifier = Modifier.size(40.dp),
    )
    Text(
        sentToKitchenTitle(number),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() },
    )
    Text(SENT_TO_KITCHEN_DETAIL, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    PrimaryButton(text = "View order", onClick = onViewOrders)
    TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back to Discover") }
}

@Composable
private fun UnavailableContent(onDone: () -> Unit) {
    Text(
        "This order can't be checked in",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        "It's no longer a draft. It may already be in the kitchen or back in your cart.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FilledTonalButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back to Discover") }
}

// The artboard's illustrated map, drawn in its own 393 x 852 space and scaled to cover the screen.
// A fixed picture, not a real map: the mockup has no location or map SDK.
private val MapGround = Color(0xFFECE6DC)
private val MapBlock = Color(0xFFE2DACD)
private val MapPark = Color(0xFFD5E6C9)
private val MapRoad = Color(0xFFFFFFFF)
private val MapBrand = Color(0xFFB3410E)
private val MapYou = Color(0xFF1A73E8)
private const val MAP_WIDTH = 393f
private const val MAP_HEIGHT = 852f
private val StorePin = Offset(214f, 318f)

@Composable
private fun CheckInMap(storeName: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.background(MapGround)) {
        val mapScale = maxOf(constraints.maxWidth / MAP_WIDTH, constraints.maxHeight / MAP_HEIGHT)
        val dx = (constraints.maxWidth - MAP_WIDTH * mapScale) / 2f
        val dy = (constraints.maxHeight - MAP_HEIGHT * mapScale) / 2f
        Canvas(Modifier.fillMaxSize()) {
            withTransform({
                translate(dx, dy)
                scale(mapScale, mapScale, pivot = Offset.Zero)
            }) {
                listOf(
                    Triple(Offset(18f, 40f), Size(120f, 140f), MapBlock),
                    Triple(Offset(186f, 30f), Size(190f, 130f), MapBlock),
                    Triple(Offset(20f, 226f), Size(116f, 196f), MapPark),
                    Triple(Offset(292f, 210f), Size(90f, 210f), MapBlock),
                    Triple(Offset(20f, 472f), Size(140f, 150f), MapBlock),
                    Triple(Offset(206f, 474f), Size(176f, 160f), MapBlock),
                ).forEach { (topLeft, size, color) -> drawRoundRect(color, topLeft, size, CornerRadius(10f)) }
                listOf(
                    Offset(-20f, 200f) to Offset(420f, 182f),
                    Offset(-20f, 448f) to Offset(420f, 436f),
                    Offset(158f, -20f) to Offset(176f, 880f),
                    Offset(270f, -20f) to Offset(256f, 880f),
                ).forEach { (from, to) -> drawLine(MapRoad, from, to, strokeWidth = 22f) }
                drawLine(MapRoad, Offset(-20f, 654f), Offset(420f, 646f), strokeWidth = 10f)
                drawLine(MapRoad, Offset(64f, -20f), Offset(82f, 880f), strokeWidth = 10f)
                // The check-in range around the store, and the customer inside it.
                drawCircle(MapBrand.copy(alpha = 0.08f), 92f, StorePin)
                drawCircle(
                    MapBrand.copy(alpha = 0.45f),
                    92f,
                    StorePin,
                    style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
                )
                drawCircle(MapYou.copy(alpha = 0.16f), 24f, Offset(196f, 376f))
                drawCircle(Color.White, 11f, Offset(196f, 376f))
                drawCircle(MapYou, 8f, Offset(196f, 376f))
            }
        }
        val density = LocalDensity.current
        val pinX = with(density) { (dx + StorePin.x * mapScale).toDp() }
        val pinY = with(density) { (dy + StorePin.y * mapScale).toDp() }
        Text(
            storeName,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF231A16),
            maxLines = 1,
            modifier = Modifier
                .offset(x = pinX - 60.dp, y = pinY - 64.dp)
                .background(Color(0xFFFFF8F6), MaterialTheme.shapes.small)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset(x = pinX - 24.dp, y = pinY - 24.dp)
                .size(48.dp)
                .background(MapBrand, RoundedCornerShape(16.dp))
                .border(3.dp, Color.White, RoundedCornerShape(16.dp)),
        ) {
            Icon(painterResource(R.drawable.ic_restaurant), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}
