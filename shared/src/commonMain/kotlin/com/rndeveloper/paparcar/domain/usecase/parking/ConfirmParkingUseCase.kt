@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.parking

import com.apptolast.customlogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.detection.ArmEvidence
import com.rndeveloper.paparcar.domain.detection.assertionBlocksRelocation
import com.rndeveloper.paparcar.domain.detection.DrivingRoute
import com.rndeveloper.paparcar.domain.detection.ports.DrivingRouteStore
import com.rndeveloper.paparcar.domain.detection.fence.VehicleFenceOwnershipPolicy
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.domain.sensor.DetectionStepAnchors
import com.rndeveloper.paparcar.domain.util.PolylineCodec
import com.rndeveloper.paparcar.domain.util.haversineMeters
import com.rndeveloper.paparcar.domain.service.DepartureEventBus
import com.rndeveloper.paparcar.domain.service.GeofenceManager
import com.rndeveloper.paparcar.domain.service.ParkingEnrichmentScheduler
import com.rndeveloper.paparcar.domain.service.ParkingSyncScheduler
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
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
    // Optional: seals the hardware step-counter baseline at confirm time so the honest-close
    // ladder (and the safety net) can measure the step budget from the moment of parking — a
    // 2-min hop beats the worker's first tick. Nullable for test doubles / platforms without a
    // step counter. [DET-HONEST-CLOSE-001]
    private val detectionStepAnchors: DetectionStepAnchors? = null,
    // Optional: the dense route the service recorded on the drive to this park. Snapshotted onto the
    // saved parking (local + remote) and cleared here so the next trip starts fresh. Nullable for
    // test doubles / platforms without a route store. [DET-ROUTE-TRACK-001]
    private val drivingRouteStore: DrivingRouteStore? = null,
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
        /** [DET-ASSERTION-OUTRANKS-INFERENCE-001] Did the confirming session's own stream witness
         *  SUSTAINED driving? Deliberately separate from [tripMaxSpeedMps], which is a PEAK: one
         *  5,33 m/s sample out of 25 cleared `minimumTripSpeedMps` on 2026-08-24 and walked the
         *  repark guard straight past a pin the user had asserted three minutes earlier. Null when
         *  the caller has no session provenance; then only the peak test applies, as before. */
        sessionSawDriving: Boolean? = null,
        /** Confirmation path that placed this pin — which trigger put the parking ("steps+egress",
         *  "safety_net_backfill", "bt", "manual", …). Persisted + synced for provenance. [DET-PIN-PROVENANCE-001] */
        detectionPath: String? = null,
        /** Non-null → this is an APPROXIMATE ZONE (honest close), stamped on the saved session so
         *  the UI renders an AREA of this radius, not a precise pin. Null = exact point (normal).
         *  [DET-HONEST-CLOSE-001] */
        zoneRadiusMeters: Float? = null,
        /** WHERE the user's body is at confirm time — the origin the step-counter baseline is
         *  sealed at, deliberately WITHOUT a default so every caller decides: for an egress
         *  confirm the body is already 100+ m from the pin, and sealing "at the pin" made a later
         *  walk home read as a ride (field 2026-07-22, Glorieta). Null = caller has no honest
         *  position → the seal stores no origin and the honest close refuses the verdict
         *  (conservative silence). [DET-STEP-BUDGET-ORIGIN-001] */
        sealPoint: GpsPoint?,
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

        // ── Assertion guard [DET-ASSERTION-OUTRANKS-INFERENCE-001] ────────────
        // Narrower sibling of the repark guard below, and the one the "Sí" of a prompt reaches.
        // That answer carries reliability 1.0 — the user's word about the FACT of being parked —
        // but a position the MACHINE chose, so it must not be the thing that waves the guard
        // through. Field 2026-08-24 20:51, Oppo/Calle Fragua: 2 min 53 s after the user confirmed
        // `a9709e31` (acc 1,25 m), a second "Sí" planted `195e72f1` 14 m away and deactivated the
        // first. The guard below could not stop it twice over — reliability 1.0 bypassed it, and
        // the session's PEAK speed (5,33 m/s, a single fix out of 25) cleared `minimumTripSpeedMps`
        // as well. So this one reads sustained driving, and asks the shared predicate.
        //
        // Same exemptions as the repark guard, on purpose: no session provenance
        // ([tripMaxSpeedMps] null → BT, manual, external callers) and verified arms pass through.
        // A hand-placed pin never arrives here at all — it carries `SpotType.MANUAL_REPORT`.
        val assertionGuardApplies = spotType == SpotType.AUTO_DETECTED &&
            tripMaxSpeedMps != null &&
            !ArmEvidence.isVerifiedLabel(armEvidence) &&
            !(sessionSawDriving ?: false)

        // ── Repark-plausibility guard [DET-SOLID-001] ─────────────────────────
        // Last line of defense, independent of which detection path confirmed: an AUTO_DETECTED
        // confirm that would REPLACE a recent nearby active session, where the confirming session
        // never observed driving AND the arm was not externally verified, is more likely a
        // pedestrian false positive (walk-away re-park) than a real re-park. Reject so the
        // coordinator can degrade to a user prompt. Bypassed by: user confirmation
        // (reliability 1.0), manual/BT paths (no provenance → tripMaxSpeedMps null), verified
        // arms, real driving in-session, distance, or age.
        val reparkGuardApplies = spotType == SpotType.AUTO_DETECTED &&
            detectionReliability < config.reliabilityUserConfirmed &&
            tripMaxSpeedMps != null && tripMaxSpeedMps < config.minimumTripSpeedMps &&
            !ArmEvidence.isVerifiedLabel(armEvidence)

        // ONE read for both: they interrogate the SAME row — the pin this vehicle already holds —
        // with two different rules, and neither may run without it. Read here rather than inside
        // each guard so the confirm path that needs neither (manual, BT, verified arms) still
        // touches the repository zero times.
        val previousActive = if (assertionGuardApplies || reparkGuardApplies) {
            userParkingRepository.getActiveSessionByVehicle(resolvedVehicleId)
        } else {
            null
        }

        if (assertionGuardApplies && previousActive != null && assertionBlocksRelocation(
                pinReliability = previousActive.detectionReliability,
                pinLocation = previousActive.location,
                candidate = location,
                nowMs = Clock.System.now().toEpochMilliseconds(),
                sessionSawDriving = false,
                userConfirmedReliability = config.reliabilityUserConfirmed,
                freshWindowMs = config.reparkPlausibilityWindowMs,
                radiusMeters = config.reparkPlausibilityRadiusMeters,
            )
        ) {
            PaparcarLogger.w(
                DIAG,
                "  ⊘ the user already asserted this car's position — an inference does not " +
                    "depose it; keeping the existing pin [DET-ASSERTION-OUTRANKS-INFERENCE-001]"
            )
            return Result.failure(PaparcarError.Parking.ImplausibleRepark)
        }

        if (reparkGuardApplies && previousActive != null) {
            val ageMs = Clock.System.now().toEpochMilliseconds() - previousActive.location.timestamp
            val distanceM = haversineMeters(
                previousActive.location.latitude, previousActive.location.longitude,
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
        // [DET-ROUTE-TRACK-001] Snapshot the driven route the service recorded on the way here onto
        // this parking, encoded compactly (local + remote). Freshness-gated: only a route whose last
        // fix is recent belongs to THIS trip — a stale one lingering from a previous aborted trip
        // (the store is cleared on confirm, not on abort) must not be attached to this park. BT parks
        // never tracked a drive, so their store is empty → no route (the honest result).
        // [ROUTE-QUALITY-001] The trip's true ORIGIN is this vehicle's previous parking — the
        // store's first element is only the first fix the tracker saw after arming, typically
        // already hundreds of metres into the drive (field 2026-08-10: the stored line began on the
        // A-491, ~500 m past the real spot). Prepend it, plausibility-capped, so the stored route
        // starts where the car actually left from (the polyline carries lat/lon only — the origin's
        // old timestamp is irrelevant).
        // [ROUTE-START-AT-CAR-001] "Previous parking" must NOT require the session to still be
        // ACTIVE: on a healthy trip the verified departure released the spot (and deactivated the
        // session) minutes after driving off, so an active-only lookup returns null at every
        // properly-detected consecutive park and the seed only ever fired when departure detection
        // had FAILED (field 2026-08-17 23:57: route born 130 m past the previous pin). The car does
        // not move on its own — its most recent pin, active or released, is where this trip began;
        // the plausibility cap below still rejects a stale cross-town origin.
        // [ROUTE-END-AT-CAR-001] The route is the DRIVING route and must END at the pin: the store
        // keeps sampling while the user walks away with GPS live, so the raw buffer is trimmed to
        // the anchor's fix (the measured end of driving) and capped with the pin as final vertex.
        val routeOrigin = (
            userParkingRepository.getActiveSessionByVehicle(resolvedVehicleId)
                ?: userParkingRepository.getPreviousSession(resolvedVehicleId, gpsPoint.timestamp)
            )?.location
        val routePolyline = encodeFreshRoute(nowMs = gpsPoint.timestamp, origin = routeOrigin, anchor = location)

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
            zoneRadiusMeters = zoneRadiusMeters,
            routePolyline = routePolyline,
        )

        PaparcarLogger.d(DIAG, "  → saveNewParkingSession BEFORE sessionId=$sessionId")
        val saved = userParkingRepository.saveNewParkingSession(session)
        PaparcarLogger.d(DIAG, "  ← saveNewParkingSession AFTER isSuccess=${saved.isSuccess}")
        if (saved.isFailure) {
            PaparcarLogger.e(DIAG, "  ✗ saveNewParkingSession failed", saved.exceptionOrNull())
            return Result.failure(PaparcarError.Parking.SaveFailed)
        }

        // [DET-ROUTE-TRACK-001] The route is now durable on the parking → clear the live store so the
        // NEXT trip starts fresh. Cleared here (route consumed) rather than on abort, so a spurious
        // job end mid-trip can't wipe an in-progress route the user is still driving.
        runCatching { drivingRouteStore?.clear() }
            .onFailure { e -> PaparcarLogger.w(DIAG, "  ⚠ route store clear failed (continuing): ${e.message}") }

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

        // [DET-HONEST-CLOSE-001] Seal the hardware step-counter baseline for THIS park's fence
        // (geofenceId == sessionId) so the step budget is measurable from the moment of parking —
        // the honest-close ladder on a 2-min hop can't wait for the safety net's first tick. Also
        // benefits the safety net (baseline present immediately). geofenceId == sessionId here.
        // [DET-STEP-BUDGET-ORIGIN-001] The seal records the BODY's position (sealPoint), not the
        // pin's — the origin any walked-vs-rode displacement must be measured from.
        runCatching { detectionStepAnchors?.seal(sessionId, sealPoint) }
            .onFailure { e -> PaparcarLogger.w(DIAG, "  ⚠ step-anchor seal failed (continuing)", e) }

        // The user has now parked at least once → the cold-start nudge has served its purpose and
        // self-disables for good. [DET-TOGGLE-002]
        appPreferences?.setHasConfirmedFirstPark()

        // A confirmed park ANSWERS any pending "where did you leave your car?" question — every
        // confirm path (auto, manual, nudge deep-link) resolves the durable record here. The tray
        // notification is dismissed by Home's reactive janitor / the nudge's own auto-cancel.
        // [DET-NUDGE-PERSIST-001]
        appPreferences?.clearPendingParkNudge()

        PaparcarLogger.d(DIAG, "■ ConfirmParking.invoke SUCCESS (notif is caller's responsibility)")
        return Result.success(session)
    }

    /**
     * The recorded driving route as an encoded polyline, or null when there is none fresh enough to
     * belong to this trip. Requires ≥2 points and a last fix within [ROUTE_FRESHNESS_MS] of
     * [nowMs] — a route whose newest fix is older than that is a leftover from a previous aborted
     * trip, not the drive that just ended here. [DET-ROUTE-TRACK-001]
     *
     * [ROUTE-END-AT-CAR-001] The stored line is trimmed to [anchor] (the pin being saved): fixes
     * the store recorded after the anchor's fix are the user's walk away from the parked car, not
     * the drive, and the anchor caps the line as its final vertex — see [DrivingRoute.endAtAnchor].
     */
    private fun encodeFreshRoute(nowMs: Long, origin: GpsPoint?, anchor: GpsPoint): String? {
        val recorded = drivingRouteStore?.points().orEmpty()
        val lastRecorded = recorded.lastOrNull() ?: return null
        val fresh = recorded.size >= 2 && lastRecorded.timestamp > 0L &&
            (nowMs - lastRecorded.timestamp) in 0..ROUTE_FRESHNESS_MS
        if (!fresh) {
            PaparcarLogger.d(DIAG, "  route store not fresh (${recorded.size} pts, last ${(nowMs - lastRecorded.timestamp) / 1000}s old) — no route attached")
            return null
        }
        val points = DrivingRoute.endAtAnchor(recorded, anchor)
        if (points.size < 2) return null
        if (points.size != recorded.size) {
            PaparcarLogger.d(DIAG, "  route tail trimmed to the parking anchor (${recorded.size} → ${points.size} pts) [ROUTE-END-AT-CAR-001]")
        }
        // [ROUTE-QUALITY-001] Prepend the previous parking as the trip's true origin. Plausibility
        // mirrors Home's live prepend: an origin further than the ceiling from the first tracked fix
        // belongs to some other story (stale session, cross-town restart) — never stretch the line
        // to it. Within a stone's throw of the first fix it adds nothing.
        val first = points.first()
        val seeded = if (origin != null) {
            val gapM = haversineMeters(origin.latitude, origin.longitude, first.latitude, first.longitude)
            if (gapM in MIN_ORIGIN_PREPEND_METERS..MAX_ORIGIN_PREPEND_METERS) {
                PaparcarLogger.d(DIAG, "  route origin seeded from previous parking (${gapM.toInt()}m before first fix) [ROUTE-QUALITY-001]")
                listOf(origin) + points
            } else {
                points
            }
        } else {
            points
        }
        // [ROUTE-GAP-HONEST-001] A "route" shorter than a real drive is the wake-up's own handful
        // of fixes, not a trip (field 2026-08-14 22:51: a safety-net backfill pin carried a 40 m
        // 5-point stub whose origin marker sat next to the destination). No measured drive → no
        // route attached; the post-park worker reconstructs trusted routeless pins (backfill /
        // manual / user / nudge) pin-to-pin, marked inferred. [ROUTE-MANUAL-PIN-INFERRED-001]
        val extentMeters = (1 until seeded.size).sumOf {
            haversineMeters(
                seeded[it - 1].latitude, seeded[it - 1].longitude,
                seeded[it].latitude, seeded[it].longitude,
            )
        }
        if (extentMeters < MIN_ROUTE_EXTENT_METERS) {
            PaparcarLogger.d(DIAG, "  route too short to be a drive (${extentMeters.toInt()}m < ${MIN_ROUTE_EXTENT_METERS.toInt()}m) — no route attached [ROUTE-GAP-HONEST-001]")
            return null
        }
        return PolylineCodec.encode(seeded).ifEmpty { null }
    }

    private companion object {
        const val DIAG = "PARKDIAG/Confirm"
        const val POOR_ACCURACY_WARN_METERS = 50f

        /** A recorded route whose newest fix is older than this at confirm time predates this trip
         *  (leftover from a previous aborted drive) → not attached. A real arrival's last fix is
         *  seconds old; the ceiling only rejects stale carry-over. [DET-ROUTE-TRACK-001] */
        const val ROUTE_FRESHNESS_MS = 30 * 60_000L

        /** Origin-prepend plausibility window [ROUTE-QUALITY-001]: below the floor the previous
         *  parking is effectively the first fix already (nothing to add); above the ceiling it is
         *  another story entirely (stale session, restart across town) — mirrors Home's live
         *  backdated-origin ceiling (MAX_BACKDATED_ORIGIN_METERS). */
        const val MIN_ORIGIN_PREPEND_METERS = 15.0
        const val MAX_ORIGIN_PREPEND_METERS = 5_000.0

        /** A recorded route whose total length is below this is not a drive — it is the wake-up's
         *  own fixes around the parked car (a backfill/sentry stub) and attaching it would draw a
         *  fake origin next to the pin. [ROUTE-GAP-HONEST-001] */
        const val MIN_ROUTE_EXTENT_METERS = 150.0
    }
}
