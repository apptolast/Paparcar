@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.domain.detection.ArmEvidence
import io.apptolast.paparcar.domain.detection.VehicleFenceOwnershipPolicy
import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.repository.ZoneRepository
import io.apptolast.paparcar.domain.util.haversineMeters
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.service.ParkingEnrichmentScheduler
import io.apptolast.paparcar.domain.service.ParkingSyncScheduler
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Persists a confirmed parking spot, registers a geofence, notifies the user,
 * and schedules background enrichment with geocoder address + POI data.
 *
 * All steps after [UserParkingRepository.saveNewParkingSession] are non-blocking:
 * - Enrichment is dispatched to [ParkingEnrichmentScheduler] (WorkManager on Android)
 *   and runs when network is available, with automatic retry.
 * - Geofence and notification fire immediately after the session is saved.
 */
@OptIn(ExperimentalUuidApi::class)
class ConfirmParkingUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val vehicleRepository: VehicleRepository,
    private val zoneRepository: ZoneRepository,
    private val geofenceService: GeofenceManager,
    private val enrichmentScheduler: ParkingEnrichmentScheduler,
    private val authRepository: AuthRepository,
    private val config: ParkingDetectionConfig,
    private val departureEventBus: DepartureEventBus,
    // Optional: marks the first confirmed park so the cold-start nudge self-disables. Nullable so the
    // existing use-case test doubles need no change — they don't exercise the nudge. [DET-TOGGLE-002]
    private val appPreferences: AppPreferences? = null,
    // Optional: retry channel for a failed geofence registration (janitor one-shot). Nullable for
    // the same test-double reason as appPreferences. [DET-SOLID-001]
    private val parkingSyncScheduler: ParkingSyncScheduler? = null,
    // Optional: diagnostics sink for the geofence-registration outcome. [DET-SOLID-001]
    private val detectionEventLogger: DetectionEventLogger? = null,
) {

    /**
     * Persists the parking spot, registers the geofence, schedules enrichment, and
     * resets [DepartureEventBus]. Pure data operation — the caller is responsible for
     * any user-facing notification (legacy `showParkingSaved` or REFACTOR-300's
     * unified `showParkingSavedConfirm` card with REVERT). This separation keeps the
     * use case single-purpose and lets each caller pick the right UX without a
     * boolean flag argument. [CONFIRM-NO-NOTIF-CLEANUP]
     */
    suspend operator fun invoke(
        location: GpsPoint,
        detectionReliability: Float,
        spotType: SpotType = SpotType.AUTO_DETECTED,
        sizeCategory: VehicleSize? = null,
        carbodyType: CarbodyType? = null,
        vehicleId: String? = null,
        /** Max GPS speed (m/s) the confirming detection session observed, or null when the caller
         *  has no session provenance (BT strategy, external callers). Feeds the repark guard. */
        tripMaxSpeedMps: Float? = null,
        /** Arm-evidence label of the confirming session (see [ArmEvidence] label constants).
         *  Verified labels bypass the repark guard. [DET-SOLID-001] */
        armEvidence: String? = null,
        /** Confirmation path that placed this pin — which trigger put the parking ("steps+egress",
         *  "safety_net_backfill", "bt", "manual", …). Persisted + synced for provenance. [DET-PIN-PROVENANCE-001] */
        detectionPath: String? = null,
    ): Result<UserParking> {
        PaparcarLogger.d(
            DIAG,
            "▶ ConfirmParking.invoke reliability=$detectionReliability spotType=$spotType vehicleId=$vehicleId"
        )

        PaparcarLogger.d(DIAG, "  → authRepository.getCurrentSession() BEFORE")
        val userId = authRepository.getCurrentSession()?.userId
            ?: run {
                PaparcarLogger.d(
                    DIAG,
                    "  ✗ getCurrentSession returned null — abort NotAuthenticated"
                )
                return Result.failure(PaparcarError.Auth.NotAuthenticated)
            }
        PaparcarLogger.d(DIAG, "  ← getCurrentSession AFTER userId=$userId")

        // Vehicle resolution:
        //   - explicit [vehicleId] → caller already knows which vehicle owns the session
        //     (BT strategy resolves it from the disconnected device address). Lookup must
        //     succeed; failing-to-resolve is a precondition violation, not a fallback case.
        //   - null → Coordinator-strategy or manual path: fall back to the user's default
        //     vehicle (legacy single-vehicle behaviour). [AUTH-001] [VEHICLE-SYNC-001]
        val vehicle = if (vehicleId != null) {
            PaparcarLogger.d(DIAG, "  → getVehicleById(userId, $vehicleId) BEFORE")
            vehicleRepository.getVehicleById(userId, vehicleId).also {
                PaparcarLogger.d(DIAG, "  ← getVehicleById AFTER vehicleId=${it?.id}")
            }
        } else {
            PaparcarLogger.d(DIAG, "  → getActiveVehicle(userId) BEFORE")
            vehicleRepository.getActiveVehicle(userId).also {
                PaparcarLogger.d(DIAG, "  ← getActiveVehicle AFTER vehicleId=${it?.id}")
            }
        }
        if (vehicle == null) {
            PaparcarLogger.e(DIAG, "  ✗ vehicle not resolvable (explicit=$vehicleId) — abort")
            return Result.failure(PaparcarError.Parking.NoDefaultVehicle)
        }

        val resolvedSizeCategory = sizeCategory ?: vehicle.sizeCategory
        val resolvedCarbodyType = carbodyType ?: vehicle.carbodyType
        val resolvedVehicleId = vehicle.id

        // Check if the parking location falls inside one of the user's private zones.
        // If so, the session is stored locally but the community Spot is never published.
        val matchedPrivateZoneId = zoneRepository.getPrivateZonesSnapshot().firstOrNull { zone ->
            haversineMeters(location.latitude, location.longitude, zone.lat, zone.lon) <= zone.radiusMeters
        }?.id
        PaparcarLogger.d(DIAG, "  privateZoneId=$matchedPrivateZoneId")

        // Private zone → HOME_GEOFENCE: the user is parking in their own saved private spot.
        // Only applies to AUTO_DETECTED — manual reports and explicit callers keep their type.
        val resolvedSpotType = if (spotType == SpotType.AUTO_DETECTED) {
            if (matchedPrivateZoneId != null) {
                PaparcarLogger.d(DIAG, "  private zone match zoneId=$matchedPrivateZoneId → HOME_GEOFENCE")
                SpotType.HOME_GEOFENCE
            } else {
                SpotType.AUTO_DETECTED
            }
        } else {
            spotType
        }

        // ── Repark-plausibility guard [DET-SOLID-001] ─────────────────────────
        // Last line of defense, independent of which detection path confirmed: an AUTO_DETECTED
        // confirm that would REPLACE a recent nearby active session, where the confirming session
        // never observed driving AND the arm was not externally verified, is more likely a
        // pedestrian false positive (walk-away re-park) than a real re-park. Reject so the
        // coordinator can degrade to a user prompt. Bypassed by: user confirmation
        // (reliability 1.0), manual/BT paths (no provenance → tripMaxSpeedMps null), verified
        // arms, real driving in-session, distance, or age.
        if (spotType == SpotType.AUTO_DETECTED &&
            detectionReliability < config.reliabilityUserConfirmed &&
            tripMaxSpeedMps != null && tripMaxSpeedMps < config.minimumTripSpeedMps &&
            !ArmEvidence.isVerifiedLabel(armEvidence)
        ) {
            val previous = userParkingRepository.getActiveSessionByVehicle(resolvedVehicleId)
            if (previous != null) {
                val ageMs = Clock.System.now().toEpochMilliseconds() - previous.location.timestamp
                val distanceM = haversineMeters(
                    previous.location.latitude, previous.location.longitude,
                    location.latitude, location.longitude,
                )
                if (ageMs < config.reparkPlausibilityWindowMs && distanceM < config.reparkPlausibilityRadiusMeters) {
                    PaparcarLogger.w(
                        DIAG,
                        "  ⊘ implausible repark — previous active ${ageMs / 1000}s old at ${distanceM.toInt()}m, " +
                            "session maxSpeed=${tripMaxSpeedMps}m/s (<${config.minimumTripSpeedMps}), evidence=$armEvidence [DET-SOLID-001]"
                    )
                    return Result.failure(PaparcarError.Parking.ImplausibleRepark)
                }
            }
        }

        val sessionId = Uuid.random().toString()
        val gpsPoint = GpsPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            speed = location.speed,
        )
        if (location.accuracy > POOR_ACCURACY_WARN_METERS) {
            PaparcarLogger.w(DIAG, "  ⚠ poor GPS accuracy=${location.accuracy}m (threshold=${POOR_ACCURACY_WARN_METERS}m) — spot position may be imprecise, geofence will be padded")
        }
        val session = UserParking(
            id = sessionId,
            userId = userId,
            vehicleId = resolvedVehicleId,
            location = gpsPoint,
            geofenceId = sessionId,
            isActive = true,
            detectionReliability = detectionReliability,
            spotType = resolvedSpotType,
            sizeCategory = resolvedSizeCategory,
            carbodyType = resolvedCarbodyType,
            privateZoneId = matchedPrivateZoneId,
            tripMaxSpeedMps = tripMaxSpeedMps,
            armEvidence = armEvidence,
            detectionPath = detectionPath,
        )

        PaparcarLogger.d(DIAG, "  → saveNewParkingSession BEFORE sessionId=$sessionId")
        val saved = userParkingRepository.saveNewParkingSession(session)
        PaparcarLogger.d(DIAG, "  ← saveNewParkingSession AFTER isSuccess=${saved.isSuccess}")
        if (saved.isFailure) {
            PaparcarLogger.e(DIAG, "  ✗ saveNewParkingSession failed", saved.exceptionOrNull())
            return Result.failure(PaparcarError.Parking.SaveFailed)
        }

        // Re-parking before the previous session ended (no confirmed departure) clears the old Room
        // row but would otherwise leave its geofence registered in Play Services (NEVER_EXPIRE) as an
        // ORPHAN — it then fires spurious GEOFENCE_EXITs that arm detection with nothing to release.
        // saveNewParkingSession returns the id of the session it just cleared; drop its geofence too.
        // geofenceId == sessionId for sessions created here, so the id doubles as the geofence id.
        saved.getOrNull()?.takeIf { it != sessionId }?.let { replacedId ->
            PaparcarLogger.d(DIAG, "  → removing replaced session's orphan geofence=$replacedId")
            geofenceService.removeGeofence(replacedId)
                .onFailure { e -> PaparcarLogger.w(DIAG, "    ⚠ removeGeofence($replacedId) failed (continuing)", e) }
        }

        // Clear the IN_VEHICLE_ENTER timestamp from the arrival trip so that departure
        // detection only triggers on a *new* IN_VEHICLE_ENTER that happens after parking
        // is saved. Without this reset, walking away from the car within the 30-min
        // vehicleEnterWindowMs would falsely confirm a departure. [BUG-WALK-DEPART-001]
        departureEventBus.reset()

        PaparcarLogger.d(DIAG, "  → enrichmentScheduler.schedule BEFORE")
        enrichmentScheduler.enqueueEnrichSession(sessionId, gpsPoint.latitude, gpsPoint.longitude)
        PaparcarLogger.d(DIAG, "  ← enrichmentScheduler.schedule AFTER")

        // [VEH-ACTIVE-FENCE-001] Only the active (or BT-paired) vehicle owns an OS geofence. An
        // inactive non-paired vehicle's session keeps its pin/TTL/safety-net but registers NO fence
        // — the swap re-creates it when the user declares this car active. Skipping here kills the
        // spurious-FGS noise (an inactive car's fence waking the FGS) at the source, not after.
        val ownsFence = VehicleFenceOwnershipPolicy.shouldOwnFence(
            vehicleIsActive = vehicle.isActive,
            isBluetoothPaired = vehicle.bluetoothDeviceId != null,
        )
        if (!ownsFence) {
            PaparcarLogger.d(DIAG, "  ⊘ inactive non-BT vehicle → no geofence by design [VEH-ACTIVE-FENCE-001]")
        } else {
            PaparcarLogger.d(DIAG, "  → geofenceService.createGeofence BEFORE")
            // Invariant: active session ⟺ registered geofence. The save is already durable; a failed
            // registration must not be silent (the departure would never be detected) — log loud and
            // schedule the janitor's one-shot restore, which re-registers from the active sessions.
            val geofenceRadius = config.geofenceRadiusFor(resolvedSizeCategory, gpsPoint.accuracy)
            geofenceService.createGeofence(
                geofenceId = sessionId,
                latitude = gpsPoint.latitude,
                longitude = gpsPoint.longitude,
                radiusMeters = geofenceRadius,
            ).onFailure { e ->
                PaparcarLogger.e(DIAG, "  ✗ createGeofence FAILED — active session without geofence; scheduling janitor restore [DET-SOLID-001]", e)
                runCatching { parkingSyncScheduler?.enqueueGeofenceRestore() }
                    .onFailure { se -> PaparcarLogger.e(DIAG, "    ✗ enqueueGeofenceRestore also failed", se) }
            }.let { result ->
                detectionEventLogger?.log(
                    DetectionEvent.GeofenceRegistration(
                        sessionId = sessionId,
                        timestampMs = gpsPoint.timestamp,
                        success = result.isSuccess,
                        radiusMeters = geofenceRadius,
                        location = gpsPoint,
                    )
                )
            }
            PaparcarLogger.d(DIAG, "  ← geofenceService.createGeofence AFTER")
        }

        // The user has now parked at least once → the cold-start nudge has served its purpose and
        // self-disables for good. [DET-TOGGLE-002]
        appPreferences?.setHasConfirmedFirstPark()

        PaparcarLogger.d(DIAG, "■ ConfirmParking.invoke SUCCESS (notif is caller's responsibility)")
        return Result.success(session)
    }

    private companion object {
        const val DIAG = "PARKDIAG/Confirm"
        const val POOR_ACCURACY_WARN_METERS = 50f
    }
}
