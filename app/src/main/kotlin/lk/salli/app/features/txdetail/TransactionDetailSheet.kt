package lk.salli.app.features.txdetail

import android.os.Build
import android.view.Window
import android.view.WindowManager
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import lk.salli.design.components.AmountText
import lk.salli.design.format.MoneyFormat
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    onDismiss: () -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        // Drop the dim scrim — we blur the content below via FLAG_BLUR_BEHIND instead, so
        // the page stays visible (just out of focus) when the sheet is open.
        scrimColor = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        SheetBlurBehind(radiusDp = 28)
        val tx = state.transaction
        if (tx == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp)) {
                Text(
                    text = if (state.loading) "Loading…" else "Transaction not found.",
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
                        ?: "Transaction",
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
                            onDismiss()
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
        }
    }
}

/**
 * Asks the platform to blur whatever window sits behind the ModalBottomSheet's Dialog.
 * Compose's ModalBottomSheet uses a Dialog under the hood; we find its Window via
 * [DialogWindowProvider] and set `FLAG_BLUR_BEHIND` + `blurBehindRadius`. No-op on API < 31
 * and on devices that advertise no blur support — the only visible consequence is the
 * scrim stays transparent and the background is just visible (not blurred).
 */
@Composable
private fun SheetBlurBehind(radiusDp: Int) {
    val view = LocalView.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    DisposableEffect(radiusDp) {
        val window: Window? = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            // Kill the dialog-level dim + opaque background so content behind actually
            // renders through. scrimColor = Transparent on the Compose side isn't enough —
            // the underlying Dialog window had its own FLAG_DIM_BEHIND + bg drawable.
            window.setDimAmount(0f)
            window.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val attrs = window.attributes
                attrs.blurBehindRadius = with(density) { radiusDp.dp.toPx() }.toInt()
                window.attributes = attrs
            }
        }
        onDispose {
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
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
