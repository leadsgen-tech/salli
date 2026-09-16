package lk.salli.app.features.planning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import lk.salli.data.planning.PlanningSnapshot
import lk.salli.design.format.MoneyFormat
import lk.salli.domain.Money
import lk.salli.domain.planning.BudgetBasis
import lk.salli.domain.planning.Commitment
import lk.salli.domain.planning.CommitmentKind
import lk.salli.domain.planning.RunwayResult

/** Compact Home card: today's safe-to-spend and the runway, tap for the breakdown. */
@Composable
fun SafeToSpendCard(snapshot: PlanningSnapshot, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val safe = snapshot.safeToSpend
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(onClickLabel = "View safe-to-spend breakdown", onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Safe to spend today", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.72f))
                Text(
                    text = safe.perDayMinor?.let { MoneyFormat.format(Money(it, snapshot.currency)) } ?: "Set a limit or build history",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(text = runwayLine(snapshot.runway), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.72f))
            }
            Icon(imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

private fun runwayLine(r: RunwayResult): String = when {
    r.countedAccounts.isEmpty() -> "Runway: no account balance known"
    r.days == null -> "Runway: no spending in the last ${r.windowDays} days"
    r.days == 0 -> "Runway under a day at your usual spending"
    else -> "Runway ${r.days} day${if (r.days == 1) "" else "s"} at your usual spending"
}

@Composable
fun SafeToSpendScreen(
    onBack: () -> Unit,
    viewModel: SafeToSpendViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(modifier = Modifier.fillMaxSize().padding(top = statusBar)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Safe to spend", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = snapshot?.cycle?.label.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val snap = snapshot ?: return@Column
        val money: (Long) -> String = { MoneyFormat.format(Money(it, snap.currency)) }
        val safe = snap.safeToSpend

        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Card {
                    Text(text = "Safe to spend today", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = safe.perDayMinor?.let(money) ?: "—",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = when {
                            safe.basis is BudgetBasis.NoHistory -> "Needs a monthly limit, or one complete period of history."
                            (safe.leftMinor ?: 0L) < 0L -> "This period is already over budget."
                            else -> "What is left for the period, spread over the ${safe.daysLeft} day${if (safe.daysLeft == 1) "" else "s"} to go."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionLabel("THIS PERIOD") }
            item {
                Card {
                    Line("Budget", basisText(safe.basis, money), safe.budgetMinor?.let(money) ?: "—")
                    Line("Spent so far", "Declines and your own transfers left out", money(safe.spentMinor))
                    Line("Spoken for", "Listed below", money(safe.committedMinor))
                    Line(
                        "Left for the period",
                        null,
                        safe.leftMinor?.let { if (it < 0L) "Over by ${money(-it)}" else money(it) } ?: "—",
                    )
                    Line("Days left", "Today included", "${safe.daysLeft}")
                }
            }

            item { SectionLabel("SPOKEN FOR BEFORE THE PERIOD ENDS") }
            if (safe.commitments.isEmpty()) {
                item { Note("Nothing due before this period ends.") }
            } else {
                items(safe.commitments) { c -> CommitmentLine(c, money) }
            }

            item { SectionLabel("RUNWAY") }
            item {
                val r = snap.runway
                val dateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
                Card {
                    Line("Balance counted", r.countedAccounts.joinToString().ifBlank { "No account balance known" }, money(r.balanceMinor))
                    if (r.notCountedAccounts.isNotEmpty()) {
                        Line("Not counted", r.notCountedAccounts.joinToString() + " · balance never seen", "—")
                    }
                    Line("Average daily spend", "Last ${r.windowDays} days", money(r.avgDailySpendMinor))
                    Line(
                        "Runway",
                        r.untilMillis?.let { "Until about ${dateFmt.format(Date(it))}" },
                        r.days?.let { if (it == 0) "Under a day" else "$it day${if (it == 1) "" else "s"}" } ?: "—",
                    )
                }
            }

            item {
                Note(
                    "Open bills count at what is still owed. Repeating payments count when you confirm them, or when Salli is sure they are fixed. " +
                        "Dated goals add what is left to save this period. Set a monthly limit in Settings → Spending period.",
                )
            }
        }
    }
}

private fun basisText(basis: BudgetBasis, money: (Long) -> String): String = when (basis) {
    is BudgetBasis.UserLimit -> "Your monthly limit"
    is BudgetBasis.MedianOfCycles -> "Median of " + basis.cycles.joinToString { "${it.label} (${money(it.spentMinor)})" }
    BudgetBasis.NoHistory -> "No complete period yet · set a limit in Settings"
}

@Composable
private fun CommitmentLine(c: Commitment, money: (Long) -> String) {
    val dateFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val kind = when (c.kind) {
        CommitmentKind.BILL -> "Bill"
        CommitmentKind.RECURRING -> "Repeating payment"
        CommitmentKind.GOAL -> "Savings goal"
    }
    Card {
        Line(c.label, kind + (c.dueAt?.let { " · ${if (c.kind == CommitmentKind.GOAL) "by" else "due"} ${dateFmt.format(Date(it))}" } ?: ""), money(c.amountMinor))
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun Line(label: String, detail: String?, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            detail?.let { Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.size(12.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}
