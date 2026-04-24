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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lk.salli.design.components.DateHeader
import lk.salli.design.components.DateRangeSelector
import lk.salli.design.components.EmptyState
import lk.salli.design.components.TransactionRow
import lk.salli.domain.Money

@Composable
fun TimelineScreen(
    onTransactionClick: (Long) -> Unit = {},
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var searchOpen by remember { mutableStateOf(false) }

    lk.salli.design.components.SalliPullToRefresh(
        isRefreshing = refreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(
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
            )
        }
        if (!searchOpen) {
            item("chart") {
                MultiAccountChart(
                    series = state.series,
                    accountsInView = state.accountsInView,
                    hiddenAccountIds = state.hiddenAccountIds,
                    onToggleAccount = { viewModel.toggleAccount(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .height(180.dp),
                )
            }
            item("monthpills") {
                MonthFilterRow(
                    activeLabel = state.range.label,
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
        }

        if (state.isEmpty) {
            item("empty") {
                EmptyState(
                    title = if (state.query.isNotBlank()) "No matches" else "Nothing in this range",
                    message = if (state.query.isNotBlank())
                        "Try a different search term or widen the date range."
                    else
                        "Try a different date range or wait for fresh bank SMS.",
                    icon = Icons.Outlined.Receipt,
                    modifier = Modifier.padding(top = 40.dp),
                )
            }
        } else {
            state.grouped.forEach { group ->
                item(key = "h-${group.label}") {
                    DateHeader(
                        label = group.label,
                        trailingAmount = formatMoneyNet(group.netMinor, group.currency),
                        trailingPositive = group.netMinor >= 0,
                    )
                }
                items(group.items, key = { it.id }) { row ->
                    TransactionRow(
                        title = row.title,
                        subtitle = row.subtitle,
                        amount = row.amount,
                        flow = row.flow,
                        leadingIcon = row.icon,
                        merchantRaw = row.merchantRaw,
                        timestamp = row.timestamp,
                        isDeclined = row.isDeclined,
                        modifier = Modifier.clickable { onTransactionClick(row.id) },
                    )
                }
            }
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
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
    ) {
        AnimatedVisibility(visible = !searchOpen, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = "Timeline",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        AnimatedVisibility(visible = searchOpen, enter = fadeIn(), exit = fadeOut()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCloseSearch) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Close search",
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search merchant, note, category…", fontSize = 14.sp) },
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
                                Icon(Icons.Outlined.Close, contentDescription = "Clear")
                            }
                        }
                    },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (!searchOpen) {
            IconButton(onClick = onOpenSearch) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                )
            }
        }
    }
}

