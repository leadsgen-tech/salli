package lk.salli.app.features.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lk.salli.design.components.stage.BubbleStage
import lk.salli.design.components.stage.HeatGrid
import lk.salli.design.components.stage.SpringOdometer
import lk.salli.design.motion.LocalReducedMotion
import lk.salli.design.theme.SalliBrandColors
import kotlinx.coroutines.launch

private val SmsPermissions = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
private val BubbleLabels = listOf(
    "COMBANK · Card purchase\nRs 4,280 at KEELLS", "BOC · Cash withdrawal\nRs 5,000 at ATM",
    "OTP 482913\nExpires in 5 min", "People's Bank · JustPay\nRs 2,400 sent",
    "SLTBILL · Payment received\nRs 11,953", "DIALOG · Special offer\nReply YES to claim",
    "HNB · Card alert\nRs 3,650 at CARGILLS", "1919 · Fuel purchase\nRs 8,200 at IOC",
    "CEB · Bill paid\nRs 6,740",
)

@Composable
fun OnboardingScreen(onDone: () -> Unit, onReviewUnknown: (() -> Unit)? = null, replay: Boolean = false, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var act by rememberSaveable { mutableIntStateOf(if (replay) 0 else state.stage.ordinal.coerceIn(0, 2)) }
    var sort by remember { mutableFloatStateOf(if (replay) 1f else 0f) }
    val sortAnimation = remember { Animatable(sort) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = LocalView.current
    val visualSortProgress = sortAnimation.value
    val lightStage = act > 1 || (act == 1 && visualSortProgress > 0.65f)
    val backgroundColor = MaterialTheme.colorScheme.background
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            window.statusBarColor = if (lightStage) backgroundColor.toArgb() else SalliBrandColors.Cobalt.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = lightStage
        }
    }
    var tick by remember { mutableIntStateOf(0) }
    val granted = remember(tick) { SmsPermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val importing = state.import.running
    fun startSort() {
        act = 1
        scope.launch {
            sortAnimation.snapTo(0f)
            sortAnimation.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 420f))
            sort = 1f
        }
    }
    LaunchedEffect(granted, act, replay) {
        if (!replay && granted && act == 1) {
            act = 2
            viewModel.runImport()
        }
    }
    LaunchedEffect(state.completionTarget) {
        when (state.completionTarget) {
            OnboardingCompletionTarget.HOME -> { viewModel.consumeCompletion(); onDone() }
            OnboardingCompletionTarget.REVIEW_UNKNOWN -> { viewModel.consumeCompletion(); onReviewUnknown?.invoke() ?: onDone() }
            null -> Unit
        }
    }
    BackHandler(enabled = importing) {}
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        OnboardingStage(act, visualSortProgress, { if (replay) onDone() else viewModel.complete(deferHistory = true) }) {
            when (act) {
                0 -> ChaosCopy(::startSort)
                1 -> SortCopy(granted, { launcher.launch(SmsPermissions) }, { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))) }, { if (granted || replay) act = 2 else viewModel.complete(deferHistory = true) })
                else -> RevealAct(state, replay, { if (!replay && granted) viewModel.runImport() }, { if (replay) onDone() else viewModel.complete() }) { if (onReviewUnknown != null) viewModel.complete(target = OnboardingCompletionTarget.REVIEW_UNKNOWN) }
            }
        }
    }
}

@Composable private fun ChaosCopy(onSort: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Text("There's a money app hiding in your inbox.", style = MaterialTheme.typography.headlineMedium)
    Text("Every swipe, transfer and bill already texts you. Salli sorts them.", style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(18.dp)); Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSort() }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SalliBrandColors.AcidLime, contentColor = SalliBrandColors.OnAcidLime)) { Text("Sort them") }
}

