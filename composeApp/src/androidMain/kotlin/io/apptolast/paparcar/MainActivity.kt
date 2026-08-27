package io.apptolast.paparcar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.apptolast.customlogin.platform.ActivityHolder
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.connectivity.ConnectivityObserver
import io.apptolast.paparcar.domain.detection.ports.DepartureWatchResumer
import io.apptolast.paparcar.domain.usecase.detection.ObserveDepartureWatchGapUseCase
import io.apptolast.paparcar.domain.event.MapFocusEventBus
import io.apptolast.paparcar.domain.event.StartAddParkingEventBus
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.presentation.app.SplashViewModel
import androidx.work.WorkManager
import io.apptolast.paparcar.detection.worker.RegisterActivityTransitionsWorker
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.getValue

class MainActivity : ComponentActivity() {
    private val splashViewModel: SplashViewModel by viewModel()
    private val permissionManager: PermissionManager by inject()
    private val activityRecognitionManager: ActivityRecognitionManager by inject()
    private val connectivityObserver: ConnectivityObserver by inject()
    private val appPreferences: AppPreferences by inject()
    private val mapFocusEventBus: MapFocusEventBus by inject()
    private val startAddParkingEventBus: StartAddParkingEventBus by inject()

    // [DET-WATCH-REACTIVATE-001] Departure-watch gap: the watcher should be live but the service is
    // dead. Closing it needs a foreground moment, which is exactly what this Activity being visible is.
    private val observeDepartureWatchGap: ObserveDepartureWatchGapUseCase by inject()
    private val departureWatchResumer: DepartureWatchResumer by inject()

    // Detects GPS toggled on/off from the quick-settings panel without leaving the app.
    // Registered dynamically (not in manifest) so it is scoped to the Activity lifecycle.
    private val gpsToggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                permissionManager.refreshPermissions()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Restore the saved locale at the process level before setContent.
        // CMP Resources reads locale from Locale.getDefault() (ResourceEnvironment.android.kt).
        // AppCompatDelegate.setApplicationLocales() is the only API that correctly updates
        // Locale.getDefault() + the application configuration on all API levels:
        //   API 33+  → delegates to LocaleManager (system-persisted, typically a no-op here)
        //   API 24-32 → updates Locale.getDefault() + application Resources configuration
        // AppPreferences.selectedLanguage is synchronously available via the blocking DataStore
        // warmup that runs at Koin singleton construction (before this point). [BUG-LANG-002]
        val savedLanguage = appPreferences.selectedLanguage
        if (savedLanguage != "auto") {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(savedLanguage))
        }

        ActivityHolder.setActivity(this)

        // installSplashScreen() must run before super.onCreate() AND before enableEdgeToEdge():
        // on API 31+ it internally calls setTheme(postSplashScreenTheme), which re-applies the
        // theme's system-bar attributes. If enableEdgeToEdge() ran first, that setTheme would
        // clobber the transparent bars and re-introduce an opaque status bar + broken insets.
        val splashScreen = installSplashScreen()

        // Keep splash screen visible until auth check completes
        splashScreen.setKeepOnScreenCondition {
            !splashViewModel.isReady
        }

        enableEdgeToEdge()
        handleNotificationFocusIntent(intent)
        super.onCreate(savedInstanceState)

        connectivityObserver.start()
        permissionManager.refreshPermissions()
        closeDepartureWatchGapWhileVisible()

        setContent {
            App(
                splashViewModel = splashViewModel,
            )

            // Arm/disarm detection from the combination of the PRODUCER permission tier (background
            // location + activity recognition) AND the user's Settings auto-detect flag. Both must be
            // true to arm; flipping either off disarms. The two are orthogonal: revoking permissions is
            // not the same as turning the feature off, but either one stops detection. [DET-READY-001d]
            // [DET-TOGGLE-001]
            LaunchedEffect(Unit) {
                combine(
                    permissionManager.permissionState.map { it.hasProducerPermissions },
                    appPreferences.observeAutoDetectParking(),
                ) { hasProducer, autoDetectEnabled -> hasProducer && autoDetectEnabled }
                    .distinctUntilChanged()
                    .onEach { shouldArm ->
                        if (shouldArm) {
                            activityRecognitionManager.registerTransitions()
                            // Re-enqueue the periodic worker so it runs with the newly granted
                            // ACTIVITY_RECOGNITION permission (KEEP avoids interrupting a running job).
                            RegisterActivityTransitionsWorker.enqueueKeep(WorkManager.getInstance(this@MainActivity))
                        } else {
                            // Producer revoked OR auto-detection turned off in Settings → stop arming.
                            activityRecognitionManager.unregisterTransitions()
                        }
                    }
                    .catch { e -> Log.e("Paparcar", "Error toggling detection arming", e) }
                    .launchIn(this)
            }
        }
    }

    /**
     * [DET-WATCH-REACTIVATE-001] While this Activity is visible, keep the departure watcher alive:
     * whenever the gap flow reports "a Coordinator car is parked, detection is on, and the service is
     * dead", rebuild it. A background worker cannot legally start a foreground service on Android 12+,
     * so a visible Activity is the app's reliable resurrection window.
     *
     * Scoped to STARTED (not `onCreate` alone) so it re-checks every time the app comes back to the
     * foreground, and collected as a STREAM so a clean install heals by itself the moment the Firestore
     * sync delivers the parked session — the one-shot read this replaces missed exactly that case.
     */
    private fun closeDepartureWatchGapWhileVisible() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                observeDepartureWatchGap()
                    .filter { hasGap -> hasGap }
                    // Never kill the collector on a repo hiccup: without the watcher the session still
                    // has the safety net, but we want the next emission to get its chance.
                    .catch { e -> Log.e("Paparcar", "Departure-watch gap stream failed", e) }
                    .collect { departureWatchResumer.resume(source = "foreground-gap") }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            gpsToggleReceiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(gpsToggleReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityObserver.stop()
        ActivityHolder.clearActivity(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationFocusIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        permissionManager.refreshPermissions()
    }

    private fun handleNotificationFocusIntent(intent: Intent?) {
        val lat = intent?.getDoubleExtra(EXTRA_FOCUS_LAT, Double.NaN)?.takeIf { !it.isNaN() }
        val lon = intent?.getDoubleExtra(EXTRA_FOCUS_LON, Double.NaN)?.takeIf { !it.isNaN() }
        if (lat != null && lon != null) mapFocusEventBus.focusAt(lat, lon)

        // Cold-start nudge notification → drop straight into add-parking pin mode. The bus
        // (CONFLATED Channel) buffers this until Home subscribes, so it survives launch from a
        // killed process. [DET-TOGGLE-002] The origin travels along so a DETECTION nudge keeps
        // detection provenance on the confirmed pin. [DET-NUDGE-PIN-PROVENANCE-001]
        if (intent?.getBooleanExtra(EXTRA_START_ADD_PARKING, false) == true) {
            startAddParkingEventBus.request(
                fromDetectionNudge = intent.getBooleanExtra(EXTRA_ADD_PARKING_FROM_DETECTION, false),
            )
        }
    }

    companion object {
        const val EXTRA_FOCUS_LAT = "extra_focus_lat"
        const val EXTRA_FOCUS_LON = "extra_focus_lon"
        const val EXTRA_START_ADD_PARKING = "extra_start_add_parking"
        const val EXTRA_ADD_PARKING_FROM_DETECTION = "extra_add_parking_from_detection"
    }
}
