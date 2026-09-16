package lk.salli.app.nav

import lk.salli.app.features.goals.GoalsScreen
import lk.salli.app.security.AppLockController
import lk.salli.app.security.LockScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lk.salli.app.features.planning.SafeToSpendScreen
import lk.salli.app.features.recurring.RecurringScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import lk.salli.app.features.bills.BillsScreen
import lk.salli.app.features.budgets.BudgetsScreen
import lk.salli.app.features.fuel.FuelPassScreen
import lk.salli.app.features.home.HomeScreen
import lk.salli.app.features.insights.InsightsScreen
import lk.salli.app.features.onboarding.OnboardingScreen
import lk.salli.app.features.settings.SettingsScreen
import lk.salli.app.features.timeline.TimelineScreen
import lk.salli.app.features.split.SplitGroupScreen
import lk.salli.app.features.split.SplitGroupsScreen
import lk.salli.app.features.txdetail.TransactionDetailSheet
import lk.salli.app.features.unknown.UnknownSmsScreen
import lk.salli.design.components.FloatingNavBar
import lk.salli.design.components.ThemeTransitionLayer

private val navRoutes: Set<String> = Destination.entries.mapTo(HashSet()) { it.route }

@Composable
fun SalliNavHost(
    startDestination: String,
    appLock: AppLockController,
    navController: NavHostController = rememberNavController(),
) {
    // The gate disposes every amount-bearing surface, including dialog and bottom-sheet windows.
    // SaveableStateProvider retains navigation/screen state and ActivityResult launcher keys so a
    // result delivered while locked is queued for the same launcher after authentication.
    val lockState by appLock.state.collectAsStateWithLifecycle()
    val stateHolder = rememberSaveableStateHolder()
    when (lockState) {
        AppLockController.State.UNLOCKED -> stateHolder.SaveableStateProvider(UNLOCKED_STATE_KEY) {
            UnlockedSalliNavHost(startDestination, navController)
        }
        AppLockController.State.LOCKED -> LockScreen(appLock)
        AppLockController.State.CHECKING ->
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    }
}

@Composable
private fun UnlockedSalliNavHost(startDestination: String, navController: NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomNav = currentRoute in navRoutes
    val currentDest = Destination.entries.firstOrNull { it.route == currentRoute }

    // The transaction detail sheet is hoisted above the NavHost rather than being a route:
    // the screen that opened it stays composed and visible underneath, and closing is the
    // sheet's own animation instead of a back-stack pop fighting it. -1 = closed.
    var detailTxId by rememberSaveable { mutableLongStateOf(-1L) }

    // ThemeTransitionLayer snapshots the current frame when a theme toggle fires and
    // animates a circular reveal outward from the toggle's tap point. Inside, we draw the
    // theme background so the activity window (light cream via themes.xml) doesn't bleed
    // through when the user is on the dark palette.
    ThemeTransitionLayer {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Route.ONBOARDING) {
                OnboardingScreen(
                    onDone = {
                        navController.navigate(Destination.HOME.route) {
                            popUpTo(Route.ONBOARDING) { inclusive = true }
                        }
                    },
                    onReviewUnknown = {
                        navController.navigate(Destination.HOME.route) {
                            popUpTo(Route.ONBOARDING) { inclusive = true }
                        }
                        navController.navigate(Route.UNKNOWN_SMS)
                    },
                )
            }
            composable(Destination.HOME.route) {
                HomeScreen(
                    onOpenSafeToSpend = { navController.navigate(Route.SAFE_TO_SPEND) },
                    onSeeAllActivity = { navController.navigateToTab(Destination.TIMELINE) },
                    onTransactionClick = { id -> detailTxId = id },
                )
            }
            composable(Destination.TIMELINE.route) {
                TimelineScreen(
                    onTransactionClick = { id -> detailTxId = id },
                )
            }
            composable(Destination.INSIGHTS.route) { InsightsScreen() }
            composable(Destination.BUDGETS.route) { BudgetsScreen() }
            composable(Destination.SETTINGS.route) {
                SettingsScreen(
                    onOpenUnknownSms = { navController.navigate(Route.UNKNOWN_SMS) },
                    onOpenBills = { navController.navigate(Route.BILLS) },
                    onOpenFuelPass = { navController.navigate(Route.FUEL_PASS) },
                    onOpenRecurring = { navController.navigate(Route.RECURRING) },
                    onOpenGoals = { navController.navigate(Route.GOALS) },
                    onOpenSplit = { navController.navigate(Route.splitGroups()) },
                    onReplayIntro = { navController.navigate(Route.ONBOARDING_REPLAY) },
                )
            }

            composable(Route.ONBOARDING_REPLAY) {
                OnboardingScreen(replay = true, onDone = { navController.popBackStack() })
            }

            composable(Route.UNKNOWN_SMS) {
                UnknownSmsScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.BILLS) {
                BillsScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.FUEL_PASS) {
                FuelPassScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.RECURRING) {
                RecurringScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.GOALS) {
                GoalsScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.SAFE_TO_SPEND) {
                SafeToSpendScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Route.SPLIT_GROUPS,
                arguments = listOf(navArgument("tx") { type = NavType.LongType; defaultValue = -1L }),
            ) {
                SplitGroupsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenGroup = { groupId, pendingTransactionId ->
                        navController.navigate(Route.splitGroup(groupId, pendingTransactionId ?: -1L)) {
                            // Once "Split this" has a group, back returns to where the user began,
                            // not to a list still asking them to pick one.
                            if (pendingTransactionId != null) popUpTo(Route.SPLIT_GROUPS) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = Route.SPLIT_GROUP,
                arguments = listOf(
                    navArgument("groupId") { type = NavType.LongType },
                    navArgument("tx") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) {
                SplitGroupScreen(onBack = { navController.popBackStack() })
            }

        }

        if (detailTxId > 0L) {
            TransactionDetailSheet(
                txId = detailTxId,
                onDismiss = { detailTxId = -1L },
                onSplit = { txId ->
                    detailTxId = -1L
                    navController.navigate(Route.splitGroups(txId))
                },
            )
        }

        if (showBottomNav) {
            // Soft bottom-up gradient mask: fades the scrolling content out into the
            // background color as it approaches the nav pill, so rows ghosting behind the
            // pill don't visually compete with it. Drawn before the pill so the pill sits
            // fully opaque on top of the fade.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
            FloatingNavBar(
                items = Destination.entries,
                selected = currentDest,
                onSelect = { dest -> navController.navigateToTab(dest) },
                label = { it.label },
                icon = { it.icon },
                key = { it.route },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
    }
}

private const val UNLOCKED_STATE_KEY = "salli-unlocked-content"

private fun NavHostController.navigateToTab(dest: Destination) {
    navigate(dest.route) {
        popUpTo(Destination.HOME.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
