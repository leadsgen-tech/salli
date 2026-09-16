package lk.salli.app.features.settings

import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.draw.alpha
import lk.salli.app.security.findFragmentActivity
import lk.salli.data.prefs.AppLockSettings
import lk.salli.domain.security.AppLockPolicy
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Speed
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Switch
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Groups
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import lk.salli.app.BuildConfig

// Community / repo URLs. Single source of truth so a future fork-and-rename only edits here.
private const val GITHUB_REPO_URL = "https://github.com/leadsgen-tech/salli"
private const val BANK_REQUEST_ISSUE_URL =
    "https://github.com/leadsgen-tech/salli/issues/new?template=bank_support_request.yml"

private fun openUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

@Composable
fun SettingsScreen(
    onOpenUnknownSms: () -> Unit = {},
    onOpenBills: () -> Unit = {},
    onOpenFuelPass: () -> Unit = {},
    onOpenRecurring: () -> Unit = {},
    onOpenGoals: () -> Unit = {},
    onOpenSplit: () -> Unit = {},
    onReplayIntro: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    val spendingLimit by viewModel.spendingLimit.collectAsStateWithLifecycle()
    val trackers by viewModel.trackers.collectAsStateWithLifecycle()
    var editingLimit by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    var confirmingDelete by remember { mutableStateOf(false) }

    // Restore picks any file the user can reach; JSON is not always tagged as such by pickers,
    // so we accept broadly and validate the contents ourselves.
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.inspectBackup(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShareCsv -> {
                    context.startActivity(
                        android.content.Intent.createChooser(event.intent, "Share export"),
                    )
                }
                is SettingsEvent.ShareFile -> {
                    context.startActivity(android.content.Intent.createChooser(event.intent, event.title))
                }
                is SettingsEvent.Message -> {
                    android.widget.Toast.makeText(context, event.text, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (editingLimit) {
        SpendingLimitDialog(
            currentMinor = spendingLimit,
            onDismiss = { editingLimit = false },
            onSave = { viewModel.setSpendingLimit(it); editingLimit = false },
        )
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

        item { SectionLabel("ACCOUNTS") }
        if (accounts.isEmpty()) {
            item {
                Text(
                    text = "Accounts appear here once a bank SMS has been read.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
        }
        items(accounts, key = { "acct-${it.id}" }) { account ->
            SwitchTile(
                icon = Icons.Outlined.AccountBalance,
                title = account.displayName,
                subtitle = if (account.isHidden) "Hidden from all screens and totals"
                else "Shown everywhere",
                checked = !account.isHidden,
                onToggle = { shown -> viewModel.setAccountHidden(account.id, hidden = !shown) },
            )
        }

        item { Spacer(Modifier.height(8.dp)) }

        item { SectionLabel("SPENDING PERIOD") }
        item {
            val cycle = remember(period.monthStartDay) {
                lk.salli.domain.DateRange.cycleFor(System.currentTimeMillis(), period.monthStartDay)
            }
            StepperTile(
                icon = Icons.Outlined.DateRange,
                title = "Month starts on day ${period.monthStartDay}",
                subtitle = if (period.monthStartDay == 1) "Calendar month · ${cycle.label}"
                else "Current period: ${cycle.label}",
                value = "${period.monthStartDay}",
                onMinus = { viewModel.setMonthStartDay(period.monthStartDay - 1) },
                onPlus = { viewModel.setMonthStartDay(period.monthStartDay + 1) },
                minusEnabled = period.monthStartDay > 1,
                plusEnabled = period.monthStartDay < 28,
            )
        }
        item {
            WeekStartTile(selected = period.weekStartDay, onSelect = { viewModel.setWeekStartDay(it) })
        }
        item {
            SettingsTile(
                icon = Icons.Outlined.Speed,
                title = "Monthly spending limit",
                subtitle = spendingLimit?.let { "${lk.salli.design.format.MoneyFormat.format(lk.salli.domain.Money(it, "LKR"))} · safe-to-spend uses this" }
                    ?: "Automatic · the median of your last three periods",
                onClick = { editingLimit = true },
            )
        }

        item { Spacer(Modifier.height(8.dp)) }

        item { SectionLabel("SUMMARIES") }
        item {
            SwitchTile(
                icon = Icons.Outlined.Notifications,
                title = "Daily summary",
                subtitle = "What you spent today, every evening",
                checked = summaries.daily,
                onToggle = { viewModel.setSummaryDaily(it) },
            )
        }
        item {
            SwitchTile(
                icon = Icons.Outlined.Notifications,
                title = "Weekly summary",
                subtitle = "Last week's spending, on your week-start day",
                checked = summaries.weekly,
                onToggle = { viewModel.setSummaryWeekly(it) },
            )
        }
        item {
            SwitchTile(
                icon = Icons.Outlined.Notifications,
                title = "Monthly summary",
                subtitle = "Last period's spending, on your month-start day",
                checked = summaries.monthly,
                onToggle = { viewModel.setSummaryMonthly(it) },
            )
        }
        item {
            StepperTile(
                icon = Icons.Outlined.Schedule,
                title = "Deliver at ${"%02d:00".format(summaries.hour)}",
                subtitle = "Summaries post once the hour has passed",
                value = "${summaries.hour}",
                onMinus = { viewModel.setSummaryHour(summaries.hour - 1) },
                onPlus = { viewModel.setSummaryHour(summaries.hour + 1) },
                minusEnabled = summaries.hour > 0,
                plusEnabled = summaries.hour < 23,
            )
        }

        item { Spacer(Modifier.height(8.dp)) }

        item { SectionLabel("WIDGET") }
        item {
            val hideAmounts by viewModel.widgetHideAmounts.collectAsStateWithLifecycle()
            SwitchTile(
                icon = Icons.Outlined.Widgets,
                title = "Hide amounts on the widget",
                subtitle = if (hideAmounts) "The home-screen widget shows Rs ••••"
                else "The home-screen widget shows your numbers",
                checked = hideAmounts,
                onToggle = { viewModel.setWidgetHideAmounts(it) },
            )
        }

        item { Spacer(Modifier.height(8.dp)) }

        item { SectionLabel("SECURITY") }
        item {
            val lock by viewModel.appLockSettings.collectAsStateWithLifecycle()
            SecurityTiles(
                lock = lock,
                isDeviceSecure = viewModel::isDeviceSecure,
                onToggleLock = { enable ->
                    val activity = context.findFragmentActivity()
                    if (activity != null) viewModel.requestAppLock(activity, enable)
                },
                onLockAfter = { seconds ->
                    context.findFragmentActivity()?.let { viewModel.requestAppLockAfter(it, seconds) }
                },
                onHideInRecents = { hide ->
                    context.findFragmentActivity()?.let { viewModel.requestHideInRecents(it, hide) }
                },
            )
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
        item { SectionLabel("TRACKERS") }
        item {
            SettingsTile(
                icon = Icons.Outlined.ReceiptLong,
                title = "Bills",
                subtitle = when (state.openBillCount) {
                    0 -> "SLT, Dialog, CEB and water bills from SMS"
                    1 -> "1 bill to pay"
                    else -> "${state.openBillCount} bills to pay"
                },
                trailing = if (state.openBillCount > 0) {
                    { UnknownBadge(state.openBillCount) }
                } else null,
                onClick = onOpenBills,
            )
        }
        item {
            SettingsTile(
                icon = Icons.Outlined.LocalGasStation,
                title = "Fuel Pass",
                subtitle = if (state.fuelVehicleCount == 0) "Weekly quota per vehicle, from 1919 SMS"
                else "${state.fuelVehicleCount} vehicle${if (state.fuelVehicleCount == 1) "" else "s"} tracked",
                onClick = onOpenFuelPass,
            )
        }
        item {
            SettingsTile(
                icon = Icons.Outlined.Autorenew,
                title = "Recurring & subscriptions",
                subtitle = when {
                    trackers.recurring == 0 -> "Repeating payments and failing card subscriptions"
                    trackers.needsAttention > 0 -> "${trackers.recurring} found · ${trackers.needsAttention} need a look"
                    else -> "${trackers.recurring} found"
                },
                trailing = if (trackers.needsAttention > 0) {
                    { UnknownBadge(trackers.needsAttention) }
                } else null,
                onClick = onOpenRecurring,
            )
        }
        item {
            SettingsTile(
                icon = Icons.Outlined.Flag,
                title = "Goals",
                subtitle = when (trackers.goals) {
                    0 -> "Save toward a target, a period at a time"
                    1 -> "1 goal"
                    else -> "${trackers.goals} goals"
                },
                onClick = onOpenGoals,
            )
        }
        item {
            SettingsTile(
                icon = Icons.Outlined.Groups,
                title = "Shared expenses",
                subtitle = "Split bills with people and see who owes whom",
                onClick = onOpenSplit,
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
                icon = Icons.Outlined.Save,
                title = "Back up (JSON)",
                subtitle = if (state.backingUp) "Preparing backup…"
                else "Everything: transactions, accounts, bills, budgets, settings",
                trailing = if (state.backingUp) {
                    { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) }
                } else null,
                onClick = { if (!state.backingUp) viewModel.backupJson() },
            )
        }
        item {
            SettingsTile(
                icon = Icons.Outlined.Restore,
                title = "Restore from backup",
                subtitle = if (state.restoring) "Restoring…" else "Replaces this device's data with a backup file",
                trailing = if (state.restoring) {
                    { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) }
                } else null,
                onClick = {
                    if (!state.restoring) restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                },
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
        item { SectionLabel("COMMUNITY") }
        item {
            BankRequestBanner(
                onClick = { openUrl(context, BANK_REQUEST_ISSUE_URL) },
            )
        }
        item {
            SettingsTile(
                icon = Icons.Outlined.Code,
                title = "Source on GitHub",
                subtitle = "Apache 2.0 · audit, fork, contribute",
                onClick = { openUrl(context, GITHUB_REPO_URL) },
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
        item { SectionLabel("ABOUT") }
        item {
            SettingsTile(
                icon = Icons.Outlined.PlayCircleOutline,
                title = "Replay the introduction",
                subtitle = "Try the Salli demo again",
                onClick = onReplayIntro,
            )
        }
        item { AboutCard() }
    }

    state.pendingRestore?.let { pending ->
        val exported = remember(pending.exportedAt) {
            java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(pending.exportedAt))
        }
        AlertDialog(
            onDismissRequest = { viewModel.cancelRestore() },
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "This replaces everything on this device with the backup from $exported " +
                        "(${pending.transactions} transactions, ${pending.rows} rows in total). This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRestore() }) {
                    Text("Replace", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelRestore() }) { Text("Cancel") }
            },
        )
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
private fun StepperTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onMinus, enabled = minusEnabled) { Text("−") }
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = onPlus, enabled = plusEnabled) { Text("+") }
        }
    }
}

/** Mon…Sun chips; [selected] and the callback use ISO numbering (1 = Monday … 7 = Sunday). */
@Composable
private fun WeekStartTile(selected: Int, onSelect: (Int) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text = "Week starts on", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            // Seven equal-width pills: FilterChips are too wide for a phone row, so Saturday
            // wrapped and Sunday fell off the edge.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                (1..7).forEach { iso ->
                    val label = java.time.DayOfWeek.of(iso).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
                    val on = iso == selected
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { onSelect(iso) },
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (on) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * App lock, its "Lock after" delay and FLAG_SECURE. The lock switch needs a screen lock on the
 * phone (re-checked on every resume, so setting one in system settings and coming back works) and
 * never flips by itself: the view model shows the system prompt and only a success changes it.
 */
@Composable
private fun SecurityTiles(
    lock: AppLockSettings,
    isDeviceSecure: () -> Boolean,
    onToggleLock: (Boolean) -> Unit,
    onLockAfter: (Int) -> Unit,
    onHideInRecents: (Boolean) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val secure = remember(resumeTick) { isDeviceSecure() }

    Column {
        SwitchTile(
            icon = Icons.Outlined.Lock,
            title = "App lock",
            subtitle = when {
                !secure -> "Set a screen lock on this phone first to use app lock"
                lock.enabled -> "On · unlock with your fingerprint or phone PIN"
                else -> "Ask for your fingerprint or phone PIN to open Salli"
            },
            checked = lock.enabled && secure,
            enabled = secure,
            onToggle = onToggleLock,
        )
        LockAfterTile(
            selected = lock.lockAfterSeconds,
            enabled = lock.enabled && secure,
            onSelect = onLockAfter,
        )
        SwitchTile(
            icon = Icons.Outlined.VisibilityOff,
            title = "Hide Salli in recent apps",
            subtitle = "Blanks Salli in the recent apps view. Also blocks screenshots and screen recording.",
            checked = lock.hideInRecents,
            onToggle = onHideInRecents,
        )
    }
}

/** Immediately / 1 min / 5 min pills, styled like the week-start picker. */
@Composable
private fun LockAfterTile(selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(text = "Lock after", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "How long Salli can sit in the background before it locks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                AppLockPolicy.lockAfterChoicesSeconds.forEach { seconds ->
                    val label = when (seconds) {
                        AppLockPolicy.IMMEDIATELY_SECONDS -> "Immediately"
                        AppLockPolicy.ONE_MINUTE_SECONDS -> "1 min"
                        else -> "5 min"
                    }
                    val on = seconds == selected
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable(enabled = enabled) { onSelect(seconds) },
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (on) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clickable(enabled = enabled) { onToggle(!checked) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(8.dp))
            Switch(checked = checked, onCheckedChange = onToggle, enabled = enabled)
        }
    }
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

/**
 * Hero card inviting users to file a bank-support request when their bank isn't parsed yet.
 * Primary-tinted so it reads as a call-to-action, not just another row. Anchors the
 * Community section so the contribution path feels first-class rather than buried in About.
 */
@Composable
private fun BankRequestBanner(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.primaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = 0.18f)),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountBalance,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Don't see your bank?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Help us add it — share a few redacted SMS samples.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.85f),
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

/**
 * About card — pulls the version straight from BuildConfig so a versionName bump in
 * gradle is the only place to change the user-visible string.
 */
@Composable
private fun AboutCard() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Salli",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.size(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Open source under Apache 2.0. Every parse runs on this device. " +
                    "The app has no network permission, so nothing can leave your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )
        }
    }
}

/** Empty field means "automatic". Amounts are whole rupees or rupees and cents. */
@Composable
private fun SpendingLimitDialog(currentMinor: Long?, onDismiss: () -> Unit, onSave: (Long?) -> Unit) {
    var text by remember {
        mutableStateOf(currentMinor?.let { if (it % 100 == 0L) (it / 100).toString() else "%d.%02d".format(java.util.Locale.US, it / 100, it % 100) }.orEmpty())
    }
    val parsed = runCatching {
        val cleaned = text.replace(",", "").trim()
        if (cleaned.isEmpty()) null
        else java.math.BigDecimal(cleaned).takeIf { it.signum() > 0 }
            ?.movePointRight(2)?.setScale(0, java.math.RoundingMode.HALF_UP)?.longValueExact()
    }
    val valid = text.isBlank() || parsed.getOrNull() != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly spending limit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Leave empty to let Salli use the median of your last three complete periods.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Limit (Rs)") },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(parsed.getOrNull()) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
