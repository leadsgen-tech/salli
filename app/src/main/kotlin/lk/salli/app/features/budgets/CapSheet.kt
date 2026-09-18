package lk.salli.app.features.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lk.salli.app.R
import lk.salli.data.db.entities.CategoryEntity
import lk.salli.design.components.CategoryChip
import lk.salli.design.components.PrimaryButton
import lk.salli.design.components.stage.Glass
import lk.salli.design.components.stage.GlassMark
import lk.salli.design.components.stage.SpringOdometer
import lk.salli.design.motion.LocalReducedMotion
import lk.salli.design.motion.rememberDeviceTilt
import lk.salli.design.theme.LocalSalliColors
import lk.salli.design.theme.SalliSpacing
import lk.salli.domain.Currency
import lk.salli.domain.DateRange
import lk.salli.domain.Money
import lk.salli.domain.money.MoneyFormat

/** What the sheet hands back: a cap over some categories, or over everything when [categoryIds] is empty. */
data class CapPayload(
    val name: String,
    val categoryIds: List<Long>,
    val capMinor: Long,
    val periodStartDay: Int,
    /** Per-category split of [capMinor], proportional to each category's usual spend. */
    val perCategoryMinor: Map<Long, Long>,
)

/**
 * Setting a cap by filling a glass. Pick what to cap, then touch the glass: the liquid rises to
 * the finger, the amount rides on the surface, and the walls carry what the last periods
 * actually cost as high-water marks with Tight / Usual / Loose rungs to snap to. Lift and the
 * level settles on a detent. No dial, no amount field; the name fills itself in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapSheet(
    categories: List<CategoryEntity>,
    history: List<CycleCategorySpend>,
    defaultPeriodStartDay: Int,
    initialCategoryId: Long?,
    onDismiss: () -> Unit,
    onSave: (CapPayload) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selected = remember { mutableStateListOf<Long>().apply { initialCategoryId?.let { add(it) } } }
    val everything = selected.isEmpty()
    val everythingLabel = stringResource(R.string.cap_everything)

    // History for the current selection, oldest first; the last entry is the running cycle.
    fun valueOf(cycle: CycleCategorySpend): Long =
        if (everything) cycle.totalMinor else selected.sumOf { cycle.byCategory[it] ?: 0L }
    val completed = history.filter { it.complete }
    val completedValues = completed.map(::valueOf)
    val usual: Long? = completedValues.filter { it > 0L }.sorted().let { v -> if (v.isEmpty()) null else v[v.size / 2] }
    val stopStep = 100_000L
    fun rounded(minor: Long): Long = ((minor + stopStep / 2) / stopStep) * stopStep
    fun shortMoney(minor: Long): String = MoneyFormat.short(Money(minor, Currency.LKR))
    // Presets: notches on the wall the finger snaps to, named by the chips under the glass.
    val presets = usual?.let { u ->
        listOf(
            GlassMark(stringResource(R.string.cap_stop_tight), rounded((u * 0.9).toLong())),
            GlassMark(stringResource(R.string.cap_stop_usual), rounded(u), strong = true),
            GlassMark(stringResource(R.string.cap_stop_loose), rounded((u * 1.1).toLong())),
        )
    }.orEmpty()
    val rungs = presets.map { it.copy(label = "") }
    // High-water marks: one dashed line per period, and one label per cluster of near-equal
    // periods, so three months that cost the same read as "Jun, Jul, Aug · Rs 132k".
    val periods = completed.map { c -> shortLabel(c.label) to valueOf(c) } +
        history.lastOrNull()?.takeIf { !it.complete && valueOf(it) > 0L }?.let { c ->
            stringResource(R.string.cap_so_far, shortLabel(c.label)) to valueOf(c)
        }.let { listOfNotNull(it) }
    val maxMinor = niceMax(maxOf(periods.maxOfOrNull { it.second } ?: 0L, (usual ?: 0L) * 16 / 10, 1_000_000L))
    val tides = periods.map { (_, v) -> GlassMark("", v) } + clusterLabels(periods, maxMinor, ::shortMoney)

    var cap by remember { mutableLongStateOf(0L) }
    var lastSelectionKey by remember { mutableStateOf("") }
    val selectionKey = selected.joinToString(",")
    if (selectionKey != lastSelectionKey) {
        // A new selection re-seats the level on its usual amount (or half the glass).
        lastSelectionKey = selectionKey
        cap = rounded(usual ?: (maxMinor / 2))
    }

    val autoName = if (everything) everythingLabel else selected.mapNotNull { id -> categories.firstOrNull { it.id == id }?.name }.joinToString(" + ")
    var name by remember { mutableStateOf("") }
    var nameEdited by remember { mutableStateOf(false) }
    if (!nameEdited) name = autoName

    val cycleDays = remember(defaultPeriodStartDay) {
        val cycle = DateRange.cycleFor(System.currentTimeMillis(), defaultPeriodStartDay)
        ((cycle.untilMillis - cycle.fromMillis) / 86_400_000L).toInt().coerceAtLeast(1)
    }
    val reduced = LocalReducedMotion.current
    val tilt by rememberDeviceTilt()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(SalliSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.cap_title) + " · " + autoName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // What to cap: everything, or any set of categories.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                CategoryChip(
                    name = everythingLabel,
                    iconName = "category",
                    colorSeed = 11,
                    selected = everything,
                    onClick = { selected.clear() },
                )
                categories.forEach { cat ->
                    CategoryChip(
                        name = cat.name,
                        iconName = cat.iconName,
                        colorSeed = cat.colorSeed,
                        selected = cat.id in selected,
                        onClick = { if (cat.id in selected) selected.remove(cat.id) else selected.add(cat.id) },
                    )
                }
            }
            Glass(
                valueMinor = cap,
                maxMinor = maxMinor,
                onChange = { cap = it },
                rungs = rungs,
                tides = tides,
                stepMinor = stopStep,
                tilt = tilt,
                reducedMotion = reduced,
                modifier = Modifier.fillMaxWidth().height(280.dp),
            ) { value ->
                Column {
                    SpringOdometer(
                        text = MoneyFormat.formatMinor(value, Currency.LKR),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        reducedMotion = reduced,
                    )
                    Text(
                        text = buildString {
                            append(stringResource(R.string.cap_per_day, MoneyFormat.formatMinor(value / cycleDays, Currency.LKR)))
                            if (usual != null) {
                                append(" · ")
                                val diff = value - rounded(usual)
                                append(
                                    when {
                                        diff == 0L -> stringResource(R.string.cap_exactly_usual)
                                        diff < 0L -> stringResource(R.string.cap_under_usual, shortMoney(-diff))
                                        else -> stringResource(R.string.cap_over_usual, shortMoney(diff))
                                    },
                                )
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (presets.isEmpty()) {
                Text(
                    text = stringResource(R.string.cap_no_history),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { p ->
                        val hit = p.minor == cap
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (hit) LocalSalliColors.current.hero else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { cap = p.minor }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = p.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (hit) LocalSalliColors.current.onHero else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            PillTextField(
                value = name,
                onValueChange = { name = it; nameEdited = true },
                placeholder = stringResource(R.string.cap_name_placeholder),
            )
            Text(
                text = stringResource(R.string.cap_resets, defaultPeriodStartDay),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(
                text = stringResource(R.string.cap_set),
                enabled = cap > 0L && name.isNotBlank(),
                onClick = {
                    onSave(
                        CapPayload(
                            name = name.trim(),
                            categoryIds = selected.toList(),
                            capMinor = cap,
                            periodStartDay = defaultPeriodStartDay,
                            perCategoryMinor = splitCap(cap, selected.toList(), completed),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** "September 2026" → "Sep". */
