package lk.salli.app.features.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lk.salli.app.FeatureFlags
import lk.salli.data.ai.LocalModel
import lk.salli.data.ai.ModelStatus
import lk.salli.domain.ParseMode

@Composable
fun SettingsScreen(
    onOpenUnknownSms: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    var confirmingDelete by remember { mutableStateOf(false) }
    var confirmingDeleteModel by remember { mutableStateOf(false) }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importModelFromUri(it) } }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShareCsv -> {
                    context.startActivity(
                        android.content.Intent.createChooser(event.intent, "Share export"),
                    )
                }
                is SettingsEvent.Message -> {
                    android.widget.Toast.makeText(context, event.text, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(top = statusBar, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        item { SectionLabel("YOU") }
        item {
            UserNameTile(
                name = state.userName,
                onChange = { viewModel.setUserName(it) },
            )
        }

        item { Spacer(Modifier.height(8.dp)) }

        if (FeatureFlags.AI_ENABLED) {
            item { SectionLabel("PARSE MODE") }
            item {
                ParseModeCard(
                    current = state.parseMode,
                    modelStatus = state.modelStatus,
                    importing = state.importingModel,
                    onPickStandard = { viewModel.setParseMode(ParseMode.STANDARD) },
                    onPickAi = { viewModel.setParseMode(ParseMode.AI) },
                    onDownload = { viewModel.downloadModel() },
                    onCancel = { viewModel.cancelDownload() },
                    onDeleteModel = { confirmingDeleteModel = true },
                    onPickFile = { pickFileLauncher.launch(arrayOf("*/*")) },
                    onTestAi = { viewModel.testAi() },
                )
            }
        } else {
            // AI mode is parked for v1 per CLAUDE.md ("No LLM in v1"). We tease it as a
            // coming-soon feature so users know it's on the roadmap, but nothing's wired up
            // behind it — the code is retained and re-enables cleanly once we flip the flag.
            item { SectionLabel("WHAT'S NEXT") }
            item { AiComingSoonTile() }
        }

        item { Spacer(Modifier.height(8.dp)) }
        item { SectionLabel("PERMISSIONS") }
        item { PermissionsTiles(onSmsGranted = { viewModel.ensureHistoricalImport() }) }

        item { Spacer(Modifier.height(8.dp)) }
        item { SectionLabel("REVIEW") }
        item {
            SettingsTile(
                icon = Icons.Outlined.Inbox,
                title = "Unknown messages",
                subtitle = if (state.unknownSmsCount == 0) "Nothing to review"
                else "${state.unknownSmsCount} bank SMS Salli couldn't parse",
                trailing = if (state.unknownSmsCount > 0) {
                    { UnknownBadge(state.unknownSmsCount) }
                } else null,
                onClick = onOpenUnknownSms,
            )
        }

        item { Spacer(Modifier.height(8.dp)) }
        item { SectionLabel("YOUR DATA") }
        item {
            val syncing by viewModel.syncing.collectAsStateWithLifecycle()
            SettingsTile(
                icon = Icons.Outlined.Sync,
                title = "Sync messages",
                subtitle = if (syncing) "Scanning your inbox…"
                else "Rescan the SMS inbox for anything missed",
                trailing = if (syncing) {
                    { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) }
                } else null,
                onClick = { if (!syncing) viewModel.resyncMessages() },
            )
        }
        item {
            SettingsTile(
                icon = Icons.Outlined.FileDownload,
                title = "Export CSV",
                subtitle = if (state.exporting) "Preparing export…" else "All transactions as a spreadsheet",
                trailing = if (state.exporting) {
                    { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) }
                } else null,
                onClick = { if (!state.exporting) viewModel.exportCsv() },
            )
        }
        item {
            SettingsTile(
                icon = Icons.Outlined.DeleteForever,
                title = "Delete all data",
                subtitle = "Clears transactions, accounts, merchants, categories",
                destructive = true,
                onClick = { confirmingDelete = true },
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
        item { SectionLabel("ABOUT") }
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("Salli v0.1.0", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Open source, Apache 2.0. All parsing happens on-device. Network is only used " +
                        "if you enable AI mode, to download the model once.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete all data?") },
            text = {
                Text(
                    "This clears every transaction, account, merchant, and category on this device. Default seed data will be re-added. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    viewModel.deleteAllData()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            },
        )
    }

    if (state.testingAi || state.aiTestResult != null) {
        AiTestDialog(
            testing = state.testingAi,
            result = state.aiTestResult,
            onDismiss = { if (!state.testingAi) viewModel.dismissAiTest() },
        )
    }

    if (confirmingDeleteModel) {
        AlertDialog(
            onDismissRequest = { confirmingDeleteModel = false },
            title = { Text("Delete AI model?") },
            text = { Text("Reclaims ~${LocalModel.SIZE_BYTES / (1024 * 1024)} MB. You can re-download it later. Parse mode drops back to Standard.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDeleteModel = false
                    viewModel.deleteModel()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDeleteModel = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ParseModeCard(
    current: ParseMode,
    modelStatus: ModelStatus,
    importing: Boolean,
    onPickStandard: () -> Unit,
    onPickAi: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDeleteModel: () -> Unit,
    onPickFile: () -> Unit,
    onTestAi: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            ModeRow(
                title = "Standard",
                subtitle = "Regex templates · instant · offline",
                selected = current == ParseMode.STANDARD,
                onClick = onPickStandard,
            )
            ModeRow(
                title = "AI",
                subtitle = aiSubtitle(modelStatus, importing),
                selected = current == ParseMode.AI,
                onClick = onPickAi,
                leading = Icons.Outlined.AutoAwesome,
                enabled = modelStatus is ModelStatus.Installed,
            )
            AiModelControls(
                status = modelStatus,
                importing = importing,
                onDownload = onDownload,
                onCancel = onCancel,
                onDelete = onDeleteModel,
                onPickFile = onPickFile,
                onTestAi = onTestAi,
            )
        }
    }
}

private fun aiSubtitle(status: ModelStatus, importing: Boolean): String {
    val mb = LocalModel.SIZE_BYTES / (1024 * 1024)
    return when {
        importing -> "Copying model…"
        status is ModelStatus.NotDownloaded -> "${LocalModel.DISPLAY_NAME} · $mb MB"
        status is ModelStatus.Queued -> "Queued…"
        status is ModelStatus.Downloading -> {
            val pct = if (status.totalBytes > 0) {
                (status.bytesDownloaded * 100 / status.totalBytes).coerceAtMost(100)
            } else 0
            "Downloading · $pct%"
        }
        status is ModelStatus.Installed -> "${LocalModel.DISPLAY_NAME} · ready"
        status is ModelStatus.Failed -> "Download failed — tap to retry"
        else -> ""
    }
}

@Composable
private fun ModeRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = if (enabled) onClick else null,
        )
        Spacer(Modifier.size(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    Icon(
                        imageVector = leading,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.size(6.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiModelControls(
    status: ModelStatus,
    importing: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onPickFile: () -> Unit,
    onTestAi: () -> Unit,
) {
    if (importing) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                "Copying file to app storage…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    when (status) {
        is ModelStatus.NotDownloaded, is ModelStatus.Failed -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                if (status is ModelStatus.Failed) {
                    Text(
                        text = "Download failed: ${status.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = onPickFile) { Text("Select file") }
                    Spacer(Modifier.size(4.dp))
                    TextButton(onClick = onDownload) {
                        Text(
                            if (status is ModelStatus.Failed) "Retry" else "Download",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        is ModelStatus.Downloading, is ModelStatus.Queued -> {
            val (done, total) = when (status) {
                is ModelStatus.Downloading -> status.bytesDownloaded to status.totalBytes
                is ModelStatus.Queued -> status.bytesDownloaded to status.totalBytes
                else -> 0L to 1L
            }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                LinearProgressIndicator(
                    progress = { if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "${formatMb(done)} / ${formatMb(total)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }
        }
        is ModelStatus.Installed -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                TextButton(onClick = onDelete) {
                    Text(
                        "Delete (${formatMb(status.sizeBytes)})",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onTestAi) {
                    Text("Test AI", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private fun formatMb(bytes: Long): String {
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb < 10) "%.1f MB".format(mb) else "%.0f MB".format(mb)
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
    )
}

/**
 * Two permission tiles — SMS and (on API 33+) Notifications. Each reports live state and, on
 * tap, either fires the permission dialog or deep-links the user to the app's notification
 * settings (Android doesn't let you re-prompt once the user has permanently denied).
 *
 * Re-reads permission state whenever the screen resumes so changes made in system settings
 * reflect immediately on return.
 */
@Composable
private fun PermissionsTiles(onSmsGranted: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Increment on every resume to force a re-read of permission state. Cheaper than wiring a
    // proper observable permission API.
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val smsGranted = remember(resumeTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED
    }
    val notificationsGranted = remember(resumeTick) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) true
        else ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Shown after a request returns denied — we walk the user through the "restricted
    // settings" toggle Android 13+ applies to sideloaded apps.
    var showRestrictedHelp by remember { mutableStateOf(false) }

    val smsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        resumeTick++
        val allGranted = results.values.all { it } && results.isNotEmpty()
        if (allGranted) {
            showRestrictedHelp = false
            onSmsGranted()
        } else {
            // Denied. Only surface the restricted-settings explainer if we're on a direct-
            // sideload install; users who came in via Obtainium / F-Droid / ADB get the
            // normal dialog and the help would confuse them.
            showRestrictedHelp = lk.salli.app.features.onboarding.smsPermissionLikelyBlocked(context)
            if (!showRestrictedHelp) openAppSettings(context)
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        resumeTick++
        if (!granted) openAppSettings(context)
    }

    SettingsTile(
        icon = Icons.Outlined.Sms,
        title = "SMS access",
        subtitle = if (smsGranted) "Granted — Salli auto-parses new transactions"
        else "Needed to read bank SMS. Tap to allow.",
        trailing = if (smsGranted) {
            { PermissionGrantedCheck() }
        } else null,
        onClick = {
            if (!smsGranted) {
                smsLauncher.launch(
                    arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS),
                )
            }
        },
    )

    if (!smsGranted && showRestrictedHelp) {
        RestrictedSettingsExplainer(onOpenAppSettings = { openAppSettings(context) })
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        SettingsTile(
            icon = Icons.Outlined.Notifications,
            title = "Notifications",
            subtitle = if (notificationsGranted)
                "Granted — transfer alerts can prompt you inline"
            else "Let Salli ask you to tag ambiguous transfers without opening the app.",
            trailing = if (notificationsGranted) {
                { PermissionGrantedCheck() }
            } else null,
            onClick = {
                if (!notificationsGranted) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
    }
}

@Composable
private fun RestrictedSettingsExplainer(onOpenAppSettings: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Android is blocking SMS access",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Apps installed outside Play Store need an extra toggle. Open this app's " +
                "info page, tap the ⋯ menu, turn on \"Allow restricted settings\", then try " +
                "the tile above again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        )
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.inverseSurface)
                .clickable(onClick = onOpenAppSettings)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Open settings",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}

@Composable
private fun PermissionGrantedCheck() {
    Icon(
        imageVector = Icons.Outlined.Check,
        contentDescription = "Granted",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
    )
}

/** Deep-link to the app's system settings page — the only reliable post-deny re-prompt path. */
private fun openAppSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

@Composable
private fun SettingsTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (trailing != null) {
                Spacer(Modifier.size(8.dp))
                trailing()
            }
        }
    }
}

@Composable
private fun UserNameTile(name: String, onChange: (String) -> Unit) {
    // Own the text locally. The VM-side Flow from DataStore echoes every keystroke back as a
    // state emission which — if wired straight into `value = name` — stomps on the user's
    // typing and bounces the cursor. We seed from the VM exactly once (when the first real
    // value arrives) and then refuse further prop-driven updates; subsequent changes all flow
    // locally-first via `onChange`.
    var text by remember { mutableStateOf(name) }
    var seeded by remember { mutableStateOf(name.isNotEmpty()) }
    LaunchedEffect(name) {
        if (!seeded && name.isNotEmpty()) {
            text = name
            seeded = true
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Your name",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Used in the Home greeting. Stays on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onChange(it)
                },
                placeholder = { Text("Your name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AiComingSoonTile() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Ask Salli about your money, on-device. Coming in a future update.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "Soon",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun AiTestDialog(
    testing: Boolean,
    result: AiTestResult?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (testing) "Running…" else "AI output") },
        text = {
            Column {
                if (testing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            "First run loads the 547 MB model — this takes ~5 s on a Pixel 6.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (result != null) {
                    if (result.error != null) {
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = result.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            text = "load ${result.loadMillis} ms · infer ${result.inferMillis} ms",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = result.output.ifBlank { "(empty response)" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !testing) { Text("Close") }
        },
    )
}

@Composable
private fun UnknownBadge(count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
