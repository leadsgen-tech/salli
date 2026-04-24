package lk.salli.app

/**
 * Compile-time gates for features we've built but aren't ready to ship. The code stays so we
 * can un-park them later without a merge — the UI just doesn't surface them.
 *
 * Per v1 scope (see CLAUDE.md · "No LLM in v1"), all AI-mode entry points are gated off.
 * Flipping [AI_ENABLED] back to true restores the Download/Test AI/Ask-Salli surface.
 */
object FeatureFlags {
    const val AI_ENABLED: Boolean = false
}
