package com.rndeveloper.paparcar.domain.usecase.detection

import com.rndeveloper.paparcar.domain.bluetooth.BluetoothScanner
import com.rndeveloper.paparcar.domain.bluetooth.BtConnection
import com.rndeveloper.paparcar.domain.detection.DetectionPhase
import com.rndeveloper.paparcar.domain.detection.DetectionRuntimeState
import com.rndeveloper.paparcar.domain.detection.ParkingStrategy
import com.rndeveloper.paparcar.domain.detection.ParkingStrategyResolver
import com.rndeveloper.paparcar.domain.detection.TripContext
import com.rndeveloper.paparcar.domain.model.DetectionReadiness
import com.rndeveloper.paparcar.domain.model.DisabledReason
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.preferredParkingSession
import com.rndeveloper.paparcar.domain.permissions.AppPermissionState
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.domain.permissions.RequiredPermission
import com.rndeveloper.paparcar.domain.permissions.missingPermissions
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Single source of truth for the Home detection banner. Combines the vehicle fleet, active
 * parking sessions, permission state and detection runtime into one [DetectionReadiness]
 * stream. [DET-READY-001b]
 *
 * Precedence (first match wins): **Disabled → Blocked → Monitoring → Parked → Ready**.
 * - Disabled before everything: no point asking for permissions when nothing can be detected.
 *   This also covers the user turning auto-detection OFF in Settings (TURNED_OFF) — if you disabled
 *   it, we surface "activate detection", not a permission nag. [DET-TOGGLE-001]
 * - Blocked before everything else: surface a broken permission even while a car is parked, so the
 *   user knows departure detection won't fire.
 * - Monitoring before Parked, but ONLY for a trip whose own car has already left: another car
 *   sitting parked says nothing about whether this one is driving, while the tracked car's own live
 *   session means it has not measurably left yet. [DET-READY-TRIP-OVER-PARKED-001]
 *
 * **Both lanes can report a trip.** Monitoring used to mean "the Coordinator is running", so the
 * deterministic lane — the one that KNOWS which car you are in, by paired MAC — was the only one
 * that could not say it, and a whole drive in the BT car read as Ready/Parked. The two lanes meet
 * here and nowhere else: this use case reads the BT connection, the BT lane still writes no
 * Coordinator state. [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001]
 */
