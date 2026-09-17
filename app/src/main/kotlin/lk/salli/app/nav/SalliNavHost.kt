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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import lk.salli.app.features.plan.PlanScreen
import lk.salli.app.features.settings.SettingsScreen
import lk.salli.app.features.timeline.TimelineScreen
import lk.salli.app.features.split.SplitGroupScreen
import lk.salli.app.features.split.SplitGroupsScreen
import lk.salli.app.features.txdetail.TransactionDetailScreen
import lk.salli.app.features.unknown.UnknownSmsScreen
import lk.salli.design.components.FloatingNavBar
import lk.salli.design.components.ThemeTransitionLayer

/** Patterns that show the bottom nav. Everything else is a full-screen push. */
private val navPatterns: Set<String> = Destination.entries.mapTo(HashSet()) { it.pattern }
private val planSubroutes = setOf(
    Route.BUDGETS, Route.BILLS, Route.RECURRING, Route.GOALS,
    Route.FUEL_PASS, Route.SPLIT_GROUPS, Route.SPLIT_GROUP,
)

@Composable
fun SalliNavHost(
    startDestination: String,
    appLock: AppLockController,
    routeRequest: String? = null,
    routeRequestId: Int = 0,
    navController: NavHostController = rememberNavController(),
) {
    // The gate disposes every amount-bearing surface, including dialog and bottom-sheet windows.
    // SaveableStateProvider retains navigation/screen state and ActivityResult launcher keys so a
    // result delivered while locked is queued for the same launcher after authentication.
    val lockState by appLock.state.collectAsStateWithLifecycle()
    val stateHolder = rememberSaveableStateHolder()
    when (lockState) {
        AppLockController.State.UNLOCKED -> stateHolder.SaveableStateProvider(UNLOCKED_STATE_KEY) {
            UnlockedSalliNavHost(startDestination, navController, routeRequest, routeRequestId)
        }
        AppLockController.State.LOCKED -> LockScreen(appLock)
        AppLockController.State.CHECKING ->
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    }
}

@Composable
private fun UnlockedSalliNavHost(
    startDestination: String,
    navController: NavHostController,
    routeRequest: String?,
    routeRequestId: Int,
) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    LaunchedEffect(routeRequestId) {
        if (routeRequestId > 0 && routeRequest == Destination.PLAN.route && currentRoute != Destination.PLAN.route) {
            navController.navigate(Destination.PLAN.route) {
                launchSingleTop = true
                popUpTo(Destination.HOME.route)
            }
        }
    }
    val showBottomNav = currentRoute in navPatterns || currentRoute in planSubroutes
    val currentDest = Destination.entries.firstOrNull { it.pattern == currentRoute }
        ?: Destination.PLAN.takeIf { currentRoute in planSubroutes }

    // Resolved here rather than inside FloatingNavBar's `label` lambda: that lambda is a
    // plain function and stringResource is composable.
    val tabLabels = Destination.entries.associateWith { stringResource(it.labelRes) }

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
            composable(
                Route.ONBOARDING,
                // Landing on Home: the reveal lifts away while Home settles in underneath.
                exitTransition = { fadeOut(tween(220)) + scaleOut(targetScale = 1.04f, animationSpec = tween(220)) },
            ) {
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
            composable(
                Destination.HOME.route,
                enterTransition = {
                    if (initialState.destination.route == Route.ONBOARDING) {
                        fadeIn(tween(360, delayMillis = 60)) + scaleIn(initialScale = 0.96f, animationSpec = tween(360))
                    } else null
                },
            ) {
                HomeScreen(
                    onOpenSafeToSpend = { navController.navigate(Route.SAFE_TO_SPEND) },
                    onSeeAllActivity = { navController.navigateToTab(Destination.ACTIVITY) },
                    onOpenSettings = { navController.navigate(Route.SETTINGS) },
                    onSeeAllPlan = { navController.navigateToTab(Destination.PLAN) },
                    onOpenUpcoming = { route -> navController.navigate(route) },
                    onOpenBudgets = { navController.navigate(Route.BUDGETS) },
                    onAccountClick = { accountId ->
                        navController.navigate(Route.activity(accountId = accountId))
                    },
                    onTransactionClick = { id -> navController.navigate(Route.transactionDetail(id)) },
                )
            }
            composable(
                route = Destination.ACTIVITY.pattern,
                arguments = listOf(
                    navArgument(Route.ARG_ACCOUNT) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(Route.ARG_CATEGORY) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(Route.ARG_QUERY) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val filters = entry.arguments.toActivityFilterArgs()
                TimelineScreen(
                    filters = filters,
                    onTransactionClick = { id -> navController.navigate(Route.transactionDetail(id)) },
                )
            }
            composable(Destination.PLAN.route) {
                PlanScreen(
                    onOpenBudgets = { navController.navigate(Route.BUDGETS) },
                    onOpenBills = { navController.navigate(Route.BILLS) },
                    onOpenRecurring = { navController.navigate(Route.RECURRING) },
                    onOpenGoals = { navController.navigate(Route.GOALS) },
                    onOpenSplit = { navController.navigate(Route.splitGroups()) },
                    onOpenFuelPass = { navController.navigate(Route.FUEL_PASS) },
                    onOpenSafeToSpend = { navController.navigate(Route.SAFE_TO_SPEND) },
                )
            }
            composable(Destination.INSIGHTS.route) {
                InsightsScreen(
                    onOpenActivityCategory = { id -> navController.navigate(Route.activity(categoryId = id)) },
                    onOpenActivityMerchant = { query -> navController.navigate(Route.activity(query = query)) },
                    onOpenActivityAccount = { id -> navController.navigate(Route.activity(accountId = id)) },
                )
            }

            composable(
                route = Route.TRANSACTION_DETAIL,
                arguments = listOf(navArgument("txId") { type = NavType.LongType }),
            ) { entry ->
                TransactionDetailScreen(
                    txId = requireNotNull(entry.arguments).getLong("txId"),
                    onBack = { navController.popBackStack() },
                    onSplit = { id -> navController.navigate(Route.splitGroups(id)) },
                )
            }

            composable(Route.BUDGETS) {
                BudgetsScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenUnknownSms = { navController.navigate(Route.UNKNOWN_SMS) },
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
                label = { tabLabels.getValue(it) },
                icon = { it.icon },
                selectedIcon = { it.selectedIcon },
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
        popUpTo(Destination.HOME.route) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
