package io.apptolast.paparcar.presentation.permissions

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.apptolast.paparcar.domain.preferences.AppPreferences

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.apptolast.paparcar.domain.permissions.OemBackgroundReliabilityManager
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.platform.LocalContext

actual @Composable fun PermissionsScreen(onPermissionsGranted: () -> Unit, focus: PermissionsFocus) {
    val viewModel = koinViewModel<PermissionsViewModel>()
    val oemReliabilityManager = koinInject<OemBackgroundReliabilityManager>()
    val appPreferences = koinInject<AppPreferences>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // CORE step 1 — foreground location only. PRODUCER sensors are no longer bundled here. [DET-READY-001i]
    val step1Permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    // PRODUCER sensors — activity recognition + notifications, requested together when the user
    // deliberately activates auto-detection.
    val producerSensorsPermissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val step1Launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        // CORE granted → just refresh. We do NOT auto-chain into PRODUCER: enabling auto-detection
        // is a deliberate choice (the "Activate detection" button). [DET-READY-001i]
        viewModel.handleIntent(PermissionsIntent.RefreshPermissions)
    }

    val producerSensorsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        viewModel.handleIntent(PermissionsIntent.RefreshPermissions)
        // Within the activate-detection flow, chain on to background location once sensors are granted.
        if (results.values.all { it }) viewModel.handleIntent(PermissionsIntent.RequestPermissions)
    }

    val step2Launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.handleIntent(PermissionsIntent.RefreshPermissions)
    }

    val btLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.handleIntent(PermissionsIntent.RefreshPermissions)
    }

    // Refresh permissions on every RESUMED lifecycle event (catches return from system settings),
    // and re-evaluate whether foreground location is permanently denied so the CTA can point to
    // system settings from the first frame instead of after the request→rationale→settings taps.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.handleIntent(PermissionsIntent.RefreshPermissions)

            val activity = context as? Activity
            val locationGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            // shouldShowRequestPermissionRationale is false BOTH before the first ask AND after a
            // permanent denial — the persisted "have we asked" flag disambiguates the two. [DET-READY-001m]
            val permanentlyDenied = activity != null && !locationGranted &&
                appPreferences.hasRequestedLocationPermission &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.ACCESS_FINE_LOCATION,
                )
            viewModel.handleIntent(PermissionsIntent.SetLocationPermanentlyDenied(permanentlyDenied))
        }
    }

    // One-shot effects
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                PermissionsEffect.RequestStep1 -> {
                    // Remember we have asked, so a later denial reads as permanent (not first-launch).
                    appPreferences.setLocationPermissionRequested()
                    step1Launcher.launch(step1Permissions)
                }
                PermissionsEffect.RequestProducerSensors ->
                    if (producerSensorsPermissions.isNotEmpty()) {
                        producerSensorsLauncher.launch(producerSensorsPermissions)
                    } else {
                        viewModel.handleIntent(PermissionsIntent.RefreshPermissions)
                    }
                PermissionsEffect.RequestStep2BackgroundLocation ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        step2Launcher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    } else {
                        viewModel.handleIntent(PermissionsIntent.RefreshPermissions)
                    }
                PermissionsEffect.RequestStepBluetooth ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    } else {
                        viewModel.handleIntent(PermissionsIntent.RefreshPermissions)
                    }
                PermissionsEffect.RequestBatteryOptimizationExemption ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            },
                        )
                    }
                PermissionsEffect.LaunchOemAutostartSettings ->
                    oemReliabilityManager.launchAutostartSettings()
                PermissionsEffect.LaunchOemBatterySettings ->
                    oemReliabilityManager.launchOemBatterySettings()
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
        onRequestBluetooth = { viewModel.handleIntent(PermissionsIntent.RequestBluetoothPermission) },
        onRequestBatteryOptimization = { viewModel.handleIntent(PermissionsIntent.RequestBatteryOptimization) },
        onRequestOemAutostart = { viewModel.handleIntent(PermissionsIntent.RequestOemAutostart) },
        onRequestOemBatterySettings = { viewModel.handleIntent(PermissionsIntent.RequestOemBatterySettings) },
        onConfirmBackgroundLocationGuide = { viewModel.handleIntent(PermissionsIntent.ConfirmBackgroundLocationGuide) },
        onDismissBackgroundLocationGuide = { viewModel.handleIntent(PermissionsIntent.DismissBackgroundLocationGuide) },
        onContinueWithCore = { viewModel.handleIntent(PermissionsIntent.ContinueWithCore) },
        focus = focus,
    )
}
