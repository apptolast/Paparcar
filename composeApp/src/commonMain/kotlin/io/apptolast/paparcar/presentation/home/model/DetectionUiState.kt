package io.apptolast.paparcar.presentation.home.model

import io.apptolast.paparcar.domain.detection.ParkingStrategy
import io.apptolast.paparcar.domain.model.DetectionReadiness
import io.apptolast.paparcar.domain.model.DisabledReason
import io.apptolast.paparcar.domain.permissions.PermissionTier

/**
 * Presentation projection of [DetectionReadiness] for the Home detection surface. [DET-READY-001h]
 *
 * The domain [DetectionReadiness] is the single source of truth (resolved by
 * `ObserveDetectionReadinessUseCase`). This type only **collapses it for the UI**:
 *  - splits `Blocked` into CORE (error) vs PRODUCER (upsell) by permission tier,
 *  - renames the Coordinator cold-start (`Ready` COORDINATOR) to [AwaitingFirstPark],
 *  - folds every "show nothing" case (Bluetooth armed, non-parking vehicle) into [Silent].
 *
 * Exactly one state is active at a time — precedence lives in the domain resolver, not here.
 * Only the four action states render a surface; [Parked] defers to the existing parked-car
 * card and [Silent] renders nothing.
 */
sealed interface DetectionUiState {
    /** No vehicle registered — nothing to detect. Action: add a car. */
    data object NoVehicle : DetectionUiState

    /** Foreground location / notifications missing — the app barely works. Action: grant CORE. */
    data object BlockedCore : DetectionUiState

    /** Background location / activity recognition missing — auto-detection off. Action: grant PRODUCER. */
    data object BlockedProducer : DetectionUiState

    /** Parked with a geofence watching for departure — rendered by the existing parked-car card. */
    data object Parked : DetectionUiState

    /** A tracking job is following the current trip — ephemeral pill. */
    data object Monitoring : DetectionUiState

    /**
     * Coordinator cold-start: has a parking-capable vehicle, no active session, not tracking.
     * Action: mark the current spot (primary) or start manual detection while driving (secondary).
     */
    data object AwaitingFirstPark : DetectionUiState

    /** Detection does not apply or is fully automatic (non-parking vehicle, Bluetooth armed). No surface. */
    data object Silent : DetectionUiState
}

/**
 * Pure projection from the domain readiness model. Keeps the locked precedence intact (it already
 * picked the single active [DetectionReadiness]); this only chooses the UI shape. [DET-READY-001h]
 */
fun DetectionReadiness.toUiState(): DetectionUiState = when (this) {
    is DetectionReadiness.Disabled -> when (reason) {
        DisabledReason.NO_VEHICLE -> DetectionUiState.NoVehicle
        DisabledReason.NON_PARKING_VEHICLE -> DetectionUiState.Silent
    }

    is DetectionReadiness.Blocked ->
        // A missing CORE permission is the more severe failure and wins the surface.
        if (missing.any { it.tier == PermissionTier.CORE }) DetectionUiState.BlockedCore
        else DetectionUiState.BlockedProducer

    is DetectionReadiness.Parked -> DetectionUiState.Parked

    is DetectionReadiness.Monitoring -> DetectionUiState.Monitoring

    is DetectionReadiness.Ready ->
        // Only the Coordinator cold-start needs a manual bootstrap. Bluetooth is fully automatic.
        if (strategy == ParkingStrategy.COORDINATOR) DetectionUiState.AwaitingFirstPark
        else DetectionUiState.Silent
}

/** True for the four states that render the action surface row (`HomeDetectionSurface`). */
val DetectionUiState.rendersActionSurface: Boolean
    get() = this == DetectionUiState.NoVehicle ||
        this == DetectionUiState.BlockedCore ||
        this == DetectionUiState.BlockedProducer ||
        this == DetectionUiState.AwaitingFirstPark
