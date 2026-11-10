package tech.vauldex.dekita.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dekita.sharedui.generated.resources.Res
import dekita.sharedui.generated.resources.ic_account
import dekita.sharedui.generated.resources.ic_discover
import dekita.sharedui.generated.resources.ic_orders
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import tech.vauldex.dekita.di.AppContainer
import tech.vauldex.dekita.presentation.account.AccountViewModel
import tech.vauldex.dekita.presentation.discover.DiscoverViewModel
import tech.vauldex.dekita.presentation.orders.OrdersViewModel
import tech.vauldex.dekita.presentation.store.StoreDetailViewModel
import tech.vauldex.dekita.ui.account.AccountScreen
import tech.vauldex.dekita.ui.discover.DiscoverScreen
import tech.vauldex.dekita.ui.orders.OrdersScreen
import tech.vauldex.dekita.ui.store.StoreDetailScreen

// One nested graph per tab, so each tab keeps its own back stack when you switch away and back.
@Serializable data object DiscoverGraph
@Serializable data object OrdersGraph
@Serializable data object AccountGraph

@Serializable data object DiscoverRoute
@Serializable data object OrdersRoute
@Serializable data object AccountRoute
@Serializable data class StoreDetailRoute(val storeId: String)

private enum class TopLevelTab(
    val graph: Any,
    val graphClass: KClass<*>,
    val rootClass: KClass<*>,
    val label: String,
    val icon: DrawableResource,
) {
    DISCOVER(DiscoverGraph, DiscoverGraph::class, DiscoverRoute::class, "Discover", Res.drawable.ic_discover),
    ORDERS(OrdersGraph, OrdersGraph::class, OrdersRoute::class, "Orders", Res.drawable.ic_orders),
    ACCOUNT(AccountGraph, AccountGraph::class, AccountRoute::class, "Account", Res.drawable.ic_account),
}

/**
 * Bottom tabs plus the back stack. Android back, predictive back and the iOS edge swipe all pop
 * through NavHost; Compose Multiplatform wires the iOS gesture itself, so nothing here is platform-specific.
 */
@Composable
fun AppShell(container: AppContainer, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
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
                        viewModel = viewModel { DiscoverViewModel(container.storeRepository) },
                        onStoreClick = { storeId ->
                            if (entry.isResumed()) navController.navigate(StoreDetailRoute(storeId))
                        },
                    )
                }
                composable<StoreDetailRoute> { entry ->
                    val storeId = entry.toRoute<StoreDetailRoute>().storeId
                    StoreDetailScreen(
                        viewModel = viewModel {
                            StoreDetailViewModel(storeId, container.storeRepository, container.menuRepository)
                        },
                        onBack = { if (entry.isResumed()) navController.popBackStack() },
                    )
                }
            }
            navigation<OrdersGraph>(startDestination = OrdersRoute) {
                composable<OrdersRoute> {
                    OrdersScreen(viewModel = viewModel { OrdersViewModel(container.orderRepository) })
                }
            }
            navigation<AccountGraph>(startDestination = AccountRoute) {
                composable<AccountRoute> {
                    AccountScreen(
                        viewModel = viewModel {
                            AccountViewModel(container.userProfile, container.paymentRepository, container.inboxRepository)
                        },
                    )
                }
            }
        }
    }
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
