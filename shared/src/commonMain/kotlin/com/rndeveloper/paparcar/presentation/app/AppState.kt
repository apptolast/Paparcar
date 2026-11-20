package com.rndeveloper.paparcar.presentation.app

import com.rndeveloper.paparcar.domain.connectivity.ConnectivityBannerPhase
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityStatus
import com.rndeveloper.paparcar.domain.entitlement.PlusMoment
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
    /** The one-time ad-measurement question is owed: there is an ad lane in this build and the
     *  user has not answered yet. [MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001] */
    val askAdMeasurementConsent: Boolean = false,
    /**
     * La plaza nueva que acaba de aparecer en una zona vigilada, para contarla DENTRO de la app —
     * o null si no hay ninguna que contar. [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001]
     *
     * ⛔ Es un aviso transitorio con la misma forma que el de conectividad, y no un segundo canal
     * paralelo: el reparto elige UNO de los dos —este o la notificación del sistema— según si el
     * mapa está delante. Que los dos suenen a la vez por la misma plaza es justo lo que esto evita.
     */
    val spotAlert: SpotAlertCue? = null,
    /**
     * What the app owes this account about its gifted Plus — the welcome, the end-of-promo
     * reminder, or nothing. [MONEY-FOUNDERS-WELCOME-001]
     *
     * Not the same thing as [plusMomentToShow]: this is the VERDICT, that one is whether it may be
     * on screen right now.
     */
    val plusMoment: PlusMoment? = null,
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

    /**
     * The Plus moment, but only when there is nothing else holding the screen.
     * [MONEY-FOUNDERS-WELCOME-001]
     *
     * ⛔ **The order between this and [askAdMeasurementConsent] lives HERE, in the state, and not
     * in whichever composable happens to be written first.** A brand-new account is exactly the
     * one that owes BOTH — it is the case, not an edge case — and two dialogs at once is the one
     * thing this project forbids outright (⛔ nunca modal sobre modal).
     *
     * The consent goes first because it is a QUESTION: it demands an answer and swallows taps
     * outside itself, so it cannot queue behind anything. The celebration is NEWS, and news waits.
     */
    val plusMomentToShow: PlusMoment?
        get() = plusMoment.takeIf { !askAdMeasurementConsent }
}

/**
 * Lo que el aviso in-app necesita decir y ofrecer: de dónde sale, y dónde está la plaza para poder
 * ir. [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001]
 *
 * ⚠️ Lleva `id` y no sólo el texto porque un aviso tiene que poder REEMPLAZARSE: si llegan dos
 * plazas seguidas, la segunda sustituye a la primera en vez de encolarse — nadie quiere leer una
 * cola de avisos sobre un mapa que ya los está enseñando.
 */
data class SpotAlertCue(
    val spotId: String,
    val origin: SpotAlertOrigin,
    val latitude: Double,
    val longitude: Double,
    /**
     * La salida se DEDUJO, nadie la midió, así que la frase no puede prometer certeza.
     * [ALERT-A-PROVISIONAL-SPOT-IS-PUSHED-BUT-NOT-SHOWN-001]
     *
     * ⛔ Va en el cue y no se deduce en el call site porque **es el único aviso que esa plaza va a
     * tener**: el id es el mismo en toda la publicación (`RELEASING → PROVISIONAL → CONFIRMED`, sobre
     * `park.id`), así que la memoria de «ya contada» hace que anunciar en PROVISIONAL consuma el aviso
     * y la promoción a CONFIRMED ya no vuelva a hablar — que es lo que el servidor dice que quiere.
     * Si la duda no viaja aquí, no viaja nunca.
     */
    val unconfirmed: Boolean = false,
)

/**
 * De cuál de los dos carriles viene el aviso, que es lo que decide la frase.
 * [ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001]
 *
 * ⛔ **Un tipo, no un `zoneName` que a veces está vacío.** El campo era `zoneName: String` y el
 * carril nuevo no tiene zona ninguna: colar `""` o un nulo para significar «cerca de ti» es
 * exactamente la clase de codificación implícita que se podre — el `when` de abajo obliga a que cada
 * carril traiga su frase, y un tercero no podrá compilar sin elegirla.
 */
sealed interface SpotAlertOrigin {
    /** Una zona que el usuario guardó y vigila (Plus). Trae su nombre porque la frase lo dice. */
    data class InZone(val zoneName: String) : SpotAlertOrigin

    /** Donde está el usuario ahora mismo (gratis, sólo con el mapa delante). No hay nombre que dar. */
    data object NearYou : SpotAlertOrigin
}
