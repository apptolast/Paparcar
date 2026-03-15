package io.apptolast.paparcar

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.permissions.PermissionManager
import android.util.Log
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val permissionManager: PermissionManager by inject()
    private val activityRecognitionManager: ActivityRecognitionManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            App(
                onOpenMapsNavigation = { lat, lon ->
                    val uri = Uri.parse("google.navigation:q=$lat,$lon&mode=d")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                    } else {
                        // Fallback: open in browser if Maps is not installed
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?daddr=$lat,$lon&directionsmode=driving")))
                    }
                },
            )

            // Observa los cambios de estado de los permisos.
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

    override fun onResume() {
        super.onResume()
        permissionManager.refreshPermissions()
    }
}
