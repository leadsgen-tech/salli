package lk.salli.app.features.home

import androidx.compose.animation.core.Spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import lk.salli.design.components.LocalThemeTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import lk.salli.app.ui.TimelineItem
import lk.salli.domain.Currency
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow

/**
 * Home — monochrome, typography-first.
 *
 * The whole screen boils down to: who are you, how much did you spend this month, what does
 * the shape of that spending look like, and what were the last few transactions. Everything
 * else (accounts, budgets, insights) lives one tap away in its own tab. This page should feel
 * like flipping open a diary — big number, one chart, a short list.
 */
@Composable
fun HomeScreen(
    onTransactionClick: (Long) -> Unit = {},
    onSeeAllActivity: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onOpenChat: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
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
                darkTheme = darkTheme,
                onToggleTheme = viewModel::toggleTheme,
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
        item {
            AccountStack(
                accounts = state.accounts,
                totalBalance = totalBalance,
                monthTrend = state.monthTrend,
                monthExpense = state.monthExpense,
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
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
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
) {
    val cal = Calendar.getInstance()
    val dateLabel = remember(cal.timeInMillis / (60 * 60 * 1000)) {
        SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(cal.time)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
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
        ThemeToggleButton(darkTheme = darkTheme, onToggle = onToggleTheme)
    }
}

@Composable
private fun ThemeToggleButton(darkTheme: Boolean, onToggle: () -> Unit) {
    // Tap target records its screen-space centre so ThemeTransitionLayer can kick off the
    // circular-reveal animation outward from the finger. Falls back to a plain toggle when
    // the transition controller isn't wired up (e.g. in Compose previews).
    val transition = LocalThemeTransition.current
    var center by remember { mutableStateOf(Offset.Zero) }
    // Show the target mode's icon — dark mode currently → display sun (tap to go light).
    val icon = if (darkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                center = Offset(
                    x = pos.x + coords.size.width / 2f,
                    y = pos.y + coords.size.height / 2f,
                )
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable {
                if (transition != null) {
                    transition.request(center) { onToggle() }
                } else {
                    onToggle()
                }
            },
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = if (darkTheme) "Switch to light mode" else "Switch to dark mode",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
    }
}

/* -------------------------------------------------------------------------- */
/* Stacked accounts + summary card                                            */
/* -------------------------------------------------------------------------- */

@Composable
private fun AccountStack(
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
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SummaryCard(
            totalBalance = totalBalance,
            monthTrend = monthTrend,
            monthExpense = monthExpense,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(28.dp),
                    clip = false,
                ),
        )
        if (accounts.isNotEmpty()) {
            AccountChipsRow(accounts = accounts)
        }
    }
}

/**
 * Horizontal strip of account chips that adapts to count:
 *  - 1–3 accounts → each takes equal weight, fills the row
 *  - 4+          → horizontal scroller with fixed-width chips
 *
 * Colour cycles through three semantic tints so visually distinct accounts stand apart
 * (green / orange / terracotta) in both palettes.
 */