@Composable
private fun IncomeExpensePills(income: Money, expense: Money) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        SummaryPill(
            label = "INCOME",
            amount = formatMoney(income),
            positive = true,
            modifier = Modifier.weight(1f),
        )
        SummaryPill(
            label = "EXPENSES",
            amount = formatMoney(expense),
            positive = false,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryPill(
    label: String,
    amount: String,
    positive: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.height(72.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (positive) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/* Chart + month pills                                                        */
/* -------------------------------------------------------------------------- */

/**
 * One line per account's cumulative expense over the selected range. Lines animate on
 * composition via a spring-driven progress that grows from 0 → 1, so the chart reads
 * as "the month drawing itself". Each line's colour cycles through the semantic palette
 * (primary / tertiary / error) matching the account chips on Home.
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
    Column(modifier = modifier) {
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
                val raw = s.cumulative
                if (raw.size < 2) return@forEachIndexed
                // Two passes of a 5-point moving average — smooths the daily zig-zags out of
                // the cumulative curve so the stroke reads as a continuous ribbon, not a
                // staircase, while still respecting the real endpoint total.
                val pass1 = FloatArray(raw.size) { i ->
                    val from = (i - 2).coerceAtLeast(0)
                    val to = (i + 2).coerceAtMost(raw.size - 1)
                    var sum = 0f
                    for (j in from..to) sum += raw[j]
                    sum / (to - from + 1)
                }
                val points = FloatArray(raw.size) { i ->
                    val from = (i - 2).coerceAtLeast(0)
                    val to = (i + 2).coerceAtMost(raw.size - 1)
                    var sum = 0f
                    for (j in from..to) sum += pass1[j]
                    sum / (to - from + 1)
                }
                val drawUpTo = (points.size * progress).toInt().coerceIn(1, points.size - 1)
                val path = androidx.compose.ui.graphics.Path().apply {
                    fun y(i: Int): Float = padTop + chartH - (points[i] / globalMax) * chartH
                    moveTo(0f, y(0))
                    val tension = 0.42f                 // more swoop than the previous 0.3
                    for (i in 0 until drawUpTo) {
                        val iPrev = (i - 1).coerceAtLeast(0)
                        val iNext = i + 1
                        val iNext2 = (i + 2).coerceAtMost(points.size - 1)
                        val x1 = stepX * i
                        val x2 = stepX * iNext
                        val c1x = x1 + (x2 - stepX * iPrev) * tension
                        val c1y = y(i) + (y(iNext) - y(iPrev)) * tension
                        val c2x = x2 - (stepX * iNext2 - x1) * tension
                        val c2y = y(iNext) - (y(iNext2) - y(i)) * tension
                        cubicTo(c1x, c1y, c2x, c2y, x2, y(iNext))
                    }
                }
                drawPath(
                    path = path,
                    color = colors[idx % colors.size],
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 7f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    ),
                )
                // Head dot sits on the last drawn smoothed point.
                val headIdx = drawUpTo.coerceAtMost(points.size - 1)
                val hx = stepX * headIdx
                val hy = padTop + chartH - (points[headIdx] / globalMax) * chartH
                drawCircle(
                    color = colors[idx % colors.size],
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
            accountsInView.forEachIndexed { idx, account ->
                val visible = account.id !in hiddenAccountIds
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
                                    if (visible) colors[idx % colors.size]
                                    else colors[idx % colors.size].copy(alpha = 0.3f),
                                ),
                        )
                    },
                )
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
                    .height(32.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.inverseSurface)
                    .clickable { showPicker = true }
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = "Custom · $activeLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
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
                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        } else {
            MonthOffsetPills(
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
                .size(32.dp)
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
    selectedOffset: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fmt = remember { java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault()) }
    val offsets = listOf(-3, -2, -1, 0, 1, 2)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        offsets.forEach { off ->
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.MONTH, off)
            val label = fmt.format(cal.time)
            val isSelected = off == selectedOffset
            val isFuture = off > 0
            val bg by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.inverseSurface
                else androidx.compose.ui.graphics.Color.Transparent,
                label = "pill-bg-$off",
            )
            val fg by androidx.compose.animation.animateColorAsState(
                targetValue = when {
                    isSelected -> MaterialTheme.colorScheme.inverseOnSurface
                    isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "pill-fg-$off",
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(bg)
                    .clickable(enabled = !isFuture) { onPick(off) },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = fg,
                )
            }
        }
    }
}

private fun formatMoney(money: Money): String {
    val symbol = if (money.currency == "LKR") "Rs " else "${money.currency} "
    val abs = kotlin.math.abs(money.minorUnits)
    val formatter = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US)
    val major = abs / 100
    val cents = abs % 100
    return "$symbol${formatter.format(major)}.${"%02d".format(cents)}"
}

/** Daily net formatter — prepends "-" for outflow days, keeps it plain for inflow days. */
private fun formatMoneyNet(minor: Long, currency: String): String {
    if (minor == 0L) return "Rs 0.00"
    val symbol = if (currency == "LKR") "Rs " else "$currency "
    val abs = kotlin.math.abs(minor)
    val major = abs / 100
    val cents = abs % 100
    val formatter = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US)
    val sign = if (minor < 0) "-" else "+"
    return "$sign$symbol${formatter.format(major)}.${"%02d".format(cents)}"
}
