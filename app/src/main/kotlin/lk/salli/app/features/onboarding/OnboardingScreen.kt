package lk.salli.app.features.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lk.salli.data.db.entities.AccountEntity
import lk.salli.design.logo.BankLogos

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
 * Four-page onboarding redesigned for smooth, performant motion and a more
 * engaging visual experience.  All animation is driven by a single [Animatable]
 * float per page, updated through [graphicsLayer] — no [AnimatedVisibility]
 * chains, no manual delay staging, minimal recomposition cost.
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
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Warm decorative circles behind everything — subtle, constant-motion feel
        // as pages slide.  Canvas is cheap; one draw per frame during page transitions.
        DecorativeCircles(pagerProgress = pagerState.currentPage + pagerState.currentPageOffsetFraction)

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            val active = pagerState.settledPage == page
            when (page) {
                PAGE_WELCOME -> WelcomePage(active = active, onNext = next)
                PAGE_PROMISE -> PromisePage(active = active, onNext = next)
                PAGE_PERMISSION -> PermissionPage(
                    active = active,
                    granted = permissionsGranted,
                    showRestrictedHelp = !permissionsGranted && permissionAttempts > 0 &&
                        smsPermissionLikelyBlocked(context),
                    onAllow = { launcher.launch(REQUIRED_PERMS) },
                    onOpenAppSettings = { openAppInfo(context) },
                    onSkip = onDone,
                )
                PAGE_IMPORT -> ImportPage(
                    active = active,
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
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 18.dp,
                ),
        )
    }

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
/* Animation primitive — one float, zero state machines                       */
/* -------------------------------------------------------------------------- */

@Composable
private fun rememberPageProgress(active: Boolean): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        } else {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180),
            )
        }
    }
    return progress.value
}

/**
 * Convenience: content enters from below with a fade.  [delay] shifts the start of the
 * motion curve so sibling items stagger naturally from the same [progress] float.
 */
private fun Modifier.enterFromBottom(progress: Float, delay: Float = 0f): Modifier =
    graphicsLayer {
        val effective = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
        alpha = effective
        translationY = (1f - effective) * 40.dp.toPx()
    }

private fun Modifier.enterFromBottomTight(progress: Float, delay: Float = 0f): Modifier =
    graphicsLayer {
        val effective = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
        alpha = effective
        translationY = (1f - effective) * 24.dp.toPx()
    }

private fun Modifier.scaleIn(progress: Float, delay: Float = 0f): Modifier =
    graphicsLayer {
        val effective = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
        alpha = effective
        scaleX = 0.92f + effective * 0.08f
        scaleY = 0.92f + effective * 0.08f
    }

/* -------------------------------------------------------------------------- */
/* Background ambience                                                        */
/* -------------------------------------------------------------------------- */

@Composable
private fun DecorativeCircles(pagerProgress: Float) {
    val accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    val secondary = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.04f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Three large orbs that drift slowly as the user pages through.
        // Their centres are a function of pagerProgress so the background feels alive
        // even when the user's finger isn't on screen.
        val cx = size.width * 0.5f
        val cy = size.height * 0.45f

        val drift = pagerProgress * 80f

        drawCircle(
            color = accent,
            radius = size.width * 0.55f,
            center = Offset(cx - 120f + drift, cy - 180f),
        )
        drawCircle(
            color = secondary,
            radius = size.width * 0.38f,
            center = Offset(cx + 140f - drift * 0.6f, cy + 200f),
        )
        drawCircle(
            color = accent.copy(alpha = 0.03f),
            radius = size.width * 0.28f,
            center = Offset(cx - 40f + drift * 0.3f, cy + 60f),
        )
    }
}

/* -------------------------------------------------------------------------- */
/* Page indicator                                                             */
/* -------------------------------------------------------------------------- */

@Composable
private fun PageIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        repeat(count) { i ->
            val active = i == current
            val width by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (active) 24.dp else 6.dp,
                animationSpec = tween(durationMillis = 240),
                label = "dotWidth",
            )
            val alpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (active) 1f else 0.25f,
                animationSpec = tween(durationMillis = 240),
                label = "dotAlpha",
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    ),
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/* 1 — Welcome                                                                */
/* -------------------------------------------------------------------------- */

@Composable
private fun WelcomePage(active: Boolean, onNext: () -> Unit) {
    val progress = rememberPageProgress(active)

    PageScaffold {
        Spacer(Modifier.weight(0.55f))

        // Sinhala greeting — secondary, enters first
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .enterFromBottom(progress, delay = 0f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "\u0D86\u0DBA\u0DD4\u0DB6\u0DDC\u0DC0\u0DB1",
                fontSize = 56.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.5).sp,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Transliteration
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .enterFromBottomTight(progress, delay = 0.12f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "ayubowan",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                letterSpacing = 3.sp,
            )
        }

        Spacer(Modifier.height(48.dp))

        // Wordmark + tagline.  A tiny primary-tinted underline appears with the wordmark —
        // gives the brand mark a little anchor without committing to a logo.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .scaleIn(progress, delay = 0.22f),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Salli",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-1).sp,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Your money,\nin your pocket.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // CTA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .enterFromBottomTight(progress, delay = 0.45f),
        ) {
            PillButton(text = "Get started", onClick = onNext)
        }
    }
}

