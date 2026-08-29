package com.rndeveloper.paparcar.domain.usecase.detection

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
 */
class ObserveDetectionReadinessUseCase(
    private val vehicleRepository: VehicleRepository,
    private val userParkingRepository: UserParkingRepository,
    private val permissionManager: PermissionManager,
    private val detectionRuntime: DetectionRuntimeState,
    private val strategyResolver: ParkingStrategyResolver,
    private val appPreferences: AppPreferences,
) {
    operator fun invoke(): Flow<DetectionReadiness> = combine(
        vehicleRepository.observeVehicles(),
        userParkingRepository.observeActiveSessions(),
        permissionManager.permissionState,
        // isRunning decides Monitoring vs Ready; trip + phase carry the Monitoring payload. Pre-combined
        // into one source so the outer combine stays within its 5-arg arity. [DEPART-CONSISTENCY-001] [DET-PHASE-001]
        combine(detectionRuntime.isRunning, detectionRuntime.trip, detectionRuntime.phase) { running, trip, phase ->
            Triple(running, trip, phase)
        },
        appPreferences.observeAutoDetectParking(),
    ) { vehicles, sessions, permissions, runtime, autoDetectEnabled ->
        resolve(vehicles, sessions, permissions, runtime.first, runtime.second, runtime.third, autoDetectEnabled)
    }

    private fun resolve(
        vehicles: List<Vehicle>,
        sessions: List<UserParking>,
        permissions: AppPermissionState,
        isRunning: Boolean,
        trip: TripContext?,
        phase: DetectionPhase,
        autoDetectEnabled: Boolean,
    ): DetectionReadiness {
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
        val followingTrip = isRunning && !trackedCarStillParked

        // The banner's parked payload is the SAME session Home focuses on, so the badge always
        // describes the car the user is looking at. [UI-PREFERRED-SESSION-RECENCY-001]
        val parkedSession = preferredParkingSession(sessions, vehicles)
        if (parkedSession != null && !followingTrip) {
            return DetectionReadiness.Parked(parkedSession)
        }

        return if (isRunning) {
            DetectionReadiness.Monitoring(
                strategy = strategy,
                departurePoint = trip?.departurePoint,
                departingVehicleId = trip?.departingVehicleId,
                phase = phase,
            )
        } else {
            DetectionReadiness.Ready(strategy)
        }
    }
}
