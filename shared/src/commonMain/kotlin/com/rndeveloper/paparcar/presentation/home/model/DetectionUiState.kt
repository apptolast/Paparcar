package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.detection.ParkingStrategy
import com.rndeveloper.paparcar.domain.model.DetectionReadiness
import com.rndeveloper.paparcar.domain.model.DisabledReason
import com.rndeveloper.paparcar.domain.permissions.PermissionTier

/**
 * Presentation projection of [DetectionReadiness] for the Home detection surface. [DET-READY-001h]
 *
 * The domain [DetectionReadiness] is the single source of truth (resolved by
 * `ObserveDetectionReadinessUseCase`). This type only **collapses it for the UI**:
 *  - keeps the CORE block (location/GPS off — the app barely works) as its own urgent state,
 *  - folds **everything else that means "detection isn't running but can be"** — the Settings flag
 *    switched off OR the producer permissions missing — into one [Inactive] "activate detection"
 *    state with a single button that asks for whatever is missing (flag + permissions). [DET-TOGGLE-001]
 *  - renames the Coordinator cold-start (`Ready` COORDINATOR) to [AwaitingFirstPark],
 *  - keeps Bluetooth-armed as its own [ArmedBluetooth] (the story surface says it, discreetly)
 *    and leaves only the true "nothing to say" case (non-parking vehicle) in [Silent].
 *    [UX-DETECTION-STORY-001]
 *
 * Exactly one state is active at a time — precedence lives in the domain resolver, not here.
 * The surface shape per state is chosen by [resolveDetectionStory] ([DetectionStory]): the four
 * action states render the loud row, [Parked]/[Monitoring]/[ArmedBluetooth] a discreet status
 * line, and [Silent] nothing. [UX-DETECTION-STORY-001]
 */
sealed interface DetectionUiState {
    /** No vehicle registered — nothing to detect. Action: add a car. */
    data object NoVehicle : DetectionUiState

    /**
     * Detection is not running but could be — either auto-detection is OFF in Settings, or the
     * producer permissions (background location + activity recognition) are missing. A single
     * "activate detection" button flips the flag on AND requests any missing permissions in one
     * tap, so the user never faces two separate steps. [DET-TOGGLE-001]
     */
    data object Inactive : DetectionUiState

    /** Foreground location / notifications missing — the app barely works. Action: grant CORE. */
    data object BlockedCore : DetectionUiState

    /** Parked with a geofence watching for departure — rendered by the existing parked-car card. */
    data object Parked : DetectionUiState

    /** A tracking job is following the current trip — ephemeral pill. */
    data object Monitoring : DetectionUiState

    /**
     * Coordinator cold-start: has a parking-capable vehicle, no active session, not tracking.
     * Action: mark the current spot (primary) or start manual detection while driving (secondary).
     */
    data object AwaitingFirstPark : DetectionUiState

    /**
     * Armed and waiting via the car's paired Bluetooth — fully automatic, nothing to ask. Split
     * from [Silent] so the detection story can SAY it is covering the car instead of going mute.
     * [UX-DETECTION-STORY-001]
     */
    data object ArmedBluetooth : DetectionUiState

    /** Detection does not apply (non-parking vehicle). No surface. */
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
        DisabledReason.TURNED_OFF -> DetectionUiState.Inactive
    }

    is DetectionReadiness.Blocked ->
        // A missing CORE permission (location/GPS) is the more severe failure — the app barely works
        // — and keeps its own urgent surface. Producer-only missing folds into the same "activate
        // detection" surface as the Settings-off case: one button, one flow. [DET-TOGGLE-001]
        if (missing.any { it.tier == PermissionTier.CORE }) DetectionUiState.BlockedCore
        else DetectionUiState.Inactive

    is DetectionReadiness.Parked -> DetectionUiState.Parked

    is DetectionReadiness.Monitoring -> DetectionUiState.Monitoring

    is DetectionReadiness.Ready ->
        // Only the Coordinator cold-start needs a manual bootstrap. Bluetooth is fully automatic —
        // but it still gets a voice (a discreet "watching" line), not silence. [UX-DETECTION-STORY-001]
        if (strategy == ParkingStrategy.COORDINATOR) DetectionUiState.AwaitingFirstPark
        else DetectionUiState.ArmedBluetooth
}

/** Detection is running or armed — a real "working" state that can be lost. Used to fire the
 *  in-app "detection stopped" snackbar only on a genuine working→stopped drop. [DET-TOGGLE-002] */
val DetectionUiState.isDetectionWorking: Boolean
    get() = this == DetectionUiState.Monitoring ||
        this == DetectionUiState.Parked ||
        this == DetectionUiState.AwaitingFirstPark ||
        this == DetectionUiState.ArmedBluetooth

/** Detection has stopped but can be re-activated in one tap (off in Settings, or producer/core
 *  permissions missing). [DET-TOGGLE-002] */
val DetectionUiState.isDetectionStopped: Boolean
    get() = this == DetectionUiState.Inactive || this == DetectionUiState.BlockedCore