@Composable
private fun AccountChipsRow(accounts: List<AccountSummary>) {
    val accountColors = listOf(
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.error,
    )
    if (accounts.size <= 3) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            accounts.forEachIndexed { i, a ->
                AccountChip(
                    account = a,
                    color = accountColors[i % accountColors.size],
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } else {
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(accounts, key = { it.id }) { a ->
                val i = accounts.indexOfFirst { it.id == a.id }
                AccountChip(
                    account = a,
                    color = accountColors[i % accountColors.size],
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
            .clip(RoundedCornerShape(18.dp))
            .background(color)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = account.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            // Card-level accounts (ComBank #5166, HSBC card, etc.) never carry a balance in
            // their SMS — render the suffix instead so the chip still tells the user which
            // physical card the row represents.
            text = account.balance?.let(::formatMoneyBold) ?: account.accountLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SummaryCard(
    totalBalance: Money,
    monthTrend: Trend?,
    monthExpense: Money,
    modifier: Modifier = Modifier,
) {
    val animatedMinor by androidx.compose.animation.core.animateIntAsState(
        targetValue = totalBalance.minorUnits.toInt().coerceAtLeast(0),
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 900,
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
        ),
        label = "summary-balance",
    )
    val display = Money(animatedMinor.toLong(), totalBalance.currency)

    // Theme-aware card: in light mode the dark ink (inverseSurface) works; in dark mode
    // inverseSurface is the Atomic Orange accent — we don't want the summary to be orange
    // because it clashes with the orange peek pill and floods the composition. Detect by
    // background luminance (cheaper than passing a flag down) and pick a neutral dark card.
    val scheme = MaterialTheme.colorScheme
    val isLight = scheme.background.luminance() > 0.5f
    val cardBg = if (isLight) scheme.inverseSurface else scheme.surfaceContainerHighest
    val cardFg = if (isLight) scheme.inverseOnSurface else scheme.onSurface
    val cardFgMuted = cardFg.copy(alpha = 0.72f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(cardBg)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Total balance",
            style = MaterialTheme.typography.labelLarge,
            color = cardFgMuted,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = formatMoneyBold(display),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = cardFg,
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
    val arrowBg =
        if (delta != null && !isUp) MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (delta != null) {
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
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                text = "${if (isUp) "+" else "-"}${kotlin.math.abs(delta)}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = fgColor,
            )
        }
        Text(
            text = "(${formatMoneyBold(monthExpense)} this month)",
            style = MaterialTheme.typography.bodyMedium,
            color = mutedFgColor,
        )
    }
}

@Composable
private fun AccountBalanceStrip(accounts: List<AccountSummary>) {
    val withBalance = accounts.filter { it.balance != null }
    if (withBalance.isEmpty()) return
    // With ≤3 accounts (the common case — most users have a current + savings + maybe a card)
    // we lay them out evenly so each pill fills the row. With more than 3 it would get
    // cramped, so we fall back to a horizontal scroller.
    if (withBalance.size <= 3) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            withBalance.forEach { a ->
                AccountBalancePill(a = a, modifier = Modifier.weight(1f))
            }
        }
    } else {
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(withBalance, key = { it.id }) { a ->
                AccountBalancePill(a = a, modifier = Modifier.width(160.dp))
            }
        }
    }
}

@Composable
private fun AccountBalancePill(a: AccountSummary, modifier: Modifier = Modifier) {
    val balance = a.balance ?: return
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = a.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatMoneyBold(balance),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
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
/* Chart                                                                      */
/* -------------------------------------------------------------------------- */

@Composable
private fun SpendChart(buckets: List<Long>, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val ghost = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val guide = MaterialTheme.colorScheme.outlineVariant
    val markerFill = MaterialTheme.colorScheme.surfaceContainerLowest

    val seriesKey = buckets.hashCode()
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessVeryLow),
        label = "chart-progress-$seriesKey",
    )

    Canvas(modifier = modifier) {
        if (buckets.size < 2) return@Canvas

        // Cumulative running total, heavily smoothed. A 5-day moving average knocks the
        // edge off day-level spikes so the line reads as a trajectory, not a staircase.
        val cumulative = LongArray(buckets.size)
        var running = 0L
        for (i in buckets.indices) {
            running += buckets[i]
            cumulative[i] = running
        }
        // Two passes of a 7-point moving average — second pass slightly softens the
        // shoulders left by the first, which is why the curve reads as a ribbon instead of
        // a sketch.
        val firstPass = FloatArray(cumulative.size) { i ->
            val from = (i - 3).coerceAtLeast(0)
            val to = (i + 3).coerceAtMost(cumulative.size - 1)
            var sum = 0f
            for (j in from..to) sum += cumulative[j].toFloat()
            sum / (to - from + 1).toFloat()
        }
        val smooth = FloatArray(cumulative.size) { i ->
            val from = (i - 2).coerceAtLeast(0)
            val to = (i + 2).coerceAtMost(cumulative.size - 1)
            var sum = 0f
            for (j in from..to) sum += firstPass[j]
            sum / (to - from + 1).toFloat()
        }
        val maxVal = smooth.maxOrNull()?.takeIf { it > 0f } ?: 1f

        val padTop = 20f
        val padBottom = 28f
        val chartH = size.height - padTop - padBottom
        val stepX = size.width / (smooth.size - 1).toFloat()

        fun pointAt(i: Int): Offset {
            val x = stepX * i
            val y = padTop + chartH - (smooth[i] / maxVal) * chartH
            return Offset(x, y)
        }

        val baselineY = padTop + chartH + 4f
        val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f, 6f))
        drawLine(
            color = guide,
            start = Offset(0f, baselineY),
            end = Offset(size.width, baselineY),
            strokeWidth = 1.5f,
            pathEffect = dash,
        )

        val drawCount = (smooth.size * progress).coerceAtLeast(2f)
        val lastIdx = drawCount.toInt().coerceAtMost(smooth.size - 1)

        // Build a smooth cubic-bezier path through the points. Control points sit at 1/3 of
        // the way from each knot toward its neighbour — classic Catmull-Rom → Bezier. Gives
        // a continuously-tangent curve that feels "drawn", not segmented.
        fun smoothPathThrough(upto: Int): Path {
            val path = Path()
            if (upto < 1) return path
            val p0 = pointAt(0)
            path.moveTo(p0.x, p0.y)
            val tension = 0.38f  // 0 = linear, 0.5 = Catmull-Rom. Higher = more swoop.
            for (i in 0 until upto) {
                val pPrev = pointAt((i - 1).coerceAtLeast(0))
                val pCurr = pointAt(i)
                val pNext = pointAt(i + 1)
                val pNext2 = pointAt((i + 2).coerceAtMost(smooth.size - 1))
                val c1 = Offset(
                    x = pCurr.x + (pNext.x - pPrev.x) * tension,
                    y = pCurr.y + (pNext.y - pPrev.y) * tension,
                )
                val c2 = Offset(
                    x = pNext.x - (pNext2.x - pCurr.x) * tension,
                    y = pNext.y - (pNext2.y - pCurr.y) * tension,
                )
                path.cubicTo(c1.x, c1.y, c2.x, c2.y, pNext.x, pNext.y)
            }
            return path
        }

        // Ghost line — full projection.
        drawPath(
            path = smoothPathThrough(smooth.size - 1),
            color = ghost,
            style = Stroke(width = 3.5f, cap = StrokeCap.Round),
        )

        // Ink line — progress-to-date.
        drawPath(
            path = smoothPathThrough(lastIdx),
            color = ink,
            style = Stroke(width = 4.5f, cap = StrokeCap.Round),
        )

        // Today marker — dashed vertical + filled ring at the ink-line head.
        val head = pointAt(lastIdx)
        drawLine(
            color = guide,
            start = Offset(head.x, padTop - 4f),
            end = Offset(head.x, padTop + chartH + 4f),
            strokeWidth = 1f,
            pathEffect = dash,
        )
        drawCircle(color = markerFill, radius = 7f, center = head)
        drawCircle(
            color = ink,
            radius = 7f,
            center = head,
            style = Stroke(width = 2f),
        )
    }
}

