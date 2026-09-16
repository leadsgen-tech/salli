package lk.salli.app.nav

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.ui.graphics.vector.ImageVector
import lk.salli.app.R

/**
 * The four tabs. One question each:
 *
 *   Home     — how am I doing right now?
 *   Activity — what happened?
 *   Plan     — what's coming, what do I owe, what am I saving?
 *   Insights — where does it go?
 *
 * Settings and Budgets used to be tabs. Settings isn't a place you live, it's a place you
 * visit, so it moved behind the gear on Home; Budgets is one of several things you plan, so it
 * moved inside Plan alongside Bills, Recurring, Goals, Shared expenses and Fuel Pass. Four is
 * the ceiling — a fifth tab means the information architecture lost an argument.
 *
 * [route] is what you navigate to; [pattern] is what Navigation reports back as the current
 * destination. They differ for Activity, which accepts optional filter arguments, so tab
 * selection and the "does this screen show the nav bar" test both match on [pattern].
 */
enum class Destination(
    val route: String,
    val pattern: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME(
        route = Route.HOME,
        pattern = Route.HOME,
        labelRes = R.string.nav_home,
        icon = Icons.Outlined.Home,
        selectedIcon = Icons.Filled.Home,
    ),

    // The route string stays "timeline" even though the label is now "Activity": it is baked
    // into saved back-stack state on installed builds, and renaming a route to match a renamed
    // label buys nothing.
    ACTIVITY(
        route = Route.ACTIVITY,
        pattern = Route.ACTIVITY_PATTERN,
        labelRes = R.string.nav_activity,
        icon = Icons.Outlined.Receipt,
        selectedIcon = Icons.Filled.Receipt,
    ),

    PLAN(
        route = Route.PLAN,
        pattern = Route.PLAN,
        labelRes = R.string.nav_plan,
        icon = Icons.AutoMirrored.Outlined.EventNote,
        selectedIcon = Icons.AutoMirrored.Filled.EventNote,
    ),

    INSIGHTS(
        route = Route.INSIGHTS,
        pattern = Route.INSIGHTS,
        labelRes = R.string.nav_insights,
        icon = Icons.Outlined.Insights,
        selectedIcon = Icons.Filled.Insights,
    ),
}

/**
 * Every route in the graph. Anything not named by [Destination] is a full-screen push with its
 * own back affordance and no bottom nav.
 */
object Route {
    const val HOME = "home"
    const val ACTIVITY = "timeline"
    const val PLAN = "plan"
    const val INSIGHTS = "insights"

    const val SETTINGS = "settings"
    const val BUDGETS = "budgets"

    const val ONBOARDING = "onboarding"
    const val ONBOARDING_REPLAY = "onboarding-replay"

    const val UNKNOWN_SMS = "unknown-sms"
    const val TRANSACTION_DETAIL = "transaction/{txId}"
    fun transactionDetail(id: Long): String = "transaction/$id"
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

    // --- Activity filter arguments --------------------------------------------------------
    // Home (tap an account), Insights (tap a category or a merchant) and Plan all want to open
    // Activity already filtered. The arguments are declared and parsed now so the deep links
    // exist and are testable; the screen ignores them until the Activity rewrite consumes them.
    // Adding them later would mean changing a route string that is already in saved back-stack
    // state, which is the expensive half of this change.
    const val ARG_ACCOUNT = "account"
    const val ARG_CATEGORY = "category"
    const val ARG_QUERY = "q"

    const val ACTIVITY_PATTERN =
        "$ACTIVITY?$ARG_ACCOUNT={$ARG_ACCOUNT}&$ARG_CATEGORY={$ARG_CATEGORY}&$ARG_QUERY={$ARG_QUERY}"

    /**
     * Builds an Activity route carrying only the filters that are actually set, so navigating
     * with no filters produces the bare `timeline` route rather than a second, distinct entry
     * that the tab bar would fail to recognise as the same destination.
     */
    fun activity(
        accountId: Long? = null,
        categoryId: Long? = null,
        query: String? = null,
    ): String {
        val params = buildList {
            accountId?.let { add("$ARG_ACCOUNT=$it") }
            categoryId?.let { add("$ARG_CATEGORY=$it") }
            query?.takeIf { it.isNotBlank() }?.let { add("$ARG_QUERY=${encodeQuery(it)}") }
        }
        return if (params.isEmpty()) ACTIVITY else "$ACTIVITY?${params.joinToString("&")}"
    }

    /**
     * Percent-encodes a search term for the route string.
     *
     * `URLEncoder` rather than `android.net.Uri.encode` so this stays testable on the JVM — and
     * the `+` fix-up matters: `URLEncoder` writes a space as `+` (that is
     * `application/x-www-form-urlencoded`, not a URI), while Navigation decodes with
     * `Uri.decode`, which leaves `+` alone. A search for "keells super" would arrive as
     * "keells+super" and match nothing.
     */
    private fun encodeQuery(raw: String): String =
        java.net.URLEncoder.encode(raw, "UTF-8").replace("+", "%20")
}

/** The filters an Activity deep link carries. All null when the tab was opened from the nav bar. */
data class ActivityFilterArgs(
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val query: String? = null,
) {
    val isEmpty: Boolean get() = accountId == null && categoryId == null && query.isNullOrBlank()

    companion object {
        val NONE = ActivityFilterArgs()

        /**
         * IDs travel as strings because a nullable `NavType.LongType` argument has no null
         * representation — Navigation substitutes `0L`, which is a *valid-looking* row id and
         * would silently filter the list down to nothing. Parsing leniently here means a
         * hand-typed or corrupted deep link degrades to "no filter" rather than to "empty list
         * with no explanation".
         */
        fun from(account: String?, category: String?, query: String?): ActivityFilterArgs =
            ActivityFilterArgs(
                accountId = account?.toLongOrNull(),
                categoryId = category?.toLongOrNull(),
                query = query?.takeIf { it.isNotBlank() },
            )
    }
}

/** Reads the Activity filter arguments out of a nav back-stack entry's bundle. */
fun Bundle?.toActivityFilterArgs(): ActivityFilterArgs = ActivityFilterArgs.from(
    account = this?.getString(Route.ARG_ACCOUNT),
    category = this?.getString(Route.ARG_CATEGORY),
    query = this?.getString(Route.ARG_QUERY),
)
