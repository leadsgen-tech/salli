package lk.salli.app.features.insights

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lk.salli.design.components.EmptyState
import lk.salli.domain.Money

@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    lk.salli.design.components.SalliPullToRefresh(
        isRefreshing = refreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(
        contentPadding = PaddingValues(top = statusBar, bottom = 140.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            HeroHeader(
                rangeLabel = state.range.label,
                totalSpend = state.totalSpend,
                totalIncome = state.totalIncome,
                onPrev = { viewModel.onPrevRange() },
                onNext = { viewModel.onNextRange() },
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
        if (state.slices.isNotEmpty()) {
            item {
                val previousSpend = state.monthlyBars
                    .getOrNull(state.monthlyBars.size - 2)?.totalMinor
                val pctDelta = if (previousSpend != null && previousSpend > 0L)
                    (((state.totalSpend.minorUnits - previousSpend).toFloat() / previousSpend) * 100).toInt()
                else null
                SpendingHeader(
                    rangeLabel = state.range.label,
                    totalSpend = state.totalSpend,
                    percentDelta = pctDelta,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                BubbleChart(
                    slices = state.slices.take(8),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(340.dp),
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
        state.topCategory?.let { top ->
            item {
                TopCategoryCallout(
                    slice = top,
                    rangeLabel = state.range.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
        if (state.slices.isEmpty() && !state.loading) {
            item {
                EmptyState(
                    title = "Nothing to analyse yet",
                    message = "As your bank SMS pile up we'll break them down by category here.",
                    icon = Icons.Outlined.Analytics,
                    modifier = Modifier.padding(top = 40.dp),
                )
            }
        } else {
            items(state.slices, key = { it.categoryId ?: -1L }) { slice ->
                CategoryRow(slice = slice)
            }
        }
    }
    }
}

/* -------------------------------------------------------------------------- */
/* Hero                                                                       */
/* -------------------------------------------------------------------------- */

@Composable
private fun HeroHeader(
    rangeLabel: String,
    totalSpend: Money,
    totalIncome: Money,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    @Suppress("UNUSED_PARAMETER") totalSpend
    @Suppress("UNUSED_PARAMETER") totalIncome
    // Minimal header — the big spend amount lives inside the LatestInsightsCard now.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = "Insights",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        ChevronButton(emoji = "‹", onClick = onPrev)
        Spacer(Modifier.width(6.dp))
        Text(
            text = rangeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Spacer(Modifier.width(6.dp))
        ChevronButton(emoji = "›", onClick = onNext)
    }
}

@Composable
private fun ChevronButton(emoji: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AnimatedAmount(money: Money) {
    // Spring the minor units on change so the number settles in instead of snapping.
    val animated by androidx.compose.animation.core.animateIntAsState(
        targetValue = money.minorUnits.toInt().coerceAtLeast(0),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "hero-amount",
    )
    val display = Money(animated.toLong(), money.currency)
    Text(
        text = formatMoney(display),
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
        ),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/* -------------------------------------------------------------------------- */
/* Bubble chart                                                               */
/* -------------------------------------------------------------------------- */

@Composable
private fun SpendingHeader(
    rangeLabel: String,
    totalSpend: Money,
    percentDelta: Int?,
    modifier: Modifier = Modifier,
) {
    val monthOnly = rangeLabel.substringBefore(' ')
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Spending in ",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = monthOnly,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatMoney(totalSpend),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (percentDelta != null) {
                Spacer(Modifier.width(10.dp))
                val isDown = percentDelta < 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.inverseSurface)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (isDown) "↘" else "↗",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "${kotlin.math.abs(percentDelta)}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        }
    }
}

private data class PackedBubble(
    val slice: InsightSlice,
    val x: Float,
    val y: Float,
    val r: Float,
)

/**
 * Packs [slices] as circles inside a [widthPx] × [heightPx] box using a force-directed
 * relaxation. Every bubble starts at a rough polar position around the centre, then
 * `ITER_COUNT` iterations push overlapping pairs apart and nudge everyone toward the
 * middle. This handles the real-world failure of greedy spiral packing where a dominant
 * bubble (e.g. Transfers at 80% of spend) leaves no room for secondary ones — the
 * relaxation always finds a home for each, even if they need to scoot past the big one.
 *
 * Sizing: area ∝ cbrt(value) (cube-root compression) and the biggest radius is capped at
 * 38% of the container's shorter side. Keeps small categories readable AND prevents a
 * single category from swallowing the view.
 */
private fun packBubbles(
    slices: List<InsightSlice>,
    widthPx: Float,
    heightPx: Float,
    fillRatio: Float = 0.55f,
    seed: Long = 0L,
): List<PackedBubble> {
    if (slices.isEmpty() || widthPx <= 0f || heightPx <= 0f) return emptyList()
    val sorted = slices.sortedByDescending { it.totalMinor }

    // Cube-root weights for stronger visual flattening than sqrt. Works better when one
    // category dwarfs the rest (common in Salli's data where Transfers dominate).
    val weights = sorted.map { kotlin.math.cbrt(it.totalMinor.toDouble()).toFloat() }
    val totalWeight = weights.sum().coerceAtLeast(1f)
    val containerArea = widthPx * heightPx
    val targetArea = containerArea * fillRatio
    val minR = 30f
    val maxR = minOf(widthPx, heightPx) * 0.38f
    val cx = widthPx / 2f
    val cy = heightPx / 2f

    // Mutable bubble store for the force sim; collapsed to immutable result at the end.
    data class Sim(val slice: InsightSlice, var x: Float, var y: Float, val r: Float)
    val rng = java.util.Random(seed)
    val bubbles = sorted.mapIndexed { i, slice ->
        val frac = weights[i] / totalWeight
        val area = targetArea * frac
        val r = kotlin.math.sqrt(area / Math.PI.toFloat()).coerceIn(minR, maxR)
        if (i == 0) {
            Sim(slice, cx, cy, r)
        } else {
            // Start each secondary at a random angle + random distance beyond maxR so
            // successive shakes land in visibly different arrangements while still
            // satisfying the constraint of "outside the central bubble".
            val angle = (rng.nextFloat() * 2 * Math.PI).toFloat()
            val dist = maxR + r + 8f + rng.nextFloat() * 20f
            Sim(
                slice = slice,
                x = (cx + dist * kotlin.math.cos(angle.toDouble())).toFloat(),
                y = (cy + dist * kotlin.math.sin(angle.toDouble())).toFloat(),
                r = r,
            )
        }
    }

    // Two-phase relaxation:
    //  phase 1 — gravity + repulsion to gather the cluster
    //  phase 2 — repulsion only, with generous padding, to guarantee no overlaps remain
    val padding = 6f
    repeat(160) {
        // Pair-wise repulsion — any overlap pushes both bubbles apart along the separation
        // vector, scaled by half each.
        for (i in bubbles.indices) {
            for (j in i + 1 until bubbles.size) {
                val a = bubbles[i]
                val b = bubbles[j]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
                val minDist = a.r + b.r + padding
                if (dist < minDist) {
                    val push = (minDist - dist) / 2f
                    val ux = dx / dist
                    val uy = dy / dist
                    a.x -= ux * push; a.y -= uy * push
                    b.x += ux * push; b.y += uy * push
                }
            }
        }
        // Gentle gravity — weak enough that it never compresses bubbles through each other.
        for (b in bubbles) {
            b.x += (cx - b.x) * 0.006f
            b.y += (cy - b.y) * 0.006f
            b.x = b.x.coerceIn(b.r, widthPx - b.r)
            b.y = b.y.coerceIn(b.r, heightPx - b.r)
        }
    }
    // Cleanup: pure separation with no gravity. Ensures the final state has every bubble
    // clear of its neighbours, at the cost of the cluster possibly drifting a touch.
    repeat(60) {
        for (i in bubbles.indices) {
            for (j in i + 1 until bubbles.size) {
                val a = bubbles[i]
                val b = bubbles[j]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
                val minDist = a.r + b.r + padding
                if (dist < minDist) {
                    val push = (minDist - dist) / 2f
                    val ux = dx / dist
                    val uy = dy / dist
                    a.x -= ux * push; a.y -= uy * push
                    b.x += ux * push; b.y += uy * push
                }
            }
        }
        for (b in bubbles) {
            b.x = b.x.coerceIn(b.r, widthPx - b.r)
            b.y = b.y.coerceIn(b.r, heightPx - b.r)
        }
    }
    return bubbles.map { PackedBubble(it.slice, it.x, it.y, it.r) }
}

@Composable
private fun BubbleChart(
    slices: List<InsightSlice>,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier.clip(RoundedCornerShape(24.dp)),
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        // Seed for the sim's initial positions. Shake-the-phone bumps this so we re-pack
        // from a fresh random spread — bubbles visually scatter and settle into a new
        // arrangement.
        val shuffleKey = androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableIntStateOf(0)
        }
        val bubbles = androidx.compose.runtime.remember(
            slices.hashCode(), widthPx, heightPx, shuffleKey.intValue,
        ) {
            packBubbles(slices, widthPx, heightPx, seed = shuffleKey.intValue.toLong())
        }

        // Shake listener — kicks a shuffle + haptic pulse every time the accelerometer
        // magnitude spikes above threshold. Debounced to one shake per ~900ms so a
        // vigorous shake doesn't restart the sim every frame.
        ShakeEffect { shuffleKey.intValue++ }

        val scheme = MaterialTheme.colorScheme
        bubbles.forEachIndexed { idx, bubble ->
            val bg = when (idx) {
                0 -> scheme.primary
                1 -> scheme.inverseSurface
                2 -> scheme.tertiary
                else -> scheme.surfaceContainerLowest
            }
            val fg = when (idx) {
                0 -> scheme.onPrimary
                1 -> scheme.inverseOnSurface
                2 -> scheme.onTertiary
                else -> scheme.onSurface
            }
            BubbleItem(
                bubble = bubble,
                bg = bg,
                fg = fg,
                enterDelayMs = (idx * 45).coerceAtMost(400),
                density = density,
            )
        }
    }
}

/**
 * Registers an accelerometer listener and calls [onShake] every time the device registers
 * a vigorous movement (magnitude delta > 14 m/s²). Debounced to avoid retrigger spam.
 * Ties haptic feedback to the shake so the gesture feels tactile.
 */
@Composable
private fun ShakeEffect(onShake: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE)
            as android.hardware.SensorManager
        val accel = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        var lastMagnitude = android.hardware.SensorManager.GRAVITY_EARTH
        var lastShakeMs = 0L
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
                val mag = kotlin.math.sqrt(x * x + y * y + z * z)
                val delta = kotlin.math.abs(mag - lastMagnitude)
                lastMagnitude = mag
                val now = System.currentTimeMillis()
                if (delta > 14f && now - lastShakeMs > 900) {
                    lastShakeMs = now
                    haptic.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                    )
                    onShake()
                }
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
        }
        if (accel != null) {
            sensorManager.registerListener(
                listener,
                accel,
                android.hardware.SensorManager.SENSOR_DELAY_GAME,
            )
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }
}

@Composable
private fun BubbleItem(
    bubble: PackedBubble,
    bg: Color,
    fg: Color,
    enterDelayMs: Int,
    density: androidx.compose.ui.unit.Density,
) {
    // Scale-in on first appearance (keyed to category so it fires once per real change,
    // not every time we re-pack from a shake).
    val scale = remember(bubble.slice.categoryId) { Animatable(0f) }
    LaunchedEffect(bubble.slice.categoryId) {
        kotlinx.coroutines.delay(enterDelayMs.toLong())
        scale.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = 0.6f,
                stiffness = 260f,
            ),
        )
    }
    // Smoothly animate x/y so shake-driven re-packing looks like physics, not a teleport.
    val animX by androidx.compose.animation.core.animateFloatAsState(
        targetValue = bubble.x,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.58f,
            stiffness = 160f,
        ),
        label = "bubble-x-${bubble.slice.categoryId ?: -1}",
    )
    val animY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = bubble.y,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.58f,
            stiffness = 160f,
        ),
        label = "bubble-y-${bubble.slice.categoryId ?: -1}",
    )
    val diameterDp = with(density) { (bubble.r * 2).toDp() }
    val xDp = with(density) { (animX - bubble.r).toDp() }
    val yDp = with(density) { (animY - bubble.r).toDp() }
    // Amount label size scales with bubble radius — the heftiest categories read loudest.
    val amountSp = (bubble.r / 14f).coerceIn(10f, 22f).sp
    val labelSp = (bubble.r / 26f).coerceIn(8f, 12f).sp

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset(x = xDp, y = yDp)
            .size(diameterDp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value; alpha = scale.value }
            .clip(CircleShape)
            .background(bg),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatMoneyShort(Money(bubble.slice.totalMinor, bubble.slice.currency)),
                fontSize = amountSp,
                fontWeight = FontWeight.Bold,
                color = fg,
                maxLines = 1,
            )
            Text(
                text = bubble.slice.categoryName,
                fontSize = labelSp,
                color = fg.copy(alpha = 0.85f),
                maxLines = 1,
            )
        }
    }
}

