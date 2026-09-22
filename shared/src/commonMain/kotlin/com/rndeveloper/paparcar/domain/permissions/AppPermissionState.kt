package com.rndeveloper.paparcar.domain.permissions

data class AppPermissionState(
    val hasLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val hasActivityRecognitionPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    // System setting — separate from runtime permissions.
    // NOT included in allPermissionsGranted to avoid redirecting the user
    // to the permissions gate just because they toggled GPS off mid-session.
    val isLocationServicesEnabled: Boolean = false,
    /**
     * La ubicación está concedida pero **aproximada**, no precisa.
     *
     * En Android esto no es una pregunta distinta y por eso el default es `false`:
     * `PermissionManagerImpl` comprueba `ACCESS_FINE_LOCATION`, así que allí conceder ubicación ya
     * es conceder precisión. En iOS sí lo es: desde iOS 14 se concede con *Ubicación precisa*
     * apagada (`CLAccuracyAuthorization.reducedAccuracy`, del orden de kilómetros) y el estado de
     * autorización sigue diciendo «concedida».
     *
     * Se nombra por lo que va MAL, no por lo que va bien, para que el default sea el honesto: una
     * fixture que no sabe de esto describe un dispositivo con ubicación precisa, que es lo normal.
     *
     * Para Paparcar no es un matiz: la unidad de trabajo es en qué plaza exacta has dejado el
     * coche. Sin precisión no hay detección que prometer, y por eso pesa en [hasProducerPermissions]
     * y no en [hasCorePermissions] — el mapa de plazas se puede seguir mirando.
     * [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001]
     */
    val hasReducedLocationAccuracy: Boolean = false,
    /**
     * El dispositivo tiene con qué reconocer actividad. `false` en hardware iOS sin coprocesador de
     * movimiento (`CMMotionActivityManager.isActivityAvailable()`).
     *
     * Cuando es `false` la fila no es un permiso pendiente: es un permiso que **no se puede
     * conceder aquí**, así que no se pinta y no cuenta para [hasProducerPermissions] — o la
     * detección quedaría «no lista» para siempre sin nada que el usuario pudiera hacer.
     */
    val isActivityRecognitionAvailable: Boolean = true,
    // Optional — only available on Android 12+. Not required for core app functionality;
    // only needed for the Bluetooth parking detection strategy.
    val hasBluetoothConnectPermission: Boolean = false,
    // Optional — not a runtime permission. User must approve via system dialog.
    // Critical on Doze-aggressive OEMs (MIUI, ColorOS, EMUI). Not in allPermissionsGranted
    // because the app still works without it; detection just degrades on OEM killers. [DOZE-001]
    val isBatteryOptimizationExempt: Boolean = false,
    /**
     * Diálogos del sistema que **ya no van a salir** — denegados, restringidos, o gastados. Pulsar
     * una fila cuyo diálogo está aquí tiene que abrir los Ajustes, no lanzar una petición que el
     * sistema ignorará en silencio. Ver [SystemPermissionDialog].
     */
    val spentDialogs: Set<SystemPermissionDialog> = emptySet(),
) {
    val allPermissionsGranted: Boolean
        get() = hasLocationPermission
            && hasBackgroundLocationPermission
            && hasActivityRecognitionPermission
            && hasNotificationPermission

    /**
     * CORE tier — the minimum to use the app at all (spot map / consumer side): foreground
     * location. Notifications are NOT here — you can browse spots without them. Gates entry to
     * Home. See [RequiredPermission] and [PermissionTier.CORE]. [DET-READY-001a] [DET-READY-001i]
     */
    val hasCorePermissions: Boolean
        get() = hasLocationPermission

    /**
     * PRODUCER tier — the full automatic-detection experience: background location, activity
     * recognition AND notifications (so detection can actually tell the user anything). Requested
     * contextually; never gates Home. [DET-READY-001a] [DET-READY-001i]
     *
     * La precisión entra aquí, y el reconocimiento de actividad solo si el dispositivo lo tiene:
     * un permiso que esta plataforma no puede conceder no puede faltar.
     * [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001]
     */
    val hasProducerPermissions: Boolean
        get() = hasBackgroundLocationPermission &&
            !hasReducedLocationAccuracy &&
            (hasActivityRecognitionPermission || !isActivityRecognitionAvailable) &&
            hasNotificationPermission
}
