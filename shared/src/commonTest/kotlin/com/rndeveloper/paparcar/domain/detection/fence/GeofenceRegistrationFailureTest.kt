package com.rndeveloper.paparcar.domain.detection.fence

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [DET-FENCE-REREGISTER-BY-CAUSE-001 §D] A failed registration used to be a bare `false`. These
 * names are what a field diagnosis reads instead — and each one demands a different fix, which is
 * why lumping them together was useless.
 */
class GeofenceRegistrationFailureTest {

    @Test
    fun should_name_the_play_services_status_codes_we_know() {
        assertEquals(
            GeofenceRegistrationFailure.NOT_AVAILABLE,
            GeofenceRegistrationFailure.fromStatusCode(GeofenceRegistrationFailure.GMS_GEOFENCE_NOT_AVAILABLE),
        )
        assertEquals(
            GeofenceRegistrationFailure.TOO_MANY_GEOFENCES,
            GeofenceRegistrationFailure.fromStatusCode(GeofenceRegistrationFailure.GMS_GEOFENCE_TOO_MANY_GEOFENCES),
        )
        assertEquals(
            GeofenceRegistrationFailure.TOO_MANY_PENDING_INTENTS,
            GeofenceRegistrationFailure.fromStatusCode(GeofenceRegistrationFailure.GMS_GEOFENCE_TOO_MANY_PENDING_INTENTS),
        )
    }

    @Test
    fun should_separate_a_missing_permission_from_an_unnamed_failure() {
        // These two demand opposite responses — one is ours to fix (we let the flow get here
        // without the permission), the other is a prompt to add a status code we had not seen.
        assertEquals(
            GeofenceRegistrationFailure.PERMISSION_DENIED,
            GeofenceRegistrationFailure.fromStatusCode(null, securityException = true),
        )
        assertEquals(
            GeofenceRegistrationFailure.UNKNOWN,
            GeofenceRegistrationFailure.fromStatusCode(null),
        )
        assertEquals(
            GeofenceRegistrationFailure.UNKNOWN,
            GeofenceRegistrationFailure.fromStatusCode(9999),
        )
    }

    @Test
    fun should_let_a_permission_failure_win_over_any_status_code() {
        // A SecurityException carrying a stale status code must still read as the permission
        // problem it is: filing it under NOT_AVAILABLE would send us hunting the user's settings.
        assertEquals(
            GeofenceRegistrationFailure.PERMISSION_DENIED,
            GeofenceRegistrationFailure.fromStatusCode(
                GeofenceRegistrationFailure.GMS_GEOFENCE_NOT_AVAILABLE,
                securityException = true,
            ),
        )
    }

    // ── iOS · CoreLocation [IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001] ──────────────────

    @Test
    fun should_name_the_corelocation_codes_we_know() {
        assertEquals(
            GeofenceRegistrationFailure.NOT_AVAILABLE,
            GeofenceRegistrationFailure.fromCLErrorCode(
                GeofenceRegistrationFailure.CL_REGION_MONITORING_FAILURE.toLong(),
            ),
        )
        assertEquals(
            GeofenceRegistrationFailure.NOT_AVAILABLE,
            GeofenceRegistrationFailure.fromCLErrorCode(
                GeofenceRegistrationFailure.CL_REGION_MONITORING_SETUP_DELAYED.toLong(),
            ),
        )
        assertEquals(
            GeofenceRegistrationFailure.UNKNOWN,
            GeofenceRegistrationFailure.fromCLErrorCode(9999L),
            "un código que no hemos visto se archiva como tal, y su mensaje sigue en el log",
        )
        assertEquals(GeofenceRegistrationFailure.UNKNOWN, GeofenceRegistrationFailure.fromCLErrorCode(null))
    }

    @Test
    fun should_read_both_ways_of_not_having_always_as_the_same_missing_permission() {
        // ⛔ Al usuario le da igual si dijo que no a la ubicación entera o si se quedó en «Mientras
        // se usa»: el efecto es el mismo —no hay valla— y el remedio también. Lo que no puede pasar
        // es que ninguno de los dos acabe en UNKNOWN, que pide otro arreglo completamente distinto.
        assertEquals(
            GeofenceRegistrationFailure.PERMISSION_DENIED,
            GeofenceRegistrationFailure.fromCLErrorCode(GeofenceRegistrationFailure.CL_DENIED.toLong()),
        )
        assertEquals(
            GeofenceRegistrationFailure.PERMISSION_DENIED,
            GeofenceRegistrationFailure.fromCLErrorCode(
                GeofenceRegistrationFailure.CL_REGION_MONITORING_DENIED.toLong(),
            ),
        )
    }

    @Test
    fun should_let_the_region_cap_win_over_the_code_that_hides_it() {
        // iOS mete el tope de 20 regiones dentro del mismo `regionMonitoringFailure` que cualquier
        // otra causa. El llamante SÍ puede contarlas, así que cuando lo hace manda su cuenta: sin
        // esto, una fuga de vallas se diagnosticaría como «el dispositivo no puede vigilar».
        assertEquals(
            GeofenceRegistrationFailure.TOO_MANY_GEOFENCES,
            GeofenceRegistrationFailure.fromCLErrorCode(
                GeofenceRegistrationFailure.CL_REGION_MONITORING_FAILURE.toLong(),
                tooManyRegions = true,
            ),
        )
    }

    @Test
    fun should_expose_a_stable_wire_label_for_every_reason() {
        // The label is persisted in the diagnostics `reason` column, so renaming a value silently
        // would break grouping across releases.
        assertEquals(
            listOf("not_available", "too_many_geofences", "too_many_pending_intents", "permission_denied", "unknown"),
            GeofenceRegistrationFailure.entries.map { it.label },
        )
    }
}
