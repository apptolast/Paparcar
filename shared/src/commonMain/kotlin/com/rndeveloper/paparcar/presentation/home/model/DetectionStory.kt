package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.detection.DetectionPhase
import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.domain.model.VehicleMonitoringStatus
import com.rndeveloper.paparcar.domain.model.displayName
import com.rndeveloper.paparcar.domain.model.monitoringStatus
import com.rndeveloper.paparcar.presentation.home.DrivingMeta
import com.rndeveloper.paparcar.presentation.home.VehicleCard

/**
 * The SINGLE detection story Home tells in the expanded sheet — one voice for "what is the app
 * doing for me right now", instead of action pills that go mute in every happy state.
 * [UX-DETECTION-STORY-001, cures C6/H5 of UX-PARK-FLOW-001]
 *
 * Exactly one story at a time, resolved by [resolveDetectionStory] with a fixed precedence
 * (urgent → quiet): [BlockedCore] → [AwaitingAnswer] → [PendingAsk] → [NoVehicle] → [Inactive] →
 * [AwaitingFirstPark] → [Driving] → [Watching] → [Hidden]. The action stories keep the loud
 * accent-bar row; [Driving] and [Watching] render a discreet one-line status (no card) so the happy
 * path finally has a voice.
 *
 * [DET-ASK-STATE-001] The two stories where the app is WAITING ON THE USER — [AwaitingAnswer] and
 * [PendingAsk] — sit at the top together, and both are resolved HERE. Until this ticket the nudge
 * was arbitrated by an `if` inside the composable, i.e. outside the very precedence this doc
 * declares: the chain said one thing and the surface did another, and only one of the two was
 * testable. One question, one place.
 *
 * The collapsed peek keeps its own phase eyebrow ("EN RUTA"/"APARCANDO…"/"¿HAS APARCADO?") — that
 * is the voice of the CLOSED sheet; this story is the voice of the OPEN sheet. Same translated
 * words, no drift.
 */
sealed interface DetectionStory {
    /** CORE permission missing — the app barely works. Loud, error-toned action row. */
    data object BlockedCore : DetectionStory

    /**
     * [DET-ASK-STATE-001] A "did you park?" question is posted and still answerable. The row asks it
     * in-app with the same two answers as the tray notification, so a user who opens Paparcar during
     * the window can resolve it without ever going to the shade — the case that silently timed out
     * on 2026-07-25.
     *
     * The whole [PendingPromptWindow] rides along rather than a copy of its fields: the vehicle
     * name is carried verbatim from the notification that posted it (null = the generic wording),
     * and since [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] so are the instant it was asked and
     * the place it is about. Splitting them into three parameters here would be three chances for
     * the row to describe a different question than the tray does.
     */
    data class AwaitingAnswer(val window: PendingPromptWindow) : DetectionStory

    /**
     * [DET-NUDGE-PERSIST-001] An unanswered "where did you leave your car?" nudge. A lost parking
     * record outranks every upsell and every happy line; only a CORE block (where the app barely
     * works) and a LIVE question with a deadline still beat it.
     */
    data object PendingAsk : DetectionStory

    /** No vehicle registered — nothing to detect. Action row. */
    data object NoVehicle : DetectionStory

    /** Detection off (Settings flag or producer permissions) — one-tap activate. Action row. */
    data object Inactive : DetectionStory

    /** Coordinator cold-start — mark your spot or declare "I'm driving". Action row. */
    data object AwaitingFirstPark : DetectionStory

    /** A trip is being followed right now. [isCandidate] = stopped, confirming the spot.
     *  [viaBluetooth] = the trip vehicle's watch method, so the row wears its identity colour.
     *  [UI-COLOR-DOCTRINE-001] */
    data class Driving(
        val vehicleName: String,
        val isCandidate: Boolean,
        val viaBluetooth: Boolean = false,
    ) : DetectionStory

