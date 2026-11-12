package tech.vauldex.dekita.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import tech.vauldex.dekita.R
import tech.vauldex.dekita.di.AppContainer
import tech.vauldex.dekita.domain.ClientPlatform
import tech.vauldex.dekita.presentation.account.AccountViewModel
import tech.vauldex.dekita.presentation.auth.EmailAuthMode
import tech.vauldex.dekita.presentation.auth.EmailAuthViewModel
import tech.vauldex.dekita.presentation.auth.ForgotPasswordViewModel
import tech.vauldex.dekita.presentation.auth.OtpViewModel
import tech.vauldex.dekita.presentation.auth.PhoneEntryViewModel
import tech.vauldex.dekita.presentation.auth.SignInViewModel
import tech.vauldex.dekita.presentation.cart.CartReviewViewModel
import tech.vauldex.dekita.presentation.checkin.CheckInViewModel
import tech.vauldex.dekita.presentation.checkin.QrScannerViewModel
import tech.vauldex.dekita.presentation.checkin.SeatCheckViewModel
import tech.vauldex.dekita.presentation.discover.DiscoverViewModel
import tech.vauldex.dekita.presentation.discover.FavoritesViewModel
import tech.vauldex.dekita.presentation.discover.HistoryViewModel
import tech.vauldex.dekita.presentation.orders.OrderTicketViewModel
import tech.vauldex.dekita.presentation.orders.OrdersViewModel
import tech.vauldex.dekita.presentation.orders.RateOrderViewModel
import tech.vauldex.dekita.presentation.payment.PaymentTarget
import tech.vauldex.dekita.presentation.payment.PaymentViewModel
import tech.vauldex.dekita.presentation.store.StoreDetailViewModel
import tech.vauldex.dekita.ui.account.AccountScreen
import tech.vauldex.dekita.ui.auth.EmailAuthScreen
import tech.vauldex.dekita.ui.auth.ForgotPasswordScreen
import tech.vauldex.dekita.ui.auth.OtpScreen
import tech.vauldex.dekita.ui.auth.PhoneEntryScreen
import tech.vauldex.dekita.ui.auth.SignInScreen
import tech.vauldex.dekita.ui.cart.CartReviewScreen
import tech.vauldex.dekita.ui.checkin.CheckInScreen
import tech.vauldex.dekita.ui.checkin.QrScannerScreen
import tech.vauldex.dekita.ui.checkin.SeatCheckScreen
import tech.vauldex.dekita.ui.discover.DiscoverScreen
import tech.vauldex.dekita.ui.discover.FavoritesScreen
import tech.vauldex.dekita.ui.discover.HistoryScreen
import tech.vauldex.dekita.ui.orders.OrderTicketScreen
import tech.vauldex.dekita.ui.orders.OrdersScreen
import tech.vauldex.dekita.ui.orders.RateOrderScreen
import tech.vauldex.dekita.ui.payment.CollectPaymentEvents
import tech.vauldex.dekita.ui.payment.PaymentSheet
import tech.vauldex.dekita.ui.store.StoreDetailScreen

// One nested graph per tab, so each tab keeps its own back stack when you switch away and back.
@Serializable data object DiscoverGraph
@Serializable data object OrdersGraph
@Serializable data object AccountGraph

@Serializable data object DiscoverRoute
@Serializable data object OrdersRoute
@Serializable data object AccountRoute
@Serializable data class StoreDetailRoute(val storeId: String)
@Serializable data object FavoritesRoute
@Serializable data object HistoryRoute
@Serializable data object CartRoute

// Auth screens sit outside the tab graphs so any tab, or the cart, can push them.
/** [isRoot] after sign-out: the only screen left, offering "Browse as guest" instead of Close. */
@Serializable data class SignInRoute(val isRoot: Boolean = false)
@Serializable data object PhoneEntryRoute
@Serializable data class OtpRoute(val phoneE164: String)
@Serializable data class EmailAuthRoute(val signUp: Boolean)
@Serializable data object ForgotPasswordRoute

