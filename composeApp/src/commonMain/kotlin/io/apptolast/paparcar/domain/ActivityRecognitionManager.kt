package io.apptolast.paparcar.domain

/**
 * Activity Recognition registration port — two independent lanes. [DET-SOLID-001][DET-AR-FIRST-001]
 *
 * The legacy AR-proximity arming API (`registerVehicleEnterArming`) was purged. Today the single
 * `registerTransitions()` call installs BOTH lanes:
 *  - **Evidence lane** (always-on): IN_VEHICLE ENTER + EXIT to a plain broadcast receiver; EXIT
 *    is a non-decisive hint for a running Coordinator; ENTER stamps `DepartureEventBus` with the
 *    true transition time as departure evidence. [DET-G-01]
 *  - **Decision lane** [DET-AR-FIRST-001]: ENTER only, delivered via `getForegroundService`
 *    straight into the Coordinator service, which runs the pure arm ladder
 *    (`EvaluateArEnterArmUseCase`) — the event still only NOMINATES; arming requires the ladder's
 *    verdict, and a bus ride costs one notification flash.
 */
interface ActivityRecognitionManager {
    fun registerTransitions()
    fun unregisterTransitions()
}
