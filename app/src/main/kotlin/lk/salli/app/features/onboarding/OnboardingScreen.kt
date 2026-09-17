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
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateIntAsState
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import java.time.LocalDate
import java.util.Locale
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
import lk.salli.design.theme.SalliTheme
import lk.salli.domain.TransactionFlow
import lk.salli.domain.money.MoneyFormat
import kotlinx.coroutines.launch

private val SmsPermissions = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
private data class SampleSms(val sender: String, val body: String, val result: String, val category: String, val amount: String)

// Fictional, redacted previews. The real inbox is never used in the introduction.
private val SampleMessages = listOf(
    SampleSms("COMBANK", "Rs. 4,280.00 spent at KEELLS on card ending 4273.", "Keells", "Groceries", "−Rs 4,280"),
    SampleSms("BOC", "Rs. 5,000.00 withdrawn from ATM using card ending 0870.", "BOC ATM", "Cash", "−Rs 5,000"),
    SampleSms("482913", "Your verification code is 482913. It expires in 5 minutes.", "", "", ""),
    SampleSms("PeoplesBank", "JustPay transfer of Rs. 2,400.00 was completed.", "JustPay transfer", "Transfers", "−Rs 2,400"),
    SampleSms("SLTBILL", "Your SLT bill payment of Rs. 11,953.00 was received.", "SLT bill", "Utilities", "−Rs 11,953"),
    SampleSms("DIALOG", "Special offer for you. Reply YES to claim.", "", "", ""),
    SampleSms("HNB", "Rs. 3,650.00 spent at CARGILLS on card ending 1734.", "Cargills", "Groceries", "−Rs 3,650"),
    SampleSms("1919", "Fuel purchase of Rs. 8,200.00 at IOC completed.", "IOC fuel", "Transport", "−Rs 8,200"),
    SampleSms("CEB", "Your electricity bill of Rs. 6,740.00 has been paid.", "CEB bill", "Utilities", "−Rs 6,740"),
)
private val SampleCardSizes = listOf(
    DpSize(218.dp, 82.dp), DpSize(184.dp, 76.dp), DpSize(170.dp, 70.dp),
    DpSize(208.dp, 80.dp), DpSize(196.dp, 78.dp), DpSize(176.dp, 70.dp),
    DpSize(214.dp, 82.dp), DpSize(186.dp, 74.dp), DpSize(194.dp, 76.dp),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit, onReviewUnknown: (() -> Unit)? = null, replay: Boolean = false, viewModel: OnboardingViewModel = hiltViewModel()) {
    SalliTheme(darkTheme = false) {
        OnboardingContent(onDone, onReviewUnknown, replay, viewModel)
    }
}

@Composable
private fun OnboardingContent(onDone: () -> Unit, onReviewUnknown: (() -> Unit)?, replay: Boolean, viewModel: OnboardingViewModel) {
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
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightStage
                isAppearanceLightNavigationBars = lightStage
            }
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

@Composable private fun OnboardingStage(act: Int, progress: Float, onSkip: () -> Unit, body: @Composable () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val stage = if (act == 0) SalliBrandColors.Cobalt else lerp(SalliBrandColors.Cobalt, MaterialTheme.colorScheme.background, progress)
    val content = if (act == 0 || progress < 0.65f) SalliBrandColors.OnCobalt else MaterialTheme.colorScheme.onBackground
    val color by animateColorAsState(stage, label = "onboarding stage")
    Box(Modifier.fillMaxSize().background(color)) {
        // The same card layer remains composed while Act 1 becomes Act 2.
        TiltSmsPhysics(
            bodySizes = SampleCardSizes,
            sortProgress = if (act == 0) 0f else progress,
            modifier = Modifier.fillMaxSize(),
            reducedMotion = LocalReducedMotion.current,
            onBodyLanded = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
            card = { Bubble(SampleMessages[it]) },
            row = { SortedRow(SampleMessages[it]) },
        )
        Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 16.dp, top = 4.dp)) {
            TextButton(onClick = onSkip, colors = ButtonDefaults.textButtonColors(contentColor = content)) { Text("Skip") }
        }
        CompositionLocalProvider(LocalContentColor provides content) {
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .then(if (act == 1) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp)
                    .navigationBarsPadding()
                    .padding(bottom = if (act == 0) 290.dp else 16.dp),
            ) { body() }
        }
    }
}

