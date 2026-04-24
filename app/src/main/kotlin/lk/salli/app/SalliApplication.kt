package lk.salli.app

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import lk.salli.data.seed.Seeder

/**
 * App process root. Implements [Configuration.Provider] so WorkManager resolves Hilt-injected
 * workers (SmsIngestWorker, ModelDownloadWorker).
 *
 * Note: we previously installed a `StrictMode.detectCleartextNetwork().penaltyDeath()` policy
 * here as a "no accidental network leak" safety net. The Android enforcement for that policy
 * works via an iptables rule on the app's UID that rejects packets flagged as cleartext. On
 * Android 14+ the detector has false positives on TLS connections (especially ones that go
 * through a 302 redirect to a signed CDN URL — exactly what HF does), which silently blocked
 * our own model download with a cryptic `UnknownHostException`. Now that AI-mode downloads are
 * a first-class user-opted feature and the only network call the app ever makes, the
 * StrictMode net gate is more foot-gun than guard — dropped.
 */
@HiltAndroidApp
class SalliApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var seeder: Seeder

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Reset any lingering StrictMode policy — earlier dev builds installed a cleartext
        // detector that netd tracks via kernel iptables rules on our UID, and those rules
        // outlive the process (even `pm clear` doesn't wipe them). An explicit empty policy
        // tells the platform to tear down the chains. Harmless no-op on fresh installs.
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        // Idempotent seed — categories + keywords show up on first launch, and any new entries
        // we ship later trickle in automatically.
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            runCatching { seeder.run() }
        }
    }
}
