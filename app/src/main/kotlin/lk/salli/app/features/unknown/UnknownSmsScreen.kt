package lk.salli.app.features.unknown

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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import lk.salli.data.db.entities.UnknownSmsEntity
import lk.salli.design.components.EmptyState

@Composable
fun UnknownSmsScreen(
    onBack: () -> Unit,
    viewModel: UnknownSmsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val export by viewModel.export.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UnknownSmsEvent.Share -> context.startActivity(android.content.Intent.createChooser(event.intent, "Send formats"))
                is UnknownSmsEvent.Message -> android.widget.Toast.makeText(context, event.text, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    if (export.visible) {
        ExportDialog(
            state = export,
            onToggleSender = viewModel::toggleSender,
            onToggleScan = viewModel::toggleScanInbox,
            onDismiss = viewModel::dismissExport,
            onShare = viewModel::share,
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = statusBar)) {
        TopBar(onBack = onBack, count = state.pending.size, onExport = viewModel::openExport)

        if (state.pending.isEmpty() && !state.isLoading) {
            EmptyState(
                title = "Nothing to review",
                message = "When Salli sees a bank SMS it can't parse, it'll show up here so you can triage.",
                icon = Icons.Outlined.Inbox,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.pending, key = { it.id }) { entry ->
                UnknownSmsRow(
                    entry = entry,
                    onIgnore = { viewModel.ignore(entry) },
                    onReport = { viewModel.reportAsTransaction(entry) },
                )
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, count: Int, onExport: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Unknown messages",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (count > 0) "$count pending" else "Nothing to review",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onExport) {
            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text("Send formats", fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * The "send formats to Salli" sheet: which senders to include, whether to also scan the inbox
 * for banks Salli doesn't list, and a plain statement of what leaves the phone.
 */
@Composable
private fun ExportDialog(
    state: ExportUiState,
    onToggleSender: (String) -> Unit,
    onToggleScan: () -> Unit,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
) {
    val nothingSelected = state.senders.none { it.selected } && !state.scanInbox
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send formats to Salli") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Builds a JSON file of the messages Salli couldn't read so a template can be written for them. " +
                        "Account, card and reference numbers keep only their last four digits; phone numbers, e-mails and names are removed; " +
                        "OTPs are never included. Amounts and wording stay, because that's what a template needs. You choose where the file goes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.senders.isNotEmpty()) {
                    Spacer(Modifier.size(12.dp))
                    Text("Senders", style = MaterialTheme.typography.labelLarge)
                    state.senders.forEach { pick ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !state.working) { onToggleSender(pick.sender) },
                        ) {
                            Checkbox(checked = pick.selected, onCheckedChange = { onToggleSender(pick.sender) }, enabled = !state.working)
                            Text(pick.sender, modifier = Modifier.weight(1f))
                            Text("${pick.count}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Also look for banks Salli doesn't know yet", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Scans the last 180 days of your inbox for money-looking messages from other senders. Up to five per sender. Nothing is stored.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.scanInbox, onCheckedChange = { onToggleScan() }, enabled = !state.working)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onShare, enabled = !state.working && !nothingSelected) {
                if (state.working) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (state.working) "Building…" else "Share")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.working) { Text("Cancel") } },
    )
}

@Composable
private fun UnknownSmsRow(
    entry: UnknownSmsEntity,
    onIgnore: () -> Unit,
    onReport: () -> Unit,
) {
    val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.senderAddress,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = fmt.format(Date(entry.receivedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(
                text = entry.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onIgnore) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("Not a transaction", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.size(6.dp))
                TextButton(onClick = onReport) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "It's a transaction",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

