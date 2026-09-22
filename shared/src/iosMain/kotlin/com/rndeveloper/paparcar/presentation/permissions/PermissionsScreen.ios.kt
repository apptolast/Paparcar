@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar.presentation.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.ios.permissions.IosPermissionRequester
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
                PermissionsEffect.RequestProducerSensors ->
                    requester.requestProducerSensors()
                PermissionsEffect.RequestActivityRecognition ->
                    requester.requestActivityRecognition()
                PermissionsEffect.RequestNotifications ->
                    requester.requestNotifications()
                PermissionsEffect.RequestStep2BackgroundLocation ->
                    requester.requestAlwaysLocation()
                PermissionsEffect.RequestStepBluetooth ->
                    requester.requestBluetooth()
                PermissionsEffect.RequestBatteryOptimizationExemption ->
                    Unit // no battery optimization concept on iOS
                PermissionsEffect.LaunchOemAutostartSettings ->
                    Unit // no OEM autostart whitelist concept on iOS
                PermissionsEffect.LaunchOemBatterySettings ->
                    Unit // no OEM battery whitelist concept on iOS
                // ⛔ Las dos llevan al MISMO sitio y no es un descuido: iOS no deja enlazar a
                // Privacidad → Localización (`App-Prefs:` es API privada y la tienda lo rechaza),
                // así que el interruptor global se alcanza a pie desde los ajustes de la app. Por
                // eso la fila lo dice con palabras — ver `platformPermissionCopy`.
                // [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001]
                PermissionsEffect.OpenAppSettings,
                PermissionsEffect.OpenLocationSettings -> openIosSettings()
                PermissionsEffect.NavigateToHome ->
                    onPermissionsGranted()
            }
        }
    }

    // Shared theme-aware UI (commonMain).
    // ⛔ TODOS los callbacks van cableados. `PermissionsContent` los tiene con default `= {}`, y un
    // `{}` NO es un `null`: `PermissionRow` sigue pintando la fila pulsable, con su ripple, sin que
    // pase nada. Once de los quince quedaron así y esta pantalla fue seis filas muertas en iOS.
    // [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001]
    PermissionsContent(
        state = state,
        onRequestPermissions = { viewModel.handleIntent(PermissionsIntent.RequestPermissions) },
        onRequestForegroundLocation = { viewModel.handleIntent(PermissionsIntent.RequestForegroundLocation) },
        onOpenLocationServices = { viewModel.handleIntent(PermissionsIntent.OpenLocationServices) },
        onRequestBackgroundLocation = { viewModel.handleIntent(PermissionsIntent.RequestBackgroundLocation) },
        onRequestPreciseLocation = { viewModel.handleIntent(PermissionsIntent.RequestPreciseLocation) },
        onRequestActivityRecognition = { viewModel.handleIntent(PermissionsIntent.RequestActivityRecognition) },
        onRequestNotifications = { viewModel.handleIntent(PermissionsIntent.RequestNotifications) },
        onRequestBluetooth = { viewModel.handleIntent(PermissionsIntent.RequestBluetoothPermission) },
        onConfirmBackgroundLocationGuide = { viewModel.handleIntent(PermissionsIntent.ConfirmBackgroundLocationGuide) },
        onDismissBackgroundLocationGuide = { viewModel.handleIntent(PermissionsIntent.DismissBackgroundLocationGuide) },
        onNotNow = { viewModel.handleIntent(PermissionsIntent.NotNow) },
        onFinish = { viewModel.handleIntent(PermissionsIntent.FinishSetup) },
        focus = focus,
    )
    // `onRequestBatteryOptimization`, `onRequestOemAutostart` y `onRequestOemBatterySettings` NO se
    // cablean a propósito: sus filas no se pintan en iOS (`isBatteryOptimizationRelevant = false`,
    // `requiresAutostartWhitelist = false`), así que no hay tap que atender. Cablearlos a un `Unit`
    // sería fingir que aquí existe un permiso que no existe.
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
