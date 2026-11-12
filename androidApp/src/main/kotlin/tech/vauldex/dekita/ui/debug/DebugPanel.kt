package tech.vauldex.dekita.ui.debug

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import tech.vauldex.dekita.R
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.data.mock.SeedAuth
import tech.vauldex.dekita.data.mock.availabilityStatus
import tech.vauldex.dekita.data.mock.debugLabel
import tech.vauldex.dekita.data.mock.defaultAvailabilityStoreId
import tech.vauldex.dekita.di.AppContainer
import tech.vauldex.dekita.domain.BusinessType
import tech.vauldex.dekita.domain.CheckInQr
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.Geofence
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.getPlatform

/** Opens the debug panel. Null in release builds, so screens can hide their debug entry points. */
val LocalOpenDebugPanel = staticCompositionLocalOf<(() -> Unit)?> { null }

/** A titled block of controls in the debug panel. */
class DebugSection(val title: String, val content: @Composable (AppContainer) -> Unit)

/** The panel's sections, in order. Later tickets add their simulation controls to this list. */
internal val debugSections: List<DebugSection> = listOf(
    // First, so it shows at half height over the screen being checked.
    DebugSection("Live availability") { container -> AvailabilityControls(container) },
    DebugSection("Build") { BuildInfo() },
    DebugSection("Mock data") { container -> MockDataControls(container) },
    DebugSection("Discover") { container -> DiscoverControls(container) },
    DebugSection("Auth") { container -> AuthControls(container) },
    DebugSection("Cart") { container -> CartControls(container) },
    DebugSection("Check-in") { container -> CheckInControls(container) },
    DebugSection("Seat check") { container -> SeatControls(container) },
)

@Composable
internal fun DebugPanel(container: AppContainer, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        DebugPanelContent(container)
    }
}

/** The panel's contents without the sheet. */
@Composable
internal fun DebugPanelContent(container: AppContainer, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
    ) {
        Text("Debug panel", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Debug builds only. Release builds never show this panel.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        debugSections.forEach { section ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                    section.content(container)
                }
            }
        }
    }
}

@Composable
private fun BuildInfo() {
    val platform = remember { getPlatform().name }
    Text("Platform: $platform", style = MaterialTheme.typography.bodyMedium)
    Text("Build: debug", style = MaterialTheme.typography.bodyMedium)
    Text("Data: in-memory mock data. Only the check-in map loads from the network.", style = MaterialTheme.typography.bodyMedium)
}

private val loadingDelayOptions = listOf(0.milliseconds, 800.milliseconds, 2.seconds)

@Composable
private fun MockDataControls(container: AppContainer) {
    val loadingDelay by container.mockConfig.loadingDelay.collectAsStateWithLifecycle()
    val liveQueue by container.mockConfig.liveQueue.collectAsStateWithLifecycle()
    val liveOrders by container.mockConfig.liveOrders.collectAsStateWithLifecycle()
    Text("Loading delay", style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        loadingDelayOptions.forEach { option ->
            FilterChip(
                selected = loadingDelay == option,
                onClick = { container.mockConfig.loadingDelay.value = option },
                label = { Text(option.toString()) },
            )
        }
    }
    FilterChip(
        selected = liveQueue,
        onClick = { container.mockConfig.liveQueue.value = !liveQueue },
        label = { Text("Live queue updates") },
    )
    FilterChip(
        selected = liveOrders,
        onClick = { container.mockConfig.liveOrders.value = !liveOrders },
        label = { Text("Live order status") },
    )
    Button(onClick = { container.resetMockData() }) { Text("Reset mock data") }
}

/** DISC-01: break the feed, or start as a new customer. Store pauses are under Live availability. */
@Composable
private fun DiscoverControls(container: AppContainer) {
    val simulator = container.storeFeedSimulator
    OutlinedButton(
        onClick = {
            simulator.failNextLoad()
            container.storeRepository.refresh()
        },
    ) { Text("Simulate store feed error") }
    OutlinedButton(onClick = { container.simulateNewUser() }) { Text("Simulate new user (no favorites or history)") }
}

