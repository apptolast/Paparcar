package com.rndeveloper.paparcar.domain.detection.fence

/**
 * [DET-FENCE-REREGISTER-BY-CAUSE-001 §D] Why a geofence registration failed, in words a diagnosis
 * can act on.
 *
 * Until this existed, a failed registration was a bare `false`: the log printed `✗FALLÓ el
 * re-registro` and the remote event carried `success = false`, and the `Throwable` — which holds
 * the Play Services status code and therefore the entire answer — was discarded at the call site.
 * Field 2026-08-20: a red ✗ on a fence that was demonstrably alive, with no way to learn why.
 *
 * The reason matters beyond tidiness: it is what tells us whether re-registration is failing for a
 * cause the app can fix (a permission it should have asked for), a cause the user can fix (location
 * turned off), or no cause at all — and that answer is the input to deciding how often the blind
 * periodic floor needs to run at all.
 *
 * Los valores nacieron espejando `com.google.android.gms.location.GeofenceStatusCodes`, y desde
 * [IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001] también los cubre `CLError`: **son las RAZONES,
 * no los códigos de nadie**. Los dos mapeos viven aquí, puros y testeables, y cada plataforma sólo
 * tiene que sacar el entero de su error.
 */
enum class GeofenceRegistrationFailure {
    /**
     * Play Services cannot serve geofences right now. In practice: the user turned location off, or
     * dropped to a battery-saving location mode that disables the geofencing engine. The user's own
     * fences are gone with it — this is the one reason that means "the car really is unwatched".
     */
    NOT_AVAILABLE,

    /** More than 100 fences registered for this app. We register 3 per parked car, so this would
     *  mean ~33 live sessions or a leak of un-removed fences. */
    TOO_MANY_GEOFENCES,

    /** More than 5 PendingIntents used. We use exactly 3 (exit, enter twin, witness) and they are
     *  stable, so this reads as an OS/GMS anomaly rather than something we did. */
    TOO_MANY_PENDING_INTENTS,

    /** Missing (or revoked mid-flight) location permission — a `SecurityException` rather than a
     *  Play Services status. Actionable by us: the permission gate let us get this far without it. */
    PERMISSION_DENIED,

    /** Play Services threw something we have no name for yet. Not a dustbin: an UNKNOWN showing up
     *  in telemetry is a prompt to add its status code here, and the raw message still rides the log. */
    UNKNOWN,
    ;

    /** Stable wire label for the diagnostics event. */
    val label: String get() = name.lowercase()

    companion object {
        // Mirrors com.google.android.gms.location.GeofenceStatusCodes. Duplicated as plain ints on
        // purpose: this file is commonMain (iOS compiles it too) and must not import GMS.
        const val GMS_GEOFENCE_NOT_AVAILABLE = 1000
        const val GMS_GEOFENCE_TOO_MANY_GEOFENCES = 1001
        const val GMS_GEOFENCE_TOO_MANY_PENDING_INTENTS = 1002

        /**
         * Maps a Play Services status code to its reason. [statusCode] is null when the failure was
         * not an `ApiException` at all (a `SecurityException`, a cancellation, an OEM surprise) —
         * pass [securityException] so a missing permission is not filed under UNKNOWN, since those
         * two demand completely different fixes.
         */
        fun fromStatusCode(statusCode: Int?, securityException: Boolean = false): GeofenceRegistrationFailure =
            when {
                securityException -> PERMISSION_DENIED
                statusCode == GMS_GEOFENCE_NOT_AVAILABLE -> NOT_AVAILABLE
                statusCode == GMS_GEOFENCE_TOO_MANY_GEOFENCES -> TOO_MANY_GEOFENCES
                statusCode == GMS_GEOFENCE_TOO_MANY_PENDING_INTENTS -> TOO_MANY_PENDING_INTENTS
                else -> UNKNOWN
            }

        // ── iOS · CoreLocation [IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001] ───────────────
        //
        // Mirrors `CLError.Code`, as plain ints for the same reason que los de arriba: este fichero
        // es commonMain y Android lo compila igual, así que no puede importar CoreLocation.
        // Sólo los tres que esta app puede provocar; el resto (heading, geocoding, ranging…) no
        // tocan una región y caen en UNKNOWN con su mensaje.

        /** `CLError.denied` — el usuario negó la ubicación a la app entera. */
        const val CL_DENIED = 1

        /** `CLError.regionMonitoringDenied` — hay ubicación, pero no «Siempre», que es lo que
         *  `startMonitoringForRegion` exige para entregar un EXIT con la app suspendida. */
        const val CL_REGION_MONITORING_DENIED = 4

        /** `CLError.regionMonitoringFailure` — la región no se pudo registrar: el tope de 20 por
         *  app, o una región que el dispositivo no sabe monitorizar. */
        const val CL_REGION_MONITORING_FAILURE = 5

        /** `CLError.regionMonitoringSetupDelayed` — no se pudo poner AHORA. No es un no definitivo,
         *  pero mientras dura la valla no existe, y eso es lo que el diagnóstico tiene que decir. */
        const val CL_REGION_MONITORING_SETUP_DELAYED = 6

        /**
         * Lo mismo para CoreLocation. [IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001]
         *
         * ⛔ Dos códigos distintos acaban en [PERMISSION_DENIED] a propósito: al usuario le da
         * igual si dijo que no a la ubicación entera o si se quedó en «Mientras se usa», porque el
         * remedio es el mismo y el efecto también — la valla no existe. Lo que NO puede pasar es
         * que cualquiera de los dos se archive como UNKNOWN, que es lo que pide otro arreglo.
         *
         * [tooManyRegions] lo contesta el llamante ANTES de pedir el registro, contando
         * `monitoredRegions`: iOS no distingue el tope de las demás causas de
         * `regionMonitoringFailure`, y nosotros sí podemos.
         */
        fun fromCLErrorCode(code: Long?, tooManyRegions: Boolean = false): GeofenceRegistrationFailure =
            when {
                tooManyRegions -> TOO_MANY_GEOFENCES
                code?.toInt() == CL_DENIED -> PERMISSION_DENIED
                code?.toInt() == CL_REGION_MONITORING_DENIED -> PERMISSION_DENIED
                code?.toInt() == CL_REGION_MONITORING_FAILURE -> NOT_AVAILABLE
                code?.toInt() == CL_REGION_MONITORING_SETUP_DELAYED -> NOT_AVAILABLE
                else -> UNKNOWN
            }
    }
}
