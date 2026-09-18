package lk.salli.app.features.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Calendar
import lk.salli.app.R
import lk.salli.app.features.budgets.PillTextField
import lk.salli.design.components.PrimaryButton
import lk.salli.design.components.stage.CapDial
import lk.salli.design.components.stage.DialMark
import lk.salli.design.components.stage.GoalJarTile
import lk.salli.design.components.stage.SpringOdometer
import lk.salli.design.motion.LocalReducedMotion
import lk.salli.design.theme.LocalSalliColors
import lk.salli.design.theme.SalliSpacing
import lk.salli.domain.Currency
import lk.salli.domain.Money
import lk.salli.domain.money.MoneyFormat

/**
 * Starting a jar, the same way a cap is set: pull the target on a dial, pick a horizon, name
 * it, done. The jar on the right previews the level the first period should reach. One sheet,
 * no separate page, the same shape as the cap sheet so the two never feel like different apps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarSheet(
    onDismiss: () -> Unit,
    onStart: (name: String, targetMinor: Long, targetDate: Long?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val reduced = LocalReducedMotion.current
    var target by remember { mutableLongStateOf(5_000_000L) }
    var months by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }

    val stops = listOf(
        DialMark("25k", 2_500_000L),
        DialMark("50k", 5_000_000L),
        DialMark("100k", 10_000_000L),
        DialMark("250k", 25_000_000L),
        DialMark("500k", 50_000_000L),
    )
    val horizons = listOf(0, 3, 6, 12)
    val perMonth = if (months > 0) target / months else null
    val placeholder = stringResource(R.string.jar_name_placeholder)

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
                text = stringResource(R.string.jar_title) + " · " + name.ifBlank { placeholder },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    SpringOdometer(
                        text = MoneyFormat.formatMinor(target, Currency.LKR),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        reducedMotion = reduced,
                    )
                    Text(
                        text = perMonth?.let { stringResource(R.string.jar_per_month, MoneyFormat.formatMinor(it, Currency.LKR)) }
                            ?: stringResource(R.string.jar_pick_date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // The jar as it will look on Plan: empty, its first-period line already drawn.
                GoalJarTile(
                    name = name.ifBlank { placeholder },
                    savedMinor = 0L,
                    targetMinor = target,
                    lineMinor = perMonth,
                    formatAmount = { MoneyFormat.short(Money(it, Currency.LKR)) },
                    onPour = {},
                    canPour = false,
                    reducedMotion = reduced,
                    jarHeight = 84.dp,
                    modifier = Modifier.width(88.dp),
                )
            }
            CapDial(
                valueMinor = target,
                maxMinor = 60_000_000L,
                onChange = { target = it },
                stops = stops,
                stepMinor = 500_000L,
                reducedMotion = reduced,
            )
            Text(
                text = stringResource(R.string.jar_by_when),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                horizons.forEach { h ->
                    val label = when (h) {
                        0 -> stringResource(R.string.jar_no_date)
                        12 -> stringResource(R.string.jar_one_year)
                        else -> stringResource(R.string.jar_months, h)
                    }
                    val hit = months == h
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (hit) LocalSalliColors.current.hero else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { months = h }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hit) LocalSalliColors.current.onHero else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            PillTextField(value = name, onValueChange = { name = it }, placeholder = placeholder)
            Spacer(Modifier.height(SalliSpacing.xxs))
            PrimaryButton(
                text = stringResource(R.string.jar_start),
                enabled = target > 0L && name.isNotBlank(),
                onClick = {
                    val date = if (months == 0) null else Calendar.getInstance().apply {
                        add(Calendar.MONTH, months)
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    onStart(name.trim(), target, date)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