// Check-in sits outside the tabs: it opens over Discover or Orders, and leaving it returns there.
@Serializable data class CheckInRoute(val orderId: String)
@Serializable data class QrScannerRoute(val orderId: String)
@Serializable data class SeatCheckRoute(val orderId: String)

// The order ticket also sits outside the tabs: payment, Discover, Orders and notifications all open it.
@Serializable data class OrderTicketRoute(val orderId: String)
@Serializable data class RateOrderRoute(val orderId: String)

private enum class TopLevelTab(
    val graph: Any,
    val graphClass: KClass<*>,
    val rootClass: KClass<*>,
    val label: String,
    @DrawableRes val icon: Int,
) {
    DISCOVER(DiscoverGraph, DiscoverGraph::class, DiscoverRoute::class, "Discover", R.drawable.ic_discover),
    ORDERS(OrdersGraph, OrdersGraph::class, OrdersRoute::class, "Orders", R.drawable.ic_orders),
    ACCOUNT(AccountGraph, AccountGraph::class, AccountRoute::class, "Account", R.drawable.ic_account),
}

/**
 * Bottom tabs plus the back stack. Back and predictive back pop through NavHost.
 * [ticketRequest] is an order id from a tapped notification; it opens that ticket when the id checks out.
 */
@Composable
fun AppShell(
    container: AppContainer,
    ticketRequest: StateFlow<String?>,
    onTicketRequestHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val requestedTicket by ticketRequest.collectAsStateWithLifecycle()
    val currentOnTicketRequestHandled by rememberUpdatedState(onTicketRequestHandled)
    LaunchedEffect(requestedTicket) {
        val orderId = requestedTicket ?: return@LaunchedEffect
        currentOnTicketRequestHandled()
        // The id comes from outside the app's screens: open only a paid order, and only while signed in.
        if (container.orderNotifications.canOpenTicket(orderId)) {
            navController.navigate(OrderTicketRoute(orderId)) { launchSingleTop = true }
        }
    }
    val destination = backStackEntry?.destination
    // As in the design, the bar shows on tab roots only and hides on pushed screens.
    val showBottomBar = destination == null || TopLevelTab.entries.any { destination.hasRoute(it.rootClass) }

    Scaffold(
        modifier = modifier,
        // Each screen handles its own top inset; the bar handles the bottom one.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.hasRoute(tab.graphClass) } == true,
                            onClick = { navController.navigateToTab(tab) },
                            icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = DiscoverGraph,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            navigation<DiscoverGraph>(startDestination = DiscoverRoute) {
                composable<DiscoverRoute> { entry ->
                    DiscoverScreen(
                        viewModel = viewModel {
                            DiscoverViewModel(
                                container.storeRepository,
                                container.orderRepository,
                                container.cartRepository,
                                container.authRepository,
                            )
                        },
                        onStoreClick = { storeId ->
                            if (entry.isResumed()) navController.navigate(StoreDetailRoute(storeId))
                        },
                        onFavoritesClick = { if (entry.isResumed()) navController.navigate(FavoritesRoute) },
                        onHistoryClick = { if (entry.isResumed()) navController.navigate(HistoryRoute) },
                        onResumeDraft = { orderId -> if (entry.isResumed()) navController.navigate(CheckInRoute(orderId)) },
                        onOpenTicket = { orderId -> if (entry.isResumed()) navController.navigate(OrderTicketRoute(orderId)) },
                    )
                }
                composable<FavoritesRoute> { entry ->
                    FavoritesScreen(
                        viewModel = viewModel { FavoritesViewModel(container.storeRepository) },
                        onStoreClick = { storeId ->
                            if (entry.isResumed()) navController.navigate(StoreDetailRoute(storeId))
                        },
                        onBack = { if (entry.isResumed()) navController.popBackStack() },
                    )
                }
                composable<HistoryRoute> { entry ->
                    HistoryScreen(
                        viewModel = viewModel { HistoryViewModel(container.storeRepository, container.orderRepository) },
                        onStoreClick = { storeId ->
                            if (entry.isResumed()) navController.navigate(StoreDetailRoute(storeId))
                        },
                        onBack = { if (entry.isResumed()) navController.popBackStack() },
                    )
                }
                composable<StoreDetailRoute> { entry ->
                    val storeId = entry.toRoute<StoreDetailRoute>().storeId
                    StoreDetailScreen(
                        viewModel = viewModel {
                            StoreDetailViewModel(
                                storeId,
                                container.storeRepository,
                                container.menuRepository,
                                container.cartRepository,
                                container.menuSimulator,
                            )
                        },
                        onBack = { if (entry.isResumed()) navController.popBackStack() },
                        onViewCart = { if (entry.isResumed()) navController.navigate(CartRoute) },
                    )
                }
                composable<CartRoute> { entry ->
                    var showPayment by rememberSaveable { mutableStateOf(false) }
                    val payment = viewModel {
                        PaymentViewModel(
                            PaymentTarget.Cart,
                            ClientPlatform.ANDROID,
                            container.paymentService,
                            container.paymentRepository,
                            container.cartRepository,
                            container.orderRepository,
                            container.storeRepository,
                        )
                    }
                    CollectPaymentEvents(payment) { orderId ->
                        navController.navigate(OrderTicketRoute(orderId)) {
                            // The cart is paid and empty, so Back from the ticket skips it.
                            popUpTo<CartRoute> { inclusive = true }
                        }
                    }
                    CartReviewScreen(
                        viewModel = viewModel {
                            CartReviewViewModel(
                                container.cartRepository,
                                container.storeRepository,
                                container.menuRepository,
                                container.orderRepository,
                                container.authRepository,
                            )
                        },
                        onBack = { if (entry.isResumed()) navController.popBackStack() },
                        // The cart is opened from its store's screen, so the menu is one step back.
                        onBrowseMenu = { if (entry.isResumed()) navController.popBackStack() },
                        onSignInRequired = { if (entry.isResumed()) navController.navigate(SignInRoute()) },
                        onPayNow = { showPayment = true },
                        onDraftSaved = { orderId ->
                            navController.navigate(CheckInRoute(orderId)) {
                                // The cart is now a draft order, so Back from check-in skips the empty cart.
                                popUpTo<CartRoute> { inclusive = true }
                            }
                        },
                    )
                    if (showPayment) PaymentSheet(payment, onDismiss = { showPayment = false })
                }
            }
            navigation<OrdersGraph>(startDestination = OrdersRoute) {
                composable<OrdersRoute> { entry ->
                    OrdersScreen(
                        viewModel = viewModel {
                            OrdersViewModel(container.orderRepository, container.inboxRepository, container.authRepository)
                        },
                        onSignIn = { if (entry.isResumed()) navController.navigate(SignInRoute()) },
                        onCheckIn = { orderId -> if (entry.isResumed()) navController.navigate(CheckInRoute(orderId)) },
                        onOpenTicket = { orderId -> if (entry.isResumed()) navController.navigate(OrderTicketRoute(orderId)) },
                        onOpenRating = { orderId -> if (entry.isResumed()) navController.navigate(RateOrderRoute(orderId)) },
                    )
                }
            }
            navigation<AccountGraph>(startDestination = AccountRoute) {
                composable<AccountRoute> { entry ->
                    AccountScreen(
                        viewModel = viewModel {
                            AccountViewModel(container.authRepository, container.paymentRepository)
                        },
                        onSignIn = { if (entry.isResumed()) navController.navigate(SignInRoute()) },
                        onSignedOut = { navController.showSignInAfterSignOut() },
                    )
                }
            }
            authDestinations(container, navController)
            checkInDestinations(container, navController)
            orderDestinations(container, navController)
        }
    }
}

