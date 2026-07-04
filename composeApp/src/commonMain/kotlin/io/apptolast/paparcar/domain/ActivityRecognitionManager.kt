package io.apptolast.paparcar.domain

/**
 * Activity Recognition as an **indicator-only** source. [DET-SOLID-001]
 *
 * The legacy AR-proximity arming API (`registerVehicleEnterArming`) was purged: AR never arms
 * detection — arming is exclusive to GEOFENCE_EXIT + MANUAL. The always-on registration delivers
 * IN_VEHICLE ENTER + EXIT to a plain broadcast receiver (no foreground service, no notification
 * flash on bus rides): EXIT is a non-decisive hint for a running Coordinator; ENTER stamps
 * `DepartureEventBus` with the true transition time as departure evidence. [DET-G-01]
 */
interface ActivityRecognitionManager {
    fun registerTransitions()
    fun unregisterTransitions()
}