/** Sign-in simulation for AUTH-01. The values shown are the documented fakes from [SeedAuth]. */
@Composable
private fun AuthControls(container: AppContainer) {
    val session by container.authRepository.session.collectAsStateWithLifecycle()
    val forceSsoFailure by container.mockConfig.forceSsoFailure.collectAsStateWithLifecycle()

    Text(
        "Session: ${session?.method?.name?.lowercase()?.let { "signed in with $it" } ?: "guest"}",
        style = MaterialTheme.typography.bodyMedium,
    )
    FilterChip(
        selected = forceSsoFailure,
        onClick = { container.mockConfig.forceSsoFailure.value = !forceSsoFailure },
        label = { Text("Force SSO failure") },
    )
    Text("Test code: ${SeedAuth.TEST_OTP}", style = MaterialTheme.typography.bodyMedium)
    Text("Test account: ${SeedAuth.TEST_EMAIL} / ${SeedAuth.TEST_PASSWORD}", style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = { container.fillSampleCart() }) { Text("Fill sample cart") }
}

/** CART-01: a cart to check out. Its store's live changes are under Live availability, which starts on it. */
@Composable
private fun CartControls(container: AppContainer) {
    val cart by container.cartRepository.cart.collectAsStateWithLifecycle()
    OutlinedButton(onClick = { container.fillSampleCart() }) { Text("Fill sample cart") }
    if (cart == null) {
        Text("The cart is empty. Fill it, or add items from a store.", style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * AVAIL-01: what a store's own app can change at any moment. Every change reaches the screens that are
 * already open (Discover, Store detail, the item sheet, Cart Review) with no refresh.
 */
@Composable
private fun AvailabilityControls(container: AppContainer) {
    val stores by container.storeRepository.stores.collectAsStateWithLifecycle()
    val cart by container.cartRepository.cart.collectAsStateWithLifecycle()
    var pickedStoreId by rememberSaveable { mutableStateOf<String?>(null) }
    val storeId = pickedStoreId ?: defaultAvailabilityStoreId(cart)
    val loaded = stores.dataOrNull()
    val store = loaded?.firstOrNull { it.id == storeId }
    val feed = container.storeFeedSimulator
    val menus = container.menuSimulator

    Text(
        "Changes reach open screens at once, with no refresh. Open the panel over a screen to watch it change.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (loaded == null || store == null) {
        Text("The store feed is not loaded.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    StorePicker(loaded, store, onPick = { pickedStoreId = it })
    Text(store.availabilityStatus(), style = MaterialTheme.typography.bodyMedium)

    val accepting = store.availability != StoreAvailability.PAUSED
    Button(
        onClick = { feed.setAvailability(store.id, if (accepting) StoreAvailability.PAUSED else StoreAvailability.OPEN) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (accepting) "Stop Accepting Orders" else "Resume Accepting Orders") }

    Text("Service mode", style = MaterialTheme.typography.labelLarge)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        BusinessType.entries.forEachIndexed { index, type ->
            SegmentedButton(
                selected = store.businessType == type,
                onClick = { feed.setBusinessType(store.id, type) },
                shape = SegmentedButtonDefaults.itemShape(index, BusinessType.entries.size),
            ) { Text(type.debugLabel, maxLines = 1) }
        }
    }

    Text("Queue", style = MaterialTheme.typography.labelLarge)
    if (store.queue == null) {
        Text("No queue while the store isn't accepting orders.", style = MaterialTheme.typography.bodyMedium)
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-1, 1, 5).forEach { delta ->
                OutlinedButton(onClick = { feed.adjustQueue(store.id, delta) }) { Text(if (delta < 0) "−1" else "+$delta") }
            }
        }
    }

    val menuFlow = remember(store.id) { container.menuRepository.menu(store.id) }
    val menu by menuFlow.collectAsStateWithLifecycle()
    val loadedMenu = menu.dataOrNull()
    if (loadedMenu == null) {
        Text("Loading the menu…", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Text("Menu items", style = MaterialTheme.typography.labelLarge)
    loadedMenu.items.forEach { item ->
        SellingRow(item.name, item.isSoldOut) { menus.setItemSoldOut(store.id, item.id, it) }
    }
    if (loadedMenu.options.isNotEmpty()) {
        Text("Options", style = MaterialTheme.typography.labelLarge)
        loadedMenu.options.forEach { option ->
            SellingRow(option.name, option.isSoldOut) { menus.setOptionSoldOut(store.id, option.id, it) }
        }
    }
}

@Composable
private fun StorePicker(stores: List<Store>, selected: Store, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_expand_more), contentDescription = "Choose a store")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            stores.forEach { store ->
                DropdownMenuItem(
                    text = { Text(store.name) },
                    onClick = {
                        onPick(store.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** An item or option with its Stop Selling / Resume Selling switch. */
@Composable
private fun SellingRow(name: String, soldOut: Boolean, onSetSoldOut: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (soldOut) "$name (sold out)" else name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (soldOut) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onSetSoldOut(!soldOut) }) { Text(if (soldOut) "Resume Selling" else "Stop Selling") }
    }
}

/** The top-bar entry to the panel, for screens that react to its live changes. Nothing in release builds. */
@Composable
internal fun DebugPanelAction() {
    val open = LocalOpenDebugPanel.current ?: return
    IconButton(onClick = open) {
        Icon(painterResource(R.drawable.ic_bug), contentDescription = "Debug panel")
    }
}

/** CHECKIN-01: arrival and QR scans, in place of GPS and the camera. Each acts on the in-store draft. */
@Composable
private fun CheckInControls(container: AppContainer) {
    val orders by container.orderRepository.orders.collectAsStateWithLifecycle()
    val stores by container.storeRepository.stores.collectAsStateWithLifecycle()
    val customer by container.locationRepository.customerLocation.collectAsStateWithLifecycle()
    val simulator = container.checkInSimulator
    var tableNumber by rememberSaveable { mutableStateOf("12") }

    val draft = orders.dataOrNull()?.filter { it.awaitsCheckIn }?.maxByOrNull { it.createdAt }
    if (draft == null) {
        Text("No in-store draft to check in. Reset mock data to get the seed draft back.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val storeAt = stores.dataOrNull()?.firstOrNull { it.id == draft.storeId }?.location
    val distance = customer?.let { storeAt?.distanceTo(it)?.roundToInt() }
    val where = when {
        distance == null -> "location unknown"
        Geofence.isInRange(distance) -> "$distance m, in range"
        else -> "$distance m, out of range"
    }
    Text("Draft #${draft.number} at ${draft.storeName}: $where", style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = simulator::simulateArrival, modifier = Modifier.weight(1f)) { Text("Simulate arrival") }
        OutlinedButton(onClick = simulator::simulateLeaving, modifier = Modifier.weight(1f)) { Text("Simulate leaving") }
    }

    Text(
        "QR scans reach the scanner only while it's open. A result pauses scanning until Scan again.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = simulator::scanStoreQr) { Text("Simulate store QR") }
    val validTable = CheckInQr.isValidTableNumber(tableNumber)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = tableNumber,
            onValueChange = { tableNumber = it.take(4) },
            label = { Text("Table") },
            isError = !validTable,
            supportingText = if (validTable) null else ({ Text("1–4 letters or digits") }),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { simulator.scanTableQr(tableNumber) }, enabled = validTable) { Text("Simulate table QR") }
    }
    OutlinedButton(onClick = simulator::scanOtherStoreQr) { Text("Simulate other-store QR") }
    OutlinedButton(onClick = simulator::scanInvalidQr) { Text("Simulate invalid QR") }
}

/** SEAT-01: "seats full" per Eat-In store, to try the seat check's fallbacks. */
@Composable
private fun SeatControls(container: AppContainer) {
    val stores by container.storeRepository.stores.collectAsStateWithLifecycle()
    Text(
        "A store with full seats skips \"Have you found a seat?\" and offers Takeout, waiting or leaving.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    stores.dataOrNull().orEmpty().filter { it.supports(FulfillmentMode.IN_STORE) }.forEach { store ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                store.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { container.storeFeedSimulator.setSeatsFull(store.id, !store.seatsFull) }) {
                Text(if (store.seatsFull) "Free seats" else "Seats full")
            }
        }
    }
}

private const val HOLD_MILLIS = 1_000L

/**
 * Calls [onTrigger] when two fingers rest still for about a second, anywhere on screen.
 * It only watches touches and never consumes them, so the screen underneath behaves as usual.
 */
internal fun Modifier.twoFingerLongPress(onTrigger: () -> Unit): Modifier = pointerInput(onTrigger) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var pressed = currentEvent.changes.filter { it.pressed }
        while (pressed.size == 1) {
            pressed = awaitPointerEvent(PointerEventPass.Initial).changes.filter { it.pressed }
        }
        if (pressed.size < 2) return@awaitEachGesture

        val origins = pressed.associate { it.id to it.position }
        val interrupted = withTimeoutOrNull(HOLD_MILLIS) {
            while (true) {
                val now = awaitPointerEvent(PointerEventPass.Initial).changes.filter { it.pressed }
                val moved = now.any { change ->
                    val origin = origins[change.id]
                    origin == null || (change.position - origin).getDistance() > viewConfiguration.touchSlop
                }
                if (now.size < 2 || moved) break
            }
            true
        }
        if (interrupted == null) onTrigger()
    }
}
