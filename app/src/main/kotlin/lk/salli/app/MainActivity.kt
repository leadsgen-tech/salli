package lk.salli.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import lk.salli.app.nav.Destination
import lk.salli.app.nav.Route
import lk.salli.app.nav.SalliNavHost
import lk.salli.data.prefs.SalliPreferences
import lk.salli.design.theme.SalliTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: SalliPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent system bars — the status/nav bar icon colour flips with the active
        // scheme via SystemBarStyle.auto so they read on either palette.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        // If SMS perms already granted (user re-opened the app), skip onboarding; otherwise
        // route to it so we can ask and do the historical import.
        val smsGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECEIVE_SMS,
            ) == PackageManager.PERMISSION_GRANTED

        val start = if (smsGranted) Destination.HOME.route else Route.ONBOARDING

        setContent {
            val dark by prefs.darkTheme.collectAsState(initial = false)
            SalliTheme(darkTheme = dark) {
                SalliNavHost(startDestination = start)
            }
        }
    }
}
