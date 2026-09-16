package lk.salli.app.features.txdetail

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