/** Compact money formatter for bubbles — strips decimals and uses k/M suffixes. */
private fun formatMoneyShort(money: Money): String {
    val symbol = when (money.currency) {
        "LKR" -> "Rs "
        "USD" -> "$"
        else -> "${money.currency} "
    }
    val major = kotlin.math.abs(money.minorUnits) / 100
    return when {
        major >= 1_000_000 -> "$symbol${"%.1f".format(major / 1_000_000.0)}M"
        major >= 1_000 -> "$symbol${"%.1f".format(major / 1_000.0)}k"
        else -> "$symbol$major"
    }
}

/* -------------------------------------------------------------------------- */
/* Latest Insights card — dark pill highlight + vertical category stack       */
/* -------------------------------------------------------------------------- */

@Composable
private fun LatestInsightsCard(
    rangeLabel: String,
    totalSpend: Money,
    previousSpend: Long?,
    slices: List<InsightSlice>,
    modifier: Modifier = Modifier,
) {
    // Left column (labels + amount) / Right column (vertical stack of category circles
    // with the selected one promoted to a dark pill showing its % share).
    val selectedState = androidx.compose.runtime.remember(slices.hashCode()) {
        androidx.compose.runtime.mutableStateOf(0)
    }
    var selectedIdx = selectedState.value
    fun setSelected(i: Int) { selectedState.value = i }
    val selectedSlice = slices.getOrNull(selectedIdx)
    val monthOnly = rangeLabel.substringBefore(' ')
    val delta = previousSpend?.let { totalSpend.minorUnits - it } ?: 0L

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // --- LEFT: label + amount + delta
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            ) {
                Text(
                    text = "Here's how you've been spending",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = "Your Latest Insights",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = monthOnly,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Total expenses last month",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatMoney(totalSpend),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (delta >= 0) "↗" else "↘",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (previousSpend != null && delta != 0L) {
                    Spacer(Modifier.height(8.dp))
                    val absDelta = Money(kotlin.math.abs(delta), totalSpend.currency)
                    Text(
                        text = "${if (delta >= 0) "+" else "-"}${formatMoney(absDelta)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            // --- RIGHT: vertical column of circular category chips
            CategoryColumn(
                slices = slices,
                selectedIdx = selectedIdx,
                onSelect = ::setSelected,
                selectedSlice = selectedSlice,
            )
        }
    }
}

