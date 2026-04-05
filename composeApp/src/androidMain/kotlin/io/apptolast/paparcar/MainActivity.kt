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
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.apptolast.customlogin.platform.ActivityHolder
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.presentation.app.SplashViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.getValue

class MainActivity : ComponentActivity() {
    private val splashViewModel: SplashViewModel by viewModel()
    private val permissionManager: PermissionManager by inject()
    private val activityRecognitionManager: ActivityRecognitionManager by inject()
    private val appPreferences: AppPreferences by inject()

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
        ActivityHolder.setActivity(this)
        enableEdgeToEdge()
        val splashScreen = installSplashScreen()

        // Keep splash screen visible until auth check completes
        splashScreen.setKeepOnScreenCondition {
            !splashViewModel.isReady
        }
        super.onCreate(savedInstanceState)

        permissionManager.refreshPermissions()

        val permState = permissionManager.permissionState.value
        val startRoute = when {
            !appPreferences.hasVehicleRegistered -> Routes.VEHICLE_REGISTRATION
            !appPreferences.isOnboardingCompleted -> Routes.ONBOARDING
            !permState.allPermissionsGranted -> Routes.PERMISSIONS
            !permState.isLocationServicesEnabled -> Routes.PERMISSIONS
            else -> Routes.HOME
        }

        setContent {
            App(
                splashViewModel = splashViewModel,
                startRoute = startRoute,
                onOpenMapsNavigation = { lat, lon ->
                    val uri = "google.navigation:q=$lat,$lon&mode=d".toUri()
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                    } else {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "https://maps.google.com/?daddr=$lat,$lon&directionsmode=driving".toUri(),
                            ),
                        )
                    }
                },
            )

            // Register Activity Recognition transitions whenever permissions are granted
            LaunchedEffect(Unit) {
                permissionManager.permissionState
                    .onEach { state ->
                        if (state.allPermissionsGranted) {
                            activityRecognitionManager.registerTransitions()
                        }
                    }
                    .catch { e -> Log.e("Paparcar", "Error registrando transiciones", e) }
                    .launchIn(this)
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
        ActivityHolder.clearActivity(this)
    }

    override fun onResume() {
        super.onResume()
        permissionManager.refreshPermissions()
    }
}
