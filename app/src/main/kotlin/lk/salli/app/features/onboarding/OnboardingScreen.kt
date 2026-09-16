package lk.salli.app.features.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import lk.salli.data.db.entities.AccountEntity
import lk.salli.design.logo.BankLogos
import lk.salli.design.theme.SalliBrandColors

private val SmsPermissions = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

/**
 * A deliberate onboarding sequence rather than a freely swipeable pager. Replay is a pure
 * product tour: it never requests permission, starts an import, or observes real accounts.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    replay: Boolean = false,
    onReviewUnknown: (() -> Unit)? = null,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val liveState = if (!replay) viewModel.state.collectAsStateWithLifecycle().value else null
    var replayStageName by rememberSaveable { mutableStateOf(OnboardingStage.WELCOME.name) }
    val stage = if (replay) {
        OnboardingStage.entries.firstOrNull { it.name == replayStageName } ?: OnboardingStage.WELCOME
    } else {
        liveState?.stage ?: OnboardingStage.WELCOME
    }

    var permissionRefresh by remember { mutableIntStateOf(0) }
    val smsGranted = remember(permissionRefresh) { hasSmsPermission(context) }
    var permissionAttempts by rememberSaveable { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner, replay) {
        if (replay) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = if (!replay) {
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionAttempts++
            permissionRefresh++
        }
    } else null

    val sequence = if (replay) {
        listOf(OnboardingStage.WELCOME, OnboardingStage.DEMO, OnboardingStage.PRIVACY, OnboardingStage.IMPORT)
    } else OnboardingStage.entries
    val position = sequence.indexOf(stage).coerceAtLeast(0)
    val importRunning = liveState?.import?.running == true
    fun show(next: OnboardingStage) {
        if (replay) replayStageName = next.name else viewModel.showStage(next)
    }
    fun back() {
        if (importRunning) return
        if (position == 0) {
            if (replay) onDone()
        } else show(sequence[position - 1])
    }
    fun finish(
        deferHistory: Boolean = false,
        target: OnboardingCompletionTarget = OnboardingCompletionTarget.HOME,
    ) {
        if (replay) onDone() else viewModel.complete(deferHistory, target)
    }

    LaunchedEffect(liveState?.completionTarget) {
        val target = liveState?.completionTarget ?: return@LaunchedEffect
        when (target) {
            OnboardingCompletionTarget.HOME -> onDone()
            OnboardingCompletionTarget.REVIEW_UNKNOWN -> onReviewUnknown?.invoke() ?: onDone()
        }
        viewModel.consumeCompletion()
    }

    BackHandler(enabled = importRunning) { /* Keep the database writer attached to this flow. */ }
    BackHandler(enabled = !importRunning && (replay || position > 0)) { back() }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        StageLayout(
            step = position + 1,
            stepCount = sequence.size,
            showBack = !importRunning && (replay || position > 0),
            onBack = ::back,
        ) {
            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    val forward = sequence.indexOf(targetState) > sequence.indexOf(initialState)
                    if (forward) {
                        (slideInHorizontally { it / 5 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 7 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it / 7 } + fadeOut())
                    }
                },
                modifier = Modifier.fillMaxSize(),
                label = "onboarding stage",
            ) { visibleStage ->
            when (visibleStage) {
                OnboardingStage.WELCOME -> WelcomeStage(
                    onContinue = { show(OnboardingStage.DEMO) },
                    onExplore = if (replay) null else ({ finish(deferHistory = smsGranted) }),
                    completing = liveState?.completing == true,
                )
                OnboardingStage.DEMO -> DemoStage(onContinue = { show(OnboardingStage.PRIVACY) })
                OnboardingStage.PRIVACY -> PrivacyStage(
                    replay = replay,
                    onContinue = {
                        show(if (replay) OnboardingStage.IMPORT else OnboardingStage.SMS_ACCESS)
                    },
                )
                OnboardingStage.SMS_ACCESS -> SmsAccessStage(
                    granted = smsGranted,
                    permissionAttempted = permissionAttempts > 0,
                    onAllow = { permissionLauncher?.launch(SmsPermissions) },
                    onOpenSettings = { openAppInfo(context) },
                    onContinue = { show(OnboardingStage.IMPORT) },
                    onMaybeLater = { finish(deferHistory = smsGranted) },
                    completing = liveState?.completing == true,
                    completionError = liveState?.completionError,
                )
                OnboardingStage.IMPORT -> if (replay) {
                    ReplayResultStage(onDone = onDone)
                } else {
                    ImportStage(
                        import = liveState?.import ?: ImportUiState(),
                        accounts = liveState?.accounts.orEmpty(),
                        completing = liveState?.completing == true,
                        completionError = liveState?.completionError,
                        onImport = viewModel::runImport,
                        onExplore = { finish(deferHistory = true) },
                        onDone = { finish() },
                        onReviewUnknown = onReviewUnknown?.let {
                            { finish(target = OnboardingCompletionTarget.REVIEW_UNKNOWN) }
                        },
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun StageLayout(
    step: Int,
    stepCount: Int,
    showBack: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = maxOf(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = top, bottom = bottom),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            } else Spacer(Modifier.size(48.dp))
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(stepCount) { index ->
                    Box(
                        Modifier
                            .width(if (index + 1 == step) 24.dp else 7.dp)
                            .height(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (index + 1 == step) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                            ),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun StagePage(
    modifier: Modifier = Modifier,
    action: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) { content() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) { action() }
    }
}

@Composable
private fun WelcomeStage(onContinue: () -> Unit, onExplore: (() -> Unit)?, completing: Boolean) {
    StagePage(
        action = {
            ExpressiveButton("Get started", onClick = onContinue)
            if (onExplore != null) {
                TextButton(
                    onClick = onExplore,
                    enabled = !completing,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text(if (completing) "Saving…" else "Explore first") }
            }
        },
    ) {
        Surface(
            color = Color(0xFFFFFBF2),
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AsyncImage(
                model = "file:///android_asset/onboarding/sms_to_expense.png",
                contentDescription = "A bank message becoming a sorted expense on a phone",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(10.dp),
            )
        }
        Spacer(Modifier.height(26.dp))
        Text(
            "SALLI",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = buildAnnotatedString {
                append("Your spending.\n")
                withStyle(
                    SpanStyle(
                        background = SalliBrandColors.AcidLime,
                        color = SalliBrandColors.OnAcidLime,
                    ),
                ) { append("Sorted.") }
            },
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Black,
                lineHeight = 44.sp,
                letterSpacing = (-1).sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.clearAndSetSemantics {
                heading()
                contentDescription = "Your spending. Sorted."
            },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Salli turns Sri Lankan bank alerts into a private, useful timeline.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DemoStage(onContinue: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var wasCategorized by remember { mutableStateOf(false) }
    fun setProgress(value: Float) {
        if ((value >= .52f) != wasCategorized) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            wasCategorized = value >= .52f
        }
        scope.launch { progress.snapTo(value) }
    }
    fun play() {
        scope.launch {
            progress.snapTo(0f)
            wasCategorized = false
            progress.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
            )
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            wasCategorized = true
        }
    }

    StagePage(action = { ExpressiveButton("Continue", onClick = onContinue) }) {
        Eyebrow("TRY IT")
        Headline("One message. One clean expense.")
        Spacer(Modifier.height(22.dp))
        MorphDemo(progress.value)
        Spacer(Modifier.height(12.dp))
        Slider(
            value = progress.value,
            onValueChange = ::setProgress,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (progress.value < .5f) "Slide to sort the sample message"
            else "Categorized locally — nothing was uploaded",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = ::play, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (progress.value > .05f) "Repeat demo" else "Play demo")
        }
    }
}

@Composable
private fun MorphDemo(progress: Float) {
    val categorized = progress >= .5f
    val shape = RoundedCornerShape(
        topStart = 26.dp,
        topEnd = 26.dp,
        bottomEnd = 26.dp,
        bottomStart = (6 + progress * 20).dp,
    )
    Surface(
        color = lerp(SalliBrandColors.Cobalt, MaterialTheme.colorScheme.surfaceContainerLowest, progress),
        contentColor = lerp(SalliBrandColors.OnCobalt, MaterialTheme.colorScheme.onSurface, progress),
        shape = shape,
        tonalElevation = if (categorized) 2.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 176.dp)
            .clearAndSetSemantics {
                contentDescription = if (categorized) {
                    "Sample transaction. Keells Super. Groceries. 4,280 rupees."
                } else {
                    "Sample bank SMS. Card purchase for 4,280 rupees at Keells Super."
                }
            },
    ) {
        Box(Modifier.fillMaxSize().padding(20.dp)) {
            Text(
                "SAMPLE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = lerp(SalliBrandColors.AcidLime, SalliBrandColors.Cobalt, progress),
                modifier = Modifier.align(Alignment.TopStart),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        alpha = (1f - progress * 1.8f).coerceIn(0f, 1f)
                        translationX = progress * -30.dp.toPx()
                    },
            ) {
                Text("COMBANK", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Card purchase Rs 4,280.00\nat KEELLS SUPER", style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = ((progress - .35f) * 1.55f).coerceIn(0f, 1f)
                        translationX = (1f - progress) * 32.dp.toPx()
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(SalliBrandColors.AcidLime),
                    contentAlignment = Alignment.Center,
                ) { Text("K", color = SalliBrandColors.OnAcidLime, fontWeight = FontWeight.Black) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Keells Super", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Groceries", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("− Rs 4,280", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PrivacyStage(replay: Boolean, onContinue: () -> Unit) {
    StagePage(action = { ExpressiveButton("Continue", onClick = onContinue) }) {
        Eyebrow("YOUR CONTROL")
        Headline("Private by construction.")
        Spacer(Modifier.height(24.dp))
        PrivacyRow(Icons.Outlined.Lock, "Processed on your phone", "Bank and bill alerts become useful records right here.")
        PrivacyRow(Icons.Outlined.CloudOff, "No network permission", "The app cannot send your SMS or spending data anywhere.")
        PrivacyRow(Icons.Outlined.VisibilityOff, "No account or tracking", "No login, analytics, ads, or telemetry.")
        Spacer(Modifier.height(16.dp))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(20.dp),
        ) {
            Text(
                if (replay) "This tour won’t read messages or change your data."
                else "Nothing uploads automatically. You choose SMS access, history scans, and any files you export.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(18.dp),
            )
        }
    }
}

@Composable
private fun PrivacyRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SmsAccessStage(
    granted: Boolean,
    permissionAttempted: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onContinue: () -> Unit,
    onMaybeLater: () -> Unit,
    completing: Boolean,
    completionError: String?,
) {
    StagePage(
        action = {
            if (granted) ExpressiveButton("Continue", onClick = onContinue)
            else ExpressiveButton(if (permissionAttempted) "Try again" else "Allow SMS access", onClick = onAllow)
            TextButton(
                onClick = onMaybeLater,
                enabled = !completing,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(if (completing) "Saving…" else "Maybe later") }
        },
    ) {
        Eyebrow("OPTIONAL ACCESS")
        Headline(if (granted) "SMS access is ready." else "Let Salli read bank alerts.")
        Spacer(Modifier.height(18.dp))
        Surface(
            color = if (granted) SalliBrandColors.AcidLime else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (granted) SalliBrandColors.OnAcidLime else MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (granted) Icons.Outlined.Check else Icons.Outlined.Sms, contentDescription = null, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(if (granted) "Allowed" else "Read and receive SMS", fontWeight = FontWeight.Bold)
                    Text(
                        if (granted) "Choose next whether to scan your older bank messages."
                        else "Used only to find bank transaction messages on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Salli does not request contacts, phone calls, location, storage, or notification access here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!granted && permissionAttempted) {
            Spacer(Modifier.height(18.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Access wasn’t granted", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "You can try again. If Android no longer shows the prompt, open this app’s settings and allow SMS there.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onOpenSettings, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
                        Text("Open app settings")
                    }
                }
            }
        }
        CompletionError(completionError)
    }
}

@Composable
private fun ImportStage(
    import: ImportUiState,
    accounts: List<AccountEntity>,
    completing: Boolean,
    completionError: String?,
    onImport: () -> Unit,
    onExplore: () -> Unit,
    onDone: () -> Unit,
    onReviewUnknown: (() -> Unit)?,
) {
    val fraction = if (import.total == 0) 0f else import.processed.toFloat() / import.total
    StagePage(
        action = {
            when {
                import.running -> ExpressiveButton("Reading messages…", onClick = {}, enabled = false)
                import.finished -> {
                    ExpressiveButton("Open Salli", onClick = onDone, enabled = !completing)
                    OutlinedButton(
                        onClick = onImport,
                        enabled = !completing,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Scan again") }
                    if (import.queued > 0 && onReviewUnknown != null) {
                        OutlinedButton(
                            onClick = onReviewUnknown,
                            enabled = !completing,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) { Text("Review ${import.queued} unknown") }
                    }
                }
                else -> {
                    ExpressiveButton(
                        if (import.error != null || import.interrupted) "Retry scan" else "Scan past messages",
                        onClick = onImport,
                    )
                    TextButton(
                        onClick = onExplore,
                        enabled = !completing,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text(if (completing) "Saving…" else "Explore first") }
                }
            }
        },
    ) {
        Eyebrow("HISTORY")
        Headline(
            when {
                import.running -> "Finding your spending."
                import.finished -> "Your timeline is ready."
                import.error != null -> "The scan stopped."
                import.interrupted -> "Continue where you left off."
                else -> "Bring in past bank alerts?"
            },
        )
        Spacer(Modifier.height(14.dp))
        Text(
            when {
                import.running && import.total > 0 -> "Read ${import.processed} of ${import.total} messages"
                import.running -> "Opening your SMS inbox…"
                import.finished && import.total == 0 -> "No messages were found. New bank alerts can still appear as they arrive."
                import.finished && import.inserted == 0 && import.queued > 0 -> "No transactions matched yet. ${import.queued} bank messages need a quick review."
                import.finished && import.inserted == 0 && (import.duplicates + import.merged) > 0 ->
                    "Scan complete. ${import.duplicates + import.merged} message${if (import.duplicates + import.merged == 1) " was" else "s were"} already reflected in Salli."
                import.finished && import.inserted == 0 -> "No supported bank transactions were found. New bank alerts can still appear as they arrive."
                import.finished -> "Found ${import.inserted} transaction${if (import.inserted == 1) "" else "s"} across ${accounts.size} account${if (accounts.size == 1) "" else "s"}."
                import.error != null -> import.error
                import.interrupted -> "The earlier scan was interrupted. Retrying is safe; duplicate transactions are ignored."
                else -> "Salli will scan the inbox once. You can skip this and start with an empty timeline."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (import.running) {
            Spacer(Modifier.height(24.dp))
            if (import.total > 0) LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            else CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        if (import.finished && import.inserted > 0) {
            Spacer(Modifier.height(22.dp))
            ResultStrip(import)
            if (accounts.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Eyebrow("ACCOUNTS DISCOVERED")
                accounts.take(3).forEach { AccountRow(it) }
                if (accounts.size > 3) Text(
                    "+${accounts.size - 3} more in Settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        CompletionError(completionError)
    }
}

@Composable
private fun ResultStrip(import: ImportUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ResultNumber(import.inserted, "transactions", Modifier.weight(1f))
        ResultNumber(import.queued, "to review", Modifier.weight(1f))
    }
}

@Composable
private fun ResultNumber(number: Int, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(number.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AccountRow(account: AccountEntity) {
    val logo = BankLogos.resolve(account.senderAddress)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (logo != null) {
            AsyncImage(
                model = BankLogos.asAssetUri(logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(CircleShape),
            )
        } else {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.AccountBalance, contentDescription = null, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(account.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                account.senderAddress + account.accountSuffix.takeIf { it != "—" }?.let { " · •••• $it" }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReplayResultStage(onDone: () -> Unit) {
    StagePage(action = { ExpressiveButton("Return to Settings", onClick = onDone) }) {
        Eyebrow("SAMPLE RESULT")
        Headline("That’s the whole flow.")
        Spacer(Modifier.height(20.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("SAMPLE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = SalliBrandColors.Cobalt)
                Spacer(Modifier.height(10.dp))
                Text("18 transactions categorized", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("No messages were read and no settings changed during this replay.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CompletionError(error: String?) {
    if (error == null) return
    Spacer(Modifier.height(14.dp))
    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun Eyebrow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.4.sp,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Headline(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black, lineHeight = 39.sp),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.clearAndSetSemantics {
            heading()
            contentDescription = text
        },
    )
}

@Composable
private fun ExpressiveButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corners by animateDpAsState(
        if (pressed) 16.dp else 30.dp,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "button corners",
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(corners),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
}

private fun hasSmsPermission(context: Context): Boolean = SmsPermissions.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

private fun openAppInfo(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

/** Used by Settings too; a hint only. The onboarding copy never claims all sideloads are blocked. */
internal fun smsPermissionLikelyBlocked(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val installer = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
    }.getOrNull()
    return installer != null && installer !in setOf(
        "dev.imranr.obtainium",
        "dev.imranr.obtainium.fdroid",
        "org.fdroid.fdroid",
        "org.fdroid.fdroid.privileged",
        "app.grapheneos.apps",
        "com.aurora.store",
    )
}