    /**
     * Discreet happy line — detection is armed and covering the ACTIVE vehicle. Only the active
     * vehicle earns this story: it is the one the Coordinator works for (strategy gate
     * [DET-STRATEGY-GATE-001]); other cars never claim to be "watched". [isParked] = a fence is
     * watching its session; [viaBluetooth] = armed by the car's paired Bluetooth instead.
     *
     * [watchBadge] makes the line HONEST: it only reads "Vigilando tu sitio" when the watch is
     * genuinely live ([ParkedWatchBadge.WATCHING]); a fragile setup warns, and a killed foreground
     * service degrades to a "reactivate" ask instead of a green lie. [DET-WATCH-HONEST-001]
     */
    data class Watching(
        val vehicleName: String,
        val isParked: Boolean,
        val viaBluetooth: Boolean,
        val watchBadge: ParkedWatchBadge = ParkedWatchBadge.WATCHING,
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
    // Honest watch health for the parked case — WATCHING when genuinely live, else fragile/interrupted.
    // Null (default) keeps the healthy "Vigilando" line, so callers that don't wire it don't regress.
    // [DET-WATCH-HONEST-001]
    parkedWatchBadge: ParkedWatchBadge? = null,
    /** [DET-ASK-STATE-001] The open "did you park?" question, or null when there is none to answer.
     *  Already filtered for expiry by `isPromptWindowOpen` upstream — this function decides
     *  precedence, not whether a clock has run out. */
    promptWindow: PendingPromptWindow? = null,
    /** [DET-NUDGE-PERSIST-001] An unanswered "where did you leave your car?" nudge is pending. */
    showParkNudge: Boolean = false,
): DetectionStory {
    val activeCard = vehicleCards.firstOrNull { it.vehicle.isActive }

    fun drivingStory(): DetectionStory {
        // Name the trip's own vehicle when the detector resolved it; fall back to the active one.
        val card = vehicleCards.firstOrNull { it.vehicle.id == drivingMeta?.vehicleId } ?: activeCard
        val name = card?.vehicle?.displayName()?.takeIf { it.isNotBlank() }
            ?: return DetectionStory.Hidden
        return DetectionStory.Driving(
            vehicleName = name,
            isCandidate = drivingMeta?.phase == DetectionPhase.Candidate,
            viaBluetooth = card.vehicle.monitoringStatus() is VehicleMonitoringStatus.Bluetooth,
        )
    }

    fun watchingStory(isParked: Boolean, badge: ParkedWatchBadge): DetectionStory {
        // Watching names the ACTIVE vehicle ONLY — never a ranked or first-of-list fallback.
        val card = activeCard ?: return DetectionStory.Hidden
        val name = card.vehicle.displayName().takeIf { it.isNotBlank() }
            ?: return DetectionStory.Hidden
        // The method is read off the vehicle itself so the row's identity colour can never disagree
        // with the garage. [UI-COLOR-DOCTRINE-001]
        return DetectionStory.Watching(
            name,
            isParked = isParked,
            viaBluetooth = card.vehicle.monitoringStatus() is VehicleMonitoringStatus.Bluetooth,
            watchBadge = badge,
        )
    }

    // A CORE block comes first even over the questions: with location off, neither answer can be
    // acted on and the app barely works. Below it, the two things the app is WAITING ON THE USER for
    // outrank everything the app wants to TELL the user — and a live question with a deadline
    // outranks a stale one. [DET-ASK-STATE-001]
    if (uiState == DetectionUiState.BlockedCore) return DetectionStory.BlockedCore
    if (promptWindow != null) return DetectionStory.AwaitingAnswer(promptWindow)
    if (showParkNudge) return DetectionStory.PendingAsk

    return when (uiState) {
        DetectionUiState.BlockedCore -> DetectionStory.BlockedCore
        DetectionUiState.NoVehicle -> DetectionStory.NoVehicle
        DetectionUiState.Inactive -> DetectionStory.Inactive
        DetectionUiState.AwaitingFirstPark -> DetectionStory.AwaitingFirstPark
        DetectionUiState.Monitoring -> drivingStory()
        // Honest: the parked line reflects whether the watch is really live. [DET-WATCH-HONEST-001]
        DetectionUiState.Parked ->
            watchingStory(isParked = true, badge = parkedWatchBadge ?: ParkedWatchBadge.WATCHING)
        // Bluetooth-armed is always covered by the receiver — honestly healthy.
        DetectionUiState.ArmedBluetooth ->
            watchingStory(isParked = false, badge = ParkedWatchBadge.WATCHING)
        DetectionUiState.Silent -> DetectionStory.Hidden
    }
}