private fun shortLabel(label: String): String = label.take(3)

/**
 * One label per cluster of periods whose levels would sit on top of each other (within 5 % of
 * the glass), placed at the cluster's mean: "Jun, Jul · Rs 132k".
 */
internal fun clusterLabels(periods: List<Pair<String, Long>>, maxMinor: Long, money: (Long) -> String): List<GlassMark> {
    if (periods.isEmpty() || maxMinor <= 0L) return emptyList()
    val gap = maxMinor * 0.05
    val sorted = periods.sortedBy { it.second }
    val clusters = mutableListOf<MutableList<Pair<String, Long>>>()
    sorted.forEach { p ->
        val last = clusters.lastOrNull()
        if (last != null && p.second - last.first().second < gap) last.add(p) else clusters.add(mutableListOf(p))
    }
    return clusters.map { c ->
        val mean = c.sumOf { it.second } / c.size
        GlassMark(c.joinToString(", ") { it.first } + " · " + money(mean), mean, line = false)
    }
}

/** The glass's brim: a round number comfortably above everything it needs to show. */
private fun niceMax(minor: Long): Long {
    val major = (minor / 100.0).coerceAtLeast(1.0)
    var step = 1000.0
    while (step * 10 <= major) step *= 10
    val candidates = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0).map { it * step }
    val nice = candidates.firstOrNull { it >= major * 1.15 } ?: step * 10
    return (nice * 100).toLong()
}

/**
 * One cap over several categories is stored as one line per category. The split follows each
 * category's usual share of the total, so a Rs 30k cap over Groceries + Transport lands where
 * the money actually goes; with no history it splits evenly.
 */
internal fun splitCap(capMinor: Long, categoryIds: List<Long>, completed: List<CycleCategorySpend>): Map<Long, Long> {
    if (categoryIds.isEmpty()) return emptyMap()
    val usual = categoryIds.associateWith { id ->
        val v = completed.map { it.byCategory[id] ?: 0L }.sorted()
        if (v.isEmpty()) 0L else v[v.size / 2]
    }
    val total = usual.values.sum()
    val shares = if (total <= 0L) categoryIds.associateWith { 1.0 / categoryIds.size } else usual.mapValues { it.value.toDouble() / total }
    val split = LinkedHashMap<Long, Long>()
    var assigned = 0L
    categoryIds.forEachIndexed { index, id ->
        val minor = if (index == categoryIds.lastIndex) capMinor - assigned else (capMinor * shares.getValue(id)).toLong()
        split[id] = minor
        assigned += minor
    }
    return split
}
