package lk.salli.app.nav

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import lk.salli.app.features.budgets.BudgetsScreen
import lk.salli.app.features.chat.ChatScreen
import lk.salli.app.features.home.HomeScreen
import lk.salli.app.features.insights.InsightsScreen
import lk.salli.app.features.onboarding.OnboardingScreen
import lk.salli.app.features.settings.SettingsScreen
import lk.salli.app.features.timeline.TimelineScreen
import lk.salli.app.features.txdetail.TransactionDetailSheet
import lk.salli.app.features.unknown.UnknownSmsScreen
import lk.salli.design.components.FloatingNavBar
import lk.salli.design.components.ThemeTransitionLayer

private val navRoutes: Set<String> = Destination.entries.mapTo(HashSet()) { it.route }

@Composable
fun SalliNavHost(
    startDestination: String,
    navController: NavHostController = rememberNavController(),
) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomNav = currentRoute in navRoutes
    val currentDest = Destination.entries.firstOrNull { it.route == currentRoute }

    // The haze source — every pixel rendered inside the NavHost feeds the blur behind the pill.
    val hazeState = remember { HazeState() }

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
            modifier = Modifier
                .fillMaxSize()
                .haze(hazeState),
        ) {
            composable(Route.ONBOARDING) {
                OnboardingScreen(
                    onDone = {
                        navController.navigate(Destination.HOME.route) {
                            popUpTo(Route.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Destination.HOME.route) {
                HomeScreen(
                    onSeeAllActivity = { navController.navigateToTab(Destination.TIMELINE) },
                    onOpenChat = { navController.navigate(Route.CHAT) },
                    onTransactionClick = { id ->
                        navController.navigate(Route.transactionDetail(id))
                    },
                )
            }
            composable(Destination.TIMELINE.route) {
                TimelineScreen(
                    onTransactionClick = { id ->
                        navController.navigate(Route.transactionDetail(id))
                    },
                )
            }
            composable(Destination.INSIGHTS.route) { InsightsScreen() }
            composable(Destination.BUDGETS.route) { BudgetsScreen() }
            composable(Destination.SETTINGS.route) {
                SettingsScreen(
                    onOpenUnknownSms = { navController.navigate(Route.UNKNOWN_SMS) },
                )
            }

            composable(Route.CHAT) {
                ChatScreen(onBack = { navController.popBackStack() })
            }

            composable(Route.UNKNOWN_SMS) {
                UnknownSmsScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Route.TRANSACTION_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) {
                TransactionDetailSheet(onDismiss = { navController.popBackStack() })
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
                label = { it.label },
                icon = { it.icon },
                key = { it.route },
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
    }
}

private fun NavHostController.navigateToTab(dest: Destination) {
    navigate(dest.route) {
        popUpTo(Destination.HOME.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
