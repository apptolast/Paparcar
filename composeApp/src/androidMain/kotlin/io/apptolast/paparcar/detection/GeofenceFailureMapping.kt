package io.apptolast.paparcar.detection

import com.google.android.gms.common.api.ApiException
import io.apptolast.paparcar.domain.detection.GeofenceRegistrationFailure

/**
 * [DET-FENCE-REREGISTER-BY-CAUSE-001 §D] Pulls the reason out of whatever
 * [io.apptolast.paparcar.domain.service.GeofenceManager.createGeofence] failed with.
 *
 * The whole platform-specific part of "why did this fail" is these few lines; the decision of what
 * each code MEANS lives in [GeofenceRegistrationFailure], pure and unit-tested. Both re-registration
 * lanes (the safety net's cure and the janitor) call this, so the two can never drift into
 * describing the same failure differently.
 */
fun Throwable.toGeofenceRegistrationFailure(): GeofenceRegistrationFailure =
    GeofenceRegistrationFailure.fromStatusCode(
        statusCode = (this as? ApiException)?.statusCode,
        securityException = this is SecurityException,
    )

/** The reason plus the raw message, for the local log — the message is what turns an UNKNOWN into
 *  a named case on the next release. The full stack goes to [PaparcarLogger.w]'s throwable arg. */
fun Throwable.geofenceFailureDetail(): String =
    "${toGeofenceRegistrationFailure().label} (${this::class.simpleName}: ${message ?: "no message"})"