@Composable
private fun CategoryColumn(
    slices: List<InsightSlice>,
    selectedIdx: Int,
    onSelect: (Int) -> Unit,
    selectedSlice: InsightSlice?,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The selected category gets a wide dark pill showing its emoji + % share.
        // AnimatedContent crossfades the pill's emoji + % when the selection changes so
        // the swap feels intentional instead of a hard cut.
        if (selectedSlice != null) {
            androidx.compose.animation.AnimatedContent(
                targetState = selectedSlice,
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(tween(220))
                        + androidx.compose.animation.slideInHorizontally(tween(260)) { it / 4 }
                    ) togetherWith
                        (androidx.compose.animation.fadeOut(tween(140))
                            + androidx.compose.animation.slideOutHorizontally(tween(180)) { -it / 4 })
                },
                label = "dark-pill",
            ) { target ->
                DarkSelectedPill(slice = target)
            }
        }
        // Remaining categories render as small ink-filled circles, tappable. Each one
        // runs its own alpha-from-0 entrance on composition so the list reveals itself
        // smoothly when the selection flips and a new category takes the chip slot.
        slices.forEachIndexed { i, s ->
            if (i == selectedIdx) return@forEachIndexed
            CategoryCircle(
                slice = s,
                onClick = { onSelect(i) },
            )
        }
    }
}

