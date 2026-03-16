package io.apptolast.paparcar.presentation.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.platform.LocalContext

actual @Composable fun PermissionsScreen(onPermissionsGranted: () -> Unit) {
    val viewModel = koinViewModel<PermissionsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Step 1: fine location + activity recognition + notifications
    val step1Permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val step1Launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        viewModel.handleIntent(PermissionsIntent.RefreshPermissions)
        if (results.values.all { it }) {
            // All step-1 permissions granted — proceed automatically to step 2
            viewModel.handleIntent(PermissionsIntent.RequestPermissions)
        }
    }

    val step2Launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.handleIntent(PermissionsIntent.RefreshPermissions)
    }

    // Refresh permissions on every RESUMED lifecycle event (catches return from system settings)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.handleIntent(PermissionsIntent.RefreshPermissions)
        }
    }

    // One-shot effects
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                PermissionsEffect.RequestStep1 ->
                    step1Launcher.launch(step1Permissions)
                PermissionsEffect.RequestStep2BackgroundLocation ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        step2Launcher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    } else {
                        viewModel.handleIntent(PermissionsIntent.RefreshPermissions)
                    }
                PermissionsEffect.OpenAppSettings ->
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        },
                    )
                PermissionsEffect.OpenLocationSettings ->
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                PermissionsEffect.NavigateToHome ->
                    onPermissionsGranted()
            }
        }
    }

    // Shared theme-aware UI (commonMain)
    PermissionsContent(
        state = state,
        onRequestPermissions = { viewModel.handleIntent(PermissionsIntent.RequestPermissions) },
    )
}