/* -------------------------------------------------------------------------- */
/* 2 — Privacy promise                                                        */
/* -------------------------------------------------------------------------- */

@Composable
private fun PromisePage(active: Boolean, onNext: () -> Unit) {
    val progress = rememberPageProgress(active)

    PageScaffold {
        Spacer(Modifier.weight(0.35f))

        // Headline
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .enterFromBottom(progress, delay = 0f),
        ) {
            Text(
                text = "Everything\nstays on\nyour phone.",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 46.sp,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-0.8).sp,
            )
        }

        Spacer(Modifier.height(40.dp))

        // Three promise cards — staggered, each anchored by an icon that makes the claim
        // skimmable in a glance.
        PromiseCard(
            icon = Icons.Outlined.PhoneAndroid,
            label = "On-device",
            text = "Bank SMS are parsed on this phone. They never leave it.",
            progress = progress,
            delay = 0.18f,
        )
        Spacer(Modifier.height(12.dp))
        PromiseCard(
            icon = Icons.Outlined.CloudOff,
            label = "No cloud",
            text = "No backend, no login, no account to create.",
            progress = progress,
            delay = 0.28f,
        )
        Spacer(Modifier.height(12.dp))
        PromiseCard(
            icon = Icons.Outlined.VisibilityOff,
            label = "No tracking",
            text = "No ads, no analytics, no telemetry pings.",
            progress = progress,
            delay = 0.38f,
        )

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .enterFromBottomTight(progress, delay = 0.52f),
        ) {
            PillButton(text = "Continue", onClick = onNext)
        }
    }
}

@Composable
private fun PromiseCard(
    icon: ImageVector,
    label: String,
    text: String,
    progress: Float,
    delay: Float,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .enterFromBottomTight(progress, delay = delay)
            // Background must come BEFORE clip so the rounded shape is honoured for the
            // ripple too. Padding goes last so it's inside the shape.
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerHighest.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        // Icon disc — primary-tinted, soft surface around the glyph so it reads as a chip,
        // not a flat icon.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(scheme.primary.copy(alpha = 0.12f)),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/* 3 — Permission                                                             */
/* -------------------------------------------------------------------------- */

@Composable
private fun PermissionPage(
    active: Boolean,
    granted: Boolean,
    showRestrictedHelp: Boolean,
    onAllow: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onSkip: () -> Unit,
) {
    val progress = rememberPageProgress(active)

    PageScaffold {
        Spacer(Modifier.weight(0.3f))

        // Visual anchor — a simple shield-like shape built from two overlapping circles
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .scaleIn(progress, delay = 0f),
            contentAlignment = Alignment.Center,
        ) {
            PermissionVisual()
        }

        Spacer(Modifier.height(36.dp))

        // Headline
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .enterFromBottom(progress, delay = 0.12f),
        ) {
            Text(
                text = "One permission,\nthen we're done.",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 42.sp,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-0.6).sp,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .enterFromBottomTight(progress, delay = 0.22f),
        ) {
            Text(
                text = "Salli reads SMS from your banks to build your transaction timeline. " +
                    "Messages stay on this phone.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp,
            )
        }

        if (showRestrictedHelp) {
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier.enterFromBottomTight(progress, delay = 0.3f),
            ) {
                RestrictedSettingsCard(onOpenAppSettings = onOpenAppSettings)
            }
        }

        Spacer(Modifier.weight(1f))

        // Actions
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .enterFromBottomTight(progress, delay = 0.38f),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
 * Tiny SMS-bubble cluster, rendered with three overlapping speech-bubble cards that share
 * the page's primary accent. Each bubble carries a faux row of "characters" rendered as
 * tiny pills so the illustration reads as a stack of redacted SMS without committing to
 * any literal text. Pure Compose — adapts to palette and fits any screen density.
 */
@Composable
private fun PermissionVisual() {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier = Modifier.size(width = 200.dp, height = 140.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Back-most bubble — faded, peeking out from behind.
        SmsBubble(
            modifier = Modifier
                .offset(x = (-46).dp, y = (-26).dp)
                .size(width = 110.dp, height = 56.dp),
            tint = surface,
            contentTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        // Middle bubble — a touch more opaque.
        SmsBubble(
            modifier = Modifier
                .offset(x = 38.dp, y = (-4).dp)
                .size(width = 130.dp, height = 60.dp),
            tint = surface,
            contentTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        )
        // Front bubble — primary-tinted, the focal point.
        Box(
            modifier = Modifier
                .offset(x = (-12).dp, y = 28.dp)
                .size(width = 150.dp, height = 64.dp)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 6.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            primary.copy(alpha = 0.95f),
                            primary.copy(alpha = 0.75f),
                        ),
                    ),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Sms,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    BubbleLine(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f), width = 92.dp)
                    BubbleLine(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f), width = 64.dp)
                }
            }
        }
    }
}

