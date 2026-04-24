package lk.salli.app.features.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lk.salli.data.db.entities.AccountEntity

// Core SMS reads + the runtime notification perm on API 33+. We bundle them into one
// system dialog so the user sees a single prompt instead of two in a row.
private val REQUIRED_PERMS: Array<String> = buildList {
    add(Manifest.permission.READ_SMS)
    add(Manifest.permission.RECEIVE_SMS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

// Only READ/RECEIVE are strictly required to continue — notifications are nice-to-have.
// If the user grants SMS but denies notifications, we still advance.
private val REQUIRED_TO_ADVANCE: Array<String> = arrayOf(
    Manifest.permission.READ_SMS,
    Manifest.permission.RECEIVE_SMS,
)

private const val PAGE_WELCOME = 0
private const val PAGE_PROMISE = 1
private const val PAGE_PERMISSION = 2
private const val PAGE_IMPORT = 3
private const val PAGE_COUNT = 4

/**
 * Four-page onboarding, monochrome and typography-first.
 *
 *   Welcome    — the Sinhala hello, then the wordmark.
 *   Promise    — three one-line privacy claims (on-device, no cloud, no account).
 *   Permission — single explanation + allow / skip.
 *   Import     — live progress bar that fills in as historical SMS are ingested.
 *
 * The AI-mode picker from the previous onboarding is gone; we ship regex-only in v1 and will
 * reintroduce a mode toggle in Settings when the AI path is real.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })

    var permissionsGranted by remember {
        mutableStateOf(REQUIRED_TO_ADVANCE.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }
    // Tracks whether we've already asked. If a second request returns denied without the
    // system dialog ever appearing, the OS is likely enforcing the "restricted setting"
    // block that Android 13+ applies to sideloaded apps. Surface the help panel so the user
    // isn't left staring at a page that won't advance.
    var permissionAttempts by remember { mutableStateOf(0) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        permissionsGranted = REQUIRED_TO_ADVANCE.all { granted[it] == true }
        permissionAttempts += 1
        if (permissionsGranted) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.runImport()
            scope.launch { pagerState.animateScrollToPage(PAGE_IMPORT) }
        }
    }

    val next: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch {
            if (pagerState.currentPage < PAGE_COUNT - 1) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            } else {
                onDone()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                PAGE_WELCOME -> WelcomePage(onNext = next)
                PAGE_PROMISE -> PromisePage(onNext = next)
                PAGE_PERMISSION -> PermissionPage(
                    granted = permissionsGranted,
                    // Show the restricted-settings card once a grant attempt has failed AND
                    // we detect a direct-sideload install. Apps installed via Obtainium /
                    // F-Droid privileged extension / ADB get the normal permission dialog,
                    // so the help would confuse more than it helps.
                    showRestrictedHelp = !permissionsGranted && permissionAttempts > 0 &&
                        smsPermissionLikelyBlocked(context),
                    onAllow = { launcher.launch(REQUIRED_PERMS) },
                    onOpenAppSettings = { openAppInfo(context) },
                    onSkip = onDone,
                )
                PAGE_IMPORT -> ImportPage(
                    import = state.import,
                    accounts = state.accounts,
                    onDone = onDone,
                )
            }
        }

        PageIndicator(
            count = PAGE_COUNT,
            current = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 18.dp),
        )
    }

    // When import finishes, wait a beat so the user can read "all done" before exiting.
    LaunchedEffect(state.import.finished, state.import.error) {
        if (state.import.finished && state.import.error == null &&
            pagerState.currentPage == PAGE_IMPORT
        ) {
            delay(1400)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onDone()
        }
    }
}

/* -------------------------------------------------------------------------- */
/* Page indicator                                                             */
/* -------------------------------------------------------------------------- */

@Composable
private fun PageIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        repeat(count) { i ->
            val active = i == current
            val width by animateFloatAsState(
                targetValue = if (active) 22f else 6f,
                // Tight tween — the bouncy spring here read as gimmicky on every page change.
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "dotWidth",
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width.dp)
                    .background(
                        color = if (active) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                        shape = CircleShape,
                    ),
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/* 1 — Welcome                                                                */
/* -------------------------------------------------------------------------- */

// Shared enter-transition factory — one tween, one easing, consistent across pages so the
// cadence doesn't vary between steps. Kept small and overlapping; the previous 500-700ms
// staggers made each element feel like it was waiting for the last one to finish.
private val enterTween = tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing)
private val enterIntTween = tween<androidx.compose.ui.unit.IntOffset>(
    durationMillis = 320,
    easing = FastOutSlowInEasing,
)
private fun slideAndFade() =
    fadeIn(enterTween) + slideInVertically(enterIntTween) { it / 10 }

@Composable
private fun WelcomePage(onNext: () -> Unit) {
    var stage by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        delay(120); stage = 1
        delay(220); stage = 2
        delay(180); stage = 3
        delay(220); stage = 4
    }
    PageScaffold {
        Spacer(Modifier.weight(0.8f))

        AnimatedVisibility(visible = stage >= 1, enter = slideAndFade()) {
            Text(
                text = "ආයුබෝවන්",
                fontSize = 64.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(4.dp))
        AnimatedVisibility(visible = stage >= 2, enter = fadeIn(enterTween)) {
            Text(
                text = "ayubowan",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(72.dp))

        AnimatedVisibility(visible = stage >= 3, enter = slideAndFade()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Salli",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Your money,\nin your pocket.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
            }
        }

        Spacer(Modifier.weight(1f))
        AnimatedVisibility(visible = stage >= 4, enter = fadeIn(enterTween)) {
            PillButton(text = "Get started", onClick = onNext)
        }
    }
}

/* -------------------------------------------------------------------------- */
/* 2 — Privacy promise                                                        */
/* -------------------------------------------------------------------------- */

@Composable
private fun PromisePage(onNext: () -> Unit) {
    var stage by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        delay(120); stage = 1
        delay(160); stage = 2
        delay(140); stage = 3
        delay(140); stage = 4
        delay(200); stage = 5
    }
    PageScaffold {
        Spacer(Modifier.weight(0.4f))
        AnimatedVisibility(visible = stage >= 1, enter = slideAndFade()) {
            Text(
                text = "Everything\nstays on\nyour phone.",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 48.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(36.dp))

        PromiseRow(
            number = "01",
            text = "Bank SMS are parsed on-device. They never leave your phone.",
            visible = stage >= 2,
        )
        Spacer(Modifier.height(16.dp))
        PromiseRow(
            number = "02",
            text = "No cloud backend, no login, no account to create.",
            visible = stage >= 3,
        )
        Spacer(Modifier.height(16.dp))
        PromiseRow(
            number = "03",
            text = "No ads. No analytics. No telemetry pings.",
            visible = stage >= 4,
        )

        Spacer(Modifier.weight(1f))
        AnimatedVisibility(visible = stage >= 5, enter = fadeIn(enterTween)) {
            PillButton(text = "Continue", onClick = onNext)
        }
    }
}

@Composable
private fun PromiseRow(number: String, text: String, visible: Boolean) {
    AnimatedVisibility(visible = visible, enter = slideAndFade()) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(32.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/* 3 — Permission                                                             */
/* -------------------------------------------------------------------------- */

@Composable
private fun PermissionPage(
    granted: Boolean,
    showRestrictedHelp: Boolean,
    onAllow: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onSkip: () -> Unit,
) {
    var stage by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        delay(120); stage = 1
        delay(200); stage = 2
        delay(220); stage = 3
    }
    PageScaffold {
        Spacer(Modifier.weight(0.4f))
        AnimatedVisibility(visible = stage >= 1, enter = slideAndFade()) {
            Text(
                text = "One\npermission,\nthen we're\ndone.",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 48.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(24.dp))
        AnimatedVisibility(visible = stage >= 2, enter = fadeIn(enterTween)) {
            Text(
                text = "Salli reads SMS from your banks to build your transaction timeline. " +
                    "Messages stay on this phone.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp,
            )
        }

        AnimatedVisibility(
            visible = showRestrictedHelp,
            enter = fadeIn(enterTween) + slideInVertically(enterIntTween) { it / 6 },
        ) {
            Column(modifier = Modifier.padding(top = 20.dp)) {
                RestrictedSettingsCard(onOpenAppSettings = onOpenAppSettings)
            }
        }

        Spacer(Modifier.weight(1f))
        AnimatedVisibility(visible = stage >= 3, enter = fadeIn(enterTween)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                PillButton(
                    text = when {
                        granted -> "Permission granted"
                        showRestrictedHelp -> "Try again"
                        else -> "Allow SMS access"
                    },
                    onClick = { if (!granted) onAllow() },
                    enabled = !granted,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Maybe later",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onSkip)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * Help card for the Android 13+ "restricted settings" roadblock. When the user installs
 * Salli outside Play Store, the OS silently blocks SMS access until they toggle
 * "Allow restricted settings" on the app's info page. The system doesn't surface this
 * anywhere the user would notice — so we explain it inline.
 */
@Composable
private fun RestrictedSettingsCard(onOpenAppSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Android is blocking SMS access",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Apps installed outside Play Store can't ask for SMS by default. To let " +
                "Salli in:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "1. Tap Open settings below\n" +
                "2. Tap the ⋯ menu at the top right\n" +
                "3. Turn on \"Allow restricted settings\"\n" +
                "4. Come back and tap Try again",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.inverseSurface)
                .clickable(onClick = onOpenAppSettings)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(
                text = "Open settings",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}

/** Deep-link to the app's own system settings page. */
private fun openAppInfo(context: android.content.Context) {
    val intent = android.content.Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    ).apply {
        data = android.net.Uri.fromParts("package", context.packageName, null)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

/**
 * Apps that install Salli through a session-based PackageInstaller (Obtainium, F-Droid's
 * privileged extension, ADB, etc.) are exempt from Android 15's SMS permission restriction.
 * If we detect we came in via one of those, there's no point showing the "allow restricted
 * settings" card — the permission dialog will work normally.
 *
 * Returns true when the restriction is likely enforced (direct-sideload install, Android 13+).
 */
internal fun smsPermissionLikelyBlocked(context: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return false
    val installer = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
    }.getOrNull()
    // Sideload via a file manager / browser usually sets installer to the default package-
    // installer UI, which is the case that actually hits restricted settings. Obtainium,
    // F-Droid (+extension), Aurora, and `adb install` all mark themselves differently or
    // leave it null.
    val friendlyInstallers = setOf(
        "dev.imranr.obtainium",
        "dev.imranr.obtainium.fdroid",
        "org.fdroid.fdroid",
        "org.fdroid.fdroid.privileged",
        "app.grapheneos.apps",
        "com.aurora.store",
    )
    if (installer == null || installer in friendlyInstallers) return false
    return true
}

/* -------------------------------------------------------------------------- */
/* 4 — Import                                                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun ImportPage(
    import: ImportUiState,
    accounts: List<AccountEntity>,
    onDone: () -> Unit,
) {
    val fraction = if (import.total <= 0) 0f
    else (import.processed.toFloat() / import.total.toFloat()).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(400),
        label = "importProgress",
    )

    PageScaffold {
        Spacer(Modifier.weight(0.4f))
        val title = when {
            import.finished && import.error == null -> "All done."
            import.error != null -> "Hmm, something went wrong."
            import.total > 0 -> "Reading your\nbank messages…"
            else -> "Getting ready…"
        }
        Text(
            text = title,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 48.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = when {
                import.finished && import.error == null -> "Found ${import.inserted} transactions across ${accounts.size} account${if (accounts.size == 1) "" else "s"}."
                import.error != null -> import.error
                import.total > 0 -> "Scanned ${import.processed} of ${import.total} messages · ${import.inserted} transactions so far."
                else -> "Scanning your inbox for bank alerts…"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp,
        )

        Spacer(Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            LinearProgressIndicator(
                progress = { animatedFraction },
                color = MaterialTheme.colorScheme.onSurface,
                trackColor = androidx.compose.ui.graphics.Color.Transparent,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                drawStopIndicator = {},
            )
        }

        Spacer(Modifier.height(32.dp))

        if (accounts.isNotEmpty()) {
            Text(
                text = "ACCOUNTS DISCOVERED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            accounts.take(4).forEach { a ->
                DiscoveredAccountRow(a)
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.weight(1f))
        if (import.finished || import.error != null) {
            PillButton(text = "Open Salli", onClick = onDone)
        }
    }
}

@Composable
private fun DiscoveredAccountRow(a: AccountEntity) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = a.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = a.senderAddress + if (a.accountSuffix != "—") " · ${a.accountSuffix}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/* Shared scaffold + pill button                                              */
/* -------------------------------------------------------------------------- */

@Composable
private fun PageScaffold(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(top = top + 56.dp, bottom = bottom + 24.dp))
            .padding(horizontal = 28.dp),
        content = content,
    )
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val bg =
        if (enabled) MaterialTheme.colorScheme.inverseSurface
        else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg =
        if (enabled) MaterialTheme.colorScheme.inverseOnSurface
        else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            fontSize = 16.sp,
        )
    }
}
