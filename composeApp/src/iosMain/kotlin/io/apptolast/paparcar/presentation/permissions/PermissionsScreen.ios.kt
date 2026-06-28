@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.apptolast.paparcar.presentation.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.ios.permissions.IosPermissionRequester
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

actual @Composable fun PermissionsScreen(onPermissionsGranted: () -> Unit, focus: PermissionsFocus) {
    val viewModel = koinViewModel<PermissionsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissionManager = koinInject<PermissionManager>()
    val requester = remember { IosPermissionRequester(permissionManager) }

    // One-shot effects
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                PermissionsEffect.RequestStep1 ->
                    requester.requestStep1()
                PermissionsEffect.RequestStep2BackgroundLocation ->
                    requester.requestAlwaysLocation()
                PermissionsEffect.RequestStepBluetooth ->
                    requester.requestBluetooth()
                PermissionsEffect.RequestBatteryOptimizationExemption ->
                    Unit // no battery optimization concept on iOS
                PermissionsEffect.LaunchOemAutostartSettings ->
                    Unit // no OEM autostart whitelist concept on iOS
                PermissionsEffect.OpenAppSettings,
                PermissionsEffect.OpenLocationSettings -> openIosSettings()
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
        focus = focus,
    )
}

private fun openIosSettings() {
    NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { url ->
        UIApplication.sharedApplication.openURL(
            url = url,
            options = emptyMap<Any?, Any?>(),
            completionHandler = null,
        )
    }
}
