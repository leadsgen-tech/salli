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
    const val TRANSACTION_DETAIL = "tx/{id}"
    fun transactionDetail(id: Long): String = "tx/$id"

    const val CHAT = "chat"
    const val UNKNOWN_SMS = "unknown-sms"
}
