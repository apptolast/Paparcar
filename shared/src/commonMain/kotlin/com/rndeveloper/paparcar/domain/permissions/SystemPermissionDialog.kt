package com.rndeveloper.paparcar.domain.permissions

/**
 * Un diálogo del sistema que la app puede levantar para pedir un permiso.
 *
 * El veredicto que acompaña a cada uno —[AppPermissionState.spentDialogs]— es si **sigue vivo**, y
 * es del DIÁLOGO, no del permiso: un permiso denegado se puede seguir concediendo, pero en los
 * Ajustes. Lo que se gasta es la vía del diálogo.
 *
 * Importa porque decide qué hace una fila al pulsarla. Levantar un diálogo que el sistema ya no va
 * a presentar es un botón muerto: el usuario pulsa, no pasa nada, y no tiene forma de saber que el
 * camino está en otro sitio. [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001]
 *
 * Quién lo rellena depende de lo que sepa cada plataforma:
 *  - iOS lo sabe exacto y síncrono (`CLAuthorizationStatus`, `CMAuthorizationStatus`,
 *    `UNAuthorizationStatus`, `CBManagerAuthorization` en `denied`/`restricted`), más un flag propio
 *    para la escalada a «Siempre», que el sistema presenta UNA vez y luego ignora en silencio.
 *  - Android no lo expone desde un `Context`: hace falta la Activity para
 *    `shouldShowRequestPermissionRationale`, así que lo empuja la pantalla vía
 *    `PermissionsIntent.SetLocationPermanentlyDenied` y solo para la ubicación. [DET-READY-001m]
 */
enum class SystemPermissionDialog {
    LOCATION,
    ALWAYS_LOCATION,
    ACTIVITY,
    NOTIFICATIONS,
    BLUETOOTH,
}
