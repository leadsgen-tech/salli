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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import lk.salli.app.features.budgets.BudgetCapMode
import lk.salli.app.features.budgets.CapDialSheet
import lk.salli.design.components.PrimaryButton
import lk.salli.design.components.stage.GoalJarTile
import lk.salli.design.components.stage.GoalJarTileWidth
import lk.salli.design.motion.LocalReducedMotion
import lk.salli.design.motion.rememberDeviceTilt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lk.salli.app.R
import lk.salli.app.sms.refreshOutcome
import lk.salli.app.features.budgets.BudgetPace
import lk.salli.app.features.budgets.BudgetUi
import lk.salli.app.features.budgets.BudgetsViewModel
import lk.salli.app.features.goals.GoalsViewModel
import lk.salli.app.features.goals.JarSheet
import lk.salli.app.features.goals.GoalRow
import lk.salli.data.upcoming.UpcomingItem
import lk.salli.data.upcoming.UpcomingKind
import lk.salli.data.upcoming.UpcomingTone
import lk.salli.design.components.HeroCard
import lk.salli.design.components.HeroEyebrow
import lk.salli.design.components.HeroFact
import lk.salli.design.components.HeroSplit
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
    val defaultPeriodStart by budgetsViewModel.defaultPeriodStartDay.collectAsStateWithLifecycle()
    val goals by goalsViewModel.state.collectAsStateWithLifecycle()
    val lastPour by goalsViewModel.lastPour.collectAsStateWithLifecycle()
    var capSheet by remember { mutableStateOf<CapSheetRequest?>(null) }
    var jarSheet by remember { mutableStateOf(false) }
    // The undo chip for a pour lives five seconds, then the pour is final.
    LaunchedEffect(lastPour) {
        if (lastPour != null) { delay(5_000); goalsViewModel.forgetLastPour() }
    }
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
                actionLabel = stringResource(if (budgets.budgets.isEmpty()) R.string.plan_new_cap else R.string.plan_budgets_manage),
                onAction = { if (budgets.budgets.isEmpty()) capSheet = CapSheetRequest(null) else onOpenBudgets() },
            )
        }
        item { Spacer(Modifier.height(SalliSpacing.xs)) }
        if (budgets.budgets.isEmpty()) {
            // No caps yet: propose the one most worth setting, computed from the last periods.
            item {
                SuggestionCard(
                    eyebrow = stringResource(R.string.plan_cap_suggestion_title),
                    body = budgets.capSuggestion?.let {
                        stringResource(R.string.plan_cap_suggestion, it.categoryName, MoneyFormat.formatMinor(it.medianMinor, "LKR"))
                    } ?: stringResource(R.string.plan_cap_suggestion_generic),
                    action = stringResource(R.string.plan_cap_pull),
                    onAction = { capSheet = CapSheetRequest(budgets.capSuggestion?.categoryId) },
                )
            }
        } else {
            item {
                GroupedList {
                    budgets.budgets.forEachIndexed { index, budget ->
                        if (index > 0) ListDivider()
                        BudgetSummaryRow(budget = budget, onClick = onOpenBudgets)
                    }
                }
            }
            item {
                TextButton(onClick = { capSheet = CapSheetRequest(null) }) {
                    Text("+ " + stringResource(R.string.plan_new_cap))
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
        val liveGoals = goals.goals.filter { !it.isArchived }
        item {
            SectionHeader(
                title = stringResource(R.string.plan_goals_title),
                actionLabel = stringResource(if (liveGoals.isEmpty()) R.string.plan_new_goal else R.string.plan_goals_all),
                onAction = { if (liveGoals.isEmpty()) jarSheet = true else onOpenGoals() },
            )
        }
        item { Spacer(Modifier.height(SalliSpacing.xs)) }
        if (!goals.loading && liveGoals.isEmpty()) {
            item {
                SuggestionCard(
                    eyebrow = stringResource(R.string.plan_goal_suggestion_title),
                    body = stringResource(R.string.plan_goal_suggestion),
                    action = stringResource(R.string.plan_goal_start),
                    onAction = { jarSheet = true },
                )
            }
        } else {
            // The jars live here, on Plan, in the same row as the button: hold one to pour.
            item {
                val tilt by rememberDeviceTilt(enabled = liveGoals.isNotEmpty())
                val reduced = LocalReducedMotion.current
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SalliSpacing.sm),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    liveGoals.forEach { goal ->
                        GoalJarTile(
                            name = goal.name,
                            savedMinor = goal.saved.minorUnits,
                            targetMinor = goal.target.minorUnits,
                            lineMinor = goal.toSaveThisCycle?.let { goal.saved.minorUnits + it.minorUnits },
                            formatAmount = { MoneyFormat.short(Money(it, goal.saved.currency)) },
                            onPour = { goalsViewModel.pour(goal.id, goal.name, it) },
                            tilt = tilt,
                            canPour = goal.linkedAccountId == null && !goal.isComplete,
                            reducedMotion = reduced,
                            modifier = Modifier.width(GoalJarTileWidth),
                        )
                    }
                }
            }
            item {
                TextButton(onClick = { jarSheet = true }) { Text("+ " + stringResource(R.string.plan_new_goal)) }
            }
            item {
                val pour = lastPour
                if (pour != null) {
                    TextButton(onClick = goalsViewModel::undoLastPour) {
                        Text(stringResource(R.string.plan_undo_pour, MoneyFormat.formatMinor(pour.amountMinor, "LKR"), pour.goalName))
                    }
                } else {
                    val needy = liveGoals.firstOrNull { (it.toSaveThisCycle?.minorUnits ?: 0L) > 0L && !it.isComplete }
                    Text(
                        text = stringResource(R.string.plan_goal_hint) + " · " + (
                            needy?.let { stringResource(R.string.plan_goal_needs, it.name, MoneyFormat.short(it.toSaveThisCycle!!)) }
                                ?: stringResource(R.string.plan_goal_on_line)
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
    if (jarSheet) {
        JarSheet(
            onDismiss = { jarSheet = false },
            onStart = { name, target, date ->
                goalsViewModel.saveGoal(id = null, name = name, targetMinor = target, targetDate = date, linkedAccountId = null)
                jarSheet = false
            },
        )
    }
    capSheet?.let { request ->
        CapDialSheet(
            categories = budgets.availableCategories,
            history = budgets.cycleHistory,
            defaultPeriodStartDay = defaultPeriodStart,
            initialCategoryId = request.categoryId,
            onDismiss = { capSheet = null },
            onSave = { payload ->
                if (payload.categoryIds.isEmpty()) {
                    budgetsViewModel.create(
                        name = payload.name, currency = "LKR", capMode = BudgetCapMode.Total,
                        lines = emptyList(), totalCapMinor = payload.capMinor,
                        accountIds = emptyList(), periodStartDay = payload.periodStartDay,
                    )
                } else {
                    budgetsViewModel.create(
                        name = payload.name, currency = "LKR", capMode = BudgetCapMode.PerCategory,
                        lines = payload.perCategoryMinor.map { it.key to it.value }, totalCapMinor = null,
                        accountIds = emptyList(), periodStartDay = payload.periodStartDay,
                    )
                }
                capSheet = null
            },
        )
    }
}

/** Opens the cap dial, optionally seated on one category. */
private data class CapSheetRequest(val categoryId: Long?)

/** A nudge computed from the user's own numbers, with one action. Replaces an empty row. */
@Composable
private fun SuggestionCard(eyebrow: String, body: String, action: String, onAction: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(SalliSpacing.md), verticalArrangement = Arrangement.spacedBy(SalliSpacing.xs)) {
            Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(body, style = MaterialTheme.typography.bodyLarge)
            PrimaryButton(text = action, onClick = onAction)
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
        // The one line that changes a decision: where this pace lands at the end of the cycle.
        if (budget.paceExpectedFraction > 0.05f && budget.totalCapMinor > 0L) {
            val projected = (budget.totalSpentMinor / budget.paceExpectedFraction).toLong()
            val spare = budget.totalCapMinor - projected
            Spacer(Modifier.height(SalliSpacing.xxs))
            Text(
                text = if (spare >= 0L) stringResource(R.string.plan_budget_projection_spare, MoneyFormat.formatMinor(spare, budget.currency))
                else stringResource(R.string.plan_budget_projection_over, MoneyFormat.formatMinor(-spare, budget.currency)),
                style = MaterialTheme.typography.bodySmall,
                color = if (spare >= 0L) MaterialTheme.colorScheme.onSurfaceVariant else LocalSalliColors.current.negative,
            )
        }
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
