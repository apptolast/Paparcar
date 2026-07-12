package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.detection.DetectionPhase
import io.apptolast.paparcar.domain.detection.ParkingStrategy
import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.location.UserLocationUi
import io.apptolast.paparcar.domain.matching.TrailMapMatcher
import io.apptolast.paparcar.domain.model.DetectionReadiness
import io.apptolast.paparcar.domain.model.DrivingPuck
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.places.RoadNetworkDataSource
import io.apptolast.paparcar.domain.places.RoadWay
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
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
    private val permissionManager: PermissionManager,
    private val userParkingRepository: UserParkingRepository,
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
        // Last parking location seen while a session was active — reused as the faded "departure point"
        // once the car leaves and a trip starts (the session is already gone by then). [TRIP-TRAIL-001]
        var lastParkingLocation: GpsPoint? = null
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

        launch {
            vehicleRepository.observeVehicles().collect { vehicles = it }
        }
        launch {
            userParkingRepository.observeActiveSessions().collect { sessions ->
                sessions.firstOrNull()?.let { lastParkingLocation = it.location }
            }
        }

        // ── Map-matching pipeline ─────────────────────────────────────────────────
        // Snaps the live trail onto OSM streets (Overpass) so the polyline follows the road instead of
        // cutting across blocks from GPS drift. Any failure keeps the raw/smoothed trail. [ROUTE-SNAP-001]
        roadNetworkDataSource?.let { roadSource ->
            launch {
                trailForMatching
                    .debounce(MAP_MATCH_DEBOUNCE_MS)
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
                    // new points, so the pedestrian walk is never drawn as the car's route. Anchor the
                    // origin dot to the departing vehicle's spot: prefer the service-resolved departure
                    // point (on Monitoring), fall back to the last seen parking location. [DEPART-CONSISTENCY-001]
                    val newTrail = if (puck.phase == DetectionPhase.Candidate) {
                        current.trail
                    } else {
                        MapTrail.append(current.trail, GpsPoint(puck.latitude, puck.longitude, puck.accuracy, 0L, 0f))
                    }
                    val depart = pair.first.departurePoint ?: lastParkingLocation
                    // Feed the matcher the origin + trail so the snapped line starts at the parking
                    // spot (also fixes the straight parking→first-fix chord). [ROUTE-SNAP-001]
                    trailForMatching.value = (listOfNotNull(depart) + newTrail)
                    current = TripUpdate(puck = puck, trail = newTrail, matchedTrail = current.matchedTrail, departurePoint = depart)
                    send(current)
                }
            }
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
    }
}
