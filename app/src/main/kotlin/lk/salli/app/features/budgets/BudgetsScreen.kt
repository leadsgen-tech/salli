package lk.salli.app.features.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGroceryStore
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalMovies
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.Money
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import lk.salli.data.db.entities.AccountEntity
import lk.salli.data.db.entities.CategoryEntity
import lk.salli.design.components.EmptyState

@Composable
fun BudgetsScreen(viewModel: BudgetsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var editing by remember { mutableStateOf<BudgetUi?>(null) }
    var creating by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        lk.salli.design.components.SalliPullToRefresh(
            isRefreshing = refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
        LazyColumn(
            contentPadding = PaddingValues(top = statusBar, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item("header") {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        text = "Budgets",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = "Each budget tracks its own cycle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.budgets.isEmpty() && !state.loading) {
                item("empty") {
                    EmptyState(
                        title = "Set your first budget",
                        message = "Cap a category like Groceries, or just set a total monthly ceiling.",
                        icon = Icons.Outlined.Savings,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 40.dp),
                    )
                }
            } else {
                items(state.budgets, key = { it.id }) { budget ->
                    BudgetCard(budget = budget, onClick = { editing = budget })
                }
            }
        }
        }

        FloatingActionButton(
            onClick = { creating = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 118.dp),
        ) {
            Icon(imageVector = Icons.Outlined.Add, contentDescription = "New budget")
        }
    }

    if (creating) {
        BudgetSheet(
            mode = BudgetSheetMode.Create,
            categories = state.availableCategories,
            accounts = state.availableAccounts,
            initial = null,
            onDismiss = { creating = false },
            onSave = { payload ->
                viewModel.create(
                    name = payload.name,
                    currency = "LKR",
                    capMode = payload.capMode,
                    lines = payload.lines,
                    totalCapMinor = payload.totalCapMinor,
                    accountIds = payload.accountIds,
                    periodStartDay = payload.periodStartDay,
                )
                creating = false
            },
            onDelete = {},
        )
    }
    editing?.let { b ->
        BudgetSheet(
            mode = BudgetSheetMode.Edit,
            categories = state.availableCategories,
            accounts = state.availableAccounts,
            initial = b,
            onDismiss = { editing = null },
            onSave = { payload ->
                viewModel.update(
                    budgetId = b.id,
                    name = payload.name,
                    capMode = payload.capMode,
                    lines = payload.lines,
                    totalCapMinor = payload.totalCapMinor,
                    accountIds = payload.accountIds,
                    periodStartDay = payload.periodStartDay,
                )
                editing = null
            },
            onDelete = {
                viewModel.delete(b.id)
                editing = null
            },
        )
    }
}

/* ------------------------------------- Budget card -------------------------------------- */

@Composable
private fun BudgetCard(budget: BudgetUi, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = budget.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = budget.cycleLabel,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ModeMicroChip(mode = budget.capMode)
                        ScopeMicroChip(scope = budget.accountScope)
                    }
                }
                PaceBadge(pace = budget.pace, progress = budget.progress)
            }
            Spacer(Modifier.height(14.dp))
            // Progress track — with a marker at expected-burn to contextualise the fill.
            ProgressTrack(
                progress = budget.progress.coerceIn(0f, 1.2f),
                expectedFraction = budget.paceExpectedFraction,
                over = budget.overBudget,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatMoney(budget.totalSpentMinor, budget.currency),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = remainingLabel(budget),
                    fontSize = 12.sp,
                    color = when (budget.pace) {
                        BudgetPace.Over -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (budget.capMode == BudgetCapMode.PerCategory && budget.lines.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    budget.lines.forEach { line -> BudgetLineRow(line) }
                }
            }
        }
    }
}

@Composable
private fun ProgressTrack(progress: Float, expectedFraction: Float, over: Boolean) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val fillColor = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(trackColor),
    ) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            color = fillColor,
            trackColor = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
        )
        // Expected-burn marker — a faint 2dp vertical line at the expected pace.
        if (expectedFraction in 0.02f..0.98f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(expectedFraction.coerceIn(0f, 1f))
                    .fillMaxSize(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(12.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)),
                )
            }
        }
    }
}