@Composable
private fun DarkSelectedPill(slice: InsightSlice) {
    // Animated integer for the percentage so the pill reads as alive.
    val animPct by androidx.compose.animation.core.animateIntAsState(
        targetValue = (slice.percent * 100).toInt(),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "pct-${slice.categoryId ?: -1}",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.inverseSurface)
            .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        ) {
            Text(
                text = emojiForCategoryName(slice.categoryName),
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$animPct%",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
    }
}

@Composable
private fun CategoryCircle(slice: InsightSlice, onClick: () -> Unit) {
    // Scale-in on first appearance so the chip slot doesn't pop.
    val appear = remember(slice.categoryId) { Animatable(0.7f) }
    val alpha = remember(slice.categoryId) { Animatable(0f) }
    LaunchedEffect(slice.categoryId) {
        kotlinx.coroutines.coroutineScope {
            launch { appear.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
            launch { alpha.animateTo(1f, tween(200)) }
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .graphicsLayer {
                scaleX = appear.value; scaleY = appear.value; this.alpha = alpha.value
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = emojiForCategoryName(slice.categoryName),
            fontSize = 14.sp,
        )
    }
}

private fun emojiForCategoryName(name: String): String = when {
    name.equals("Groceries", ignoreCase = true) -> "🛒"
    name.equals("Food & Dining", ignoreCase = true) -> "🍔"
    name.equals("Transport", ignoreCase = true) -> "🚕"
    name.equals("Fuel", ignoreCase = true) -> "⛽"
    name.equals("Utilities", ignoreCase = true) -> "💡"
    name.equals("Online Subscriptions", ignoreCase = true) -> "📺"
    name.equals("Shopping", ignoreCase = true) -> "🛍️"
    name.equals("Healthcare", ignoreCase = true) -> "🩺"
    name.equals("Education", ignoreCase = true) -> "📚"
    name.equals("Entertainment", ignoreCase = true) -> "🎬"
    name.equals("Rent", ignoreCase = true) -> "🏠"
    name.equals("Salary", ignoreCase = true) -> "💰"
    name.equals("Transfers", ignoreCase = true) -> "💸"
    name.equals("Cash", ignoreCase = true) -> "💵"
    name.equals("Fees", ignoreCase = true) -> "🧾"
    else -> "🧾"
}

/* -------------------------------------------------------------------------- */
/* Top category callout                                                       */
/* -------------------------------------------------------------------------- */

@Composable
private fun TopCategoryCallout(
    slice: InsightSlice,
    rangeLabel: String,
    modifier: Modifier = Modifier,
) {
    val monthOnly = remember(rangeLabel) {
        // "April 2026" → "April". Keeps the sentence human.
        rangeLabel.substringBefore(' ')
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            text = "You spent",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${formatMoney(Money(slice.totalMinor, slice.currency))} on ${slice.categoryName} in $monthOnly",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 22.sp,
        )
    }
}

/* -------------------------------------------------------------------------- */
/* Category list row                                                          */
/* -------------------------------------------------------------------------- */

@Composable
private fun CategoryRow(slice: InsightSlice) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = slice.categoryName,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatMoney(Money(slice.totalMinor, slice.currency)),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    )
}

/* -------------------------------------------------------------------------- */
/* Formatting                                                                 */
/* -------------------------------------------------------------------------- */

private fun formatMoney(money: Money): String {
    val symbol = when (money.currency) {
        "LKR" -> "Rs "
        "USD" -> "$"
        else -> "${money.currency} "
    }
    val abs = kotlin.math.abs(money.minorUnits)
    val major = abs / 100
    val cents = abs % 100
    val formatter = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US)
    return "$symbol${formatter.format(major)}.${"%02d".format(cents)}"
}