@Composable
private fun RevealAct(state: OnboardingState, replay: Boolean, onStart: () -> Unit, onDone: () -> Unit, onReview: () -> Unit) {
    val total = state.inboxSummary?.totalMessages ?: state.import.total
    val checkedTarget = if (replay) state.savedTransactionCount else state.import.processed
    val checked by animateIntAsState(checkedTarget, tween(durationMillis = 450), label = "history count")
    val cells = remember(state.historyDays, state.import.previews) {
        val firstDay = LocalDate.now().toEpochDay() - 363
        FloatArray(364).also { values ->
            (state.historyDays + state.import.previews.map { it.dayEpoch }).forEach { day ->
                val position = (day - firstDay).toInt()
                if (position in values.indices) values[position] += 1f
            }
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
    StageScaffold(footer = {
        state.import.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!replay && !state.import.running && !state.import.finished && state.import.error == null) {
            Button(onClick = onStart, Modifier.fillMaxWidth()) { Text("Read my history") }
        }
        if (!state.import.running && (replay || state.import.finished || state.import.error != null)) {
            Button(onClick = onDone, Modifier.fillMaxWidth()) { Text("Open Salli") }
            if (state.import.queued > 0) TextButton(onClick = onReview, Modifier.fillMaxWidth()) { Text("Review ${state.import.queued} unknown messages") }
        }
    }) {
        Text(if (replay) "Your saved history" else if (state.import.running) "Your history is coming together" else "Your history is ready", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (replay) "A look at what Salli already found on this phone."
            else "${String.format(Locale.US, "%,d", total)} messages from ${state.inboxSummary?.senders?.size ?: state.accounts.size} banks, read on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(16.dp)) {
                Text("YOUR YEAR", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                HeatGrid(columns = 26, rows = 14, values = cells, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth(), minAlpha = 0.12f, reducedMotion = LocalReducedMotion.current,
                    contentDescription = "Spending activity across the past year")
                Spacer(Modifier.height(10.dp))
                Text("Each square is a day. Your saved transactions light it up.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(String.format(Locale.US, "%,d", checked), style = MaterialTheme.typography.displayLarge)
        Text(if (replay) "transactions already in Salli" else "messages checked${if (state.import.running && total > 0) " of ${String.format(Locale.US, "%,d", total)}" else ""}", style = MaterialTheme.typography.bodyMedium)
        if (!replay) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RevealMetric("${state.import.inserted}", "new added", Modifier.weight(1f))
                RevealMetric("${state.savedTransactionCount}", "in Salli", Modifier.weight(1f))
                RevealMetric("${state.import.queued}", "to review", Modifier.weight(1f))
            }
        }
        if (state.import.previews.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text("JUST ADDED", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            state.import.previews.asReversed().forEach { preview ->
                val amount = if (preview.flow == TransactionFlow.EXPENSE) -preview.amountMinor else preview.amountMinor
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(preview.title ?: preview.sender ?: "Bank transaction", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Text(MoneyFormat.formatMinor(amount, preview.currency, signed = true), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun RevealMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StageScaffold(footer: @Composable () -> Unit, body: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
                start = 24.dp, end = 24.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { body() }
        footer()
    }
}
@Composable private fun Bubble(sample: SampleSms) {
    Surface(shape = RoundedCornerShape(16.dp), shadowElevation = 4.dp, color = Color.White, modifier = Modifier.fillMaxSize().padding(2.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(sample.sender, style = MaterialTheme.typography.labelMedium, color = Color(0xFF111827))
                Text("now", style = MaterialTheme.typography.labelSmall, color = Color(0xFF667085))
            }
            Spacer(Modifier.height(2.dp))
            Text(sample.body, style = MaterialTheme.typography.labelSmall, color = Color(0xFF303846), maxLines = 2)
        }
    }
}
@Composable private fun SortedRow(sample: SampleSms) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxSize().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(sample.result, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(sample.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(sample.amount, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
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
