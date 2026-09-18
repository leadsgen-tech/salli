package lk.salli.app.features.txdetail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import lk.salli.design.components.BankAvatar
import lk.salli.design.motion.LocalReducedMotion
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import lk.salli.app.R
import lk.salli.design.components.AmountText
import lk.salli.design.components.CategoryChip
import lk.salli.design.components.GroupedList
import lk.salli.design.components.ListDivider
import lk.salli.design.components.ListRow
import lk.salli.design.format.MoneyFormat
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow

/** Route version of detail. The former sheet remains only for saved back-stack compatibility. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    txId: Long,
    onBack: () -> Unit,
    onSplit: (Long) -> Unit = {},
    viewModel: TransactionDetailViewModel = hiltViewModel(key = "transaction-detail-route"),
) {
    LaunchedEffect(txId) { viewModel.open(txId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tx = state.transaction?.takeIf { it.id == txId }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transaction_detail_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.transaction_detail_back)) } },
            )
        },
    ) { padding ->
        if (tx == null) {
            Text(
                if (state.loading) stringResource(R.string.transaction_detail_loading) else stringResource(R.string.transaction_detail_missing),
                modifier = Modifier.padding(padding).padding(20.dp),
            )
            return@Scaffold
        }
        val flow = TransactionFlow.fromId(tx.flowId)
        var note by remember(tx.id, tx.note) { mutableStateOf(tx.note.orEmpty()) }
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
        ) {
            Column {
                Text(tx.note?.takeIf { it.isNotBlank() } ?: tx.merchantRaw ?: stringResource(R.string.transaction_detail_title_fallback), style = MaterialTheme.typography.headlineSmall)
                Text(SimpleDateFormat("EEE d MMM · h:mm a", Locale.getDefault()).format(Date(tx.timestamp)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                AmountText(Money(tx.amountMinor, tx.amountCurrency), flow, tx.isDeclined, style = MaterialTheme.typography.displaySmall)
            }
            if (state.counterpartAccountName != null && state.counterpartAmountMinor != null) {
                // This leg is the sender when it is the larger one: the sending bank keeps the fee.
                val sending = tx.amountMinor >= state.counterpartAmountMinor!!
                MoveStage(
                    fromName = if (sending) state.accountName ?: "" else state.counterpartAccountName!!,
                    fromSender = if (sending) state.accountSender else state.counterpartSender,
                    toName = if (sending) state.counterpartAccountName!! else state.accountName ?: "",
                    toSender = if (sending) state.counterpartSender else state.accountSender,
                    moved = Money(minOf(tx.amountMinor, state.counterpartAmountMinor!!), tx.amountCurrency),
                    fee = (tx.amountMinor - state.counterpartAmountMinor!!).let { kotlin.math.abs(it) }
                        .takeIf { it > 0L }?.let { Money(it, tx.amountCurrency) },
                    key = tx.id,
                )
            }
            GroupedList {
                ListRow(stringResource(R.string.transaction_detail_account), trailing = { Text(state.accountName ?: "—") })
                tx.balanceMinor?.let { ListDivider(); ListRow(stringResource(R.string.transaction_detail_balance), trailing = { Text(MoneyFormat.format(Money(it, tx.amountCurrency))) }) }
                tx.feeMinor?.takeIf { it > 0 }?.let { ListDivider(); ListRow(stringResource(R.string.transaction_detail_fee), trailing = { Text(MoneyFormat.format(Money(it, tx.amountCurrency))) }) }
            }
            Column {
                Text(stringResource(R.string.transaction_detail_category), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    state.categories.forEach { category -> CategoryChip(category.name, category.iconName, category.colorSeed, selected = state.categoryId == category.id, onClick = { viewModel.changeCategory(category.id) }) }
                }
            }
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.transaction_detail_note)) }, modifier = Modifier.fillMaxWidth())
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { viewModel.setNote(note) }) { Text(stringResource(R.string.transaction_detail_save)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { viewModel.setExcluded(!tx.isHidden); onBack() }) { Text(stringResource(if (tx.isHidden) R.string.transaction_detail_include else R.string.transaction_detail_exclude)) }
            }
            if (tx.rawBody != null) Text(tx.rawBody!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (flow == TransactionFlow.EXPENSE && !tx.isDeclined) TextButton(onClick = { onSplit(tx.id) }) { Text(stringResource(R.string.transaction_detail_split)) }
        }
    }
}


/**
 * An own transfer, shown as the thing it is: the amount leaves one bank card and lands on the
 * other, on the spatial spring, then the line under it says what moved and what it cost.
 */
@Composable
private fun MoveStage(
    fromName: String,
    fromSender: String?,
    toName: String,
    toSender: String?,
    moved: Money,
    fee: Money?,
    key: Long,
) {
    val progress = remember(key) { Animatable(0f) }
    val reduced = LocalReducedMotion.current
    LaunchedEffect(key) {
        if (reduced) progress.snapTo(1f) else {
            progress.snapTo(0f)
            // A short pause so the eye finds the two cards before the money moves.
            kotlinx.coroutines.delay(250)
            progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 200f))
        }
    }
    var pillWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.transaction_detail_own_transfer),
            style = MaterialTheme.typography.titleMedium,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cardWidth = 150.dp
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MoveBankCard(name = fromName, sender = fromSender, width = cardWidth)
                MoveBankCard(name = toName, sender = toSender, width = cardWidth)
            }
            val inset = with(density) { 12.dp.toPx() }
            val travel = with(density) { maxWidth.toPx() } - pillWidth - 2 * inset
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 10.dp)
                    .onSizeChanged { pillWidth = it.width }
                    .offset { IntOffset((inset + travel.coerceAtLeast(0f) * progress.value).roundToInt(), 0) },
            ) {
                Text(
                    MoneyFormat.format(moved),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Text(
            if (fee == null) stringResource(R.string.transaction_detail_moved, MoneyFormat.format(moved))
            else stringResource(R.string.transaction_detail_moved_fee, MoneyFormat.format(moved), MoneyFormat.format(fee)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MoveBankCard(name: String, sender: String?, width: androidx.compose.ui.unit.Dp) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.width(width).height(104.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            BankAvatar(sender = sender, size = 28.dp, displayName = name)
            Spacer(Modifier.height(6.dp))
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
