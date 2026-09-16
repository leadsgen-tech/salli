package lk.salli.domain.security

/**
 * When the app lock should engage. Pure and clock-free so the rules are unit-testable; the
 * Android side feeds it the monotonic time Salli went to the background and the time it came
 * back.
 */
object AppLockPolicy {

    const val IMMEDIATELY_SECONDS = 0
    const val ONE_MINUTE_SECONDS = 60
    const val FIVE_MINUTES_SECONDS = 300

    /** The "Lock after" choices offered in Settings, in display order. */
    val lockAfterChoicesSeconds: List<Int> = listOf(IMMEDIATELY_SECONDS, ONE_MINUTE_SECONDS, FIVE_MINUTES_SECONDS)

    /** Anything that is not one of the offered choices (an edited backup, say) means immediately. */
    fun normaliseLockAfter(seconds: Int): Int =
        if (seconds in lockAfterChoicesSeconds) seconds else IMMEDIATELY_SECONDS

    /**
     * A fresh process starts locked whenever the lock is on. Without a screen lock on the device
     * there is no way to authenticate, so the lock never engages rather than shutting the owner out.
     */
    fun shouldLockOnColdStart(enabled: Boolean, deviceSecure: Boolean): Boolean = enabled && deviceSecure

    /** App lock always protects the recents thumbnail; users may also opt into it separately. */
    fun shouldProtectWindow(enabled: Boolean, hideInRecents: Boolean): Boolean =
        enabled || hideInRecents

    /** Increasing an active lock's delay weakens it and therefore needs authentication. */
    fun requiresAuthForLockAfterChange(enabled: Boolean, currentSeconds: Int, newSeconds: Int): Boolean =
        enabled && normaliseLockAfter(newSeconds) > normaliseLockAfter(currentSeconds)

    /** Turning recents protection off weakens it; turning it on never needs authentication. */
    fun requiresAuthForRecentsChange(
        deviceSecure: Boolean,
        currentlyHidden: Boolean,
        hide: Boolean,
    ): Boolean = deviceSecure && currentlyHidden && !hide

    /**
     * Whether coming back to the foreground at [nowMillis] should lock, given Salli went to the
     * background at [backgroundedAtMillis] (null when it never did in this process).
     *
     * A clock that reads earlier than the moment Salli left cannot say how long it was away, so
     * it locks: failing closed is the safe direction.
     */
    fun shouldLockOnReturn(
        enabled: Boolean,
        deviceSecure: Boolean,
        lockAfterSeconds: Int,
        backgroundedAtMillis: Long?,
        nowMillis: Long,
    ): Boolean {
        if (!enabled || !deviceSecure) return false
        val leftAt = backgroundedAtMillis ?: return false
        val away = nowMillis - leftAt
        if (away < 0L) return true
        return away >= normaliseLockAfter(lockAfterSeconds) * 1_000L
    }
}
