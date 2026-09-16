package lk.salli.app.features.insights

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lk.salli.app.R
import lk.salli.design.components.*
import lk.salli.design.format.MoneyFormat
import lk.salli.domain.Money

@Composable
fun InsightsScreen(
    onOpenActivityCategory: (Long) -> Unit = {},
    onOpenActivityMerchant: (String) -> Unit = {},
    onOpenActivityAccount: (Long) -> Unit = {},
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    SalliPullToRefresh(refreshing, viewModel::refresh, Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), bottom = 132.dp)) {
            item { Header(state.range.label, state.totalSpend, viewModel::onPrevRange, viewModel::onNextRange) }
            if (state.slices.isEmpty() && !state.loading) {
                item { EmptyState(stringResource(R.string.insights_empty_title), stringResource(R.string.insights_empty_message), Icons.Outlined.Analytics, Modifier.padding(top = 40.dp)) }
            } else {
                item { MiniBarChart(state.monthlyBars.map { it.totalMinor }, Modifier.padding(horizontal = 20.dp, vertical = 12.dp), highlight = state.monthlyBars.indexOfFirst { it.isCurrent }.takeIf { it >= 0 }) }
                item { SectionHeader(stringResource(R.string.insights_by_category), Modifier.padding(horizontal = 20.dp)) }
                item { GroupedList(Modifier.padding(horizontal = 20.dp)) { state.slices.forEachIndexed { i, s -> if (i > 0) ListDivider(); ListRow(s.categoryName, subtitle = stringResource(R.string.insights_category_subtitle, (s.percent * 100).toInt()), leading = { CategoryIcon(s.iconName, s.colorSeed) }, trailing = { Text(MoneyFormat.format(Money(s.totalMinor, s.currency))) }, onClick = s.categoryId?.let { id -> { onOpenActivityCategory(id) } }) } } }
                if (state.merchants.isNotEmpty()) { item { SectionHeader(stringResource(R.string.insights_merchants), Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) }; item { GroupedList(Modifier.padding(horizontal = 20.dp)) { state.merchants.forEachIndexed { i, m -> if(i>0) ListDivider(); ListRow(m.name, subtitle = stringResource(R.string.insights_visits, m.count), trailing={Text(MoneyFormat.format(Money(m.totalMinor,m.currency)))}, onClick={onOpenActivityMerchant(m.name)}) } } } }
                if (state.accounts.isNotEmpty()) { item { SectionHeader(stringResource(R.string.insights_accounts), Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) }; item { GroupedList(Modifier.padding(horizontal = 20.dp)) { state.accounts.forEachIndexed { i, a -> if(i>0) ListDivider(); ListRow(a.name, subtitle = stringResource(R.string.insights_account_subtitle), trailing={Text(MoneyFormat.format(Money(a.totalMinor,a.currency)))}, onClick={onOpenActivityAccount(a.id)}) } } } }
                item { CashFlow(state.totalIncome, state.totalSpend) }
            }
        }
    }
}

@Composable private fun Header(range: String, spent: Money, previous: () -> Unit, next: () -> Unit) = Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.insights_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); IconButton(previous) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.insights_previous)) }; Text(range); IconButton(next) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.insights_next)) } }
    Text(stringResource(R.string.insights_spent, MoneyFormat.format(spent)), style = MaterialTheme.typography.titleLarge)
}

@Composable private fun CashFlow(income: Money, expense: Money) = Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) { SectionHeader(stringResource(R.string.insights_cash_flow)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(R.string.insights_income, MoneyFormat.format(income)), color = MaterialTheme.colorScheme.tertiary); Text(stringResource(R.string.insights_expense, MoneyFormat.format(expense))); Text(stringResource(R.string.insights_net, MoneyFormat.format(Money(income.minorUnits - expense.minorUnits, expense.currency)))) } }