private fun NavGraphBuilder.authDestinations(container: AppContainer, navController: NavController) {
    composable<SignInRoute> { entry ->
        val isRoot = entry.toRoute<SignInRoute>().isRoot
        SignInScreen(
            viewModel = viewModel { SignInViewModel(container.authRepository) },
            isRoot = isRoot,
            onClose = {
                if (entry.isResumed()) {
                    if (isRoot) navController.showFreshDiscover() else navController.popBackStack()
                }
            },
            onPhone = { if (entry.isResumed()) navController.navigate(PhoneEntryRoute) },
            onEmail = { if (entry.isResumed()) navController.navigate(EmailAuthRoute(signUp = true)) },
            onSignedIn = { navController.finishSignIn() },
        )
    }
    composable<PhoneEntryRoute> { entry ->
        PhoneEntryScreen(
            viewModel = viewModel { PhoneEntryViewModel(container.authRepository) },
            onBack = { if (entry.isResumed()) navController.popBackStack() },
            onCodeSent = { phone -> if (entry.isResumed()) navController.navigate(OtpRoute(phone.e164)) },
        )
    }
    composable<OtpRoute> { entry ->
        val phoneE164 = entry.toRoute<OtpRoute>().phoneE164
        OtpScreen(
            viewModel = viewModel { OtpViewModel(phoneE164, container.authRepository) },
            onBack = { if (entry.isResumed()) navController.popBackStack() },
            onSignedIn = { navController.finishSignIn() },
        )
    }
    composable<EmailAuthRoute> { entry ->
        val mode = if (entry.toRoute<EmailAuthRoute>().signUp) EmailAuthMode.SIGN_UP else EmailAuthMode.LOG_IN
        EmailAuthScreen(
            viewModel = viewModel { EmailAuthViewModel(mode, container.authRepository) },
            onBack = { if (entry.isResumed()) navController.popBackStack() },
            onForgotPassword = { if (entry.isResumed()) navController.navigate(ForgotPasswordRoute) },
            onSignedIn = { navController.finishSignIn() },
        )
    }
    composable<ForgotPasswordRoute> { entry ->
        ForgotPasswordScreen(
            viewModel = viewModel { ForgotPasswordViewModel(container.authRepository) },
            onBack = { if (entry.isResumed()) navController.popBackStack() },
        )
    }
}

