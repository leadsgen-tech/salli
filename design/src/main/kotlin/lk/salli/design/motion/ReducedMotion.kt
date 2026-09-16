package lk.salli.design.motion

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * True when the user has animations switched off system-wide (Developer options → "Animator
 * duration scale: off", or an accessibility profile that does the same).
 *
 * Every custom animation in Salli checks this and falls back to crossfade-or-instant. M3's own
 * components already honour the platform setting; ours are hand-rolled `Animatable`s and
 * `withFrameNanos` loops, which do not.
 *
 * Read it with [LocalReducedMotion] rather than querying `Settings.Global` at a call site —
 * the provider recomputes on resume, so toggling the setting and coming back to the app takes
 * effect without a restart.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Reads the current animator duration scale. `0f` means "animations off". */
fun isReducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f

/**
 * Observes the system animator scale, re-reading it every time the host lifecycle resumes.
 * Cheap: one `Settings.Global` lookup per resume, no content observer to leak.
 */
@Composable
fun rememberReducedMotion(): State<Boolean> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember(context) { mutableStateOf(isReducedMotion(context)) }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.value = isReducedMotion(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}
