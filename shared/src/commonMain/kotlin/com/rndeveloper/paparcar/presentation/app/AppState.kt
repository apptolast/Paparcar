package com.rndeveloper.paparcar.presentation.app

import com.rndeveloper.paparcar.domain.connectivity.ConnectivityBannerPhase
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityStatus
import com.rndeveloper.paparcar.domain.preferences.ThemeMode

data class AppState(
    val permissionsGranted: Boolean = false,
    val locationServicesEnabled: Boolean = false,
    /** Every PRODUCER permission (background location, activity recognition, notifications) is
     *  granted — detection can genuinely run. [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001] */
    val detectionPermissionsGranted: Boolean = false,
    val hasVehicle: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val imperialUnits: Boolean = false,
    val connectivity: ConnectivityStatus = ConnectivityStatus.Online,
    /** Drives the root connectivity banner (Hidden/Offline/Restored). [CONN-BANNER-001] */
    val connectivityBanner: ConnectivityBannerPhase = ConnectivityBannerPhase.Hidden,
    val hasSeenGpsAccuracyDisclaimer: Boolean = false,
    /** Device-local "I accept the Privacy Policy" tick — gates every sign-in CTA until given.
     *  [AUTH-A-SIGN-IN-ASKS-FOR-CONSENT-FIRST-001] */
    val hasAcceptedLegalConsent: Boolean = false,
    /**
     * La plaza nueva que acaba de aparecer en una zona vigilada, para contarla DENTRO de la app —
     * o null si no hay ninguna que contar. [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001]
     *
     * ⛔ Es un aviso transitorio con la misma forma que el de conectividad, y no un segundo canal
     * paralelo: el reparto elige UNO de los dos —este o la notificación del sistema— según si el
     * mapa está delante. Que los dos suenen a la vez por la misma plaza es justo lo que esto evita.
     */
    val spotAlert: SpotAlertCue? = null,
) {
    /** True only when runtime permissions AND GPS are both ready. */
    val isFullyOperational: Boolean
        get() = permissionsGranted && locationServicesEnabled

    /** Detection is fully armed: CORE + GPS + every PRODUCER permission. Drives the one-time
     *  GPS-accuracy disclaimer — it talks about detection, so it waits for detection.
     *  [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001] */
    val isDetectionReady: Boolean
        get() = isFullyOperational && detectionPermissionsGranted

    val isOffline: Boolean
        get() = connectivity == ConnectivityStatus.Offline

    /** Show once when detection is ready and the disclaimer hasn't been dismissed yet. */
    val showGpsAccuracyDisclaimer: Boolean
        get() = isDetectionReady && !hasSeenGpsAccuracyDisclaimer
}

/**
 * Lo que el aviso in-app necesita decir y ofrecer: de qué zona es, y dónde está la plaza para poder
 * ir. [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001]
 *
 * ⚠️ Lleva `id` y no sólo el texto porque un aviso tiene que poder REEMPLAZARSE: si llegan dos
 * plazas seguidas, la segunda sustituye a la primera en vez de encolarse — nadie quiere leer una
 * cola de avisos sobre un mapa que ya los está enseñando.
 */
data class SpotAlertCue(
    val spotId: String,
    val zoneName: String,
    val latitude: Double,
    val longitude: Double,
)
