@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar.permissions

import com.rndeveloper.paparcar.domain.permissions.AppPermissionState
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.domain.permissions.SystemPermissionDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerAuthorizationDenied
import platform.CoreBluetooth.CBManagerAuthorizationRestricted
import platform.CoreLocation.CLAccuracyAuthorization
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreMotion.CMAuthorizationStatusAuthorized
import platform.CoreMotion.CMAuthorizationStatusDenied
import platform.CoreMotion.CMAuthorizationStatusRestricted
import platform.CoreMotion.CMMotionActivityManager
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue

/**
 * iOS implementation of [PermissionManager].
 *
 * Location, motion and Bluetooth auth are read synchronously; notification auth needs an async
 * callback, so the synchronous fields land first and the flow is updated again when it resolves.
 *
 * Lee TRES cosas que en Android no son preguntas separadas y que antes se daban por supuestas
 * [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001]:
 *  - **La precisión.** Desde iOS 14 la ubicación se concede en modo aproximado sin dejar de estar
 *    «concedida». Para una app que coloca una plaza, no distinguirlo es la mentira más cara.
 *  - **El Bluetooth.** Se lee por la propiedad de CLASE `CBCentralManager.authorization`, que NO
 *    levanta el diálogo; instanciar el manager sí lo levantaría, y leer el estado no puede tener
 *    ese efecto.
 *  - **Qué diálogos se han gastado.** `denied`/`restricted` significan que el sistema ya no va a
 *    preguntar; verlo aquí es lo que permite que la fila lleve a los Ajustes en vez de a la nada.
 */
class IosPermissionManagerImpl : PermissionManager {

    private val locationManager = CLLocationManager()
    private val _permissionState = MutableStateFlow(AppPermissionState())
    override val permissionState: StateFlow<AppPermissionState> = _permissionState.asStateFlow()

    override fun refreshPermissions() {
        val authStatus = locationManager.authorizationStatus
        val hasLocation = authStatus == kCLAuthorizationStatusAuthorizedWhenInUse ||
            authStatus == kCLAuthorizationStatusAuthorizedAlways
        val hasBackground = authStatus == kCLAuthorizationStatusAuthorizedAlways
        // La precisión solo se pregunta de una ubicación que existe: sin permiso, el valor no
        // describe nada y contarlo nombraría el mismo hueco dos veces.
        val reducedAccuracy = hasLocation &&
            locationManager.accuracyAuthorization !=
                CLAccuracyAuthorization.CLAccuracyAuthorizationFullAccuracy

        val activityAvailable = CMMotionActivityManager.isActivityAvailable()
        val activityStatus = CMMotionActivityManager.authorizationStatus()
        val hasActivity = activityAvailable && activityStatus == CMAuthorizationStatusAuthorized

        val btAuthorization = CBCentralManager.authorization
        val hasBluetooth = btAuthorization == CBManagerAuthorizationAllowedAlways


        val spent = buildSet {
            if (authStatus == kCLAuthorizationStatusDenied ||
                authStatus == kCLAuthorizationStatusRestricted
            ) {
                add(SystemPermissionDialog.LOCATION)
                add(SystemPermissionDialog.ALWAYS_LOCATION)
            }
            // Con «Mientras se usa» concedido, la escalada a «Siempre» se presenta UNA vez; después
            // el sistema la descarta en silencio. Ver [AlwaysLocationAsk].
            if (authStatus == kCLAuthorizationStatusAuthorizedWhenInUse &&
                AlwaysLocationAsk.wasPresented
            ) {
                add(SystemPermissionDialog.ALWAYS_LOCATION)
            }
            if (activityStatus == CMAuthorizationStatusDenied ||
                activityStatus == CMAuthorizationStatusRestricted
            ) {
                add(SystemPermissionDialog.ACTIVITY)
            }
            if (btAuthorization == CBManagerAuthorizationDenied ||
                btAuthorization == CBManagerAuthorizationRestricted
            ) {
                add(SystemPermissionDialog.BLUETOOTH)
            }
        }

        // Synchronous fields updated immediately; notification status updated via callback.
        val current = _permissionState.value
        _permissionState.value = current.copy(
            hasLocationPermission = hasLocation,
            hasBackgroundLocationPermission = hasBackground,
            hasReducedLocationAccuracy = reducedAccuracy,
            hasActivityRecognitionPermission = hasActivity,
            isActivityRecognitionAvailable = activityAvailable,
            hasBluetoothConnectPermission = hasBluetooth,
            // [IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001] Optimista y luego corregido: un estado
            // AUTORIZADO ya prueba que el interruptor global está encendido —con él apagado,
            // CoreLocation informa «denegado» para toda app—, así que el caso normal no parpadea
            // nunca un «GPS apagado» que no es verdad. El valor real llega justo abajo.
            isLocationServicesEnabled = hasLocation || current.isLocationServicesEnabled,
            // Las notificaciones las resuelve el callback de abajo: se conserva lo que ya hubiera
            // para no borrar un «denegado» conocido entre una lectura y la siguiente.
            spentDialogs = spent + current.spentDialogs.filter {
                it == SystemPermissionDialog.NOTIFICATIONS
            },
        )

        // ⛔ `CLLocationManager.locationServicesEnabled()` es una llamada BLOQUEANTE a `locationd`, y
        // Xcode la señala en el hilo principal («can cause UI unresponsiveness») — y cada llamador de
        // este método es un ViewModel en Main. Se lee en una cola de fondo y se publica la respuesta.
        // [IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001]
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            val servicesEnabled = CLLocationManager.locationServicesEnabled()
            _permissionState.update { it.copy(isLocationServicesEnabled = servicesEnabled) }
        }

        // Async notification check — updates the StateFlow when the callback resolves.
        UNUserNotificationCenter.currentNotificationCenter()
            .getNotificationSettingsWithCompletionHandler { settings ->
                val status = settings?.authorizationStatus
                val granted = status == UNAuthorizationStatusAuthorized
                val notificationsSpent = status == UNAuthorizationStatusDenied
                _permissionState.value = _permissionState.value.let { state ->
                    state.copy(
                        hasNotificationPermission = granted,
                        spentDialogs = if (notificationsSpent) {
                            state.spentDialogs + SystemPermissionDialog.NOTIFICATIONS
                        } else {
                            state.spentDialogs - SystemPermissionDialog.NOTIFICATIONS
                        },
                    )
                }
            }
    }
}
