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
import io.apptolast.paparcar.domain.places.RoadNetworkDataSource
import io.apptolast.paparcar.domain.places.RoadWay
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

/**
 * Owns the two reactive pipelines that render the live trip on the home map:
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
 * Like [HomeGeocodingController], this controller is presentation-layer-agnostic: it never touches
 * [HomeState] or the [HomeViewModel]. It reads the pieces of state it needs through provider lambdas
 * and publishes results through callbacks, so the VM stays the single writer of state (preserving the
 * "one sink" invariant) and the controller stays trivial to reason about in isolation.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeTripController(
    private val scope: CoroutineScope,
    // A provider rather than the concrete ObserveDetectionReadinessUseCase: the use case combines six
    // collaborators, so a lambda seam keeps the controller unit-testable by feeding DetectionReadiness
    // directly — consistent with the other provider lambdas below.
    private val observeDetectionReadiness: () -> Flow<DetectionReadiness>,
    private val locationDataSource: LocationDataSource,
    // Free OSM map-matching of the trip trail. Nullable so platforms without a road source (iOS for
    // now) skip matching gracefully and keep the raw/smoothed trail. [ROUTE-SNAP-001]
    private val roadNetworkDataSource: RoadNetworkDataSource?,
    // Providers — the slices of HomeState the pipelines read (kept as lambdas so the controller never
    // depends on HomeState).
    private val vehicles: () -> List<Vehicle>,
    private val hasCorePermissions: () -> Boolean,
    private val currentTrail: () -> List<GpsPoint>,
    private val currentMatchedTrail: () -> List<GpsPoint>,
    // Callbacks — the VM translates these into a single updateState { copy(...) } write.
    private val onTrip: (puck: DrivingPuck?, trail: List<GpsPoint>, departure: GpsPoint?) -> Unit,
    private val onMatchedTrail: (List<GpsPoint>) -> Unit,
    private val tag: String = TAG,
) {

    // Last parking location seen while a session was active — reused as the faded "departure point"
    // once the car leaves and a trip starts (the session is already gone by then). [TRIP-TRAIL-001]
    private var lastParkingLocation: GpsPoint? = null

    // Last GPS fix seen while the trip was in the Driving phase — the spot the car stopped at. Used to
    // freeze the driving puck in place during the Candidate phase (user walking away from the car) and
    // reset when the trip ends. [DET-PHASE-001]
    private var lastDrivingLocation: UserLocationUi? = null

    // ── Map-matching (free OSM snap-to-roads) ───────────────────────────────────
    // The origin + raw trail fed to the matcher; debounced so we snap off the hot path. Roads are
    // cached per trip bbox so a growing trip only refetches when it leaves the cached area. [ROUTE-SNAP-001]
    private val trailForMatching = MutableStateFlow<List<GpsPoint>>(emptyList())
    private var cachedRoads: List<RoadWay> = emptyList()
    private var cachedRoadsBbox: Bbox? = null

    /** Launches both pipelines on [scope]. Call once from the VM's init. */
    fun start() {
        subscribeDrivingPuck()
        subscribeMapMatching()
    }

    /**
     * Remembers where the car is parked so the trip can show it as the faded departure point once the
     * session clears and driving begins. Fed by the VM's active-session subscription. [TRIP-TRAIL-001]
     */
    fun rememberParkingLocation(location: GpsPoint) {
        lastParkingLocation = location
    }

    /**
     * Drives the live driving puck (own car, top-down, heading-rotated) — only while detection is
     * actively monitoring a trip. Subscribes the heading-aware high-accuracy stream just for that
     * window (battery-bounded), tagging it with the active vehicle's body shape. Null otherwise →
     * the map falls back to the native location dot. [MAP-ICONS-V2]
     */
    private fun subscribeDrivingPuck() {
        observeDetectionReadiness()
            .map { it as? DetectionReadiness.Monitoring }
            .distinctUntilChanged()
            .flatMapLatest { monitoring ->
                if (monitoring != null && hasCorePermissions()) {
                    locationDataSource.observeUiLocation().map { monitoring to it }
                } else {
                    flowOf<Pair<DetectionReadiness.Monitoring, UserLocationUi>?>(null)
                }
            }
            .onEach { pair ->
                val puck = pair?.let { (monitoring, loc) ->
                    // Prefer the vehicle that actually departed (resolved by the service from the
                    // geofence-exit session, carried on Monitoring) over the "active vehicle" guess,
                    // so the puck shows the right car after the user switches active vehicles.
                    // [DEPART-CONSISTENCY-001]
                    val vehicle = monitoring.departingVehicleId
                        ?.let { vid -> vehicles().firstOrNull { it.id == vid } }
                        ?: monitoredVehicle(vehicles(), monitoring.strategy)
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
                    // Trip ended — drop the live trail, the matched trail and the departure marker.
                    lastDrivingLocation = null
                    trailForMatching.value = emptyList()
                    cachedRoads = emptyList()
                    cachedRoadsBbox = null
                    // matchedTrail is cleared immediately (in the same state write as the rest) rather
                    // than waiting for the debounced map-matching pipeline to observe the emptied
                    // trailForMatching — otherwise the snapped line lingers ~MAP_MATCH_DEBOUNCE_MS after
                    // the trip ends. [ROUTE-SNAP-001]
                    onTrip(null, emptyList(), null)
                    onMatchedTrail(emptyList())
                } else {
                    // Extend the breadcrumb only while driving — a frozen car (Candidate) contributes no
                    // new points, so the pedestrian walk is never drawn as the car's route. Anchor the
                    // origin dot to the departing vehicle's spot: prefer the service-resolved departure
                    // point (on Monitoring), fall back to the last seen parking location. [DEPART-CONSISTENCY-001]
                    val newTrail = if (puck.phase == DetectionPhase.Candidate) {
                        currentTrail()
                    } else {
                        TripTrail.append(currentTrail(), GpsPoint(puck.latitude, puck.longitude, puck.accuracy, 0L, 0f))
                    }
                    val depart = pair.first.departurePoint ?: lastParkingLocation
                    // Feed the matcher the origin + trail so the snapped line starts at the parking
                    // spot (also fixes the straight parking→first-fix chord). [ROUTE-SNAP-001]
                    trailForMatching.value = (listOfNotNull(depart) + newTrail)
                    onTrip(puck, newTrail, depart)
                }
            }
            .launchIn(scope)
    }

    /**
     * Snaps the live trail onto OSM streets for free (Overpass map-matching) so the polyline follows
     * the road instead of cutting across blocks from GPS drift. Roads are fetched once per trip bbox
     * (refetched only when the trip leaves the cached area) and the snap runs debounced off the main
     * thread. Any failure leaves the matched trail empty → the map keeps the raw/smoothed trail.
     * No-op when no road source is wired (e.g. iOS). [ROUTE-SNAP-001]
     */
    private fun subscribeMapMatching() {
        val roadSource = roadNetworkDataSource ?: return
        trailForMatching
            .debounce(MAP_MATCH_DEBOUNCE_MS)
            .onEach { trail ->
                if (trail.size < MIN_MATCH_POINTS) {
                    if (currentMatchedTrail().isNotEmpty()) onMatchedTrail(emptyList())
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
                onMatchedTrail(matched)
            }
            .catch { e -> PaparcarLogger.w(tag, "map-matching error", e) }
            .launchIn(scope)
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
