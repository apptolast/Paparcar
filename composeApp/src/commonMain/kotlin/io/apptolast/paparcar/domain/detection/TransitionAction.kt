package io.apptolast.paparcar.domain.detection

/**
 * Result of [HandleVehicleTransitionUseCase]: tells the Android Service what to do
 * with the detection job after handling an IN_VEHICLE activity-transition event.
 */
sealed interface TransitionAction {
    /** Launch (or restart) the Coordinator detection pipeline. */
    data object StartCoordinatorDetection : TransitionAction
    /** Stop the service only if no detection job is currently active. */
    data object StopIfIdle : TransitionAction
    /** Duplicate event — nothing to do. */
    data object Ignore : TransitionAction
}
