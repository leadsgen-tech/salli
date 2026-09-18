package lk.salli.app.features.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lk.salli.design.components.DateHeader
import lk.salli.design.components.DateRangeSelector
import lk.salli.design.components.EmptyState
import lk.salli.design.components.TransactionRow
import lk.salli.design.components.MiniBarChart
import lk.salli.app.R
import lk.salli.domain.Money
import lk.salli.app.nav.ActivityFilterArgs
import lk.salli.app.sms.refreshOutcome

@Composable
fun TimelineScreen(
    onTransactionClick: (Long) -> Unit = {},
    filters: ActivityFilterArgs = ActivityFilterArgs.NONE,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    LaunchedEffect(filters) { viewModel.applyFilters(filters) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val refreshStatus by viewModel.refreshStatus.collectAsStateWithLifecycle()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var searchOpen by remember(filters) { mutableStateOf(!filters.query.isNullOrBlank()) }
    var filtersOpen by remember { mutableStateOf(false) }
    var categoryTarget by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var scrubbedDay by remember { mutableStateOf<Int?>(null) }
    // The scrub label fades on its own; the list stays where the scrub sent it.
    LaunchedEffect(scrubbedDay) { if (scrubbedDay != null) { kotlinx.coroutines.delay(2_500); scrubbedDay = null } }

    lk.salli.design.components.SalliPullToRefresh(
        isRefreshing = refreshing,
        onRefresh = { viewModel.refresh() },
        outcome = refreshOutcome(refreshStatus),
        onOutcomeConsumed = viewModel::consumeRefreshStatus,
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = statusBar, bottom = 120.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item("topbar") {
            TimelineTopBar(
                searchOpen = searchOpen,
                query = state.query,
                onOpenSearch = { searchOpen = true },
                onCloseSearch = { searchOpen = false; viewModel.clearQuery() },
                onQueryChange = { viewModel.onQueryChanged(it) },
                activeFilterCount = listOfNotNull(state.selectedAccountId, state.selectedCategoryId).size +
                    (if (state.type != ActivityType.ALL) 1 else 0) +
                    (if (state.showExcluded) 1 else 0),
                onOpenFilters = { filtersOpen = true },
            )
        }
        if (!searchOpen) {
            item("monthpills") {
                MonthFilterRow(
                    activeLabel = state.range.label,
                    baseMillis = state.cycleStartMillis,
                    selectedOffset = state.monthOffset,
                    onPickOffset = { viewModel.onPickMonthOffset(it) },
                    onPickCustom = { viewModel.onPickRange(it) },
                )
            }
            item("pills") {
                IncomeExpensePills(
                    income = state.totalIncome,
                    expense = state.totalExpense,
                )
            }
            item("daily-bars") {
                Column {
                    // Scrub the bars and the list follows: release on a day and it scrolls there.
                    MiniBarChart(
                        values = state.dailySpend,
                        highlight = state.dailySpend.lastIndex,
                        selected = scrubbedDay,
                        height = 40.dp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        onSelect = { day ->
                            scrubbedDay = day
                            val dayMs = 24L * 60 * 60 * 1000
                            val target = state.range.fromMillis + day * dayMs
                            val fixed = if (searchOpen) 1 else 4
                            var index = fixed
                            var found: Int? = null
                            for (group in state.grouped) {
                                if (group.dayStartMillis in (target - dayMs / 2)..(target + dayMs / 2)) { found = index; break }
                                index += 1 + group.items.size
                            }
                            found?.let { scope.launch { listState.animateScrollToItem(it) } }
                        },
                    )
                    val day = scrubbedDay
                    if (day != null) {
                        val dayMs = 24L * 60 * 60 * 1000
                        val stamp = state.range.fromMillis + day * dayMs
                        val amount = state.dailySpend.getOrNull(day) ?: 0L
                        Text(
                            text = java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()).format(java.util.Date(stamp)) +
                                " · " + formatMoney(Money(amount, state.totalExpense.currency)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
            }
        }

        if (state.isEmpty) {
            item("empty") {
                EmptyState(
                    title = if (state.query.isNotBlank()) stringResource(R.string.activity_no_matches)
                    else stringResource(R.string.activity_nothing_here),
                    message = if (state.query.isNotBlank())
                        stringResource(R.string.activity_no_matches_message)
                    else
                        stringResource(R.string.activity_nothing_here_message),
                    icon = Icons.Outlined.Receipt,
                    modifier = Modifier.padding(top = 40.dp),
                )
            }
        } else {
            state.grouped.forEach { group ->
                stickyHeader(key = "h-${group.label}") {
                    DateHeader(
                        label = group.label,
                        trailingAmount = formatMoneyNet(group.netMinor, group.currency),
                        trailingPositive = group.netMinor >= 0,
                        modifier = Modifier.background(MaterialTheme.colorScheme.background),
                    )
                }
                items(group.items, key = { it.id }) { row ->
                    // A row that arrives while the list is open slides into its slot.
                    Box(modifier = Modifier.animateItem()) {
                        CategorySwipeRow(
                            row = row,
                            onClick = { onTransactionClick(row.id) },
                            onCategory = { categoryTarget = row.id },
                        )
                    }
                }
            }
        }
    }
    }
    if (filtersOpen) {
        ActivityFilterSheet(
            state = state,
            selectedAccountId = state.selectedAccountId,
            selectedCategoryId = state.selectedCategoryId,
            onDismiss = { filtersOpen = false },
            onApply = { account, category, type, transfers, excluded ->
                viewModel.updateFilters(account, category, type, transfers, excluded)
                filtersOpen = false
            },
        )
    }
    categoryTarget?.let { transactionId ->
        ActivityCategoryPicker(
            categories = state.categories,
            onDismiss = { categoryTarget = null },
            onPick = { categoryId ->
                viewModel.changeCategory(transactionId, categoryId)
                categoryTarget = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySwipeRow(row: lk.salli.app.ui.TimelineItem, onClick: () -> Unit, onCategory: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        if (value == SwipeToDismissBoxValue.EndToStart) onCategory()
        false
    })
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterEnd,
                // TransactionRow retains its normal screen gutter at rest. A full cobalt
                // background bled through that gutter and made every row look selected.
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).padding(end = 28.dp),
            ) { Icon(Icons.Outlined.Label, contentDescription = stringResource(R.string.activity_category_action), tint = MaterialTheme.colorScheme.primary) }
        },
        content = {
            TransactionRow(
                title = row.title, subtitle = row.subtitle, amount = row.amount, flow = row.flow,
                leadingIcon = row.icon, merchantRaw = row.merchantRaw, timestamp = row.timestamp,
                isDeclined = row.isDeclined, isOwnTransfer = row.isOwnTransfer,
                excludedLabel = if (row.isExcluded) stringResource(R.string.activity_excluded_label) else null,
                pairSenders = if (row.isOwnTransfer) row.fromSender to row.toSender else null,
                categoryIconName = row.categoryIconName,
                categoryColorSeed = row.categoryColorSeed,
                monogram = row.monogram,
                accountSender = row.accountSender,
                badge = row.badge?.let { lk.salli.design.components.TileBadge.valueOf(it.name) },
                modifier = Modifier.clickable { onClick() },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityCategoryPicker(
    categories: List<lk.salli.data.db.entities.CategoryEntity>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(20.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.activity_category_action), style = MaterialTheme.typography.titleLarge)
            categories.forEach { category ->
                TextButton(onClick = { onPick(category.id) }, modifier = Modifier.fillMaxWidth()) { Text(category.name) }
            }
        }
    }
}

@Composable
private fun TimelineTopBar(
    searchOpen: Boolean,
    query: String,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    activeFilterCount: Int,
    onOpenFilters: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
    ) {
        AnimatedVisibility(visible = !searchOpen, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = stringResource(R.string.activity_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        AnimatedVisibility(visible = searchOpen, enter = fadeIn(), exit = fadeOut()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCloseSearch) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.activity_close_search),
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.activity_search_placeholder), fontSize = 14.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.activity_clear_search))
                            }
                        }
                    },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (!searchOpen) {
            IconButton(onClick = onOpenSearch, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.activity_search),
                )
            }
            IconButton(onClick = onOpenFilters) {
                Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.activity_filters))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityFilterSheet(
    state: TimelineUiState,
    selectedAccountId: Long?,
    selectedCategoryId: Long?,
    onDismiss: () -> Unit,
    onApply: (Long?, Long?, ActivityType, Boolean, Boolean) -> Unit,
) {
    var account by remember(selectedAccountId) { mutableStateOf(selectedAccountId) }
    var category by remember(selectedCategoryId) { mutableStateOf(selectedCategoryId) }
    var type by remember(state.type) { mutableStateOf(state.type) }
    var transfers by remember(state.showOwnTransfers) { mutableStateOf(state.showOwnTransfers) }
    var excluded by remember(state.showExcluded) { mutableStateOf(state.showExcluded) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.activity_filters), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { account = null; category = null; type = ActivityType.ALL; transfers = true; excluded = false }) {
                    Text(stringResource(R.string.activity_filters_clear))
                }
            }
            FilterGroup(stringResource(R.string.activity_filters_account)) {
                state.accountsInView.forEach { item ->
                    FilterChip(selected = account == item.id, onClick = { account = if (account == item.id) null else item.id }, label = { Text(item.displayName) })
                }
            }
            FilterGroup(stringResource(R.string.activity_filters_category)) {
                state.categories.forEach { item ->
                    FilterChip(selected = category == item.id, onClick = { category = if (category == item.id) null else item.id }, label = { Text(item.name) })
                }
            }
            FilterGroup(stringResource(R.string.activity_filters_type)) {
                listOf(
                    ActivityType.ALL to R.string.activity_filters_all,
                    ActivityType.SPENDING to R.string.activity_filters_spending,
                    ActivityType.INCOME to R.string.activity_filters_income,
                    ActivityType.TRANSFERS to R.string.activity_filters_transfers,
                ).forEach { (value, label) ->
                    FilterChip(selected = type == value, onClick = { type = value }, label = { Text(stringResource(label)) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.activity_filters_transfers_toggle), modifier = Modifier.weight(1f))
                Switch(checked = transfers, onCheckedChange = { transfers = it })
            }
            // The only way back for an excluded transaction: list it here, muted, open it, Include.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.activity_filters_excluded_toggle), modifier = Modifier.weight(1f))
                Switch(checked = excluded, onCheckedChange = { excluded = it })
            }
            TextButton(onClick = { onApply(account, category, type, transfers, excluded) }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.activity_filters_done))
            }
        }
    }
}

