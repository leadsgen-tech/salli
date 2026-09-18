package lk.salli.app.features.budgets

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lk.salli.app.R
import lk.salli.data.db.entities.CategoryEntity
import lk.salli.design.components.CategoryChip
import lk.salli.design.components.PrimaryButton
import lk.salli.design.components.stage.CapDial
import lk.salli.design.components.stage.DialMark
import lk.salli.design.components.stage.LiquidFill
import lk.salli.design.components.stage.SpringOdometer
import lk.salli.design.motion.rememberDeviceTilt
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import lk.salli.design.motion.LocalReducedMotion
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
 * Setting a cap by pulling a dial. Pick what to cap, then drag: the dial starts from what the
 * last periods actually cost, snaps to Tight / Usual / Loose, and the lines under the number
 * turn the cap into a decision. No amount field; the name fills itself in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapDialSheet(
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
    val stops = usual?.let { u ->
        listOf(
            DialMark(stringResource(R.string.cap_stop_tight), rounded((u * 0.9).toLong())),
            DialMark(stringResource(R.string.cap_stop_usual), rounded(u)),
            DialMark(stringResource(R.string.cap_stop_loose), rounded((u * 1.1).toLong())),
        )
    }.orEmpty()
    val ghosts = completed.map { c -> DialMark("${shortLabel(c.label)} ${MoneyFormat.short(Money(valueOf(c), Currency.LKR))}", valueOf(c)) } +
        history.lastOrNull()?.takeIf { !it.complete }?.let { c ->
            DialMark(stringResource(R.string.cap_so_far, MoneyFormat.short(Money(valueOf(c), Currency.LKR))), valueOf(c))
        }.let { listOfNotNull(it) }
    val maxMinor = niceMax(maxOf(ghosts.maxOfOrNull { it.minor } ?: 0L, (usual ?: 0L) * 16 / 10, 1_000_000L))

    var cap by remember { mutableLongStateOf(0L) }
    var lastSelectionKey by remember { mutableStateOf("") }
    val selectionKey = selected.joinToString(",")
    if (selectionKey != lastSelectionKey) {
        // A new selection re-seats the dial on its usual amount (or the middle of the track).
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

    // The sheet is the vessel: the cap you pull is the level the liquid rises to.
    val tilt by rememberDeviceTilt()
    var stirring by remember { mutableStateOf(false) }
    LaunchedEffect(cap) { stirring = true; delay(700); stirring = false }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Box {
        LiquidFill(
            fraction = if (maxMinor > 0L) cap.toFloat() / maxMinor else 0f,
            alpha = 0.16f,
            tilt = tilt,
            stirring = stirring,
            reducedMotion = reduced,
            modifier = Modifier.matchParentSize(),
        )
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
            SpringOdometer(
                text = MoneyFormat.formatMinor(cap, Currency.LKR),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                reducedMotion = reduced,
            )
            Text(
                text = if (usual != null) {
                    stringResource(R.string.cap_usual_line, MoneyFormat.formatMinor(usual, Currency.LKR), autoName.lowercase())
                } else {
                    stringResource(R.string.cap_no_history)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CapDial(
                valueMinor = cap,
                maxMinor = maxMinor,
                onChange = { cap = it },
                ghosts = ghosts,
                usualMinor = usual,
                stops = stops,
                reducedMotion = reduced,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = buildString {
                    append(stringResource(R.string.cap_per_day, MoneyFormat.formatMinor(cap / cycleDays, Currency.LKR)))
                    if (usual != null) {
                        append(" · ")
                        val diff = cap - usual
                        append(
                            when {
                                diff == 0L -> stringResource(R.string.cap_exactly_usual)
                                diff < 0L -> stringResource(R.string.cap_under_usual, MoneyFormat.short(Money(-diff, Currency.LKR)))
                                else -> stringResource(R.string.cap_over_usual, MoneyFormat.short(Money(diff, Currency.LKR)))
                            },
                        )
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(SalliSpacing.xs))
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
}
/** "September 2026" → "Sep". */
private fun shortLabel(label: String): String = label.take(3)

/** The dial's end: a round number comfortably above everything it needs to show. */
private fun niceMax(minor: Long): Long {
    val major = (minor / 100.0).coerceAtLeast(1.0)
    var step = 1000.0
    while (step * 10 <= major) step *= 10
    val candidates = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * step }
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
