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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
private val BubbleLabels = listOf("COMBANK\nCard purchase", "BOC\nATM withdrawal", "OTP 482913", "PeoplesBank\nJustPay", "SLTBILL\nRs 11,953", "DIALOG promo", "HNB\nCard alert", "1919\nFuel", "CEB\nBill")

@Composable
fun OnboardingScreen(onDone: () -> Unit, onReviewUnknown: (() -> Unit)? = null, replay: Boolean = false, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var act by rememberSaveable { mutableIntStateOf(if (replay) 0 else state.stage.ordinal.coerceIn(0, 2)) }
    var sort by remember { mutableFloatStateOf(if (replay) 1f else 0f) }
    val sortAnimation = remember { Animatable(sort) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
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
        Box(Modifier.fillMaxSize()) {
            when (act) {
                0 -> ChaosAct({ if (replay) onDone() else viewModel.complete(deferHistory = true) }, ::startSort)
                1 -> SortAct(sortAnimation.value, granted, { sort = it }, { launcher.launch(SmsPermissions) }, { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))) }, { act = 2 }, { if (replay) onDone() else viewModel.complete(deferHistory = true) })
                else -> RevealAct(state, replay, { if (!replay && granted) viewModel.runImport() }, { if (replay) onDone() else viewModel.complete() }) { if (onReviewUnknown != null) viewModel.complete(target = OnboardingCompletionTarget.REVIEW_UNKNOWN) }
            }
        }
    }
}

@Composable private fun ChaosAct(onSkip: () -> Unit, onSort: () -> Unit) = StageScaffold(SalliBrandColors.Cobalt, SalliBrandColors.OnCobalt, onSkip) {
    val haptic = LocalHapticFeedback.current
    BubbleStage(BubbleLabels.map { androidx.compose.ui.unit.DpSize(142.dp, 54.dp) }, 0f, Modifier.fillMaxWidth().height(360.dp), setOf(2, 5), reducedMotion = LocalReducedMotion.current, bubble = { Bubble(BubbleLabels[it]) }, row = { Bubble(BubbleLabels[it]) })
    Text("There's a money app hiding in your inbox.", style = MaterialTheme.typography.headlineMedium)
    Text("Every swipe, transfer and bill already texts you. Salli sorts them.", style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(18.dp)); Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSort() }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SalliBrandColors.AcidLime, contentColor = SalliBrandColors.OnAcidLime)) { Text("Sort them") }
}

@Composable private fun SortAct(progress: Float, granted: Boolean, onProgress: (Float) -> Unit, onAllow: () -> Unit, onSettings: () -> Unit, onContinue: () -> Unit, onSkip: () -> Unit) = StageScaffold(lerp(SalliBrandColors.Cobalt, MaterialTheme.colorScheme.background, progress), MaterialTheme.colorScheme.onBackground, onSkip) {
    BubbleStage(
        bodySizes = BubbleLabels.map { androidx.compose.ui.unit.DpSize(142.dp, 54.dp) },
        sortProgress = progress,
        modifier = Modifier.fillMaxWidth().height(230.dp),
        discarded = setOf(2, 5),
        reducedMotion = LocalReducedMotion.current,
        bubble = { Bubble(BubbleLabels[it]) },
        row = { Bubble(if (it == 0) "Keells Super · Groceries" else BubbleLabels[it]) },
    )
    Text("OTPs and promos: ignored.", style = MaterialTheme.typography.labelLarge)
    Text("One clean timeline.", style = MaterialTheme.typography.displaySmall)
    Text("Sorted on your phone. Nothing leaves it.", style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(20.dp)); SpringOdometer("Rs 27,830", style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(20.dp))
    if (granted) Text("SMS access is ready. Your existing bank messages can build your history.") else {
        Text("Salli needs to read your SMS to do this for real.")
        Row { FilterChip(true, {}, label = { Text("Reads bank alerts") }); Spacer(Modifier.width(8.dp)); FilterChip(true, {}, label = { Text("Never keeps chats") }) }
        Button(onClick = onAllow, Modifier.fillMaxWidth()) { Text("Allow SMS access") }; TextButton(onClick = onSettings) { Text("Open settings") }
    }
    Button(onClick = onContinue, Modifier.fillMaxWidth()) { Text("Continue") }
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
    Column(Modifier.fillMaxSize().background(color).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
        if (skip != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = skip) { Text("Skip") } }
        CompositionLocalProvider(LocalContentColor provides content) { body() }
    }
}
@Composable private fun Bubble(text: String) { Surface(shape = RoundedCornerShape(18.dp), shadowElevation = 4.dp, modifier = Modifier.padding(4.dp)) { Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge) } }
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