@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) { content() }
    }
}

@Composable
private fun IncomeExpensePills(income: Money, expense: Money) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        InlineSummary(stringResource(R.string.activity_income_label), formatMoney(income), true)
        InlineSummary(stringResource(R.string.activity_expense_label), formatMoney(expense), false)
    }
}

@Composable
private fun InlineSummary(
    label: String,
    amount: String,
    positive: Boolean,
) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.width(4.dp))
    Text(amount, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = if (positive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)
}

/* -------------------------------------------------------------------------- */
/* Chart + month pills                                                        */
/* -------------------------------------------------------------------------- */

/**
 * One line per account's cumulative expense over the selected range. Lines animate on
 * composition. Each point is the real cumulative daily value; the path deliberately avoids
 * smoothing that could visually overstate or understate spending between points.
 */
@Composable
private fun MultiAccountChart(
    series: List<lk.salli.app.features.timeline.AccountSeries>,
    accountsInView: List<lk.salli.app.features.timeline.AccountSummary>,
    hiddenAccountIds: Set<Long>,
    onToggleAccount: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.onSurface,
    )
    // Filters remove entries from [series], so indexing that filtered list would shift colours
    // and make the interactive legend lie. Account order is the stable source for both.
    val colorByAccountId = accountsInView.mapIndexed { index, account ->
        account.id to colors[index % colors.size]
    }.toMap()
    val guide = MaterialTheme.colorScheme.outlineVariant
    // Re-draw from 0 every time the series identity changes (month switched, accounts
    // updated, etc.). `animateFloatAsState` on a fixed target of 1f won't re-animate once
    // it reaches 1, so we explicitly reset with an Animatable keyed on the series hash.
    val seriesKey = series.hashCode()
    val progressAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(seriesKey) {
        progressAnim.snapTo(0f)
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 700,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
        )
    }
    val progress = progressAnim.value
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = "Cumulative spending",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.Canvas(
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
            val padTop = 12f
            val padBottom = 18f
            val chartH = size.height - padTop - padBottom
            // Dotted baseline
            drawLine(
                color = guide,
                start = androidx.compose.ui.geometry.Offset(0f, size.height - padBottom),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height - padBottom),
                strokeWidth = 1.2f,
                pathEffect = androidx.compose.ui.graphics.PathEffect
                    .dashPathEffect(floatArrayOf(3f, 6f)),
            )
            if (series.isEmpty()) return@Canvas
            val globalMax = series.maxOf { it.cumulative.maxOrNull() ?: 0f }.takeIf { it > 0f }
                ?: return@Canvas
            val days = series.first().cumulative.size.coerceAtLeast(2)
            val stepX = size.width / (days - 1).toFloat()

            series.forEachIndexed { idx, s ->
                val points = s.cumulative
                if (points.size < 2) return@forEachIndexed
                val lineColor = colorByAccountId[s.accountId] ?: colors[idx % colors.size]
                val drawUpTo = (points.size * progress).toInt().coerceIn(1, points.size - 1)
                val path = androidx.compose.ui.graphics.Path().apply {
                    fun y(i: Int): Float = padTop + chartH - (points[i] / globalMax) * chartH
                    moveTo(0f, y(0))
                    for (i in 0 until drawUpTo) {
                        val iNext = i + 1
                        val x2 = stepX * iNext
                        lineTo(x2, y(iNext))
                    }
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 7f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    ),
                )
                // Head dot sits on the last real point drawn.
                val headIdx = drawUpTo.coerceAtMost(points.size - 1)
                val hx = stepX * headIdx
                val hy = padTop + chartH - (points[headIdx] / globalMax) * chartH
                drawCircle(
                    color = lineColor,
                    radius = 6f,
                    center = androidx.compose.ui.geometry.Offset(hx, hy),
                )
            }
        }
            Spacer(Modifier.height(8.dp))
        // Legend doubles as an account filter. Styled as Material FilterChips with visible
        // borders + checkmark on selected so the interaction is discoverable — a plain
        // coloured-dot row read as a passive legend, which it isn't.
            val scrollState = rememberScrollState()
            Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            ) {
            Text(
                text = "Accounts",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            accountsInView.forEach { account ->
                val visible = account.id !in hiddenAccountIds
                val accountColor = colorByAccountId.getValue(account.id)
                androidx.compose.material3.FilterChip(
                    selected = visible,
                    onClick = { onToggleAccount(account.id) },
                    label = {
                        Text(
                            text = account.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(
                                    if (visible) accountColor
                                    else accountColor.copy(alpha = 0.3f),
                                ),
                        )
                    },
                )
            }
            }
        }
    }
}

