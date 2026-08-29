@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.home

import com.rndeveloper.paparcar.domain.detection.DetectionPhase
import com.rndeveloper.paparcar.domain.detection.ports.DrivingRouteStore
import com.rndeveloper.paparcar.domain.detection.ParkingStrategy
import com.rndeveloper.paparcar.domain.location.LocationDataSource
import com.rndeveloper.paparcar.domain.location.UserLocationUi
import com.rndeveloper.paparcar.domain.matching.TrailMapMatcher
import com.rndeveloper.paparcar.domain.model.DetectionReadiness
import com.rndeveloper.paparcar.domain.model.DrivingPuck
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.domain.places.RoadNetworkDataSource
import com.rndeveloper.paparcar.domain.places.RoadWay
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.domain.util.haversineMeters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Everything the live trip contributes to Home's state, emitted as ONE atomic value so the map never
 * renders a half-updated trip (e.g. a cleared puck with a lingering matched trail — the old two-callback
 * split needed an explicit extra clear for that). [ROUTE-SNAP-001]
 */
data class TripUpdate(
    val puck: DrivingPuck?,
    val trail: List<GpsPoint>,
    val matchedTrail: List<GpsPoint>,
    val departurePoint: GpsPoint?,
) {
    companion object {
        /** No trip in progress — the map falls back to the native location dot. */
        val IDLE = TripUpdate(puck = null, trail = emptyList(), matchedTrail = emptyList(), departurePoint = null)
    }
}

