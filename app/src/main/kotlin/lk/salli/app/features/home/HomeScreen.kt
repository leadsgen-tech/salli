package lk.salli.app.features.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import lk.salli.app.features.planning.SafeToSpendCard
import lk.salli.app.features.planning.SafeToSpendViewModel
import lk.salli.app.R
import lk.salli.app.ui.TimelineItem
import lk.salli.design.components.SalliIconButton
import lk.salli.domain.money.MoneyFormat
import lk.salli.design.theme.BankBrand
import lk.salli.design.theme.SalliBrandColors
import lk.salli.domain.Currency
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow

/**
 * Home — a concise financial dashboard.
 *
 * The whole screen boils down to: who are you, how much did you spend this month, what does
 * the shape of that spending look like, and what were the last few transactions. Everything
 * else (accounts, budgets, insights) lives one tap away in its own tab.
 */
@Composable
fun HomeScreen(
    onTransactionClick: (Long) -> Unit = {},
    onSeeAllActivity: () -> Unit = {},
    onOpenSafeToSpend: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onAccountClick: (Long) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    planningViewModel: SafeToSpendViewModel = hiltViewModel(),
) {
    val planning by planningViewModel.snapshot.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val grouped = remember(state.recent) { groupByDay(state.recent) }
    val totalBalance = remember(state.accounts) { computeTotalBalance(state.accounts) }

    lk.salli.design.components.SalliPullToRefresh(
        isRefreshing = refreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(
        contentPadding = PaddingValues(top = statusBar + 4.dp, bottom = 140.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            TopBar(
                userName = state.userName,
                onOpenSettings = onOpenSettings,
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
        item {
            AccountStack(
                onAccountClick = onAccountClick,
                accounts = state.accounts,
                totalBalance = totalBalance,
                monthTrend = state.monthTrend,
                monthExpense = state.monthExpense,
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
        planning?.let { snap ->
            item(key = "safe-to-spend") { SafeToSpendCard(snapshot = snap, onClick = onOpenSafeToSpend) }
        }
        item { Spacer(Modifier.height(20.dp)) }
        if (grouped.isNotEmpty()) {
            grouped.forEachIndexed { i, group ->
                item(key = "hdr-${group.dayMillis}") {
                    DayHeader(
                        label = group.label,
                        total = group.total,
                        topSpacing = if (i == 0) 8.dp else 18.dp,
                    )
                }
                items(group.rows, key = { it.id }) { row ->
                    lk.salli.design.components.TransactionRow(
                        title = row.title,
                        subtitle = row.subtitle,
                        amount = row.amount,
                        flow = row.flow,
                        leadingIcon = row.icon,
                        merchantRaw = row.merchantRaw,
                        isDeclined = row.isDeclined,
                        isOwnTransfer = row.isOwnTransfer,
                        modifier = Modifier.clickable { onTransactionClick(row.id) },
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item { SeeAllRow(onClick = onSeeAllActivity) }
        }
    }
    }
}

/* -------------------------------------------------------------------------- */
/* Top bar                                                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun TopBar(
    userName: String,
    onOpenSettings: () -> Unit,
) {
    val cal = Calendar.getInstance()
    val dateLabel = remember(cal.timeInMillis / (60 * 60 * 1000)) {
        SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(cal.time)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = userName.ifBlank { "Welcome" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The theme toggle used to sit here. Appearance is a setting you change twice a year,
        // not twice a day, so it moved into Settings (which is what this gear opens) and took
        // the circular-reveal transition with it.
        SalliIconButton(
            icon = Icons.Outlined.Settings,
            contentDescription = stringResource(R.string.action_settings),
            onClick = onOpenSettings,
        )
    }
}

/* -------------------------------------------------------------------------- */
/* Stacked accounts + summary card                                            */
/* -------------------------------------------------------------------------- */

@Composable
private fun AccountStack(
    onAccountClick: (Long) -> Unit,
    accounts: List<AccountSummary>,
    totalBalance: Money,
    monthTrend: Trend?,
    monthExpense: Money,
) {
    // Show every account that the parser has seen at least one transaction for. Accounts that
    // never carry a balance in their SMS (ComBank card-level, HSBC card, etc.) used to be
    // filtered out completely, which made entire banks vanish from Home; the chip itself
    // gracefully renders without a balance line.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryCard(
            totalBalance = totalBalance,
            monthTrend = monthTrend,
            monthExpense = monthExpense,
            modifier = Modifier.fillMaxWidth(),
        )
        if (accounts.isNotEmpty()) {
            AccountChipsRow(accounts = accounts, onAccountClick = onAccountClick)
        }
    }
}

/**
 * Horizontal strip of account chips that adapts to count:
 *  - 1–3 accounts → each takes equal weight, fills the row
 *  - 4+          → horizontal scroller with fixed-width chips
 *
 * A bank-colour edge keeps account identity visible without turning the whole card into an
 * inaccessible brand-colour surface.
 */
@Composable
private fun AccountChipsRow(accounts: List<AccountSummary>, onAccountClick: (Long) -> Unit) {
    if (accounts.size <= 3) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            accounts.forEach { a ->
                AccountChip(
                    account = a,
                    color = BankBrand.forSender(a.senderAddress).secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } else {
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(accounts, key = { it.id }) { a ->
                AccountChip(
                    account = a,
                    color = BankBrand.forSender(a.senderAddress).secondary,
                    modifier = Modifier.width(160.dp),
                )
            }
        }
    }
}

@Composable
private fun AccountChip(
    account: AccountSummary,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(color))
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = account.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
        // Single line, same style for every account. Real balances come from BOC's
        // `Av_Bal` / People's Bank's anchor-plus-delta imputation; for senders that never
        // ship a balance (ComBank cards, the Q+ account) we fall back to the signed net of
        // tracked activity. A card with only outflows reads as `−Rs 45,385.00`, an account
        // with mixed flow as `Rs 14,153.28` — same visual, no extra labels.
            val display: Money? = account.balance ?: account.activityNet
            if (display != null) {
                Text(
                    text = MoneyFormat.formatWithMinus(display),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    totalBalance: Money,
    monthTrend: Trend?,
    monthExpense: Money,
    modifier: Modifier = Modifier,
) {
    val cardBg = SalliBrandColors.Cobalt
    val cardFg = SalliBrandColors.OnCobalt
    val cardFgMuted = cardFg.copy(alpha = 0.76f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(cardBg)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Total balance",
            style = MaterialTheme.typography.labelLarge,
            color = cardFgMuted,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = MoneyFormat.formatWithMinus(totalBalance),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = cardFg,
            maxLines = 1,
        )
        Spacer(Modifier.height(10.dp))
        MonthDeltaRow(
            monthTrend = monthTrend,
            monthExpense = monthExpense,
            fgColor = cardFg,
            mutedFgColor = cardFgMuted,
        )
    }
}

@Composable
private fun MonthDeltaRow(
    monthTrend: Trend?,
    monthExpense: Money,
    fgColor: Color,
    mutedFgColor: Color,
) {
    val delta = monthTrend?.percentDelta
    val isUp = monthTrend?.isUp ?: false
    val arrowBg = if (delta != null && !isUp) {
        SalliBrandColors.AcidLime
    } else {
        SalliBrandColors.OnCobalt.copy(alpha = 0.18f)
    }
    val arrowFg = if (delta != null && !isUp) {
        SalliBrandColors.OnAcidLime
    } else {
        SalliBrandColors.OnCobalt
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (delta != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(arrowBg),
                ) {
                    Text(
                        text = if (isUp) "↑" else "↓",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = arrowFg,
                    )
                }
                Text(
                    text = "${if (isUp) "+" else "-"}${kotlin.math.abs(delta)}% vs previous period",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = fgColor,
                )
            }
        }
        Text(
            text = "${MoneyFormat.formatWithMinus(monthExpense)} spent this period",
            style = MaterialTheme.typography.bodyMedium,
            color = mutedFgColor,
        )
    }
}

/** Sum balances across accounts sharing the dominant currency. Mixed-currency accounts
 *  are rare in Salli (LKR-first) so we just pick the most common currency and report that. */
private fun computeTotalBalance(accounts: List<AccountSummary>): Money {
    val byCurrency = accounts.mapNotNull { it.balance }.groupBy { it.currency }
    val dominant = byCurrency.entries.maxByOrNull { it.value.size } ?: return Money.zero(Currency.LKR)
    return dominant.value.fold(Money.zero(dominant.key)) { acc, m -> acc + m }
}

/* -------------------------------------------------------------------------- */
/* Transaction list                                                           */
/* -------------------------------------------------------------------------- */

private data class DayGroup(
    val label: String,
    val dayMillis: Long,
    val rows: List<TimelineItem>,
    val total: Money,
)

private fun groupByDay(rows: List<TimelineItem>): List<DayGroup> {
    val bucketed = rows.groupBy { row ->
        val c = Calendar.getInstance().apply {
            timeInMillis = row.timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        c.timeInMillis
    }
    val todayMs = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val dayMs = 24L * 60 * 60 * 1000
    val headerFmt = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
    return bucketed.entries
        .sortedByDescending { it.key }
        .map { (bucket, list) ->
            val label = when (bucket) {
                todayMs -> "Today"
                todayMs - dayMs -> "Yesterday"
                else -> headerFmt.format(Date(bucket))
            }
            // Net: expense-negative, income-positive, transfers excluded. Currency is the
            // first row's — rows with mixed currencies are rare enough to ignore.
            val currency = list.firstOrNull()?.amount?.currency ?: Currency.LKR
            val net = list.fold(0L) { acc, row ->
                if (row.isDeclined) return@fold acc
                if (row.amount.currency != currency) return@fold acc
                when (row.flow) {
                    TransactionFlow.INCOME -> acc + row.amount.minorUnits
                    TransactionFlow.EXPENSE -> acc - row.amount.minorUnits
                    else -> acc
                }
            }
            DayGroup(
                label = label,
                dayMillis = bucket,
                rows = list,
                total = Money(net, currency),
            )
        }
}

@Composable
private fun DayHeader(label: String, total: Money, topSpacing: androidx.compose.ui.unit.Dp) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 22.dp,
                end = 22.dp,
                top = topSpacing,
                bottom = 8.dp,
            ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val absTotal = Money(kotlin.math.abs(total.minorUnits), total.currency)
        val sign = if (total.minorUnits < 0) "-" else if (total.minorUnits > 0) "+" else ""
        Text(
            text = "$sign${MoneyFormat.formatWithMinus(absTotal)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


@Composable
private fun SeeAllRow(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "See all activity",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
