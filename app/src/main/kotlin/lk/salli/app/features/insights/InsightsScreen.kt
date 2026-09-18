package lk.salli.app.features.insights

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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lk.salli.app.R
import lk.salli.app.sms.refreshOutcome
import lk.salli.design.components.BankAvatar
import lk.salli.design.components.CategoryIcon
import lk.salli.design.components.EmptyState
import lk.salli.design.components.GroupedList
import lk.salli.design.components.ListDivider
import lk.salli.design.components.ListRow
import lk.salli.design.components.MerchantAvatar
import lk.salli.design.components.MiniBarChart
import lk.salli.design.components.PaceBar
import lk.salli.design.components.SalliPullToRefresh
import lk.salli.design.components.SalliTone
import lk.salli.design.components.SectionHeader
import lk.salli.design.components.StatTile
import lk.salli.design.components.StatusPill
import lk.salli.design.theme.SalliShapeTokens
import lk.salli.design.theme.SalliSpacing
import lk.salli.domain.Money
import lk.salli.domain.money.MoneyFormat

@Composable
fun InsightsScreen(
    onOpenActivityCategory: (Long) -> Unit = {},
    onOpenActivityMerchant: (String) -> Unit = {},
    onOpenActivityAccount: (Long) -> Unit = {},
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val refreshStatus by viewModel.refreshStatus.collectAsStateWithLifecycle()
    val gutter = SalliSpacing.screenGutter

    SalliPullToRefresh(
        isRefreshing = refreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
        outcome = refreshOutcome(refreshStatus),
        onOutcomeConsumed = viewModel::consumeRefreshStatus,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                bottom = 132.dp,
            ),
        ) {
            item {
                InsightsHeader(
                    range = state.range.label,
                    previous = viewModel::onPrevRange,
                    next = viewModel::onNextRange,
                )
            }
            // Empty only when nothing was spent AND nothing was moved; a period that is all
            // transfers still has a story to tell.
            if (state.slices.isEmpty() && state.movedTo.isEmpty() && !state.loading) {
                item {
                    EmptyState(
                        title = stringResource(R.string.insights_empty_title),
                        message = stringResource(R.string.insights_empty_message),
                        icon = Icons.Outlined.Analytics,
                        modifier = Modifier.padding(top = 40.dp),
                    )
                }
            } else if (!state.loading) {
                item {
                    InsightSummary(
                        state = state,
                        onSelectMonth = viewModel::onSelectMonthlyBar,
                    )
                }
                if (state.slices.isNotEmpty()) {
                    item { Spacer(Modifier.height(SalliSpacing.sectionGap)) }
                    item {
                        SectionHeader(
                            title = stringResource(R.string.insights_by_category),
                            modifier = Modifier.padding(horizontal = gutter),
                        )
                    }
                    item { Spacer(Modifier.height(SalliSpacing.xs)) }
                    item {
                        GroupedList(Modifier.padding(horizontal = gutter)) {
                            state.slices.forEachIndexed { index, slice ->
                                if (index > 0) ListDivider()
                                CategoryInsightRow(
                                    slice = slice,
                                    onClick = slice.categoryId?.let { id -> { onOpenActivityCategory(id) } },
                                )
                            }
                        }
                    }
                }

                if (state.merchants.isNotEmpty()) {
                    item { Spacer(Modifier.height(SalliSpacing.sectionGap)) }
                    item {
                        SectionHeader(
                            title = stringResource(R.string.insights_merchants),
                            modifier = Modifier.padding(horizontal = gutter),
                        )
                    }
                    item { Spacer(Modifier.height(SalliSpacing.xs)) }
                    item {
                        GroupedList(Modifier.padding(horizontal = gutter)) {
                            state.merchants.forEachIndexed { index, merchant ->
                                if (index > 0) ListDivider()
                                ListRow(
                                    title = merchant.name,
                                    subtitle = stringResource(R.string.insights_visits, merchant.count),
                                    leading = { MerchantAvatar(merchant.name) },
                                    trailing = {
                                        Text(MoneyFormat.format(Money(merchant.totalMinor, merchant.currency)))
                                    },
                                    onClick = { onOpenActivityMerchant(merchant.name) },
                                )
                            }
                        }
                    }
                }

                if (state.accounts.isNotEmpty()) {
                    item { Spacer(Modifier.height(SalliSpacing.sectionGap)) }
                    item {
                        SectionHeader(
                            title = stringResource(R.string.insights_accounts),
                            modifier = Modifier.padding(horizontal = gutter),
                        )
                    }
                    item { Spacer(Modifier.height(SalliSpacing.xs)) }
                    item {
                        GroupedList(Modifier.padding(horizontal = gutter)) {
                            state.accounts.forEachIndexed { index, account ->
                                if (index > 0) ListDivider()
                                ListRow(
                                    title = account.name,
                                    subtitle = stringResource(R.string.insights_account_subtitle),
                                    leading = {
                                        BankAvatar(
                                            sender = account.senderAddress,
                                            displayName = account.name,
                                            size = 40.dp,
                                        )
                                    },
                                    trailing = {
                                        Text(MoneyFormat.format(Money(account.totalMinor, account.currency)))
                                    },
                                    onClick = { onOpenActivityAccount(account.id) },
                                )
                            }
                        }
                    }
                }

                if (state.movedTo.isNotEmpty()) {
                    item { Spacer(Modifier.height(SalliSpacing.sectionGap)) }
                    item {
                        SectionHeader(
                            title = stringResource(R.string.insights_moved_header),
                            modifier = Modifier.padding(horizontal = gutter),
                        )
                    }
                    item { Spacer(Modifier.height(SalliSpacing.xs)) }
                    item {
                        GroupedList(Modifier.padding(horizontal = gutter)) {
                            state.movedTo.forEachIndexed { index, moved ->
                                if (index > 0) ListDivider()
                                ListRow(
                                    title = when {
                                        moved.isOwn -> stringResource(R.string.insights_moved_own)
                                        moved.name.isBlank() -> stringResource(R.string.insights_moved_unnamed)
                                        else -> moved.name
                                    },
                                    subtitle = pluralStringResource(R.plurals.insights_moved_count, moved.count, moved.count),
                                    leading = { CategoryIcon(iconName = "swap_horiz", colorSeed = 11) },
                                    trailing = { Text(MoneyFormat.format(Money(moved.totalMinor, moved.currency))) },
                                )
                            }
                        }
                    }
                }

                // Cash flow is the ledger view: out = spent + moved, so net stays honest.
                item {
                    CashFlow(
                        state.totalIncome,
                        Money(state.totalSpend.minorUnits + state.totalMoved.minorUnits, state.totalSpend.currency),
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightsHeader(range: String, previous: () -> Unit, next: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SalliSpacing.screenGutter, vertical = SalliSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.insights_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = SalliShapeTokens.pill,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = previous) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        stringResource(R.string.insights_previous),
                    )
                }
                Text(
                    text = range,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = next) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        stringResource(R.string.insights_next),
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightSummary(state: InsightsUiState, onSelectMonth: (Int) -> Unit) {
    Column(
        modifier = Modifier.padding(
            start = SalliSpacing.screenGutter,
            end = SalliSpacing.screenGutter,
            top = SalliSpacing.sm,
        ),
    ) {
        val hasMoved = state.totalMoved.minorUnits > 0L
        val out = Money(state.totalSpend.minorUnits + state.totalMoved.minorUnits, state.totalSpend.currency)
        Text(
            text = MoneyFormat.format(out),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SalliSpacing.xs),
        ) {
            Text(
                text = stringResource(if (hasMoved) R.string.insights_out_this_period else R.string.insights_spent_this_period),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.topCategory?.let { top ->
                StatusPill(
                    text = stringResource(
                        R.string.insights_top_category,
                        top.categoryName,
                        (top.percent * 100).toInt(),
                    ),
                    tone = SalliTone.NEUTRAL,
                )
            }
        }
        if (hasMoved) {
            Spacer(Modifier.height(SalliSpacing.xs))
            // Spent and moved side by side, never added: one bought things, the other went somewhere.
            Row(horizontalArrangement = Arrangement.spacedBy(SalliSpacing.xs)) {
                StatusPill(
                    text = stringResource(R.string.insights_spent_pill, MoneyFormat.format(state.totalSpend)),
                    tone = SalliTone.NEUTRAL,
                )
                StatusPill(
                    text = stringResource(R.string.insights_moved_pill, MoneyFormat.format(state.totalMoved)),
                    tone = SalliTone.NEUTRAL,
                )
            }
        }
        Spacer(Modifier.height(SalliSpacing.lg))
        MiniBarChart(
            values = state.monthlyBars.map { it.totalMinor },
            highlight = state.monthlyBars.indexOfFirst { it.isCurrent }.takeIf { it >= 0 },
            height = 80.dp,
            onSelect = onSelectMonth,
        )
        Spacer(Modifier.height(SalliSpacing.xs))
        Row(modifier = Modifier.fillMaxWidth()) {
            state.monthlyBars.forEachIndexed { index, bar ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            selected = bar.isCurrent
                            contentDescription = buildString {
                                append(bar.label)
                                append(", ")
                                append(MoneyFormat.short(Money(bar.totalMinor, state.totalSpend.currency)))
                            }
                        }
                        .clickable(role = Role.Tab) { onSelectMonth(index) }
                        .padding(vertical = 12.dp),
                ) {
                    Text(
                        text = bar.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (bar.isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryInsightRow(slice: InsightSlice, onClick: (() -> Unit)?) {
    Column {
        ListRow(
            title = slice.categoryName,
            subtitle = stringResource(
                R.string.insights_category_detail,
                slice.count,
                (slice.percent * 100).toInt(),
            ),
            leading = { CategoryIcon(slice.iconName, slice.colorSeed) },
            trailing = { Text(MoneyFormat.format(Money(slice.totalMinor, slice.currency))) },
            onClick = onClick,
        )
        PaceBar(
            progress = slice.percent,
            modifier = Modifier.padding(
                start = 76.dp,
                end = SalliSpacing.md,
                bottom = SalliSpacing.sm,
            ),
        )
    }
}

@Composable
private fun CashFlow(income: Money, expense: Money) {
    Column(
        modifier = Modifier.padding(
            horizontal = SalliSpacing.screenGutter,
            vertical = SalliSpacing.sectionGap,
        ),
    ) {
        SectionHeader(stringResource(R.string.insights_cash_flow))
        Spacer(Modifier.height(SalliSpacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SalliSpacing.xs),
        ) {
            StatTile(
                label = stringResource(R.string.insights_income_label),
                value = MoneyFormat.short(income),
                tone = SalliTone.POSITIVE,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.insights_expense_label),
                value = MoneyFormat.short(expense),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.insights_net_label),
                value = MoneyFormat.short(Money(income.minorUnits - expense.minorUnits, expense.currency)),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
