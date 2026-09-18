package lk.salli.app.features.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import lk.salli.design.theme.SalliShapeTokens
import lk.salli.design.motion.LocalReducedMotion
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
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
import lk.salli.app.features.budgets.BudgetsViewModel
import lk.salli.app.features.budgets.BudgetUi
import lk.salli.app.R
import lk.salli.app.sms.refreshOutcome
import lk.salli.data.upcoming.UpcomingItem
import lk.salli.data.upcoming.UpcomingKind
import lk.salli.data.upcoming.UpcomingRoutes
import lk.salli.domain.home.HeadlineBill
import lk.salli.domain.home.HeadlineBudget
import lk.salli.domain.home.HeadlineKind
import lk.salli.domain.home.HomeHeadline
import lk.salli.domain.home.HomeHeadlineInput
import lk.salli.design.components.HeroCard
import lk.salli.design.components.BankAvatar
import lk.salli.design.components.EmptyState
import lk.salli.design.components.HeroEyebrow
import lk.salli.design.components.HeroFact
import lk.salli.design.components.HeroSplit
import lk.salli.design.components.GroupedList
import lk.salli.design.components.ListDivider
import lk.salli.design.components.ListRow
import lk.salli.design.components.PaceBar
import lk.salli.design.components.SalliTone
import lk.salli.design.components.SectionHeader
import lk.salli.design.components.stage.SpringOdometer
import lk.salli.design.theme.LocalSalliColors
import lk.salli.design.theme.SalliSpacing
import lk.salli.app.ui.TimelineItem
import lk.salli.app.ui.foldOwnTransfers
import androidx.compose.ui.platform.LocalContext
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
    onSeeAllPlan: () -> Unit = {},
    onOpenUpcoming: (String) -> Unit = {},
    onOpenBudgets: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    planningViewModel: SafeToSpendViewModel = hiltViewModel(),
    budgetsViewModel: BudgetsViewModel = hiltViewModel(),
) {
    val planning by planningViewModel.snapshot.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val refreshStatus by viewModel.refreshStatus.collectAsStateWithLifecycle()
    val upcoming by viewModel.upcoming.collectAsStateWithLifecycle()
    val budgets by budgetsViewModel.state.collectAsStateWithLifecycle()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val todayLabel = stringResource(R.string.home_today)
    val yesterdayLabel = stringResource(R.string.home_yesterday)
    val movedLabel = stringResource(R.string.home_moved_between_accounts)
    val resources = LocalContext.current.resources
    val grouped = remember(state.recent, todayLabel, yesterdayLabel, movedLabel) {
        groupByDay(state.recent.take(8), todayLabel, yesterdayLabel) { rows ->
            foldOwnTransfers(rows, movedLabel) { n -> resources.getQuantityString(R.plurals.home_moves, n, n) }
        }
    }
    val totalBalance = remember(state.accounts) { computeTotalBalance(state.accounts) }

    lk.salli.design.components.SalliPullToRefresh(
        isRefreshing = refreshing,
        onRefresh = { viewModel.refresh() },
        outcome = refreshOutcome(refreshStatus),
        onOutcomeConsumed = viewModel::consumeRefreshStatus,
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(
        contentPadding = PaddingValues(top = statusBar + 4.dp, bottom = 140.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            TopBar(
                daysLeft = planning?.safeToSpend?.daysLeft,
                onOpenSettings = onOpenSettings,
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
        if (!state.loaded) {
            item {
                Box(
                    Modifier.fillMaxWidth().height(180.dp)
                        .padding(horizontal = SalliSpacing.screenGutter)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                )
            }
        } else if (state.isEmpty) {
            item {
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    message = stringResource(R.string.home_empty_message),
                    modifier = Modifier.height(320.dp),
                )
            }
        } else {
        item {
            AccountStack(
                onAccountClick = onAccountClick,
                accounts = state.accounts,
                totalBalance = totalBalance,
                monthTrend = state.monthTrend,
                monthExpense = state.monthExpense,
                monthMoved = state.monthMoved,
                onOpenSafeToSpend = onOpenSafeToSpend,
                safeTodayMinor = planning?.safeToSpend?.perDayMinor?.takeIf { it > 0L },
                budgetMinor = planning?.safeToSpend?.budgetMinor,
                expectedProgress = planning?.let {
                    ((it.now - it.cycle.fromMillis).toFloat() /
                        (it.cycle.untilMillis - it.cycle.fromMillis).coerceAtLeast(1L)).coerceIn(0f, 1f)
                },
            )
        }
        if (upcoming.isNotEmpty()) {
            item { Spacer(Modifier.height(SalliSpacing.sectionGap)) }
            item {
                Column(Modifier.padding(horizontal = SalliSpacing.screenGutter)) {
                    SectionHeader(
                        title = stringResource(R.string.home_up_next),
                        actionLabel = stringResource(R.string.home_see_all),
                        onAction = onSeeAllPlan,
                    )
                    Spacer(Modifier.height(SalliSpacing.xs))
                    GroupedList {
                        upcoming.take(3).forEachIndexed { index, next ->
                            if (index > 0) ListDivider()
                            UpcomingRow(next, onClick = { onOpenUpcoming(next.deepLink) })
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(SalliSpacing.sectionGap)) }
        item {
            RightNowLine(
                upcoming = upcoming,
                budgets = budgets.budgets,
                safeTodayMinor = planning?.safeToSpend?.perDayMinor,
                currency = planning?.currency ?: state.monthExpense.currency,
                onOpenUpcoming = onOpenUpcoming,
                onOpenBudgets = onOpenBudgets,
                onOpenSafeToSpend = onOpenSafeToSpend,
            )
        }
        if (grouped.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.home_recent),
                    modifier = Modifier.padding(horizontal = SalliSpacing.screenGutter),
                    actionLabel = stringResource(R.string.home_see_all),
                    onAction = onSeeAllActivity,
                )
            }
            grouped.forEachIndexed { i, group ->
                item(key = "hdr-${group.dayMillis}") {
                    DayHeader(
                        label = group.label,
                        total = group.total,
                        topSpacing = if (i == 0) 8.dp else 18.dp,
                    )
                }
                item(key = "rows-${group.dayMillis}") {
                    GroupedList(Modifier.padding(horizontal = SalliSpacing.screenGutter)) {
                        group.rows.forEachIndexed { index, row ->
                            if (index > 0) ListDivider()
                            lk.salli.design.components.TransactionRow(
                                title = row.title,
                                subtitle = row.subtitle,
                                amount = row.amount,
                                flow = row.flow,
                                leadingIcon = row.icon,
                                merchantRaw = row.merchantRaw,
                                timestamp = row.timestamp,
                                isDeclined = row.isDeclined,
                                isOwnTransfer = row.isOwnTransfer,
                                pairSenders = if (row.isOwnTransfer) row.fromSender to row.toSender else null,
                                categoryIconName = row.categoryIconName,
                                categoryColorSeed = row.categoryColorSeed,
                                monogram = row.monogram,
                                accountSender = row.accountSender,
                                badge = row.badge?.let { lk.salli.design.components.TileBadge.valueOf(it.name) },
                                standalone = false,
                                // A folded "moved between your accounts" row opens Activity, where each move is listed.
                                modifier = Modifier.clickable { if (row.foldedMoves > 0) onSeeAllActivity() else onTransactionClick(row.id) },
                            )
                        }
                    }
                }
            }
        }
        }
    }
    }
}

/* -------------------------------------------------------------------------- */
/* Top bar                                                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun TopBar(
    daysLeft: Int?,
    onOpenSettings: () -> Unit,
) {
    val cal = Calendar.getInstance()
    val periodLabel = remember(cal.timeInMillis / (60 * 60 * 1000), daysLeft) {
        SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.time)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Text(
            text = if (daysLeft == null) periodLabel else stringResource(R.string.home_period_days_left, periodLabel, daysLeft),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
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
    monthMoved: Money,
    onOpenSafeToSpend: () -> Unit,
    safeTodayMinor: Long?,
    budgetMinor: Long?,
    expectedProgress: Float?,
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
        // One card, two faces: what left this period on the front, each bank's balance on
        // the back. The account chips used to sit under the card; they live on the back now.
        SummaryCard(
            totalBalance = totalBalance,
            monthTrend = monthTrend,
            monthExpense = monthExpense,
            monthMoved = monthMoved,
            onOpenSafeToSpend = onOpenSafeToSpend,
            safeTodayMinor = safeTodayMinor,
            budgetMinor = budgetMinor,
            expectedProgress = expectedProgress,
            hasBalance = accounts.any { it.balance != null },
            accounts = accounts,
            onAccountClick = onAccountClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The hero with two faces. Tap anywhere on it and it turns over on a spring: the front is
 * what left this period, the back is every bank's balance, full width, no truncation. Both
 * faces are composed so the card keeps the taller height and never jumps mid-turn.
 */
@Composable
private fun SummaryCard(
    totalBalance: Money,
    monthTrend: Trend?,
    monthExpense: Money,
    monthMoved: Money,
    onOpenSafeToSpend: () -> Unit,
    safeTodayMinor: Long?,
    budgetMinor: Long?,
    expectedProgress: Float?,
    hasBalance: Boolean,
    accounts: List<AccountSummary>,
    onAccountClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val canFlip = accounts.isNotEmpty()
    var flipped by remember { mutableStateOf(false) }
    val turn = remember { Animatable(0f) }
    LaunchedEffect(flipped, reduced) {
        val target = if (flipped) 180f else 0f
        if (reduced) turn.snapTo(target) else turn.animateTo(target, spring(dampingRatio = 0.72f, stiffness = 170f))
    }
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .height(IntrinsicSize.Max)
            .graphicsLayer {
                rotationY = turn.value
                cameraDistance = 16f * density.density
            }
            .clip(SalliShapeTokens.hero)
            .clickable(enabled = canFlip) { flipped = !flipped },
    ) {
        val showBack = turn.value > 90f
        Box(Modifier.fillMaxHeight().graphicsLayer { alpha = if (showBack) 0f else 1f }) {
            SummaryFront(
                totalBalance = totalBalance,
                monthTrend = monthTrend,
                monthExpense = monthExpense,
                monthMoved = monthMoved,
                onOpenSafeToSpend = onOpenSafeToSpend,
                safeTodayMinor = safeTodayMinor,
                budgetMinor = budgetMinor,
                expectedProgress = expectedProgress,
                hasBalance = hasBalance,
                canFlip = canFlip,
                enabled = !showBack,
            )
        }
        if (canFlip) {
            // Pre-turned by a half, so it reads the right way round once the card is over.
            Box(Modifier.fillMaxHeight().graphicsLayer { rotationY = 180f; alpha = if (showBack) 1f else 0f }) {
                SummaryBack(accounts = accounts, onAccountClick = onAccountClick, enabled = showBack)
            }
        }
    }
}

