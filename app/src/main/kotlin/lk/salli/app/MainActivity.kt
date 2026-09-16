package lk.salli.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import lk.salli.app.sms.SmsRefresher
import lk.salli.app.security.AppLockController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import lk.salli.app.nav.Destination
import lk.salli.app.nav.Route
import lk.salli.app.nav.SalliNavHost
import lk.salli.data.prefs.SalliPreferences
import lk.salli.data.prefs.ThemeMode
import lk.salli.design.theme.SalliTheme

// FragmentActivity (a ComponentActivity) because BiometricPrompt hosts itself in a fragment.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private var widgetRouteRequest by mutableStateOf<String?>(null)
    private var widgetRouteRequestId by mutableIntStateOf(0)

    @Inject lateinit var prefs: SalliPreferences
    @Inject lateinit var appLock: AppLockController
    @Inject lateinit var smsRefresher: SmsRefresher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recreating BiometricPrompt reconnects its callback to the retained prompt fragment
        // after rotation. Window protection is applied before Compose can draw its first frame.
        appLock.attach(this)
        applyWindowProtection(appLock.protectWindow.value)
        lifecycleScope.launch {
            appLock.protectWindow.collect(::applyWindowProtection)
        }
        // Transparent system bars — the status/nav bar icon colour flips with the active
        // scheme via SystemBarStyle.auto so they read on either palette.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        setContent {
            // Tri-state preference collapsed at the last possible moment: SYSTEM has to be
            // resolved inside composition so the app repaints when the phone flips into
            // night mode, rather than only on the next cold start.
            val themeMode by prefs.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val dark = themeMode.resolve(isSystemInDarkTheme())
            val introduced by prefs.onboardingCompleted.collectAsState(initial = null)
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            SalliTheme(darkTheme = dark) {
                if (introduced == null) {
                    Surface(Modifier.fillMaxSize()) {
                        Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                } else {
                    // Keep the initial graph stable when onboarding saves completion before
                    // navigating. Skipping SMS is a valid, persistent first-run choice.
                    val start = remember {
                        if (introduced == true) intent.getStringExtra(EXTRA_START_ROUTE) ?: Destination.HOME.route else Route.ONBOARDING
                    }
                    SalliNavHost(
                        startDestination = start,
                        appLock = appLock,
                        routeRequest = widgetRouteRequest,
                        routeRequestId = widgetRouteRequestId,
                    )
                }
            }
        }
    }

    companion object { const val EXTRA_START_ROUTE = "lk.salli.app.extra.START_ROUTE" }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetRouteRequest = intent.getStringExtra(EXTRA_START_ROUTE)
        widgetRouteRequestId++
    }

    private fun applyWindowProtection(protect: Boolean) {
        if (protect) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onResume() {
        super.onResume()
        // A grant made in Android Settings doesn't deliver our permission-launcher result.
        // Reconcile it here, but let first-run onboarding own its visible import flow.
        lifecycleScope.launch {
            if (!prefs.onboardingCompleted.first()) return@launch
            val smsGranted = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
                .all { ContextCompat.checkSelfPermission(this@MainActivity, it) == PackageManager.PERMISSION_GRANTED }
            if (smsGranted) smsRefresher.ensureHistoricalImport()
        }
    }
}