/**
 * Leaves the auth flow. Started from "Pay now", the cart is still on the back stack (switching tabs saves and
 * pops the Discover stack, so it is there only in that case), and the user returns to it with its items.
 * Otherwise the user lands on a fresh Discover with nothing to go back to.
 */
private fun NavController.finishSignIn() {
    if (popBackStack<CartRoute>(inclusive = false)) return
    showFreshDiscover()
}

private fun NavController.showFreshDiscover() {
    navigate(DiscoverGraph) { popUpTo(graph.id) { inclusive = true } }
}

/** After sign-out only the sign-in screen is left, so Back can't reach a signed-in screen. */
private fun NavController.showSignInAfterSignOut() {
    navigate(SignInRoute(isRoot = true)) { popUpTo(graph.id) { inclusive = true } }
    // Tabs saved by earlier tab switches would otherwise come back on the next visit.
    listOf(DiscoverGraph, OrdersGraph, AccountGraph).forEach { clearBackStack(it) }
}

private fun NavGraphBuilder.checkInDestinations(container: AppContainer, navController: NavController) {
    composable<CheckInRoute> { entry ->
        val orderId = entry.toRoute<CheckInRoute>().orderId
        CheckInScreen(
            viewModel = viewModel {
                CheckInViewModel(orderId, container.orderRepository, container.storeRepository, container.locationRepository)
            },
            onBack = { if (entry.isResumed()) navController.popBackStack() },
            onScanQr = { if (entry.isResumed()) navController.navigate(QrScannerRoute(orderId)) },
            onCheckedIn = { if (entry.isResumed()) navController.navigate(SeatCheckRoute(orderId)) },
        )
    }
    composable<QrScannerRoute> { entry ->
        val orderId = entry.toRoute<QrScannerRoute>().orderId
        QrScannerScreen(
            viewModel = viewModel {
                QrScannerViewModel(orderId, container.orderRepository, container.storeRepository, container.qrScanner)
            },
            onClose = { if (entry.isResumed()) navController.popBackStack() },
            // The scanner is done once it checks in, so Back from the seat check returns to check-in.
            onContinue = {
                if (entry.isResumed()) {
                    navController.navigate(SeatCheckRoute(orderId)) { popUpTo<QrScannerRoute> { inclusive = true } }
                }
            },
        )
    }
    composable<SeatCheckRoute> { entry ->
        val orderId = entry.toRoute<SeatCheckRoute>().orderId
        SeatCheckScreen(
            viewModel = viewModel {
                SeatCheckViewModel(orderId, container.orderRepository, container.storeRepository, container.cartRepository)
            },
            // "Yes, I have a seat" opens PAY-01's payment sheet for the draft. Android offers Google Pay as its wallet.
            payment = viewModel {
                PaymentViewModel(
                    PaymentTarget.Draft(orderId),
                    ClientPlatform.ANDROID,
                    container.paymentService,
                    container.paymentRepository,
                    container.cartRepository,
                    container.orderRepository,
                    container.storeRepository,
                )
            },
            onBack = { if (entry.isResumed()) navController.popBackStack() },
            onSwitchedToTakeout = { storeId -> navController.openTakeoutCart(storeId) },
            // Back to check-in, which keeps the check-in, so Start Cooking brings the seat check back.
            onWaitInLine = { navController.popBackStack<SeatCheckRoute>(inclusive = true) },
            onExit = { navController.leaveCheckInForDiscover() },
            onPaid = { paidOrderId ->
                // Check-in is over once the order is paid, so Back from the ticket leaves the whole flow.
                navController.navigate(OrderTicketRoute(paidOrderId)) { popUpTo<CheckInRoute> { inclusive = true } }
            },
        )
    }
}

