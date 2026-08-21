package io.apptolast.paparcar.domain.detection

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
 * Values mirror `com.google.android.gms.location.GeofenceStatusCodes`; the mapping lives here, pure
 * and unit-testable, while the platform side only has to pull the integer out of the exception.
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
    }
}
