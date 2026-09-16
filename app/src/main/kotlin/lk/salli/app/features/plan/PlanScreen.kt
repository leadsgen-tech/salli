package lk.salli.app.features.plan

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lk.salli.app.R
import lk.salli.app.features.budgets.BudgetPace
import lk.salli.app.features.budgets.BudgetUi
import lk.salli.app.features.budgets.BudgetsViewModel
import lk.salli.design.components.GroupedList
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
    viewModel: PlanViewModel = hiltViewModel(),
    budgetsViewModel: BudgetsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val budgets by budgetsViewModel.state.collectAsStateWithLifecycle()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

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
        item { SectionHeader(title = stringResource(R.string.plan_budgets_header)) }
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
                    budgets.budgets.forEach { budget ->
                        BudgetSummaryRow(budget = budget, onClick = onOpenBudgets)
                        ListDivider()
                    }
                    ListRow(
                        title = stringResource(R.string.plan_budgets_all),
                        subtitle = stringResource(R.string.plan_budgets_all_subtitle),
                        onClick = onOpenBudgets,
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
                    icon = Icons.Outlined.Flag,
                    title = stringResource(R.string.plan_goals_title),
                    subtitle = if (state.goalCount == 0) {
                        stringResource(R.string.plan_goals_empty)
                    } else {
                        pluralStringResource(R.plurals.plan_goals_count, state.goalCount, state.goalCount)
                    },
                    onClick = onOpenGoals,
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
