package lk.salli.domain.security

import com.google.common.truth.Truth.assertThat
import lk.salli.domain.security.AppLockPolicy.FIVE_MINUTES_SECONDS
import lk.salli.domain.security.AppLockPolicy.IMMEDIATELY_SECONDS
import lk.salli.domain.security.AppLockPolicy.ONE_MINUTE_SECONDS
import org.junit.jupiter.api.Test

class AppLockPolicyTest {

    private val left = 1_000_000L

    private fun onReturn(
        lockAfter: Int,
        awayMillis: Long,
        enabled: Boolean = true,
        secure: Boolean = true,
        backgroundedAt: Long? = left,
    ) = AppLockPolicy.shouldLockOnReturn(
        enabled = enabled,
        deviceSecure = secure,
        lockAfterSeconds = lockAfter,
        backgroundedAtMillis = backgroundedAt,
        nowMillis = left + awayMillis,
    )

    @Test
    fun `immediately locks on any return`() {
        assertThat(onReturn(IMMEDIATELY_SECONDS, awayMillis = 0)).isTrue()
        assertThat(onReturn(IMMEDIATELY_SECONDS, awayMillis = 800)).isTrue()
    }

    @Test
    fun `one minute locks only once a full minute has passed`() {
        assertThat(onReturn(ONE_MINUTE_SECONDS, awayMillis = 59_999)).isFalse()
        assertThat(onReturn(ONE_MINUTE_SECONDS, awayMillis = 60_000)).isTrue()
        assertThat(onReturn(ONE_MINUTE_SECONDS, awayMillis = 3_600_000)).isTrue()
    }

    @Test
    fun `five minutes locks only once five full minutes have passed`() {
        assertThat(onReturn(FIVE_MINUTES_SECONDS, awayMillis = 60_000)).isFalse()
        assertThat(onReturn(FIVE_MINUTES_SECONDS, awayMillis = 299_999)).isFalse()
        assertThat(onReturn(FIVE_MINUTES_SECONDS, awayMillis = 300_000)).isTrue()
    }

    @Test
    fun `a clock that went backwards fails closed`() {
        assertThat(onReturn(FIVE_MINUTES_SECONDS, awayMillis = -1)).isTrue()
        assertThat(onReturn(ONE_MINUTE_SECONDS, awayMillis = -86_400_000)).isTrue()
    }

    @Test
    fun `never locks when the lock is off or the device has no screen lock`() {
        assertThat(onReturn(IMMEDIATELY_SECONDS, awayMillis = 600_000, enabled = false)).isFalse()
        assertThat(onReturn(IMMEDIATELY_SECONDS, awayMillis = 600_000, secure = false)).isFalse()
        assertThat(onReturn(IMMEDIATELY_SECONDS, awayMillis = -1, secure = false)).isFalse()
    }

    @Test
    fun `no recorded trip to the background means nothing to decide`() {
        assertThat(onReturn(IMMEDIATELY_SECONDS, awayMillis = 10_000, backgroundedAt = null)).isFalse()
    }

    @Test
    fun `cold start locks only when on and the device is secure`() {
        assertThat(AppLockPolicy.shouldLockOnColdStart(enabled = true, deviceSecure = true)).isTrue()
        assertThat(AppLockPolicy.shouldLockOnColdStart(enabled = true, deviceSecure = false)).isFalse()
        assertThat(AppLockPolicy.shouldLockOnColdStart(enabled = false, deviceSecure = true)).isFalse()
    }

    @Test
    fun `unknown lock-after values fall back to immediately`() {
        assertThat(AppLockPolicy.normaliseLockAfter(60)).isEqualTo(60)
        assertThat(AppLockPolicy.normaliseLockAfter(300)).isEqualTo(300)
        assertThat(AppLockPolicy.normaliseLockAfter(42)).isEqualTo(IMMEDIATELY_SECONDS)
        assertThat(onReturn(lockAfter = 42, awayMillis = 1)).isTrue()
    }

    @Test
    fun `window is protected whenever app lock or explicit recents hiding is on`() {
        assertThat(AppLockPolicy.shouldProtectWindow(enabled = true, hideInRecents = false)).isTrue()
        assertThat(AppLockPolicy.shouldProtectWindow(enabled = false, hideInRecents = true)).isTrue()
        assertThat(AppLockPolicy.shouldProtectWindow(enabled = true, hideInRecents = true)).isTrue()
        assertThat(AppLockPolicy.shouldProtectWindow(enabled = false, hideInRecents = false)).isFalse()
    }

    @Test
    fun `only lengthening an active lock delay requires authentication`() {
        assertThat(AppLockPolicy.requiresAuthForLockAfterChange(true, 0, 60)).isTrue()
        assertThat(AppLockPolicy.requiresAuthForLockAfterChange(true, 60, 300)).isTrue()
        assertThat(AppLockPolicy.requiresAuthForLockAfterChange(true, 300, 0)).isFalse()
        assertThat(AppLockPolicy.requiresAuthForLockAfterChange(false, 0, 300)).isFalse()
    }

    @Test
    fun `only disabling recents protection requires authentication`() {
        assertThat(AppLockPolicy.requiresAuthForRecentsChange(true, currentlyHidden = false, hide = true)).isFalse()
        assertThat(AppLockPolicy.requiresAuthForRecentsChange(true, currentlyHidden = true, hide = false)).isTrue()
        assertThat(AppLockPolicy.requiresAuthForRecentsChange(true, currentlyHidden = true, hide = true)).isFalse()
        // Without a device credential there is nothing to authenticate against; recents hiding
        // must still be reversible on those phones.
        assertThat(AppLockPolicy.requiresAuthForRecentsChange(false, currentlyHidden = true, hide = false)).isFalse()
    }
}