private fun NavGraphBuilder.orderDestinations(container: AppContainer, navController: NavController) {
    composable<OrderTicketRoute> { entry ->
        val orderId = entry.toRoute<OrderTicketRoute>().orderId
        OrderTicketScreen(
            viewModel = viewModel {
                OrderTicketViewModel(
                    orderId,
                    container.orderRepository,
                    container.authRepository,
                    container.paymentRepository,
                    container.inboxRepository,
                    container.orderSimulator,
                )
            },
            onBack = { if (entry.isResumed()) navController.popBackStack() },
            onConfirmed = {
                // The order is closed, so Back from the rating skips its ticket.
                navController.navigate(RateOrderRoute(orderId)) { popUpTo<OrderTicketRoute> { inclusive = true } }
            },
            // Opened again later: the receipt stays underneath, so leaving Rate returns to it. Also fired as the
            // ticket opens, before its entry is resumed, so this guards double taps with single-top instead.
            onOpenRating = { navController.navigate(RateOrderRoute(orderId)) { launchSingleTop = true } },
        )
    }
    composable<RateOrderRoute> { entry ->
        val orderId = entry.toRoute<RateOrderRoute>().orderId
        RateOrderScreen(
            viewModel = viewModel {
                RateOrderViewModel(
                    orderId,
                    container.orderRepository,
                    container.storeRepository,
                    container.inboxRepository,
                    container.authRepository,
                )
            },
            onDone = { if (entry.isResumed()) navController.popBackStack() },
        )
    }
}

/**
 * SEAT-01 Switch to Takeout: closes check-in and opens Cart Review on the store's menu, so "Add more items"
 * and Back lead there. Reuses the store screen when check-in was opened from it (the "Head to store" path).
 */
private fun NavController.openTakeoutCart(storeId: String) {
    popBackStack<CheckInRoute>(inclusive = true)
    val onStore = currentBackStackEntry?.takeIf { it.destination.hasRoute(StoreDetailRoute::class) }
        ?.toRoute<StoreDetailRoute>()?.storeId == storeId
    if (!onStore) {
        navigateToTab(TopLevelTab.DISCOVER)
        navigate(StoreDetailRoute(storeId))
    }
    navigate(CartRoute)
}

/** SEAT-01 Cancel / Exit: closes check-in and returns to Discover. The draft is kept. */
private fun NavController.leaveCheckInForDiscover() {
    popBackStack<CheckInRoute>(inclusive = true)
    if (!popBackStack<DiscoverRoute>(inclusive = false)) navigateToTab(TopLevelTab.DISCOVER)
}

private fun NavController.navigateToTab(tab: TopLevelTab) {
    navigate(tab.graph) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Ignores taps that land while a transition is still running, so a double tap can't push or pop twice. */
private fun NavBackStackEntry.isResumed() = lifecycle.currentState == Lifecycle.State.RESUMED
