package lk.salli.app.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Top-level tabs shown in the bottom navigation bar. */
enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    HOME(route = "home", label = "Home", icon = Icons.Outlined.Home),
    TIMELINE(route = "timeline", label = "Timeline", icon = Icons.Outlined.Receipt),
    INSIGHTS(route = "insights", label = "Insights", icon = Icons.Outlined.DonutLarge),
    BUDGETS(route = "budgets", label = "Budgets", icon = Icons.Outlined.Savings),
    SETTINGS(route = "settings", label = "Settings", icon = Icons.Outlined.Settings),
}

object Route {
    const val ONBOARDING = "onboarding"
    const val ONBOARDING_REPLAY = "onboarding-replay"

    const val UNKNOWN_SMS = "unknown-sms"
    const val BILLS = "bills"
    const val FUEL_PASS = "fuel-pass"
    const val RECURRING = "recurring"
    const val GOALS = "goals"
    const val SAFE_TO_SPEND = "safe-to-spend"

    /** Groups list. `tx` > 0 means "Split this" handed over a transaction to place in a group. */
    const val SPLIT_GROUPS = "split?tx={tx}"
    const val SPLIT_GROUP = "split/{groupId}?tx={tx}"
    fun splitGroups(pendingTransactionId: Long = -1L): String = "split?tx=$pendingTransactionId"
    fun splitGroup(groupId: Long, pendingTransactionId: Long = -1L): String = "split/$groupId?tx=$pendingTransactionId"
}
