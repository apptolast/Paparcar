package io.apptolast.paparcar.presentation.home.model

import io.apptolast.paparcar.domain.detection.DetectionPhase
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.presentation.home.DrivingMeta
import io.apptolast.paparcar.presentation.home.VehicleCard

/**
 * The SINGLE detection story Home tells in the expanded sheet — one voice for "what is the app
 * doing for me right now", instead of action pills that go mute in every happy state.
 * [UX-DETECTION-STORY-001, cures C6/H5 of UX-PARK-FLOW-001]
 *
 * Exactly one story at a time, resolved by [resolveDetectionStory] with a fixed precedence
 * (urgent → quiet): [BlockedCore] → [NoVehicle] → [Inactive] → [AwaitingFirstPark] → [Driving] →
 * [Watching] → [Hidden]. The four action stories keep the loud accent-bar row; [Driving] and
 * [Watching] render a discreet one-line status (no card) so the happy path finally has a voice.
 *
 * The collapsed peek keeps its own phase eyebrow ("EN RUTA"/"APARCANDO…") — that is the voice of
 * the CLOSED sheet; this story is the voice of the OPEN sheet. Same translated words, no drift.
 */
sealed interface DetectionStory {
    /** CORE permission missing — the app barely works. Loud, error-toned action row. */
    data object BlockedCore : DetectionStory

    /** No vehicle registered — nothing to detect. Action row. */
    data object NoVehicle : DetectionStory

    /** Detection off (Settings flag or producer permissions) — one-tap activate. Action row. */
    data object Inactive : DetectionStory

    /** Coordinator cold-start — mark your spot or declare "I'm driving". Action row. */
    data object AwaitingFirstPark : DetectionStory

    /** A trip is being followed right now. [isCandidate] = stopped, confirming the spot. */
    data class Driving(val vehicleName: String, val isCandidate: Boolean) : DetectionStory

    /**
     * Discreet happy line — detection is armed and covering the ACTIVE vehicle. Only the active
     * vehicle earns this story: it is the one the Coordinator works for (strategy gate
     * [DET-STRATEGY-GATE-001]); other cars never claim to be "watched". [isParked] = a fence is
     * watching its session; [viaBluetooth] = armed by the car's paired Bluetooth instead.
     */
    data class Watching(
        val vehicleName: String,
        val isParked: Boolean,
        val viaBluetooth: Boolean,
    ) : DetectionStory

    /** Nothing to say (non-parking vehicle, or no resolvable vehicle to talk about). */
    data object Hidden : DetectionStory
}

/**
 * Pure projection (state × trip meta × vehicles) → the one [DetectionStory] to render.
 * Lives here — not in the composable — so the precedence is unit-testable. [UX-DETECTION-STORY-001]
 */
fun resolveDetectionStory(
    uiState: DetectionUiState,
    drivingMeta: DrivingMeta?,
    vehicleCards: List<VehicleCard>,
): DetectionStory {
    val activeCard = vehicleCards.firstOrNull { it.vehicle.isActive }

    fun drivingStory(): DetectionStory {
        // Name the trip's own vehicle when the detector resolved it; fall back to the active one.
        val name = vehicleCards.firstOrNull { it.vehicle.id == drivingMeta?.vehicleId }
            ?.vehicle?.displayName()?.takeIf { it.isNotBlank() }
            ?: activeCard?.vehicle?.displayName()?.takeIf { it.isNotBlank() }
            ?: return DetectionStory.Hidden
        return DetectionStory.Driving(
            vehicleName = name,
            isCandidate = drivingMeta?.phase == DetectionPhase.Candidate,
        )
    }

    fun watchingStory(isParked: Boolean, viaBluetooth: Boolean): DetectionStory {
        // Watching names the ACTIVE vehicle ONLY — never a ranked or first-of-list fallback.
        val name = activeCard?.vehicle?.displayName()?.takeIf { it.isNotBlank() }
            ?: return DetectionStory.Hidden
        return DetectionStory.Watching(name, isParked = isParked, viaBluetooth = viaBluetooth)
    }

    return when (uiState) {
        DetectionUiState.BlockedCore -> DetectionStory.BlockedCore
        DetectionUiState.NoVehicle -> DetectionStory.NoVehicle
        DetectionUiState.Inactive -> DetectionStory.Inactive
        DetectionUiState.AwaitingFirstPark -> DetectionStory.AwaitingFirstPark
        DetectionUiState.Monitoring -> drivingStory()
        DetectionUiState.Parked -> watchingStory(isParked = true, viaBluetooth = false)
        DetectionUiState.ArmedBluetooth -> watchingStory(isParked = false, viaBluetooth = true)
        DetectionUiState.Silent -> DetectionStory.Hidden
    }
}
