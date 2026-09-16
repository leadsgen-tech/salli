package lk.salli.app.features.txdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale
import lk.salli.design.components.AmountText
import lk.salli.design.format.MoneyFormat
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow
import lk.salli.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    txId: Long,
    onDismiss: () -> Unit,
    /** "Split this": closes the sheet, then hands the transaction id to the split flow. */
    onSplit: (Long) -> Unit = {},
    viewModel: TransactionDetailViewModel = hiltViewModel(key = "transaction-detail"),
) {
    // Runs every time the sheet enters composition, including reopening the same transaction.
    LaunchedEffect(txId) { viewModel.open(txId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Programmatic closes (Save) run the slide-down first and only then remove the sheet, so
    // the exit is one smooth motion instead of a hard cut. Swipe/scrim/back dismissals have
    // already animated by the time onDismissRequest fires.
    val animatedDismiss: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        // Plain scrim on purpose. The previous cross-window blur (FLAG_BLUR_BEHIND) made
        // SurfaceFlinger re-blur the whole screen every frame of the slide animation, which
        // is exactly the stutter users saw when closing the sheet.
    ) {
        // Ignore a previous transaction's state for the frame before open() resets it.
        val tx = state.transaction?.takeIf { it.id == txId }
        if (tx == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp)) {
                Text(
                    text = if (state.loading) stringResource(R.string.transaction_detail_loading)
                    else stringResource(R.string.transaction_detail_missing),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            return@ModalBottomSheet
        }

        val flow = TransactionFlow.fromId(tx.flowId)
        val amount = Money(tx.amountMinor, tx.amountCurrency)
        val dateFmt = remember { SimpleDateFormat("EEEE, d MMM · h:mm a", Locale.getDefault()) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Title block — note > merchant > generic "Transaction".
            Column {
                Text(
                    text = tx.note?.takeIf { it.isNotBlank() }
                        ?: tx.merchantRaw?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.transaction_detail_title_fallback),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = dateFmt.format(Date(tx.timestamp)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AmountText(
                money = amount,
                flow = flow,
                isDeclined = tx.isDeclined,
                style = MaterialTheme.typography.displaySmall,
            )

            MetaRow(label = "Account", value = state.accountName ?: "—")
            state.counterpartAccountName?.let { other ->
                val toward = if (flow == TransactionFlow.INCOME) "from" else "to"
                MetaRow(label = "Own transfer", value = "$toward $other")
            }
            if (tx.balanceMinor != null) {
                MetaRow(
                    label = "Balance after",
                    value = MoneyFormat.format(Money(tx.balanceMinor!!, tx.amountCurrency)),
                )
            }
            if (tx.feeMinor != null && tx.feeMinor!! > 0L) {
                MetaRow(
                    label = "Fee",
                    value = MoneyFormat.format(Money(tx.feeMinor!!, tx.amountCurrency)),
                )
            }
            if (tx.isDeclined) {
                MetaRow(label = "Status", value = "Declined by bank — not charged")
            }
            state.linkedSplit?.let { link ->
                val share = link.myShareMinor?.let { " · your share ${MoneyFormat.format(Money(it, link.currency))}" }.orEmpty()
                MetaRow(label = "Split", value = "In ${link.groupName}$share")
            }
            if (!tx.isDeclined && flow == TransactionFlow.EXPENSE) {
                var splitting by remember { mutableStateOf(false) }
                TextButton(
                    enabled = !splitting,
                    onClick = {
                        splitting = true
                        // hide() throws if the sheet is already closing (a drag, a second tap), so a
                        // cancelled hide never navigates; the flag ignores repeat taps.
                        scope.launch {
                            sheetState.hide()
                            onSplit(tx.id)
                        }
                    },
                ) {
                    Text(if (state.linkedSplit == null) "Split this with others" else "Split again")
                }
            }

            // Category picker
            Column {
                Text(
                    text = "CATEGORY",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    state.categories.forEach { cat ->
                        FilterChip(
                            selected = state.categoryId == cat.id,
                            onClick = { viewModel.changeCategory(cat.id) },
                            label = { Text(cat.name) },
                        )
                    }
                }
            }

            // Inline note input: single pill row with a baked-in "Save" button on the right
            // that only appears when the draft diverges from the persisted note. Once saved,
            // the new note becomes the row title in Home/Timeline.
            var noteDraft by remember(tx.id, tx.note) { mutableStateOf(tx.note.orEmpty()) }
            val dirty = noteDraft != tx.note.orEmpty()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(start = 18.dp, end = 6.dp),
            ) {
                // Placeholder + input live in the same decoration box so they share a
                // vertical centre line — avoids the "placeholder sits at the top, cursor
                // drops to the bottom when you tap" misalignment.
                BasicTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    decorationBox = { inner ->
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (noteDraft.isEmpty()) {
                                Text(
                                    text = "Add a note (becomes the title)",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        }
                    },
                )
                // Always on screen so the user sees the action target; disabled until
                // there's something to save. Tapping it commits + closes the sheet.
                val saveBg = if (dirty) MaterialTheme.colorScheme.inverseSurface
                else MaterialTheme.colorScheme.surfaceContainerHighest
                val saveFg = if (dirty) MaterialTheme.colorScheme.inverseOnSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(saveBg)
                        .clickable(enabled = dirty) {
                            viewModel.setNote(noteDraft)
                            animatedDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "Save",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = saveFg,
                    )
                }
            }

            // Raw SMS (collapsed by default via small font)
            if (!tx.rawBody.isNullOrBlank()) {
                Column {
                    Text(
                        text = "SMS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            text = tx.rawBody!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }

            TextButton(
                onClick = { viewModel.setExcluded(!tx.isHidden); animatedDismiss() },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    text = stringResource(
                        if (tx.isHidden) R.string.transaction_detail_include
                        else R.string.transaction_detail_exclude,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(6.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = CircleShape,
                modifier = Modifier.size(6.dp),
                content = {},
            )
        }
        Spacer(Modifier.size(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
