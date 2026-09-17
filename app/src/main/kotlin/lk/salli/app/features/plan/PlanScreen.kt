package lk.salli.app.features.plan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lk.salli.app.R
import lk.salli.app.sms.refreshOutcome
import lk.salli.app.features.budgets.BudgetPace
import lk.salli.app.features.budgets.BudgetUi
import lk.salli.app.features.budgets.BudgetsViewModel
import lk.salli.app.features.goals.GoalsViewModel
import lk.salli.app.features.goals.GoalRow
import lk.salli.data.upcoming.UpcomingItem
import lk.salli.data.upcoming.UpcomingKind
import lk.salli.data.upcoming.UpcomingTone
import lk.salli.design.components.HeroCard
import lk.salli.design.components.HeroEyebrow
import lk.salli.design.components.HeroFact
import lk.salli.design.components.HeroSplit
import lk.salli.design.components.RingProgress
import lk.salli.design.components.stage.SpringOdometer
import lk.salli.design.theme.LocalSalliColors
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import lk.salli.domain.planning.CommitmentKind
import lk.salli.design.components.GroupedList
import lk.salli.design.components.CategoryIcon
import lk.salli.design.components.ListDivider
import lk.salli.design.components.ListRow
import lk.salli.design.components.PaceBar
import lk.salli.design.components.SalliTone
import lk.salli.design.components.SectionHeader
import lk.salli.design.components.StatusPill
import lk.salli.design.theme.SalliSpacing
import lk.salli.domain.Money
import lk.salli.domain.money.MoneyFormat

/**
 * Plan — what's coming, what I owe, what I'm saving.
 *
 * Two things live here today, and both are real:
 *
 *  - **Budgets**, rendered inline with their live pace, because a tab that is only a menu is a
 *    tab that wastes a tap. Budgets stopped being a top-level tab (you check "how am I doing"
 *    constantly; you set a cap occasionally), so Plan is what keeps it one tap away.
 *  - **More**, the five rows promoted out of the Settings "Trackers" section — Bills,
 *    Recurring, Goals, Shared expenses, Fuel Pass — with the same live subtitles and badges
 *    they had there. Settings now holds settings; a bill due on Thursday is not a setting.
 *
 * The rest of §7 (the "spoken for this period" hero, the 14-day ribbon, inline Goals cards)
 * belongs to the full Plan phase. Nothing here is a placeholder: every row
 * is backed by a query, and a section with nothing to say would be absent rather than empty.
 *
 * Budget pace comes straight off [BudgetsViewModel] rather than being recomputed, so Plan and
 * the Budgets screen can never disagree about whether you are over.
 */
