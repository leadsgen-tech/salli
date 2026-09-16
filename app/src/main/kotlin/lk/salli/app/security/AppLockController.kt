package lk.salli.app.security

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lk.salli.data.prefs.AppLockSettings
import lk.salli.data.prefs.SalliPreferences
import lk.salli.domain.security.AppLockPolicy

/**
 * Owns the app lock for the whole process. The lock is a UI gate only: SMS ingest, the
 * transaction prompt notification and reminders never look at it.
 *
 *  - A new process starts locked when the lock is on (decided once the setting has been read;
 *    until then [state] is [State.CHECKING] and the UI shows nothing).
 *  - [ProcessLifecycleOwner] ON_STOP records when Salli left; ON_START locks when
 *    [AppLockPolicy.shouldLockOnReturn] says the time away reached the "Lock after" setting.
 *  - A phone without a screen lock cannot authenticate, so the lock never engages there.
 *
 * Times come from [SystemClock.elapsedRealtime], which changing the wall clock cannot move.
 */
@Singleton
class AppLockController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SalliPreferences,
) : DefaultLifecycleObserver {

    enum class State { CHECKING, LOCKED, UNLOCKED }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }

    private val _state = MutableStateFlow(State.CHECKING)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Whether every activity window must carry FLAG_SECURE. Starts fail-closed. */
    private val _protectWindow = MutableStateFlow(true)
    val protectWindow: StateFlow<Boolean> = _protectWindow.asStateFlow()

    /** True when the lock screen should open the prompt by itself the next time it is resumed. */
    private val _autoPrompt = MutableStateFlow(false)
    val autoPrompt: StateFlow<Boolean> = _autoPrompt.asStateFlow()

    /** Last prompt error worth showing (lockout, no credential). Cancels are not errors. */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var started = false
    private var settings: AppLockSettings? = null
    private var processInForeground = false
    private var firstForegroundEvaluated = false
    private var backgroundedAt: Long? = null

    /**
     * Set while a prompt is up. The device-credential screen can stop and restart Salli's
     * activity; that round trip must not count as leaving the app, or "Immediately" would lock
     * again the moment the owner unlocked. Stale after [AUTH_GRACE_MS] in case a callback is lost.
     */
    private var authStartedAt: Long? = null
    private var attachedActivity: FragmentActivity? = null
    private var prompt: BiometricPrompt? = null
    private var pendingAuth: PendingAuth? = null

    /** Call once from Application.onCreate, on the main thread. */
    fun start() {
        if (started) return
        started = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            prefs.appLock.collect { s ->
                val first = settings == null
                settings = s
                _protectWindow.value = AppLockPolicy.shouldProtectWindow(s.enabled, s.hideInRecents)
                when {
                    first && processInForeground -> evaluateFirstForeground(s)
                    // Never leave the owner behind a lock that has just been turned off.
                    !s.enabled && _state.value == State.LOCKED -> _state.value = State.UNLOCKED
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        processInForeground = false
        backgroundedAt = clock()
    }

    override fun onStart(owner: LifecycleOwner) {
        processInForeground = true
        val s = settings ?: return // the cold-start decision is still pending and covers this
        if (!firstForegroundEvaluated) {
            evaluateFirstForeground(s)
            backgroundedAt = null
            return
        }
        if (authInProgress()) return
        val now = clock()
        val secure = isDeviceSecure()
        when (_state.value) {
            State.UNLOCKED ->
                if (AppLockPolicy.shouldLockOnReturn(s.enabled, secure, s.lockAfterSeconds, backgroundedAt, now)) engage()
            State.LOCKED ->
                if (s.enabled && secure) _autoPrompt.value = true else _state.value = State.UNLOCKED
            State.CHECKING -> Unit
        }
        backgroundedAt = null
    }

    private fun evaluateFirstForeground(s: AppLockSettings) {
        firstForegroundEvaluated = true
        if (AppLockPolicy.shouldLockOnColdStart(s.enabled, isDeviceSecure())) engage()
        else _state.value = State.UNLOCKED
    }

    private fun engage() {
        _state.value = State.LOCKED
        _autoPrompt.value = true
    }

    /** Returns true once per lock engagement; the lock screen then opens the prompt itself. */
    fun consumeAutoPrompt(): Boolean {
        if (!_autoPrompt.value) return false
        _autoPrompt.value = false
        return true
    }

    fun isDeviceSecure(): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /**
     * Reconnects BiometricPrompt's callback to the activity-scoped retained fragment. AndroidX
     * requires constructing a new prompt from every recreated FragmentActivity.
     */
    fun attach(activity: FragmentActivity) {
        if (attachedActivity === activity) return
        attachedActivity = activity
        prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), biometricCallback)
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (attachedActivity !== activity) return
                authStartedAt = null
                prompt = null
                attachedActivity = null
                if (!activity.isChangingConfigurations) pendingAuth = null
                owner.lifecycle.removeObserver(this)
            }
        })
    }

    /** The lock screen's prompt. Success is the only way out of [State.LOCKED]. */
    fun unlock(activity: FragmentActivity) {
        authenticate(activity, title = "Unlock Salli", subtitle = null) { outcome ->
            when (outcome) {
                is Outcome.Success -> {
                    backgroundedAt = null
                    _lastError.value = null
                    _state.value = State.UNLOCKED
                }
                is Outcome.Failure -> if (outcome.message != null) _lastError.value = outcome.message
            }
        }
    }

    /**
     * Turns the lock on or off, but only after the owner passes the prompt, so nobody enables a
     * lock they cannot open and nobody else can switch it off. [onError] gets anything other than
     * a cancel.
     */
    fun setEnabledAfterAuth(activity: FragmentActivity, enable: Boolean, onError: (String) -> Unit) {
        authenticate(
            activity,
            title = if (enable) "Turn on app lock" else "Turn off app lock",
            subtitle = "Confirm it's you",
        ) { outcome ->
            when (outcome) {
                is Outcome.Success -> scope.launch {
                    backgroundedAt = null
                    prefs.setAppLockEnabled(enable)
                }
                is Outcome.Failure -> outcome.message?.let(onError)
            }
        }
    }

    /** Longer delays weaken an active lock and require auth; shorter delays are safe directly. */
    fun setLockAfter(activity: FragmentActivity, seconds: Int, onError: (String) -> Unit) {
        val normalised = AppLockPolicy.normaliseLockAfter(seconds)
        val current = settings
        val weakensActiveLock = AppLockPolicy.requiresAuthForLockAfterChange(
            enabled = current?.enabled == true,
            currentSeconds = current?.lockAfterSeconds ?: AppLockPolicy.IMMEDIATELY_SECONDS,
            newSeconds = normalised,
        )
        if (!weakensActiveLock) {
            scope.launch { prefs.setAppLockAfterSeconds(normalised) }
            return
        }
        authenticate(activity, title = "Change lock delay", subtitle = "Confirm it's you") { outcome ->
            when (outcome) {
                is Outcome.Success -> scope.launch { prefs.setAppLockAfterSeconds(normalised) }
                is Outcome.Failure -> outcome.message?.let(onError)
            }
        }
    }

    /** Enabling recents protection is direct; disabling existing protection requires auth. */
    fun setHideInRecents(activity: FragmentActivity, hide: Boolean, onError: (String) -> Unit) {
        if (!AppLockPolicy.requiresAuthForRecentsChange(isDeviceSecure(), settings?.hideInRecents == true, hide)) {
            scope.launch { prefs.setHideInRecents(hide) }
            return
        }
        authenticate(activity, title = "Show Salli in recent apps", subtitle = "Confirm it's you") { outcome ->
            when (outcome) {
                is Outcome.Success -> scope.launch { prefs.setHideInRecents(false) }
                is Outcome.Failure -> outcome.message?.let(onError)
            }
        }
    }

    private sealed interface Outcome {
        data object Success : Outcome
        /** [message] is null for cancels, which need no feedback. */
        data class Failure(val message: String?) : Outcome
    }

    private data class PendingAuth(val onOutcome: (Outcome) -> Unit)

    private val biometricCallback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            finishAuthentication(Outcome.Success)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                errorCode == BiometricPrompt.ERROR_CANCELED
            finishAuthentication(Outcome.Failure(if (cancelled) null else errString.toString()))
        }
        // onAuthenticationFailed (a finger that did not match) leaves the prompt up.
    }

    private fun finishAuthentication(outcome: Outcome) {
        authStartedAt = null
        val pending = pendingAuth ?: return
        pendingAuth = null
        pending.onOutcome(outcome)
    }

    private fun authInProgress(): Boolean {
        val at = authStartedAt ?: return false
        val elapsed = clock() - at
        if (pendingAuth != null && elapsed in 0 until AUTH_GRACE_MS) return true
        authStartedAt = null
        pendingAuth = null
        return false
    }

    private fun authenticate(activity: FragmentActivity, title: String, subtitle: String?, onOutcome: (Outcome) -> Unit) {
        if (!isDeviceSecure()) {
            onOutcome(Outcome.Failure("Set a screen lock on this phone first"))
            return
        }
        if (pendingAuth != null) return
        attach(activity)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                } else {
                    @Suppress("DEPRECATION")
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()
        authStartedAt = clock()
        pendingAuth = PendingAuth(onOutcome)
        try {
            checkNotNull(prompt).authenticate(info)
        } catch (error: Exception) {
            authStartedAt = null
            pendingAuth = null
            onOutcome(Outcome.Failure(error.message ?: "Couldn't show the unlock prompt"))
        }
    }

    private companion object {
        const val AUTH_GRACE_MS = 2 * 60 * 1000L
    }
}

/** Compose's LocalContext can be a wrapper around the activity; BiometricPrompt needs the activity. */
fun Context.findFragmentActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}