@Composable private fun SortCopy(granted: Boolean, onAllow: () -> Unit, onSettings: () -> Unit, onContinue: () -> Unit) {
    Text("9 messages in", style = MaterialTheme.typography.labelLarge)
    Text("7 transactions out.", style = MaterialTheme.typography.displaySmall)
    Text("OTPs and promos stay out of your spending. Everything is sorted on your phone.", style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(20.dp))
    if (granted) Text("SMS access is ready. Your existing bank messages can build your history.") else {
        Text("Salli needs to read your SMS to do this for real.")
        Row { FilterChip(true, {}, label = { Text("Reads bank alerts") }); Spacer(Modifier.width(8.dp)); FilterChip(true, {}, label = { Text("Never keeps chats") }) }
        Button(onClick = onAllow, Modifier.fillMaxWidth()) { Text("Allow SMS access") }; TextButton(onClick = onSettings) { Text("Open settings") }
    }
    Button(onClick = onContinue, Modifier.fillMaxWidth()) { Text(if (granted) "Find my history" else "Continue without SMS") }
}

private val SortedLabels = listOf(
    "COMBANK · KEELLS · Groceries", "BOC ATM · Cash withdrawal", "OTP ignored",
    "People's Bank · JustPay · Transfer", "SLTBILL · Utilities", "DIALOG promo ignored",
    "HNB · CARGILLS · Card purchase", "1919 · IOC · Fuel", "CEB · Utilities",
)

@Composable private fun OnboardingStage(act: Int, progress: Float, onSkip: () -> Unit, body: @Composable () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val stage = if (act == 0) SalliBrandColors.Cobalt else lerp(SalliBrandColors.Cobalt, MaterialTheme.colorScheme.background, progress)
    val content = if (act == 0 || progress < 0.65f) SalliBrandColors.OnCobalt else MaterialTheme.colorScheme.onBackground
    StageScaffold(stage, content, onSkip) {
        // One persistent layer spans both acts. Its keyed bodies keep their physical positions
        // when progress changes, so sorting is a continuation of the pile rather than a reset.
        Box(Modifier.fillMaxWidth().height(720.dp)) {
            Column(Modifier.align(Alignment.TopCenter)) { body() }
            SmsCardStage(
                sortProgress = if (act == 0) 0f else progress,
                reducedMotion = LocalReducedMotion.current,
                onBodyLanded = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
            )
        }
    }
}

/** A responsive, deliberately overlapping SMS scene. The cards share keys across sorting. */
@Composable
private fun SmsCardStage(sortProgress: Float, reducedMotion: Boolean, onBodyLanded: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val cardW = with(density) { 200.dp.toPx() }
        val cardH = with(density) { 80.dp.toPx() }
        val gap = with(density) { 26.dp.toPx() }
        val cards = remember { List(BubbleLabels.size) { Animatable(-cardH) } }
        val progress = sortProgress.coerceIn(0f, 1f)
        BubbleLabels.forEachIndexed { index, _ ->
            val target = if (progress == 0f) {
                height - cardH - with(density) { 12.dp.toPx() } - (index.toFloat() * gap).coerceAtMost(height - cardH)
            } else {
                with(density) { (8 + index * 48).dp.toPx() }
            }
            LaunchedEffect(index, target, reducedMotion) {
                if (reducedMotion) cards[index].snapTo(target)
                else if (progress == 0f) {
                    kotlinx.coroutines.delay(index * 115L)
                    cards[index].animateTo(target, spring(dampingRatio = 0.72f, stiffness = 300f))
                    onBodyLanded()
                } else cards[index].animateTo(target, spring(dampingRatio = 0.86f, stiffness = 500f))
            }
            val x = if (progress == 0f) ((width - cardW) / 2f + (index % 3 - 1) * 18f) else with(density) { 12.dp.toPx() }
            val rowAlpha = if (progress > 0.35f) ((progress - 0.35f) / 0.25f).coerceIn(0f, 1f) else 0f
            Box(
                Modifier.offset { IntOffset(x.roundToInt(), cards[index].value.roundToInt()) }
                    .width(200.dp).height(80.dp).graphicsLayer { alpha = (1f - rowAlpha) * if (index in setOf(2, 5)) (1f - progress) else 1f },
            ) { Bubble(BubbleLabels[index]) }
            if (index !in setOf(2, 5)) {
                Box(
                    Modifier.offset { IntOffset(with(density) { 12.dp.toPx() }.roundToInt(), cards[index].value.roundToInt()) }
                        .fillMaxWidth().height(44.dp).graphicsLayer { alpha = rowAlpha },
                ) { SortedRow(SortedLabels[index]) }
            }
        }
    }
}

