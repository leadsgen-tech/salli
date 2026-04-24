package lk.salli.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lk.salli.domain.DateRange

/**
 * A row that shows the active [range] with prev/next month arrows and a calendar pill that
 * opens a Material3 DateRangePicker for arbitrary-range selection. Used consistently across
 * Insights, Timeline, and Budgets so the user has one mental model for time navigation.
 *
 * Calls:
 *  - [onPrev]: step backward by one month (or page, depending on screen)
 *  - [onNext]: step forward by one month (or page)
 *  - [onRange]: user picked a custom range via the dialog
 */
@Composable
fun DateRangeSelector(
    range: DateRange,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onRange: (DateRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showingPicker by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = "Previous period",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .clickable { showingPicker = true },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = range.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "Next period",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    if (showingPicker) {
        CustomRangeDialog(
            initial = range,
            onDismiss = { showingPicker = false },
            onConfirm = { picked ->
                showingPicker = false
                onRange(picked)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(
    initial: DateRange,
    onDismiss: () -> Unit,
    onConfirm: (DateRange) -> Unit,
) {
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial.fromMillis,
        // DateRangePicker uses inclusive end; our range is half-open so subtract a millisecond
        // so the "until" millis round-trips to the last included day.
        initialSelectedEndDateMillis = initial.untilMillis - 1,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            DateRangePicker(
                state = pickerState,
                title = null,
                showModeToggle = false,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val from = pickerState.selectedStartDateMillis
                    val to = pickerState.selectedEndDateMillis
                    if (from != null && to != null) {
                        onConfirm(DateRange.ofUtc(from, to))
                    } else {
                        onDismiss()
                    }
                },
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