class ObserveDetectionReadinessUseCase(
    private val vehicleRepository: VehicleRepository,
    private val userParkingRepository: UserParkingRepository,
    private val permissionManager: PermissionManager,
    private val detectionRuntime: DetectionRuntimeState,
    private val strategyResolver: ParkingStrategyResolver,
    private val appPreferences: AppPreferences,
    private val bluetoothScanner: BluetoothScanner,
) {
    /**
     * What the two detection lanes are doing right now, pre-combined into one source so the outer
     * combine stays within its 5-arg arity.
     *
     * [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001] The BT set is here and not read on
     * demand for one reason: the ACL edge has to PUSH. `strategyFor` below could already answer
     * "connected to a paired car?", but only when somebody asks, and getting into the car makes no
     * other source emit — so the answer would arrive whenever something else happened to change.
     */
    private data class LaneSnapshot(
        val isRunning: Boolean,
        val trip: TripContext?,
        val phase: DetectionPhase,
        val btConnections: List<BtConnection>,
    )

    operator fun invoke(): Flow<DetectionReadiness> = combine(
        vehicleRepository.observeVehicles(),
        userParkingRepository.observeActiveSessions(),
        permissionManager.permissionState,
        // isRunning decides Monitoring vs Ready; trip + phase carry the Monitoring payload.
        // [DEPART-CONSISTENCY-001] [DET-PHASE-001]
        combine(
            detectionRuntime.isRunning,
            detectionRuntime.trip,
            detectionRuntime.phase,
            bluetoothScanner.observeConnectedPairedCars(),
        ) { running, trip, phase, btConnected -> LaneSnapshot(running, trip, phase, btConnected) },
        appPreferences.observeAutoDetectParking(),
    ) { vehicles, sessions, permissions, lanes, autoDetectEnabled ->
        resolve(vehicles, sessions, permissions, lanes, autoDetectEnabled)
    }

    private fun resolve(
        vehicles: List<Vehicle>,
        sessions: List<UserParking>,
        permissions: AppPermissionState,
        lanes: LaneSnapshot,
        autoDetectEnabled: Boolean,
    ): DetectionReadiness {
        val isRunning = lanes.isRunning
        val trip = lanes.trip
        if (vehicles.isEmpty()) {
            return DetectionReadiness.Disabled(DisabledReason.NO_VEHICLE)
        }

        val strategy = strategyResolver.strategyFor(vehicles)
        if (strategy == ParkingStrategy.NONE) {
            return DetectionReadiness.Disabled(DisabledReason.NON_PARKING_VEHICLE)
        }

        // User intent wins over permissions and parked state: if auto-detection is switched off,
        // Home shows the "activate detection" nudge rather than asking for permissions. [DET-TOGGLE-001]
        if (!autoDetectEnabled) {
            return DetectionReadiness.Disabled(DisabledReason.TURNED_OFF)
        }

        // GPS toggle off is, for the user, the same "location is off" problem as a missing
        // foreground-location permission — surface it as a CORE block so Home shows the red
        // "turn on location" row, instead of force-navigating to the permissions screen. [DET-READY-001i]
        val missing = buildSet {
            addAll(permissions.missingPermissions())
            if (!permissions.isLocationServicesEnabled) add(RequiredPermission.FOREGROUND_LOCATION)
        }
        if (missing.isNotEmpty()) {
            return DetectionReadiness.Blocked(missing)
        }

        // A tracked trip outranks the OTHER cars' parked sessions. Under multi-parking
        // [MULTI-PARKING-001] a second car sitting parked used to mask the trip entirely: the
        // banner stayed Parked, so Home never subscribed the live location stream and the whole
        // trip surface (driving puck, breadcrumb, drawn route, driver-follow camera) stayed dark
        // while detection itself worked and published the spot normally.
        //
        // Doctrine-safe because it asks about the TRACKED car, not about "any trip is running":
        // an arm at the car (AR ENTER waiting for ride proof) leaves that car's session active, so
        // it keeps reading Parked until the departure is CONFIRMED and its session cleared — the
        // banner never claims a drive that measured movement has not proved. Manual arms carry no
        // trip context, so they cannot attribute a car and keep the parked reading.
        // [DET-READY-TRIP-OVER-PARKED-001]
        val trackedVehicleId = trip?.departingVehicleId
        val trackedCarStillParked = trackedVehicleId == null ||
            sessions.any { it.vehicleId == trackedVehicleId }
        val coordinatorFollowing = isRunning && !trackedCarStillParked

        // [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001] The OTHER lane also follows trips,
        // and until now it could not say so: `DetectionRuntimeState` is the Coordinator's private
        // state, and under BLUETOOTH the Coordinator is suppressed by design — so the whole trip in
        // the paired car read as Ready/Parked, banner and driving puck included. The ACL link to a
        // paired MAC is the strongest "I am in THIS car" evidence the app has; the accident was that
        // only one lane owned the field that says it.
        //
        // Deliberately the SAME bar as the Coordinator gets, not an exception for being
        // deterministic: a car whose own session is still live has not measurably left, so it stays
        // Parked until the departure clears it. Connecting while still parked must not claim a drive.
        // [DET-READY-TRIP-OVER-PARKED-001]
        val btVehicleId = carYouAreIn(lanes.btConnections.filter { c -> vehicles.any { it.id == c.vehicleId } })
        val btFollowing = btVehicleId != null && sessions.none { it.vehicleId == btVehicleId }
        val followingTrip = coordinatorFollowing || btFollowing

        // The banner's parked payload is the SAME session Home focuses on, so the badge always
        // describes the car the user is looking at. [UI-PREFERRED-SESSION-RECENCY-001]
        val parkedSession = preferredParkingSession(sessions, vehicles)
        if (parkedSession != null && !followingTrip) {
            return DetectionReadiness.Parked(parkedSession)
        }

        // [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001] **Which lane noticed the trip and
        // WHICH CAR it is are two different questions**, and the second is answered by the strongest
        // evidence available, never by whoever spoke first. A connected MAC NAMES the car; the
        // coordinator INFERS it from a geofence exit or an AR boarding. When they disagree the app
        // has already chosen: `EvaluateBtArbitrationUseCase` makes a paired-car edge SUPERSEDE a live
        // coordinator session. The map has to say what detection believes — and the puck's glyph is
        // an IDENTITY ("which of my cars is this"), so painting the wrong one is not a cosmetic slip
        // but a false statement. [UI-COLOR-DOCTRINE-001]
        val followedVehicleId = if (btFollowing) btVehicleId else trip?.departingVehicleId

        // The coordinator's payload — its phase and its departure point — describes the car IT is
        // tracking. Once BT has named a different car, those are somebody else's facts: a foreign
        // `Candidate` would freeze this puck where another car stopped, and a foreign departure point
        // would draw this trip's origin dot at another car's kerb. When BT names the coordinator's
        // OWN car, or says nothing, the payload applies exactly as it always did — including the
        // manual arm that carries no trip context but still has a real phase.
        val payloadIsForeign = btFollowing && btVehicleId != trip?.departingVehicleId

        return if (isRunning || btFollowing) {
            DetectionReadiness.Monitoring(
                strategy = strategy,
                departurePoint = if (payloadIsForeign) null else trip?.departurePoint,
                departingVehicleId = followedVehicleId,
                phase = if (payloadIsForeign) DetectionPhase.Driving else lanes.phase,
            )
        } else {
            DetectionReadiness.Ready(strategy)
        }
    }

    /**
     * Which of the paired cars you are actually IN, when the fleet has more than one.
     * [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001]
     *
     * The detection lane never had this problem — `BluetoothConnectionReceiver` resolves the vehicle
     * from the event's own MAC, so a disconnect always pins the car that disconnected. It is only
     * this reading, which starts from a SET of live links, that has to pick one; the first version
     * did it with `firstOrNull` over the fleet, i.e. by repository order, which is a guess wearing a
     * function's clothes.
     *
     * **The car you got into LAST is the car you are in.** Two links can be up at once — you park the
     * van, walk to the car, and the van's head unit is still powered for a while — and the newer
     * `ACL_CONNECTED` is what separates them. The stamp is already on disk: the receiver writes
     * `recordConnected` and `markConnected` in the same breath.
     *
     * When recency cannot decide — a tie, or a link with no usable stamp sitting next to others —
     * this names NOBODY rather than picking one. A puck's glyph is an identity, so the wrong car is a
     * false statement about which car you are driving; no puck is merely less information. A single
     * live link needs no ordering and is returned whatever its stamp says.
     */
    private fun carYouAreIn(connections: List<BtConnection>): String? {
        if (connections.size <= 1) return connections.firstOrNull()?.vehicleId
        val stamped = connections.filter { it.connectedAtMs > 0L }.sortedByDescending { it.connectedAtMs }
        // An unstamped link could be the most recent one for all we know, so it does not lose: it
        // makes the whole ranking unusable.
        if (stamped.size != connections.size) return null
        val newest = stamped.first()
        if (stamped[1].connectedAtMs == newest.connectedAtMs) return null
        return newest.vehicleId
    }
}