@Composable private fun RevealAct(state: OnboardingState, replay: Boolean, onStart: () -> Unit, onDone: () -> Unit, onReview: () -> Unit) = StageScaffold(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.onBackground, null) {
    val total = if (replay) 120 else state.import.total
    val found = if (replay) 890 else state.import.inserted
    Text(if (replay) "Sample history" else "${total} messages from ${state.accounts.size.coerceAtLeast(1)} banks", style = MaterialTheme.typography.headlineSmall)
    Text(if (replay) "A private preview of your year" else "Your history lights up as Salli reads it", style = MaterialTheme.typography.bodyLarge)
    val cells = remember(state.import.previews, replay) {
        FloatArray(364).also { values ->
            if (replay) for (i in values.indices) values[i] = (i % 7).toFloat()
            else state.import.previews.forEach { values[(it.dayEpoch % 364).toInt().coerceAtLeast(0)] += 1f }
        }
    }
    val haptic = LocalHapticFeedback.current
    var lastInserted by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.import.inserted) {
        if (state.import.inserted >= lastInserted + 25) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            lastInserted = state.import.inserted
        }
    }
    Spacer(Modifier.height(16.dp)); HeatGrid(columns = 52, rows = 7, values = cells, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth(), reducedMotion = LocalReducedMotion.current)
    Spacer(Modifier.height(12.dp)); SpringOdometer("${found}", style = MaterialTheme.typography.displayLarge)
    Text(if (state.import.running) "Reading ${state.import.processed} of ${total}" else "transactions found")
    state.import.previews.firstOrNull()?.let { Text((it.title ?: "Transaction") + " · " + it.amountMinor, style = MaterialTheme.typography.bodySmall) }
    if (!replay && !state.import.running && !state.import.finished) Button(onClick = onStart, Modifier.fillMaxWidth()) { Text("Read my history") }
    state.import.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (!state.import.running && (replay || state.import.finished || state.import.error != null)) {
        Button(onClick = onDone, Modifier.fillMaxWidth()) { Text("Open Salli") }
        if (state.import.queued > 0) TextButton(onClick = onReview) { Text("Review ${state.import.queued} unknown messages") }
    }
}

@Composable private fun StageScaffold(stage: Color, content: Color, skip: (() -> Unit)?, body: @Composable () -> Unit) {
    val color by animateColorAsState(stage, label = "onboarding stage")
    Column(Modifier.fillMaxSize().background(color).verticalScroll(rememberScrollState()).padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp, start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.Top) {
        if (skip != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = skip, colors = ButtonDefaults.textButtonColors(contentColor = if (stage.luminance() < 0.5f) SalliBrandColors.OnCobalt else MaterialTheme.colorScheme.primary)) { Text("Skip") } }
        CompositionLocalProvider(LocalContentColor provides content) { body() }
    }
}
@Composable private fun Bubble(text: String) { Surface(shape = RoundedCornerShape(14.dp), shadowElevation = 4.dp, modifier = Modifier.padding(2.dp)) { Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, maxLines = 2) } }
@Composable private fun SortedRow(text: String) { Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxSize().padding(vertical = 2.dp)) { Box(contentAlignment = Alignment.CenterStart) { Text(text, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1) } } }
private fun lerp(a: Color, b: Color, f: Float) = Color(a.red + (b.red-a.red)*f, a.green + (b.green-a.green)*f, a.blue + (b.blue-a.blue)*f, a.alpha + (b.alpha-a.alpha)*f)

private fun hasSmsPermission(context: Context): Boolean = SmsPermissions.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

private fun openAppInfo(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

/** Used by Settings as a hint when Android restricts sideloaded SMS permissions. */
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
        "dev.imranr.obtainium", "dev.imranr.obtainium.fdroid", "org.fdroid.fdroid",
        "org.fdroid.fdroid.privileged", "app.grapheneos.apps", "com.aurora.store",
    )
}