@Composable
private fun SmsBubble(
    modifier: Modifier = Modifier,
    tint: Color,
    contentTint: Color,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp))
            .background(tint)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            BubbleLine(contentTint, width = 70.dp)
            BubbleLine(contentTint.copy(alpha = contentTint.alpha * 0.7f), width = 48.dp)
        }
    }
}

@Composable
private fun BubbleLine(color: Color, width: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(6.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * Help card for the Android 13+ "restricted settings" roadblock.
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
    active: Boolean,
    import: ImportUiState,
    accounts: List<AccountEntity>,
    onDone: () -> Unit,
) {
    val progress = rememberPageProgress(active)
    val fraction = if (import.total <= 0) 0f
    else (import.processed.toFloat() / import.total.toFloat()).coerceIn(0f, 1f)

    val animatedFraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(500),
        label = "importProgress",
    )
    // Smoothly count the visible numbers up as transactions land \u2014 no jarring jumps when the
    // importer batches insertions. Same animation length as the bar so the percentage and the
    // counter agree at every frame.
    val animatedProcessed by animateIntAsState(
        targetValue = import.processed,
        animationSpec = tween(500),
        label = "importProcessed",
    )
    val animatedInserted by animateIntAsState(
        targetValue = import.inserted,
        animationSpec = tween(500),
        label = "importInserted",
    )
    val finished = import.finished && import.error == null

    PageScaffold {
        Spacer(Modifier.weight(0.35f))

        val title = when {
            finished -> "All done."
            import.error != null -> "Hmm, something went wrong."
            import.total > 0 -> "Reading your\nbank messages..."
            else -> "Getting ready..."
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .enterFromBottom(progress, delay = 0f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 44.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.6).sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (finished) {
                    Spacer(Modifier.width(12.dp))
                    FinishedTick()
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .enterFromBottomTight(progress, delay = 0.12f),
        ) {
            Text(
                text = when {
                    finished ->
                        "Found $animatedInserted transactions across ${accounts.size} account${if (accounts.size == 1) "" else "s"}."
                    import.error != null -> import.error
                    import.total > 0 ->
                        "Scanned $animatedProcessed of ${import.total} messages \u00b7 $animatedInserted transactions so far."
                    else -> "Scanning your inbox for bank alerts..."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp,
            )
        }

        Spacer(Modifier.height(32.dp))

        // Progress bar — alive, with a subtle glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .enterFromBottomTight(progress, delay = 0.2f),
        ) {
            Column {
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
                        trackColor = Color.Transparent,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        drawStopIndicator = {},
                    )
                }
                if (import.total > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${(animatedFraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        if (accounts.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .enterFromBottomTight(progress, delay = 0.3f),
            ) {
                Text(
                    text = "ACCOUNTS DISCOVERED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            accounts.take(4).forEachIndexed { idx, a ->
                DiscoveredAccountChipReveal(account = a, index = idx)
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.weight(1f))

        if (import.finished || import.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .enterFromBottomTight(progress, delay = 0.4f),
            ) {
                PillButton(text = "Open Salli", onClick = onDone)
            }
        }
    }
}

/**
 * Animated checkmark badge shown next to "All done." when import completes. Uses a quick
 * spring scale-in so it lands with a small bit of life — the only "celebration" moment in
 * the onboarding flow, kept restrained on purpose.
 */
@Composable
private fun FinishedTick() {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                alpha = scale.value.coerceIn(0f, 1f)
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    ) {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Each discovered account chip animates in independently the first time it composes,
 * using the account id as the key so a recomposition (progress tick) doesn't replay
 * the entrance.
 */
@Composable
private fun DiscoveredAccountChipReveal(account: AccountEntity, index: Int) {
    val chipProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 120L)
        chipProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = chipProgress.value
                translationY = (1f - chipProgress.value) * 20.dp.toPx()
            },
    ) {
        DiscoveredAccountRow(account)
    }
}

@Composable
private fun DiscoveredAccountRow(a: AccountEntity) {
    val logoPath = BankLogos.resolve(a.senderAddress)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (logoPath != null) {
            AsyncImage(
                model = BankLogos.asAssetUri(logoPath),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Outlined.AccountBalance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = a.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = a.senderAddress + if (a.accountSuffix != "\u2014") " \u00b7 ${a.accountSuffix}" else "",
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
    val bg = if (enabled) MaterialTheme.colorScheme.inverseSurface
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (enabled) MaterialTheme.colorScheme.inverseOnSurface
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(
                elevation = if (enabled) 4.dp else 0.dp,
                shape = CircleShape,
                clip = false,
            )
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
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
