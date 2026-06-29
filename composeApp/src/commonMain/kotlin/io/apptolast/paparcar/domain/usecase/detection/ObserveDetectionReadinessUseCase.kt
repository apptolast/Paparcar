package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.detection.DetectionRuntimeState
import io.apptolast.paparcar.domain.detection.ParkingStrategy
import io.apptolast.paparcar.domain.detection.ParkingStrategyResolver
import io.apptolast.paparcar.domain.model.DetectionReadiness
import io.apptolast.paparcar.domain.model.DisabledReason
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.permissions.AppPermissionState
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.permissions.RequiredPermission
import io.apptolast.paparcar.domain.permissions.missingPermissions
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Single source of truth for the Home detection banner. Combines the vehicle fleet, active
 * parking sessions, permission state and detection runtime into one [DetectionReadiness]
 * stream. [DET-READY-001b]
 *
 * Precedence (first match wins): **Disabled → Blocked → Parked → Monitoring → Ready**.
 * - Disabled before everything: no point asking for permissions when nothing can be detected.
 *   This also covers the user turning auto-detection OFF in Settings (TURNED_OFF) — if you disabled
 *   it, we surface "activate detection", not a permission nag. [DET-TOGGLE-001]
 * - Blocked before Parked: surface a broken permission even while a car is parked, so the user
 *   knows departure detection won't fire.
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
        detectionRuntime.isRunning,
        appPreferences.observeAutoDetectParking(),
    ) { vehicles, sessions, permissions, isRunning, autoDetectEnabled ->
        resolve(vehicles, sessions, permissions, isRunning, autoDetectEnabled)
    }

    private fun resolve(
        vehicles: List<Vehicle>,
        sessions: List<UserParking>,
        permissions: AppPermissionState,
        isRunning: Boolean,
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

        // Any active session means the car is parked; prefer one with a geofence
        // (the "watching for departure" signal) for the banner payload.
        val parkedSession = sessions.firstOrNull { it.geofenceId != null } ?: sessions.firstOrNull()
        if (parkedSession != null) {
            return DetectionReadiness.Parked(parkedSession)
        }

        return if (isRunning) {
            DetectionReadiness.Monitoring(strategy)
        } else {
            DetectionReadiness.Ready(strategy)
        }
    }
}