/**
 * Single filter row: six month pills (−3 … +2) plus a trailing calendar icon that opens a
 * [DateRangePicker]. Selecting a month snaps to that calendar month; picking a custom
 * range deselects all pills and shows a small "Custom: …" chip in place of the pill row.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MonthFilterRow(
    activeLabel: String,
    baseMillis: Long,
    selectedOffset: Int?,
    onPickOffset: (Int) -> Unit,
    onPickCustom: (lk.salli.domain.DateRange) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val isCustom = selectedOffset == null

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        if (isCustom) {
            // Custom-range active — show the label as a single chip, tap to re-open the
            // picker. A small "×" at the end reverts to the current month.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { showPicker = true }
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = "Custom · $activeLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                IconButton(
                    onClick = { onPickOffset(0) },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Reset to current month",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        } else {
            MonthOffsetPills(
                baseMillis = baseMillis,
                selectedOffset = selectedOffset,
                onPick = onPickOffset,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.width(6.dp))
        // Calendar icon — opens a Material3 DateRangePicker dialog for custom ranges.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { showPicker = true },
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = "Pick a custom range",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }

    if (showPicker) {
        val pickerState = androidx.compose.material3.rememberDateRangePickerState()
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val from = pickerState.selectedStartDateMillis
                    val to = pickerState.selectedEndDateMillis ?: from
                    if (from != null && to != null) {
                        onPickCustom(lk.salli.domain.DateRange.ofUtc(from, to))
                    }
                    showPicker = false
                }) { Text("Apply") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showPicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            androidx.compose.material3.DateRangePicker(state = pickerState)
        }
    }
}

@Composable
private fun MonthOffsetPills(
    baseMillis: Long,
    selectedOffset: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fmt = remember { java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault()) }
    val offsets = listOf(-3, -2, -1, 0, 1, 2)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        offsets.forEach { off ->
            // Pills are named after the month each cycle *starts* in, so with a 25th start
            // the pill for the current cycle reads "Aug" in mid-September.
            val cal = java.util.Calendar.getInstance()
            if (baseMillis > 0L) cal.timeInMillis = baseMillis
            cal.add(java.util.Calendar.MONTH, off)
            val label = fmt.format(cal.time)
            val isSelected = off == selectedOffset
            val isFuture = off > 0
            val bg by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else androidx.compose.ui.graphics.Color.Transparent,
                label = "pill-bg-$off",
            )
            val fg by androidx.compose.animation.animateColorAsState(
                targetValue = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                    isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "pill-fg-$off",
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(48.dp)
                    .height(48.dp)
                    .clickable(enabled = !isFuture) { onPick(off) },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(44.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(bg),
                ) {
                    Text(text = label, style = MaterialTheme.typography.labelMedium, color = fg)
                }
            }
        }
    }
}

private fun formatMoney(money: Money): String = lk.salli.domain.money.MoneyFormat.format(money)

/** Daily net formatter — prepends "-" for outflow days, keeps it plain for inflow days. */
private fun formatMoneyNet(minor: Long, currency: String): String =
    lk.salli.domain.money.MoneyFormat.formatMinor(minor, currency, signed = true)