@Composable
fun PlanScreen(
    onOpenBudgets: () -> Unit = {},
    onOpenBills: () -> Unit = {},
    onOpenRecurring: () -> Unit = {},
    onOpenGoals: () -> Unit = {},
    onOpenSplit: () -> Unit = {},
    onOpenFuelPass: () -> Unit = {},
    onOpenSafeToSpend: () -> Unit = {},
    viewModel: PlanViewModel = hiltViewModel(),
    budgetsViewModel: BudgetsViewModel = hiltViewModel(),
    goalsViewModel: GoalsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val budgets by budgetsViewModel.state.collectAsStateWithLifecycle()
    val goals by goalsViewModel.state.collectAsStateWithLifecycle()
    val upcoming by viewModel.upcoming.collectAsStateWithLifecycle()
    val planning by viewModel.planningSnapshot.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val refreshStatus by viewModel.refreshStatus.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now(ZoneId.of("Asia/Colombo")).toEpochDay() }
    val bringIntoView = remember(upcoming) {
        upcoming.map { it.dueEpochDay }.distinct().associateWith { BringIntoViewRequester() }
    }
    val scope = rememberCoroutineScope()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    lk.salli.design.components.SalliPullToRefresh(
        isRefreshing = refreshing,
        onRefresh = viewModel::refresh,
        outcome = refreshOutcome(refreshStatus),
        onOutcomeConsumed = viewModel::consumeRefreshStatus,
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = SalliSpacing.screenGutter,
            end = SalliSpacing.screenGutter,
            top = statusBar + SalliSpacing.xs,
            bottom = 140.dp,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = stringResource(R.string.plan_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = SalliSpacing.xs),
            )
        }

        item { Spacer(Modifier.height(SalliSpacing.sm)) }
        planning?.let { snapshot ->
            item {
                val safe = snapshot.safeToSpend
                val spokenFor = safe.commitments
                    .filter { it.kind == CommitmentKind.BILL || it.kind == CommitmentKind.RECURRING }
                    .sumOf { it.amountMinor }
                HeroCard(modifier = Modifier.clickable(onClick = onOpenSafeToSpend)) {
                    HeroEyebrow(stringResource(R.string.plan_safe_today))
                    Spacer(Modifier.height(SalliSpacing.xs))
                    SpringOdometer(
                        text = safe.perDayMinor?.let { MoneyFormat.formatMinor(it, snapshot.currency) }
                            ?: stringResource(R.string.plan_safe_today_unavailable),
                        style = MaterialTheme.typography.displayMedium,
                        color = LocalSalliColors.current.onHero,
                    )
                    if (safe.leftMinor != null || spokenFor > 0L) {
                        HeroSplit {
                            safe.leftMinor?.let { left ->
                                HeroFact(
                                    label = stringResource(R.string.plan_left_this_period),
                                    value = MoneyFormat.formatMinor(left, snapshot.currency),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (spokenFor > 0L) {
                                HeroFact(
                                    label = stringResource(R.string.plan_committed),
                                    value = MoneyFormat.formatMinor(spokenFor, snapshot.currency),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    upcoming.firstOrNull()?.let { next ->
                        Spacer(Modifier.height(SalliSpacing.xs))
                        Text(
                            text = stringResource(R.string.plan_next_due, next.title),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalSalliColors.current.onHero.copy(alpha = 0.82f),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(SalliSpacing.sectionGap)) }
        }
        item {
            SectionHeader(
                title = stringResource(R.string.plan_budgets_header),
                actionLabel = stringResource(R.string.plan_budgets_manage),
                onAction = onOpenBudgets,
            )
        }
        item { Spacer(Modifier.height(SalliSpacing.xs)) }
        item {
            GroupedList {
                if (budgets.budgets.isEmpty()) {
                    ListRow(
                        title = stringResource(R.string.plan_budgets_empty_title),
                        subtitle = stringResource(R.string.plan_budgets_empty_subtitle),
                        onClick = onOpenBudgets,
                    )
                } else {
                    budgets.budgets.forEachIndexed { index, budget ->
                        if (index > 0) ListDivider()
                        BudgetSummaryRow(budget = budget, onClick = onOpenBudgets)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(SalliSpacing.sectionGap)) }

        if (upcoming.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.plan_up_next)) }
            item { Spacer(Modifier.height(SalliSpacing.xs)) }
            item {
                PlanRibbon(today = today, upcoming = upcoming) { day ->
                    scope.launch { bringIntoView[day]?.bringIntoView() }
                }
            }
            item { Spacer(Modifier.height(SalliSpacing.sm)) }
            item {
                GroupedList {
                    upcoming.forEachIndexed { index, item ->
                        if (index > 0) ListDivider()
                        PlanUpcomingRow(
                            item = item,
                            modifier = Modifier.bringIntoViewRequester(bringIntoView.getValue(item.dueEpochDay)),
                            onOpenBills = onOpenBills,
                            onOpenRecurring = onOpenRecurring,
                            onOpenFuelPass = onOpenFuelPass,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(SalliSpacing.sectionGap)) }
        }
        item {
            SectionHeader(
                title = stringResource(R.string.plan_goals_title),
                actionLabel = stringResource(R.string.plan_goals_all),
                onAction = onOpenGoals,
            )
        }
        val liveGoals = goals.goals.filter { !it.isArchived }
        if (!goals.loading && liveGoals.isEmpty()) {
            item { Spacer(Modifier.height(SalliSpacing.xs)) }
            item {
                GroupedList {
                    ListRow(
                        title = stringResource(R.string.plan_goals_title),
                        subtitle = stringResource(R.string.plan_goals_empty),
                        onClick = onOpenGoals,
                    )
                }
            }
        } else {
            item { Spacer(Modifier.height(SalliSpacing.xs)) }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SalliSpacing.sm),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    liveGoals.forEach { goal -> GoalPreview(goal, onOpenGoals) }
                }
            }
        }

        item { Spacer(Modifier.height(SalliSpacing.sectionGap)) }
        item { SectionHeader(title = stringResource(R.string.plan_more_header)) }
        item { Spacer(Modifier.height(SalliSpacing.xs)) }
        item {
            GroupedList {
                TrackerRow(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = stringResource(R.string.plan_bills_title),
                    subtitle = if (state.openBillCount == 0) {
                        stringResource(R.string.plan_bills_empty)
                    } else {
                        pluralStringResource(
                            R.plurals.plan_bills_open,
                            state.openBillCount,
                            state.openBillCount,
                        )
                    },
                    badge = state.openBillCount.takeIf { it > 0 },
                    onClick = onOpenBills,
                )
                ListDivider()
                TrackerRow(
                    icon = Icons.Outlined.Autorenew,
                    title = stringResource(R.string.plan_recurring_title),
                    subtitle = when {
                        state.recurringCount == 0 -> stringResource(R.string.plan_recurring_empty)
                        state.recurringNeedsAttention > 0 -> stringResource(
                            R.string.plan_recurring_found_attention,
                            state.recurringCount,
                            state.recurringNeedsAttention,
                        )
                        else -> stringResource(R.string.plan_recurring_found, state.recurringCount)
                    },
                    badge = state.recurringNeedsAttention.takeIf { it > 0 },
                    onClick = onOpenRecurring,
                )
                ListDivider()
                TrackerRow(
                    icon = Icons.Outlined.Groups,
                    title = stringResource(R.string.plan_split_title),
                    subtitle = stringResource(R.string.plan_split_subtitle),
                    onClick = onOpenSplit,
                )
                ListDivider()
                TrackerRow(
                    icon = Icons.Outlined.LocalGasStation,
                    title = stringResource(R.string.plan_fuel_title),
                    subtitle = if (state.fuelVehicleCount == 0) {
                        stringResource(R.string.plan_fuel_empty)
                    } else {
                        pluralStringResource(
                            R.plurals.plan_fuel_vehicles,
                            state.fuelVehicleCount,
                            state.fuelVehicleCount,
                        )
                    },
                    onClick = onOpenFuelPass,
                )
            }
        }
    }
    }
}

@Composable
private fun PlanRibbon(today: Long, upcoming: List<UpcomingItem>, onSelect: (Long) -> Unit) {
    val byDay = remember(upcoming) { upcoming.groupBy { it.dueEpochDay } }
    val weekday = remember { DateTimeFormatter.ofPattern("EEE", Locale.getDefault()) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(SalliSpacing.xs),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        repeat(14) { offset ->
            val day = today + offset
            val date = LocalDate.ofEpochDay(day)
            val items = byDay[day].orEmpty()
            val dotColor = when {
                items.any { it.kind == UpcomingKind.BILL } -> LocalSalliColors.current.warning
                items.any { it.kind == UpcomingKind.FUEL_ELIGIBLE } -> LocalSalliColors.current.positive
                items.isNotEmpty() -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SalliSpacing.xxs),
                modifier = Modifier
                    .width(48.dp)
                    .background(
                        if (offset == 0) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.background,
                        RoundedCornerShape(16.dp),
                    )
                    .clickable(enabled = items.isNotEmpty()) { onSelect(day) }
                    .padding(vertical = SalliSpacing.xs),
            ) {
                Text(
                    date.format(weekday),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Box(Modifier.size(5.dp).background(dotColor, CircleShape))
            }
        }
    }
}

@Composable
private fun PlanUpcomingRow(
    item: UpcomingItem,
    modifier: Modifier,
    onOpenBills: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenFuelPass: () -> Unit,
) {
    val subtitle = when (item.kind) {
        UpcomingKind.BILL -> stringResource(R.string.plan_kind_bill)
        UpcomingKind.RECURRING -> stringResource(R.string.plan_kind_recurring)
        UpcomingKind.FUEL_ELIGIBLE -> stringResource(R.string.plan_kind_fuel_eligible)
        UpcomingKind.FUEL_RESET -> stringResource(R.string.plan_kind_fuel_reset)
    }
    val action = when (item.kind) {
        UpcomingKind.BILL -> onOpenBills
        UpcomingKind.RECURRING -> onOpenRecurring
        UpcomingKind.FUEL_ELIGIBLE, UpcomingKind.FUEL_RESET -> onOpenFuelPass
    }
    ListRow(
        title = item.title,
        subtitle = subtitle,
        modifier = modifier,
        leading = {
            CategoryIcon(
                iconName = when (item.kind) {
                    UpcomingKind.BILL -> "receipt_long"
                    UpcomingKind.RECURRING -> "autorenew"
                    UpcomingKind.FUEL_ELIGIBLE, UpcomingKind.FUEL_RESET -> "local_gas_station"
                },
                colorSeed = when (item.kind) {
                    UpcomingKind.BILL -> 4
                    UpcomingKind.RECURRING -> 8
                    UpcomingKind.FUEL_ELIGIBLE, UpcomingKind.FUEL_RESET -> 3
                },
            )
        },
        trailing = item.amountMinor?.let { amount ->
            { Text(MoneyFormat.formatMinor(amount, item.currency ?: "LKR")) }
        },
        showChevron = true,
        onClick = action,
    )
}

@Composable
private fun GoalPreview(goal: GoalRow, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.width(170.dp).clickable(onClick = onClick),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(SalliSpacing.xs),
            modifier = Modifier.padding(SalliSpacing.md),
        ) {
            RingProgress(progress = goal.percent / 100f)
            Text(goal.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                stringResource(
                    R.string.plan_goal_saved_of_target,
                    MoneyFormat.short(goal.saved),
                    MoneyFormat.short(goal.target),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TrackerRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badge: Int? = null,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        leading = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailing = badge?.let { count -> { StatusPill(text = count.toString(), tone = SalliTone.WARNING) } },
        onClick = onClick,
        showChevron = true,
    )
}

@Composable
private fun BudgetSummaryRow(budget: BudgetUi, onClick: () -> Unit) {
    val spent = Money(budget.totalSpentMinor, budget.currency)
    val cap = Money(budget.totalCapMinor, budget.currency)

    ListRow(
        title = budget.name,
        subtitle = stringResource(
            R.string.plan_budget_spent_of_cap,
            MoneyFormat.short(spent),
            MoneyFormat.short(cap),
        ),
        trailing = {
            StatusPill(text = stringResource(budget.pace.labelRes()), tone = budget.pace.tone())
        },
        onClick = onClick,
    )
    // The pace bar sits under the row rather than inside it: the expected-burn tick needs the
    // full width to be readable, and a sliver squeezed next to a badge reads as noise.
    Column(
        modifier = Modifier.padding(
            start = SalliSpacing.md,
            end = SalliSpacing.md,
            bottom = SalliSpacing.sm,
        ),
    ) {
        PaceBar(
            progress = budget.progress,
            expected = budget.paceExpectedFraction,
            tone = budget.pace.tone(),
        )
    }
}

private fun BudgetPace.tone(): SalliTone = when (this) {
    BudgetPace.Under -> SalliTone.POSITIVE
    BudgetPace.OnPace -> SalliTone.NEUTRAL
    BudgetPace.Hot -> SalliTone.WARNING
    BudgetPace.Over -> SalliTone.NEGATIVE
}

private fun BudgetPace.labelRes(): Int = when (this) {
    BudgetPace.Under -> R.string.plan_pace_under
    BudgetPace.OnPace -> R.string.plan_pace_on
    BudgetPace.Hot -> R.string.plan_pace_hot
    BudgetPace.Over -> R.string.plan_pace_over
}
