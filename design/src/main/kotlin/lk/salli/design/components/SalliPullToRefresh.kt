package lk.salli.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import lk.salli.design.R
import lk.salli.design.components.stage.CapsulePhase
import lk.salli.design.components.stage.StatusCapsule
import lk.salli.design.motion.LocalReducedMotion

/** A settled inbox pass result supplied by the app layer. The design module never reads SMS. */
data class PullRefreshOutcome(val message: String, val failed: Boolean = false)

/** The same five-phase refresh capsule is used on every primary screen. */
@Composable
fun SalliPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    outcome: PullRefreshOutcome? = null,
    onOutcomeConsumed: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    val haptic = LocalHapticFeedback.current
    val reducedMotion = LocalReducedMotion.current
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val fallbackUpToDate = stringResource(R.string.refresh_up_to_date)
    val fallbackFailed = stringResource(R.string.refresh_failed)
    var startedAt by remember { mutableLongStateOf(0L) }
    var showingResult by remember { mutableStateOf<PullRefreshOutcome?>(null) }
    val armed = startedAt == 0L && state.distanceFraction >= 1f

    LaunchedEffect(armed) {
        if (armed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // A local inbox scan may finish between frames. Keep Checking visible for 650 ms.
    LaunchedEffect(startedAt, isRefreshing, outcome) {
        if (startedAt == 0L || showingResult != null) return@LaunchedEffect
        delay((MIN_SHOW_MS - (System.currentTimeMillis() - startedAt)).coerceAtLeast(0L))
        val settled = outcome ?: if (!isRefreshing) PullRefreshOutcome(fallbackUpToDate) else null
        if (settled != null) {
            showingResult = settled
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    LaunchedEffect(startedAt, showingResult) {
        if (startedAt > 0L && showingResult == null) {
            delay(REFRESH_TIMEOUT_MS)
            if (showingResult == null) showingResult = PullRefreshOutcome(fallbackFailed, failed = true)
        }
    }
    LaunchedEffect(showingResult) {
        if (showingResult != null && showingResult?.failed == false) {
            delay(DONE_HOLD_MS)
            onOutcomeConsumed()
            showingResult = null
            startedAt = 0L
        }
    }

    val phase = when {
        showingResult?.failed == true -> CapsulePhase.Failed
        showingResult != null -> CapsulePhase.Done
        startedAt > 0L -> CapsulePhase.Refreshing
        armed -> CapsulePhase.Armed
        else -> CapsulePhase.Pulling
    }
    val visible = startedAt > 0L || state.distanceFraction > 0.01f
    val message = when (phase) {
        CapsulePhase.Pulling -> stringResource(R.string.refresh_pull)
        CapsulePhase.Armed -> stringResource(R.string.refresh_release)
        CapsulePhase.Refreshing -> stringResource(R.string.refresh_checking)
        CapsulePhase.Done, CapsulePhase.Failed -> showingResult?.message.orEmpty()
    }
    fun requestRefresh() {
        showingResult = null
        onOutcomeConsumed()
        startedAt = System.currentTimeMillis()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onRefresh()
    }

    PullToRefreshBox(
        isRefreshing = startedAt > 0L && showingResult == null,
        onRefresh = { requestRefresh() },
        state = state,
        modifier = modifier,
        indicator = {
            if (visible) {
                StatusCapsule(
                    phase = phase,
                    distanceFraction = state.distanceFraction.coerceIn(0f, 1f),
                    message = message,
                    reducedMotion = reducedMotion,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset + 8.dp),
                    action = if (phase == CapsulePhase.Failed) {
                        {
                            TextButton(onClick = { requestRefresh() }) {
                                Text(stringResource(R.string.refresh_retry), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else null,
                )
            }
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                translationY = if (startedAt > 0L || reducedMotion) 0f
                else state.distanceFraction.coerceIn(0f, 1f) * with(density) { 56.dp.toPx() }
            },
        ) { content() }
    }
}

private const val MIN_SHOW_MS = 650L
private const val DONE_HOLD_MS = 1_100L
private const val REFRESH_TIMEOUT_MS = 15_000L