@Composable
private fun PaceBadge(pace: BudgetPace, progress: Float) {
    val (label, bg, fg) = when (pace) {
        BudgetPace.Over -> Triple("OVER", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        BudgetPace.Hot -> Triple("HOT", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        BudgetPace.OnPace -> Triple("ON PACE", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface)
        BudgetPace.Under -> Triple("UNDER", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    }
    Column(horizontalAlignment = Alignment.End) {
        Surface(color = bg, shape = RoundedCornerShape(10.dp)) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${(progress * 100).toInt()}%",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ModeMicroChip(mode: BudgetCapMode) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = if (mode == BudgetCapMode.Total) "Total cap" else "By category",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun ScopeMicroChip(scope: AccountScope) {
    val label = when {
        scope.allAccounts -> "All accounts"
        scope.specificAccounts.size == 1 -> scope.specificAccounts.first().displayName
        else -> "${scope.specificAccounts.size} accounts"
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun BudgetLineRow(line: BudgetLineUi) {
    val color = Color(line.colorSeed)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .background(color.copy(alpha = 0.18f), CircleShape),
        ) {
            Icon(
                imageVector = iconForName(line.iconName),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.categoryName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            LinearProgressIndicator(
                progress = { line.progress.coerceIn(0f, 1f) },
                color = if (line.overBudget) MaterialTheme.colorScheme.error else color,
                trackColor = color.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .padding(top = 4.dp),
            )
        }
        Spacer(Modifier.size(10.dp))
        Text(
            text = formatMoney(line.spent.minorUnits, line.cap.currency) +
                " / " + formatMoney(line.cap.minorUnits, line.cap.currency),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* --------------------------------- Create / Edit sheet ---------------------------------- */

private enum class BudgetSheetMode { Create, Edit }

private data class SavePayload(
    val name: String,
    val capMode: BudgetCapMode,
    val lines: List<Pair<Long, Long>>,
    val totalCapMinor: Long?,
    val accountIds: List<Long>,
    val periodStartDay: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetSheet(
    mode: BudgetSheetMode,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    initial: BudgetUi?,
    onDismiss: () -> Unit,
    onSave: (SavePayload) -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var capMode by remember { mutableStateOf(initial?.capMode ?: BudgetCapMode.PerCategory) }
    var totalCapText by remember {
        mutableStateOf(
            if (initial?.capMode == BudgetCapMode.Total) majorString(initial.totalCapMinor) else "",
        )
    }
    var periodStartDay by remember { mutableStateOf(1) }

    val caps = remember { mutableStateMapOf<Long, String>() }
    val selected = remember { mutableStateListOf<Long>() }
    // Empty = all accounts; that's the default for a new budget, too.
    val scopedAccounts = remember { mutableStateListOf<Long>() }

    LaunchedEffect(initial) {
        caps.clear(); selected.clear(); scopedAccounts.clear()
        if (initial != null) {
            initial.lines.forEach { line ->
                caps[line.categoryId] = majorString(line.cap.minorUnits)
                selected += line.categoryId
            }
            if (!initial.accountScope.allAccounts) {
                scopedAccounts += initial.accountScope.specificAccounts.map { it.id }
            }
        }
    }

    val canSave = name.isNotBlank() && when (capMode) {
        BudgetCapMode.Total -> (parseMajorToMinor(totalCapText) ?: 0L) > 0L
        BudgetCapMode.PerCategory -> selected.any {
            (caps[it]?.let { s -> parseMajorToMinor(s) } ?: 0L) > 0L
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (mode == BudgetSheetMode.Create) "New budget" else "Edit budget",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (mode == BudgetSheetMode.Edit) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Mode toggle — two full-width pills that read like a choice, not a switch.
            ModeToggle(capMode = capMode, onSelect = { capMode = it })

            // Name pill
            SectionLabel("Name this budget")
            PillTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "e.g. Monthly essentials",
            )

            // Total-cap input appears only in Total mode.
            if (capMode == BudgetCapMode.Total) {
                SectionLabel("Monthly ceiling")
                AmountPill(
                    value = totalCapText,
                    onValueChange = { totalCapText = it },
                    placeholder = "Rs 0",
                )
            }

            // Scope picker — always visible. "All accounts" is the implicit default when the
            // specific-accounts row is empty.
            SectionLabel("Which accounts?")
            ScopePicker(
                accounts = accounts,
                scopedAccountIds = scopedAccounts,
                onToggle = { id ->
                    if (id in scopedAccounts) scopedAccounts -= id else scopedAccounts += id
                },
                onAll = { scopedAccounts.clear() },
            )
        }

        // Category picker + cap rows (per-category mode only).
        if (capMode == BudgetCapMode.PerCategory) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                SectionLabel("Pick categories to cap")
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    categories.forEach { cat ->
                        val isSelected = cat.id in selected
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) selected -= cat.id else selected += cat.id
                            },
                            label = { Text(cat.name) },
                            leadingIcon = {
                                Icon(
                                    imageVector = iconForName(cat.iconName),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val selectedCategories = selected.mapNotNull { id -> categories.firstOrNull { it.id == id } }
                items(selectedCategories, key = { it.id }) { cat ->
                    CapRow(
                        category = cat,
                        value = caps[cat.id].orEmpty(),
                        onValueChange = { caps[cat.id] = it },
                        onRemove = { selected -= cat.id; caps.remove(cat.id) },
                    )
                }
                if (selectedCategories.isEmpty()) {
                    item {
                        Text(
                            text = "Pick at least one category above to set its cap.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        }

        // Period start day — compact horizontal picker, hugs the bottom so it's out of the way
        // but still discoverable.
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            SectionLabel("Resets on")
            Spacer(Modifier.height(6.dp))
            PeriodStartDayPicker(
                selected = periodStartDay,
                onSelect = { periodStartDay = it },
            )
        }

        // Save pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (canSave) MaterialTheme.colorScheme.inverseSurface
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                )
                .clickable(enabled = canSave) {
                    val payload = SavePayload(
                        name = name,
                        capMode = capMode,
                        lines = if (capMode == BudgetCapMode.PerCategory) {
                            selected.mapNotNull { id ->
                                val cents = parseMajorToMinor(caps[id].orEmpty()) ?: return@mapNotNull null
                                if (cents <= 0L) null else id to cents
                            }
                        } else emptyList(),
                        totalCapMinor = if (capMode == BudgetCapMode.Total) parseMajorToMinor(totalCapText) else null,
                        accountIds = scopedAccounts.toList(),
                        periodStartDay = periodStartDay,
                    )
                    onSave(payload)
                    scope.launch { sheetState.hide() }
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (mode == BudgetSheetMode.Create) "Create budget" else "Save changes",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (canSave) MaterialTheme.colorScheme.inverseOnSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/* --------------------------------- Sheet pieces ----------------------------------------- */

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ModeToggle(capMode: BudgetCapMode, onSelect: (BudgetCapMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModeOption(
            label = "By category",
            selected = capMode == BudgetCapMode.PerCategory,
            onClick = { onSelect(BudgetCapMode.PerCategory) },
            modifier = Modifier.weight(1f),
        )
        ModeOption(
            label = "Total cap",
            selected = capMode == BudgetCapMode.Total,
            onClick = { onSelect(BudgetCapMode.Total) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeOption(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceContainerHighest
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun AmountPill(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    BasicTextField(
        value = value,
        onValueChange = { new ->
            val cleaned = new.filter { it.isDigit() || it == '.' }
            onValueChange(cleaned)
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.displaySmall.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Rs",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder.removePrefix("Rs ").ifBlank { "0" },
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            }
        },
    )
}

@Composable
private fun ScopePicker(
    accounts: List<AccountEntity>,
    scopedAccountIds: List<Long>,
    onToggle: (Long) -> Unit,
    onAll: () -> Unit,
) {
    val allSelected = scopedAccountIds.isEmpty()
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 8.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = allSelected,
                onClick = { onAll() },
                label = { Text("All accounts") },
                leadingIcon = if (allSelected) {
                    {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
        items(accounts, key = { it.id }) { account ->
            val isSelected = account.id in scopedAccountIds
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(account.id) },
                label = { Text(account.displayName) },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else null,
            )
        }
    }
}

@Composable
private fun PeriodStartDayPicker(selected: Int, onSelect: (Int) -> Unit) {
    // Show common choices as chips, rest as a compact row. Keep it at 28 max — a period that
    // starts on Feb 29 would be a footgun; clamping mirrors real paycheck behaviour.
    val common = listOf(1, 5, 10, 15, 20, 25, 28)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(common, key = { it }) { day ->
            val isSelected = day == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.inverseSurface
                        else MaterialTheme.colorScheme.surfaceContainer,
                    )
                    .clickable(onClick = { onSelect(day) })
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = if (day == 1) "1st (calendar)" else "${day}${suffix(day)}",
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.inverseOnSurface
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun suffix(day: Int): String = when {
    day in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}

@Composable
private fun CapRow(
    category: CategoryEntity,
    value: String,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val color = Color(category.colorSeed)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .background(color.copy(alpha = 0.18f), CircleShape),
        ) {
            Icon(
                imageVector = iconForName(category.iconName),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Cap (LKR)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = { new ->
                val cleaned = new.filter { it.isDigit() || it == '.' }
                onValueChange(cleaned)
            },
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            singleLine = true,
            modifier = Modifier.width(110.dp),
            decorationBox = { inner ->
                Box(
                    contentAlignment = Alignment.CenterEnd,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = "0",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.size(4.dp))
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/* --------------------------------- Helpers ---------------------------------------------- */

private fun remainingLabel(budget: BudgetUi): String {
    val absMinor = kotlin.math.abs(budget.remainingMinor)
    val prefix = if (budget.remainingMinor < 0) "Over by " else "Left "
    return "$prefix${formatMoney(absMinor, budget.currency)}"
}

private fun parseMajorToMinor(raw: String): Long? {
    val trimmed = raw.trim().takeIf { it.isNotEmpty() } ?: return null
    val dot = trimmed.indexOf('.')
    return try {
        if (dot < 0) trimmed.toLong() * 100
        else {
            val whole = trimmed.substring(0, dot).ifEmpty { "0" }.toLong()
            val frac = trimmed.substring(dot + 1).take(2).padEnd(2, '0').toLong()
            whole * 100 + frac
        }
    } catch (_: NumberFormatException) {
        null
    }
}

private fun majorString(minor: Long): String {
    val whole = minor / 100
    val cents = minor % 100
    return if (cents == 0L) whole.toString() else "$whole.${"%02d".format(cents)}"
}

private fun formatMoney(minor: Long, currency: String): String {
    val symbol = if (currency == "LKR") "Rs " else "$currency "
    val abs = kotlin.math.abs(minor)
    val major = abs / 100
    val cents = abs % 100
    val formatter = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US)
    return "$symbol${formatter.format(major)}.${"%02d".format(cents)}"
}

private fun iconForName(name: String): ImageVector = when (name) {
    "restaurant", "fastfood", "food" -> Icons.Outlined.Restaurant
    "cafe" -> Icons.Outlined.LocalCafe
    "grocery", "groceries" -> Icons.Outlined.LocalGroceryStore
    "transport", "car" -> Icons.Outlined.DirectionsCar
    "taxi", "ride" -> Icons.Outlined.LocalTaxi
    "fuel" -> Icons.Outlined.Toll
    "utilities", "bill" -> Icons.Outlined.Wifi
    "subscriptions" -> Icons.Outlined.Subscriptions
    "shopping" -> Icons.Outlined.ShoppingBag
    "healthcare", "medical" -> Icons.Outlined.LocalHospital
    "education" -> Icons.Outlined.School
    "entertainment", "movies" -> Icons.Outlined.LocalMovies
    "rent", "home" -> Icons.Outlined.Home
    "salary", "income" -> Icons.Outlined.AttachMoney
    "cash", "atm" -> Icons.Outlined.Money
    "fees" -> Icons.Outlined.PieChart
    else -> Icons.Outlined.Receipt
}