/**
 * Self-contained owner of the two reactive pipelines that render the live trip on the home map:
 *
 * - **Driving puck**: subscribes the heading-aware high-accuracy location stream only while detection
 *   is actively monitoring a trip (battery-bounded), builds the top-down [DrivingPuck] tagged with the
 *   departing vehicle, accumulates the breadcrumb trail, and freezes the puck at the last driving fix
 *   during the Candidate phase (user walking away from the car). [MAP-ICONS-V2] [DET-PHASE-001]
 *   [DEPART-CONSISTENCY-001]
 *
 * - **Map-matching**: snaps the live trail onto OSM streets for free (Overpass) so the polyline follows
 *   the road instead of cutting across blocks from GPS drift. Roads are fetched once per trip bbox and
 *   the snap runs debounced off the main thread. No-op when no road source is wired (e.g. iOS).
 *   [ROUTE-SNAP-001]
 *
 * Built by Koin with its own collaborators (it observes the vehicle fleet, the active sessions and the
 * permission state itself — nothing is spoon-fed from `HomeState`) and exposes a single **cold**
 * [updates] flow. The ViewModel just collects it into `updateState`, staying the one writer of state
 * ("one sink"). No scope, no callbacks, no feedback providers.
 *
 * `observeDetectionReadiness` stays a provider lambda rather than the concrete use case: the use case
 * combines six collaborators and is not cheaply fakeable, so the functional seam keeps this controller
 * unit-testable by feeding [DetectionReadiness] directly.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeTripController(
    private val observeDetectionReadiness: () -> Flow<DetectionReadiness>,
    private val locationDataSource: LocationDataSource,
    // Free OSM map-matching of the trip trail. Nullable so platforms without a road source (iOS for
    // now) skip matching gracefully and keep the raw/smoothed trail. [ROUTE-SNAP-001]
    private val roadNetworkDataSource: RoadNetworkDataSource?,
    private val vehicleRepository: VehicleRepository,
    // The active parked sessions (0..N, one per parked vehicle). The trip's origin is re-resolved
    // from here when the service's in-memory departure point was lost to a process death mid-trip,
    // so the route is drawn from the spot the car actually left even after a cold app restart —
    // never from wherever the app happened to reopen. [DET-ROUTE-ORIGIN-002]
    private val userParkingRepository: UserParkingRepository,
    // The dense driving route the detection service records durably while tracking. Restored on a
    // fresh trail so the drawn line is the REAL trip — surviving background / cold-start mid-trip —
    // instead of a shortest-path reconstruction. Nullable: iOS has no platform store yet, and the
    // parked-spot seed remains the fallback. [DET-ROUTE-TRACK-001]
    private val drivingRouteStore: DrivingRouteStore?,
    private val permissionManager: PermissionManager,
    private val tag: String = TAG,
) {

    /**
     * Cold flow of the complete trip picture. All pipeline state (breadcrumb trail, matched trail,
     * road cache, freeze anchor) lives INSIDE the flow, scoped to one collection — nothing reads
     * `HomeState` back. `channelFlow` children all run on the collector's dispatcher (the VM's
     * Main.immediate), so the local mutable state below is confined to a single thread.
     */
    val updates: Flow<TripUpdate> = channelFlow {
        // Latest fleet, for tagging the puck with the departing vehicle's body/colour.
        var vehicles = emptyList<Vehicle>()
        // Last GPS fix seen while the trip was in the Driving phase — the spot the car stopped at. Used to
        // freeze the driving puck in place during the Candidate phase (user walking away from the car) and
        // reset when the trip ends. [DET-PHASE-001]
        var lastDrivingLocation: UserLocationUi? = null
        // The one source of truth for what has been emitted — each pipeline updates its slice and
        // re-sends the whole value, which is what makes updates atomic.
        var current = TripUpdate.IDLE

        // ── Map-matching state (free OSM snap-to-roads) ──────────────────────────
        // The origin + raw trail fed to the matcher; debounced so we snap off the hot path. Roads are
        // cached per trip bbox so a growing trip only refetches when it leaves the cached area. [ROUTE-SNAP-001]
        val trailForMatching = MutableStateFlow<List<GpsPoint>>(emptyList())
        var cachedRoads: List<RoadWay> = emptyList()
        var cachedRoadsBbox: Bbox? = null

        // The vehicle's currently-parked sessions — the durable, offline-first fallback for the trip
        // origin. The service publishes the departure point in memory at arm time, but a process
        // death mid-trip (aggressive OEM kill) wipes it; Room still holds the parked spot, so the
        // route origin survives a cold restart. [DET-ROUTE-ORIGIN-002]
        var activeSessions = emptyList<UserParking>()

        launch {
            vehicleRepository.observeVehicles().collect { vehicles = it }
        }
        launch {
            userParkingRepository.observeActiveSessions().collect { activeSessions = it }
        }

        // ── Map-matching pipeline ─────────────────────────────────────────────────
        // Snaps the live trail onto OSM streets (Overpass) so the polyline follows the road instead of
        // cutting across blocks from GPS drift. Any failure keeps the raw/smoothed trail. [ROUTE-SNAP-001]
        roadNetworkDataSource?.let { roadSource ->
            launch {
                trailForMatching
                    .debounce(MAP_MATCH_DEBOUNCE_MS.milliseconds)
                    .onEach { trail ->
                        if (trail.size < MIN_MATCH_POINTS) {
                            if (current.matchedTrail.isNotEmpty()) {
                                current = current.copy(matchedTrail = emptyList())
                                send(current)
                            }
                            return@onEach
                        }
                        val tight = boundingBox(trail, 0.0)
                        if (cachedRoadsBbox?.contains(tight) != true) {
                            val fetch = boundingBox(trail, ROADS_FETCH_MARGIN_DEG)
                            roadSource.getRoads(fetch.minLat, fetch.minLon, fetch.maxLat, fetch.maxLon)
                                .onSuccess { roads ->
                                    if (roads.isNotEmpty()) {
                                        cachedRoads = roads
                                        cachedRoadsBbox = fetch
                                    }
                                }
                                .onFailure { e -> PaparcarLogger.w(tag, "road fetch failed — keeping raw trail", e) }
                        }
                        if (cachedRoads.isEmpty()) {
                            PaparcarLogger.d(tag, "map-match: no roads for bbox — keeping raw trail")
                            return@onEach
                        }
                        val matched = withContext(Dispatchers.Default) { TrailMapMatcher.snap(trail, cachedRoads) }
                        PaparcarLogger.d(tag, "map-match: ${trail.size} pts → ${matched.size} snapped, roads=${cachedRoads.size} ways")
                        current = current.copy(matchedTrail = matched)
                        send(current)
                    }
                    .catch { e -> PaparcarLogger.w(tag, "map-matching error", e) }
                    .collect()
            }
        }

        // ── Driving puck pipeline ─────────────────────────────────────────────────
        // Live puck (own car, top-down, heading-rotated) — only while detection is actively monitoring
        // a trip. Subscribes the heading-aware high-accuracy stream just for that window
        // (battery-bounded). Null otherwise → the map falls back to the native location dot. [MAP-ICONS-V2]
        observeDetectionReadiness()
            .map { it as? DetectionReadiness.Monitoring }
            .distinctUntilChanged()
            .flatMapLatest { monitoring ->
                if (monitoring != null && permissionManager.permissionState.value.hasCorePermissions) {
                    locationDataSource.observeUiLocation().map { monitoring to it }
                } else {
                    flowOf<Pair<DetectionReadiness.Monitoring, UserLocationUi>?>(null)
                }
            }
            .collect { pair ->
                val puck = pair?.let { (monitoring, loc) ->
                    // Prefer the vehicle that actually departed (resolved by the service from the
                    // geofence-exit session, carried on Monitoring) over the "active vehicle" guess,
                    // so the puck shows the right car after the user switches active vehicles.
                    // [DEPART-CONSISTENCY-001]
                    val vehicle = monitoring.departingVehicleId
                        ?.let { vid -> vehicles.firstOrNull { it.id == vid } }
                        ?: monitoredVehicle(vehicles, monitoring.strategy)
                    // In the Candidate phase the user has stopped and is walking away from the car, so
                    // the puck must STAY at the spot the car stopped — not chase the pedestrian's GPS.
                    // Freeze it at the last driving fix; if the stop turns out NOT to be a park (the
                    // detector reverts to Driving), live tracking resumes from the current fix. [DET-PHASE-001]
                    val isCandidate = monitoring.phase == DetectionPhase.Candidate
                    val anchor = if (isCandidate) {
                        lastDrivingLocation ?: loc
                    } else {
                        loc.also { lastDrivingLocation = it }
                    }
                    DrivingPuck(
                        latitude = anchor.latitude,
                        longitude = anchor.longitude,
                        bearingDegrees = anchor.bearingDegrees,
                        accuracy = anchor.accuracy,
                        carbodyType = vehicle?.carbodyType,
                        sizeCategory = vehicle?.sizeCategory,
                        color = vehicle?.color,
                        vehicleId = vehicle?.id,
                        phase = monitoring.phase,
                    )
                }
                if (puck == null) {
                    // Trip ended — drop the live trail, the matched trail and the departure marker in a
                    // single atomic emission (the matched trail must NOT wait for the debounced matching
                    // pipeline to observe the emptied trailForMatching, or it lingers ~2.5 s). [ROUTE-SNAP-001]
                    lastDrivingLocation = null
                    trailForMatching.value = emptyList()
                    cachedRoads = emptyList()
                    cachedRoadsBbox = null
                    current = TripUpdate.IDLE
                    send(current)
                } else {
                    // Extend the breadcrumb only while driving — a frozen car (Candidate) contributes no
                    // new points, so the pedestrian walk is never drawn as the car's route. A NEW trip
                    // (empty trail) is seeded with the departing session's parked location when the
                    // service resolved one, so the route is drawn from the spot the car actually left
                    // even when detection woke mid-trip; the map-matcher snaps the parking→first-fix
                    // gap onto streets. [DET-ROUTE-ORIGIN-001]
                    val newTrail = if (puck.phase == DetectionPhase.Candidate) {
                        current.trail
                    } else {
                        val base = if (current.trail.isEmpty()) {
                            // A fresh trail (first fix / cold restart mid-trip). Rebuild the route
                            // from the durable sources so it is the REAL trip, not a reconstruction:
                            //  1. the parked spot as the backdated origin (service departure point, or
                            //     the vehicle's active session from Room — survives a process death —
                            //     or, when the verified departure already RELEASED that session
                            //     mid-trip, the vehicle's most recent previous parking: the car
                            //     still left from that pin [ROUTE-START-AT-CAR-001]),
                            //  2. the dense route the service recorded while tracking, restored from
                            //     disk so background / cold-start doesn't lose the driven path.
                            // The origin is prepended so the line still starts at the spot the car
                            // left; the map-matcher snaps the origin→first-recorded gap onto streets.
                            // Empty store (iOS / first trip) → just the seeded origin (the fallback).
                            // [DET-ROUTE-ORIGIN-002] [DET-ROUTE-TRACK-001]
                            val originHint = pair.first.departurePoint
                                ?: parkedOriginFor(puck.vehicleId, activeSessions)
                                ?: previousParkedOriginFor(puck.vehicleId)
                            val origin = backdatedOrigin(originHint, puck)
                            val recorded = freshRecordedRoute()
                            buildList {
                                origin?.let { add(it) }
                                addAll(recorded)
                            }
                        } else {
                            current.trail
                        }
                        MapTrail.append(base, GpsPoint(puck.latitude, puck.longitude, puck.accuracy, 0L, 0f))
                    }
                    // The trip's origin: the seeded parking spot when present, else the first measured
                    // fix (first trip ever / manual start / rejected seed). [DET-ROUTE-ORIGIN-001]
                    val depart = newTrail.firstOrNull()
                    trailForMatching.value = newTrail
                    current = TripUpdate(puck = puck, trail = newTrail, matchedTrail = current.matchedTrail, departurePoint = depart)
                    send(current)
                }
            }
    }

    /**
     * The trip's backdated origin: the departing session's parked location (resolved by the service
     * from the geofence-exit/AR/sentry session and carried on [DetectionReadiness.Monitoring]), or
     * null when unknown or implausibly far from the live fix (stale session — better a short route
     * than an invented one). PRESENTATION-ONLY by construction: this synthetic point exists solely in
     * the assembled [TripUpdate]; the detection evidence pipeline reads measured fixes upstream and
     * never sees it. [DET-ROUTE-ORIGIN-001]
     */
    /**
     * The parked spot to backdate the trip origin to when the service's in-memory departure point is
     * gone (process death mid-trip). Reads the vehicle's active session from Room — durable and
     * offline-first. Matches the departing vehicle by id; falls back to the sole active session only
     * when the puck's vehicle is unresolved and exactly one car is parked (the single-car case).
     * Never guesses among multiple parked cars — better no seed than another car's spot. The 5 km
     * plausibility ceiling is applied by the caller via [backdatedOrigin]. [DET-ROUTE-ORIGIN-002]
     */
    /**
     * The service-recorded route, but only when it belongs to the CURRENT trip: its newest fix must
     * be recent. The store is cleared at confirm, so between trips it is empty; but an aborted trip
     * leaves its route until a genuine-new-trip gap-reset, and that stale route must not be drawn on
     * the next trip. A real in-progress trip's last fix is seconds/minutes old. [DET-ROUTE-TRACK-001]
     */
    private fun freshRecordedRoute(): List<GpsPoint> {
        val points = drivingRouteStore?.points().orEmpty()
        val last = points.lastOrNull() ?: return emptyList()
        val ageMs = kotlin.time.Clock.System.now().toEpochMilliseconds() - last.timestamp
        return if (last.timestamp > 0L && ageMs in 0..RECORDED_ROUTE_FRESHNESS_MS) points else emptyList()
    }

    private fun parkedOriginFor(vehicleId: String?, sessions: List<UserParking>): GpsPoint? {
        val byVehicle = vehicleId?.let { vid -> sessions.firstOrNull { it.vehicleId == vid } }
        val session = byVehicle ?: sessions.singleOrNull()?.takeIf { vehicleId == null }
        return session?.location
    }

    /**
     * [ROUTE-START-AT-CAR-001] The origin when the vehicle's session is no longer ACTIVE: on a
     * healthy trip the verified departure released the spot minutes after driving off, so a cold
     * restart mid-trip finds no active session — but the car still left from its most recent pin.
     * Vehicle-scoped only (never guesses among cars); the caller's 5 km plausibility ceiling
     * ([backdatedOrigin]) rejects a stale cross-town pin.
     */
    private suspend fun previousParkedOriginFor(vehicleId: String?): GpsPoint? {
        vehicleId ?: return null
        return runCatching {
            userParkingRepository.getPreviousSession(
                vehicleId = vehicleId,
                beforeTimestamp = kotlin.time.Clock.System.now().toEpochMilliseconds(),
            )
        }.getOrNull()?.location
    }

    private fun backdatedOrigin(parkedAt: GpsPoint?, fix: DrivingPuck): GpsPoint? {
        parkedAt ?: return null
        val gapMeters = haversineMeters(parkedAt.latitude, parkedAt.longitude, fix.latitude, fix.longitude)
        if (gapMeters > MAX_BACKDATED_ORIGIN_METERS) {
            PaparcarLogger.w(tag, "backdated origin rejected — ${gapMeters.toInt()} m from live fix (stale session?)")
            return null
        }
        // The gap is THE metric of how late detection woke — worth surfacing per trip. [DET-ROUTE-ORIGIN-001]
        PaparcarLogger.i(tag, "trip origin backdated to parked spot — woke ${gapMeters.toInt()} m into the trip")
        return parkedAt
    }

    /** Lat/lon bounding box of [points] padded by [marginDeg] degrees on every side. */
    private fun boundingBox(points: List<GpsPoint>, marginDeg: Double): Bbox {
        val lats = points.map { it.latitude }
        val lons = points.map { it.longitude }
        return Bbox(
            minLat = lats.min() - marginDeg,
            minLon = lons.min() - marginDeg,
            maxLat = lats.max() + marginDeg,
            maxLon = lons.max() + marginDeg,
        )
    }

    private data class Bbox(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double) {
        fun contains(o: Bbox): Boolean =
            o.minLat >= minLat && o.minLon >= minLon && o.maxLat <= maxLat && o.maxLon <= maxLon
    }

    /**
     * The vehicle the active detection strategy is following — so the puck shows the right car.
     * Under [ParkingStrategy.BLUETOOTH] that's the BT-paired vehicle (detected regardless of which
     * is primary), otherwise the primary/active one. Mirrors [ParkingStrategyResolver.strategyFor].
     * [MAP-ICONS-V2]
     */
    private fun monitoredVehicle(vehicles: List<Vehicle>, strategy: ParkingStrategy): Vehicle? =
        when (strategy) {
            ParkingStrategy.BLUETOOTH ->
                vehicles.firstOrNull { it.bluetoothDeviceId != null } ?: vehicles.firstOrNull { it.isActive }
            else ->
                vehicles.firstOrNull { it.isActive } ?: vehicles.firstOrNull()
        }

    private companion object {
        const val TAG = "HomeTripController"

        // Map-matching: debounce the snap so a growing trail doesn't re-match every fix; min points to
        // bother snapping; how much to pad the road-fetch bbox so a growing trip rarely refetches.
        const val MAP_MATCH_DEBOUNCE_MS = 2500L
        const val MIN_MATCH_POINTS = 3
        const val ROADS_FETCH_MARGIN_DEG = 0.004 // ~400 m around the trip bbox [ROUTE-SNAP-001]

        // Plausibility ceiling for seeding the trail with the parked-spot origin: beyond this the
        // session is presumed stale and the trip falls back to first-fix origin. [DET-ROUTE-ORIGIN-001]
        const val MAX_BACKDATED_ORIGIN_METERS = 5_000.0

        // A recorded route whose newest fix is older than this predates the current trip (leftover
        // from a previous aborted drive) and must not be restored onto the live line. [DET-ROUTE-TRACK-001]
        const val RECORDED_ROUTE_FRESHNESS_MS = 30 * 60_000L
    }
}