@Composable
private fun SummaryBack(
    accounts: List<AccountSummary>,
    onAccountClick: (Long) -> Unit,
    enabled: Boolean,
) {
    val onHero = LocalSalliColors.current.onHero
    HeroCard(modifier = Modifier.fillMaxHeight()) {
        HeroEyebrow(stringResource(R.string.home_balances_by_bank))
        Spacer(Modifier.height(SalliSpacing.xs))
        accounts.forEach { account ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // No pointer node at all while this face is hidden: an invisible clickable on
                // top of the front would swallow the tap that should turn the card.
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (enabled) Modifier.clickable { onAccountClick(account.id) } else Modifier)
                    .padding(vertical = 8.dp),
            ) {
                BankAvatar(sender = account.senderAddress, displayName = account.displayName, size = 32.dp)
                Spacer(Modifier.width(12.dp))
                // The display name already carries the account number; no second line.
                Text(
                    text = account.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = onHero,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                // Real balance where the bank ships one; otherwise the signed net we tracked.
                val display: Money? = account.balance ?: account.activityNet
                if (display != null) {
                    Text(
                        text = MoneyFormat.formatWithMinus(display),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = onHero,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f, fill = true))
        Spacer(Modifier.height(SalliSpacing.xs))
        Text(
            text = stringResource(R.string.home_flip_back),
            style = MaterialTheme.typography.labelSmall,
            color = onHero.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun SummaryFront(
    totalBalance: Money,
    monthTrend: Trend?,
    monthExpense: Money,
    monthMoved: Money,
    onOpenSafeToSpend: () -> Unit,
    safeTodayMinor: Long?,
    budgetMinor: Long?,
    expectedProgress: Float?,
    hasBalance: Boolean,
    canFlip: Boolean,
    enabled: Boolean,
) {
    val onHero = LocalSalliColors.current.onHero
    // Out = spent + moved. The big number is everything that left; the line under the bar
    // says how much of it bought something and how much just went somewhere.
    val hasMoved = monthMoved.minorUnits > 0L
    val monthOut = Money(monthExpense.minorUnits + monthMoved.minorUnits, monthExpense.currency)
    HeroCard(modifier = Modifier.fillMaxHeight()) {
        HeroEyebrow(stringResource(if (hasMoved) R.string.home_out_this_period else R.string.home_spent_this_period))
        Spacer(Modifier.height(SalliSpacing.xs))
        SpringOdometer(
            text = MoneyFormat.format(monthOut),
            style = MaterialTheme.typography.displayMedium,
            color = onHero,
        )
        if (hasMoved) {
            Spacer(Modifier.height(SalliSpacing.xs))
            Text(
                text = stringResource(
                    R.string.home_spent_moved_line,
                    MoneyFormat.short(monthExpense),
                    MoneyFormat.short(monthMoved),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = onHero.copy(alpha = 0.85f),
            )
        }
        if (budgetMinor != null && budgetMinor > 0L) {
            Spacer(Modifier.height(SalliSpacing.md))
            PaceBar(
                progress = monthExpense.minorUnits.toFloat() / budgetMinor,
                expected = expectedProgress,
                tone = when {
                    monthExpense.minorUnits > budgetMinor -> SalliTone.NEGATIVE
                    expectedProgress != null && monthExpense.minorUnits.toFloat() / budgetMinor > expectedProgress -> SalliTone.WARNING
                    else -> SalliTone.POSITIVE
                },
                trackColor = onHero.copy(alpha = 0.2f),
                tickColor = onHero,
            )
        }
        monthTrend?.percentDelta?.let { delta ->
            Spacer(Modifier.height(SalliSpacing.xs))
            Text(
                text = stringResource(R.string.home_vs_last_period, if (delta > 0) "+$delta" else delta.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = onHero.copy(alpha = 0.8f),
            )
        }
        if (safeTodayMinor != null || hasBalance) {
            HeroSplit {
                if (safeTodayMinor != null) {
                    HeroFact(
                        label = stringResource(R.string.home_safe_today),
                        value = MoneyFormat.formatMinor(safeTodayMinor, monthExpense.currency),
                        valueColor = LocalSalliColors.current.positive,
                        modifier = Modifier.weight(1f).then(if (enabled) Modifier.clickable(onClick = onOpenSafeToSpend) else Modifier),
                    )
                }
                if (hasBalance) {
                    HeroFact(
                        label = stringResource(R.string.home_balance),
                        value = MoneyFormat.formatWithMinus(totalBalance),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (canFlip) {
            Spacer(Modifier.weight(1f, fill = true))
            Spacer(Modifier.height(SalliSpacing.xs))
            Text(
                text = stringResource(R.string.home_flip_front),
                style = MaterialTheme.typography.labelSmall,
                color = onHero.copy(alpha = 0.7f),
            )
        }
    }
}

/** Sum balances across accounts sharing the dominant currency. Mixed-currency accounts
 *  are rare in Salli (LKR-first) so we just pick the most common currency and report that. */
private fun computeTotalBalance(accounts: List<AccountSummary>): Money {
    val byCurrency = accounts.mapNotNull { it.balance }.groupBy { it.currency }
    val dominant = byCurrency.entries.maxByOrNull { it.value.size } ?: return Money.zero(Currency.LKR)
    return dominant.value.fold(Money.zero(dominant.key)) { acc, m -> acc + m }
}

@Composable
private fun UpcomingRow(item: UpcomingItem, onClick: () -> Unit) {
    val today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Colombo")).toEpochDay()
    val days = item.dueEpochDay - today
    val subtitle = when (item.kind) {
        UpcomingKind.BILL -> when {
            days < 0 -> stringResource(R.string.home_overdue)
            days == 0L -> stringResource(R.string.home_due_today)
            days == 1L -> stringResource(R.string.home_due_tomorrow)
            else -> stringResource(R.string.home_due_in_days, days)
        }
        UpcomingKind.RECURRING -> when (days) {
            0L -> stringResource(R.string.home_expected_today)
            1L -> stringResource(R.string.home_expected_tomorrow)
            else -> stringResource(R.string.home_expected_in_days, days)
        }
        UpcomingKind.FUEL_ELIGIBLE -> when (days) {
            0L -> stringResource(R.string.home_fuel_eligible_today)
            1L -> stringResource(R.string.home_fuel_eligible_tomorrow)
            else -> stringResource(R.string.home_fuel_eligible_in_days, days)
        }
        UpcomingKind.FUEL_RESET -> when (days) {
            0L -> stringResource(R.string.home_fuel_reset_today)
            1L -> stringResource(R.string.home_fuel_reset_tomorrow)
            else -> stringResource(R.string.home_fuel_reset_in_days, days)
        }
    }
    ListRow(
        title = item.title,
        subtitle = subtitle,
        trailing = item.amountMinor?.let { amount ->
            { Text(MoneyFormat.formatMinor(amount, item.currency ?: Currency.LKR)) }
        },
        onClick = onClick,
    )
}

@Composable
private fun RightNowLine(
    upcoming: List<UpcomingItem>,
    budgets: List<BudgetUi>,
    safeTodayMinor: Long?,
    currency: String,
    onOpenUpcoming: (String) -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenSafeToSpend: () -> Unit,
) {
    val today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Colombo")).toEpochDay()
    val bills = upcoming.filter { it.kind == UpcomingKind.BILL }.map {
        HeadlineBill(it.title, it.amountMinor ?: 0L, it.currency ?: currency, it.dueEpochDay)
    }
    val headline = HomeHeadline.of(HomeHeadlineInput(
        overdueBills = bills.filter { it.dueEpochDay < today },
        billsDueToday = bills.filter { it.dueEpochDay == today },
        budgetsOver = budgets.filter { it.overBudget }.map {
            HeadlineBudget(it.name, -it.remainingMinor, it.currency)
        },
        unknownSmsCount = 0,
        safeToSpendTodayMinor = safeTodayMinor,
        currency = currency,
    )) ?: return
    val text = when (headline.kind) {
        HeadlineKind.BILL_OVERDUE -> stringResource(R.string.home_headline_overdue, headline.params.label.orEmpty())
        HeadlineKind.BILL_DUE_TODAY -> stringResource(R.string.home_headline_due_today, headline.params.label.orEmpty())
        HeadlineKind.BUDGET_OVER -> stringResource(R.string.home_headline_budget_over, headline.params.label.orEmpty())
        HeadlineKind.UNKNOWN_SMS -> return
        HeadlineKind.SAFE_TO_SPEND -> stringResource(
            R.string.home_headline_safe,
            MoneyFormat.formatMinor(headline.params.amountMinor ?: 0L, currency),
        )
        HeadlineKind.NEW_SINCE_LAST_OPEN -> return
    }
    val action = when (headline.kind) {
        HeadlineKind.BILL_OVERDUE, HeadlineKind.BILL_DUE_TODAY -> { { onOpenUpcoming(UpcomingRoutes.BILLS) } }
        HeadlineKind.UNKNOWN_SMS -> return
        HeadlineKind.BUDGET_OVER -> onOpenBudgets
        else -> onOpenSafeToSpend
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = SalliSpacing.screenGutter).clickable(onClick = action),
    )
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

private fun groupByDay(
    rows: List<TimelineItem>,
    todayLabel: String,
    yesterdayLabel: String,
    fold: (List<TimelineItem>) -> List<TimelineItem> = { it },
): List<DayGroup> {
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
                todayMs -> todayLabel
                todayMs - dayMs -> yesterdayLabel
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
                rows = fold(list),
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
        Text(
            text = MoneyFormat.format(total, signed = true),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