/* -------------------------------------------------------------------------- */
/* Month pill row                                                             */
/* -------------------------------------------------------------------------- */

@Composable
private fun MonthPillRow() {
    // 3 months leading up to current, + 2 projections — static in v1. Tapping a future pill
    // doesn't do anything yet; the design intent is to preview the navigation.
    val now = remember { Calendar.getInstance() }
    val fmt = remember { SimpleDateFormat("MMM", Locale.getDefault()) }
    val months = remember {
        val c = Calendar.getInstance()
        c.add(Calendar.MONTH, -3)
        (0..5).map {
            val label = fmt.format(c.time)
            c.add(Calendar.MONTH, 1)
            label
        }
    }
    val currentIdx = 3  // the 4th pill = current month (0..5, offset -3)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        months.forEachIndexed { i, label ->
            val isSelected = i == currentIdx
            val isFuture = i > currentIdx
            val bg = when {
                isSelected -> MaterialTheme.colorScheme.inverseSurface
                else -> Color.Transparent
            }
            val fg = when {
                isSelected -> MaterialTheme.colorScheme.inverseOnSurface
                isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = fg,
                )
            }
        }
    }
    // Suppress lint — `now` is referenced indirectly via fmt formatting in the remember block.
    @Suppress("UNUSED_EXPRESSION") now
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
            text = "$sign${formatMoneyBold(absTotal)}",
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
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "See all activity",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* -------------------------------------------------------------------------- */
/* Formatting                                                                 */
/* -------------------------------------------------------------------------- */

private fun formatMoneyBold(money: Money): String {
    val symbol = when (money.currency) {
        "LKR" -> "Rs "
        "USD" -> "$"
        else -> "${money.currency} "
    }
    val abs = kotlin.math.abs(money.minorUnits)
    val major = abs / 100
    val cents = abs % 100
    val formatter = java.text.NumberFormat.getIntegerInstance(Locale.US)
    return "$symbol${formatter.format(major)}.${"%02d".format(cents)}"
}
