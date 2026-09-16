package lk.salli.design.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Salli's two haptics, and only these two.
 *
 *   [tick]  — a threshold was crossed: pull-to-refresh armed, a bar scrubbed onto a new day,
 *             a category picked, an account discovered during the import.
 *   [thump] — something finished: refresh done, import complete, a sort choreography landed.
 *
 * Never on a plain button tap. The platform already does that, and doubling it makes the phone
 * feel twitchy.
 *
 * Implemented on the two `HapticFeedbackType` constants that are available on every API level
 * Salli supports (26+): `TextHandleMove` is the light tick, `LongPress` the heavier thump.
 */
@Immutable
class SalliHaptics internal constructor(private val delegate: HapticFeedback) {

    /** Light tap for a threshold crossing. */
    fun tick() {
        delegate.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    /** Heavier tap for a completion. */
    fun thump() {
        delegate.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

/** Remembers a [SalliHaptics] bound to the current composition's haptic feedback provider. */
@Composable
fun rememberSalliHaptics(): SalliHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { SalliHaptics(feedback) }
}
