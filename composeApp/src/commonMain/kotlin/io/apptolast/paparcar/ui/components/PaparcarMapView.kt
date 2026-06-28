package io.apptolast.paparcar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swmansion.kmpmaps.core.AndroidMapProperties
import com.swmansion.kmpmaps.core.AndroidMarkerOptions
import com.swmansion.kmpmaps.core.AndroidUISettings
import com.swmansion.kmpmaps.core.CameraPosition
import com.swmansion.kmpmaps.core.Circle
import com.swmansion.kmpmaps.core.Coordinates
import com.swmansion.kmpmaps.core.GoogleMapsAnchor
import com.swmansion.kmpmaps.core.GoogleMapsMapStyleOptions
import com.swmansion.kmpmaps.core.Map
import com.swmansion.kmpmaps.core.MapProperties
import com.swmansion.kmpmaps.core.MapTheme
import com.swmansion.kmpmaps.core.MapType
import com.swmansion.kmpmaps.core.MapUISettings
import com.swmansion.kmpmaps.core.Marker
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.DrivingPuck
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkedVehicleSummary
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.domain.model.ZoneIcon

import io.apptolast.paparcar.presentation.map.CameraTarget
import io.apptolast.paparcar.presentation.util.SpotReliabilityUiState
import io.apptolast.paparcar.presentation.util.toReliabilityUiState
import io.apptolast.paparcar.presentation.util.zoneIconFor
import io.apptolast.paparcar.ui.theme.PapBlue
import io.apptolast.paparcar.ui.theme.PapForestDark
import io.apptolast.paparcar.ui.theme.PapGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

// ─────────────────────────────────────────────────────────────────────────────
// PaparcarMapView — reusable map surface
//
// Single entry point for every map screen: HomeScreen (FULL), AddFreeSpotScreen
// (POSITION_ONLY + animated pin), ParkingLocationScreen (READ_ONLY).
// Specific overlays (sheet, FAB column, search bar, glass surfaces) live in
// their host screens — this component only owns the map surface, markers,
// crosshair / center pin, loading state and camera animation.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bundles every flag that changes how `PaparcarMapView` renders or behaves.
 *
 * @property interactionMode Gates user gestures (drag, zoom, rotate). See
 *   [MapInteractionMode]. `READ_ONLY` also short-circuits the `onCameraMove`
 *   callback so callers never receive movement events.
 * @property showFreeSpotOverlays When `false`, spot and cluster markers are
 *   skipped. The parking ("my-car") marker is independent — it renders whenever
 *   `parkingLocation` is non-null, regardless of this flag.
 * @property centerPin When non-null, replaces the default crosshair indicator
 *   with a drop-in pin animation. Every variant shares the same white-teardrop
 *   molde + ground-shadow + bounce-on-camera-settle behaviour; only the inner
 *   silhouette varies: [CenterPinKind.Report] = "P" letter; [CenterPinKind.Parking]
 *   = inner disc + car glyph (MyVehicleMarker family); [CenterPinKind.Zone] =
 *   inner disc + chosen zone icon (ZoneMarker family).
 * @property initialCamera Seed camera position used on first composition when
 *   no live `userLocation` and no dynamic `cameraTarget` are available.
 * @property mapType Underlying tile style (NORMAL / SATELLITE / TERRAIN).
 * @property styleMode Selects between LIGHT and DARK Google Maps styles. `AUTO`
 *   resolves from `MaterialTheme.colorScheme.background.luminance()`.
 */
data class PaparcarMapConfig(
    val interactionMode: MapInteractionMode = MapInteractionMode.FULL,
    val showFreeSpotOverlays: Boolean = true,
    val centerPin: CenterPinKind? = null,
    val initialCamera: CameraTarget? = null,
    val mapType: MapType = MapType.TERRAIN,
    val styleMode: MapStyleMode = MapStyleMode.AUTO,
)

/**
 * Variant of the animated centre pin. Null in [PaparcarMapConfig.centerPin]
 * means "show the default crosshair indicator". Each subtype maps 1:1 to a
 * composable in PaparcarMapMarkers.kt.
 */
sealed class CenterPinKind {
    /** Outlined teardrop with a "P" inside — molde for the Reporting flow. */
    data object Report : CenterPinKind()
    /** Outlined teardrop with the parked-car silhouette inside — molde for the AddingParking flow. */
    data object Parking : CenterPinKind()
    /** Outlined teardrop with the user's chosen zone icon inside — molde for the AddingZone flow. */
    data class Zone(val icon: androidx.compose.ui.graphics.vector.ImageVector) : CenterPinKind()
}

/**
 * Controls which user gestures are allowed on the map.
 *
 *  - `FULL`: pan / zoom / rotate / tilt all enabled. Default for HomeScreen.
 *  - `POSITION_ONLY`: same gestures as FULL, but typically paired with
 *    `showFreeSpotOverlays = false` and a non-null `centerPin` so the
 *    user picks a location by moving the map underneath a fixed pin
 *    (Home Reporting / AddingZone flows).
 *  - `READ_ONLY`: every gesture disabled and `onCameraMove` is suppressed.
 *    Used for purely informational map renders (ParkingLocationScreen detail).
 */
enum class MapInteractionMode { FULL, POSITION_ONLY, READ_ONLY }

/**
 *  - `AUTO`: resolves DARK vs LIGHT from the current Material colour scheme.
 *  - `LIGHT` / `DARK`: forces the corresponding style regardless of theme.
 */
enum class MapStyleMode { AUTO, LIGHT, DARK }

// ── Crosshair / pulse animations ─────────────────────────────────────────────
private const val CROSSHAIR_SCALE_HIDDEN  = 0f
private const val CROSSHAIR_SCALE_AIMING  = 1.25f
private const val CROSSHAIR_SCALE_NORMAL  = 1f
private const val PULSE_INITIAL_ALPHA     = 0.28f
private const val PULSE_INITIAL_SCALE     = 0.6f
private const val PULSE_MAX_SCALE         = 1.7f
private const val PULSE_ANIM_MS           = 500
private const val LOADING_ARC_ANIM_MS     = 1100
private const val LOADING_FADE_MS         = 300

// ── Location indicator (canvas drawing) ──────────────────────────────────────
private val   LOCATION_INDICATOR_BOX_SIZE = 48.dp
private val   RING_CANVAS_SIZE            = 30.dp
private const val RING_RADIUS_FACTOR      = 0.38f
private const val RING_STROKE_DP          = 1.5f
private const val PULSE_STROKE_DP         = 1.2f
private const val SHADOW_EXTRA_STROKE     = 1.5f
private const val SHADOW_OFFSET_X         = 1f
private const val SHADOW_OFFSET_Y         = 1.5f
private const val SHADOW_RADIUS_OFFSET    = 0.5f
// Discreet crosshair: small centre dot + fine ring; the soft theme-aware colour (onSurfaceVariant)
// keeps it present but unobtrusive on the map. [MAP-ICONS-V2]
private const val CENTER_DOT_SHADOW_RADIUS_DP = 2.2f
private const val CENTER_DOT_RADIUS_DP    = 2.0f
private const val LOADING_ARC_SWEEP_ANGLE = 260f
private const val REPORT_MODE_SHADOW_ALPHA = 0.35f
private const val NORMAL_MODE_SHADOW_ALPHA = 0.22f

// ── Loading arc gradient stops ────────────────────────────────────────────────
private const val LOADING_GRADIENT_TAIL_START = 0.716f
private const val LOADING_GRADIENT_HEAD       = 0.721f
private const val LOADING_GRADIENT_CUTOFF     = 0.726f

// ── Zone radius circle overlay ───────────────────────────────────────────────
private const val ZONE_CIRCLE_FILL_ALPHA    = 0.07f
private const val ZONE_CIRCLE_STROKE_ALPHA  = 0.30f
private const val ZONE_CIRCLE_STROKE_DP    = 2f
// Saved-zone rings fade with the rest of the map when a pin/selection dims spots.
private const val ZONE_CIRCLE_DIM_FACTOR   = 0.5f

// ── Clustering degree thresholds ─────────────────────────────────────────────
private const val CLUSTER_ZOOM_LEVEL_13   = 13f
private const val CLUSTER_ZOOM_LEVEL_12   = 12f
private const val CLUSTER_ZOOM_LEVEL_11   = 11f
private const val CLUSTER_THRESHOLD_13    = 0.004
private const val CLUSTER_THRESHOLD_12    = 0.008
private const val CLUSTER_THRESHOLD_11    = 0.016
private const val CLUSTER_THRESHOLD_10    = 0.032

// ── Bounds-to-zoom mapping ────────────────────────────────────────────────────
private const val BOUNDS_DELTA_220M  = 0.002f
private const val BOUNDS_DELTA_550M  = 0.005f
private const val BOUNDS_DELTA_1100M = 0.010f
private const val BOUNDS_DELTA_2200M = 0.020f
private const val BOUNDS_DELTA_4500M = 0.040f
private const val ZOOM_STREET        = 17f
private const val ZOOM_CLOSE         = 16f
private const val ZOOM_DEFAULT       = 15f
private const val ZOOM_NEIGHBORHOOD  = 14f
private const val ZOOM_DISTRICT      = 13f
private const val ZOOM_WIDE          = 12f

// Per-marker metadata that travelled in Marker.title before — we now hold it
// off-marker so we can set Marker.title = null on every marker and suppress
// Google Maps' default info window (the "title + snippet" balloon shown on
// tap). Coordinates is a stable, value-equal key since we pass spot/zone lats
// and lons through unchanged.
private data class SpotMeta(
    val spotId: String,
    val sizeCategory: VehicleSize?,
    val enRouteCount: Int,
)

// ── Marker content IDs ──────────────────────────────────────────────────────
// Badge markers: contentId encodes vehicleId + sizeCategory + selection + dim
// state, so the bitmap cache stores one entry per vehicle×state×dim and
// kmpmaps regenerates the bitmap whenever any of those flips. [MAP-MARKERS-DIM-002]
private fun vehicleBadgeContentId(
    v: ParkedVehicleSummary,
    selected: Boolean,
    dim: Boolean = false,
): String {
    val state = when {
        selected -> "sel"
        dim      -> "dim"
        else     -> "nrm"
    }
    return "vehicle_badge_${v.vehicleId.take(8)}_${v.sizeCategory?.name ?: "def"}_$state"
}

private const val MARKER_MY_CAR          = "my_car"
private const val MARKER_MY_CAR_DIM      = "my_car_dim"
private const val MARKER_MY_CAR_SELECTED = "my_car_selected"

// Google Maps renders billboard markers by screen-Y when zIndex is equal,
// so the selected marker must carry an explicit higher zIndex to always
// render on top regardless of geographic position.
private const val MARKER_Z_INDEX_SELECTED = 1f
private const val MARKER_Z_INDEX_NORMAL   = 0f
private val SELECTED_MARKER_OPTIONS = AndroidMarkerOptions(zIndex = MARKER_Z_INDEX_SELECTED)
private val NORMAL_MARKER_OPTIONS   = AndroidMarkerOptions(zIndex = MARKER_Z_INDEX_NORMAL)
// Zone label is centred on the circle centre (0.5, 0.5) — not bottom-anchored — so
// it sits IN the middle of the radius ring instead of floating above it. Lowest
// zIndex so spot/vehicle markers always render on top. [ZONE-AREA-001]
private val ZONE_MARKER_OPTIONS = AndroidMarkerOptions(
    anchor = GoogleMapsAnchor(0.5f, 0.5f),
    zIndex = MARKER_Z_INDEX_NORMAL,
)

// Free-spot markers — one cached bitmap per (reliability tier × visual state).
// Tier (HIGH/MEDIUM/LOW/MANUAL) is baked into contentId so the rasterized marker
// colour matches the peek modal badge. [MAP-MARKERS-RELIABILITY-001]
// Dim state IS encoded in contentId (NRM vs DIM are separate cached bitmaps)
// because kmpmaps caches by contentId; if dim were applied post-rasterization
// via a Modifier.alpha, the cached bitmap would never refresh when dimSpots
// toggled without the list of Marker instances also changing. [MAP-MARKERS-DIM-002]
private const val MARKER_FREE_SPOT_PREFIX = "free_spot_"
// Largest distinct en-route count baked into a contentId; anything higher
// collapses to this bucket and renders as "9+". Keeps the bitmap-cache key set
// bounded while still giving each count 1..9 its own pill. [BOLT-MARKERS-001]
private const val EN_ROUTE_BUCKET_MAX = 10
private fun freeSpotContentId(
    tier: SpotReliabilityUiState,
    selected: Boolean,
    dim: Boolean,
    enRouteCount: Int = 0,
): String {
    val state = when {
        selected -> "sel"
        dim      -> "dim"
        else     -> "nrm"
    }
    // En-route overrides the tier colour (blue "reserved") so its key encodes
    // the count bucket instead of the tier; the cache stores one bitmap per
    // (bucket × state) rather than per (tier × state). [BOLT-MARKERS-001]
    return if (enRouteCount > 0) {
        "${MARKER_FREE_SPOT_PREFIX}er${enRouteCount.coerceAtMost(EN_ROUTE_BUCKET_MAX)}_$state"
    } else {
        "${MARKER_FREE_SPOT_PREFIX}${tier.name.lowercase()}_$state"
    }
}

// Alpha applied to dimmed markers — visible enough to deter duplicate
// reports, subordinate enough that the centre pin dominates.
private const val DIM_MARKER_ALPHA = 0.35f
private const val MARKER_CLUSTER     = "cluster"
private const val MARKER_CLUSTER_DIM = "cluster_dim"

// ── Location-active driving puck ──────────────────────────────────────────────
// kmpmaps 0.8.1 has no marker rotation, so heading is baked into the bitmap: the contentId carries
// the carbody + a heading bucket (every HEADING_BUCKET_DEG°) and the composable rotates by the
// bucket angle. Anchored centre so the car pivots around the coordinate. [MAP-ICONS-V2]
private const val LOCATION_ACTIVE_PREFIX = "loc_active_"
private const val HEADING_BUCKET_DEG = 5
private const val NO_HEADING_BUCKET = -1
private val LOCATION_MARKER_OPTIONS = AndroidMarkerOptions(
    anchor = GoogleMapsAnchor(0.5f, 0.5f),
    zIndex = MARKER_Z_INDEX_SELECTED,
)
private fun headingBucket(bearingDegrees: Float?): Int =
    if (bearingDegrees == null) NO_HEADING_BUCKET
    else (((bearingDegrees / HEADING_BUCKET_DEG).roundToInt() * HEADING_BUCKET_DEG) % 360 + 360) % 360
private fun locationActiveContentId(carbody: CarbodyType?, bucket: Int): String =
    "$LOCATION_ACTIVE_PREFIX${carbody?.name ?: "def"}_$bucket"

/**
 * Wraps non-focus marker content with a fixed [DIM_MARKER_ALPHA]. This is
 * only ever called from the `_dim` handler branches — `dim` is baked into
 * the marker's `contentId` by the list builder, so kmpmaps caches NRM and
 * DIM as separate bitmaps and refreshes the bitmap as soon as the contentId
 * flips (e.g. on selection change). [MAP-MARKERS-DIM-002]
 */
@Composable
private fun DimWrapper(content: @Composable () -> Unit) {
    Box(modifier = Modifier.alpha(DIM_MARKER_ALPHA)) {
        content()
    }
}
// Zone markers are keyed by iconKey: zones sharing the same icon reuse the
// same cached bitmap, which is correct since the visual is identical.
private const val MARKER_ZONE_PREFIX         = "zone_"
private const val CAMERA_MOVING_DEBOUNCE_MS  = 280L

// ── Clustering ───────────────────────────────────────────────────────────────
/** Zoom level at or above which spots are rendered as individual markers (no clustering). */
private const val ZOOM_CLUSTER_DISABLE = 14f

private data class SpotCluster(val lat: Double, val lon: Double, val spots: List<Spot>)

/** Degree threshold used to group nearby spots at a given zoom level. */
private fun clusterThresholdDeg(zoom: Float): Double = when {
    zoom >= ZOOM_CLUSTER_DISABLE -> 0.0
    zoom >= CLUSTER_ZOOM_LEVEL_13 -> CLUSTER_THRESHOLD_13
    zoom >= CLUSTER_ZOOM_LEVEL_12 -> CLUSTER_THRESHOLD_12
    zoom >= CLUSTER_ZOOM_LEVEL_11 -> CLUSTER_THRESHOLD_11
    else                          -> CLUSTER_THRESHOLD_10
}

/**
 * Greedy single-pass clustering: each spot seeds a cluster and absorbs
 * all remaining spots within [thresholdDeg] in both lat and lon.
 * Returns one [SpotCluster] per group; size-1 clusters are individual spots.
 */
private fun clusterSpots(spots: List<Spot>, thresholdDeg: Double): List<SpotCluster> {
    if (thresholdDeg == 0.0) {
        return spots.map { SpotCluster(it.location.latitude, it.location.longitude, listOf(it)) }
    }
    val remaining = spots.toMutableList()
    val clusters = mutableListOf<SpotCluster>()
    while (remaining.isNotEmpty()) {
        val seed = remaining.removeFirst()
        val group = mutableListOf(seed)
        val iter = remaining.iterator()
        while (iter.hasNext()) {
            val other = iter.next()
            if (abs(other.location.latitude - seed.location.latitude) < thresholdDeg &&
                abs(other.location.longitude - seed.location.longitude) < thresholdDeg
            ) {
                group.add(other)
                iter.remove()
            }
        }
        clusters.add(
            SpotCluster(
                lat = group.sumOf { it.location.latitude } / group.size,
                lon = group.sumOf { it.location.longitude } / group.size,
                spots = group,
            ),
        )
    }
    return clusters
}

// ─────────────────────────────────────────────────────────────────────────────
// Public composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Reusable Paparcar map surface.
 *
 * Behaviour is parameterised entirely through [config] — the same composable
 * powers HomeScreen (FULL + free-spot overlays), AddFreeSpotScreen
 * (POSITION_ONLY + animated center pin) and ParkingLocationScreen detail
 * (READ_ONLY).
 *
 * The host screen owns everything *around* the map: bottom sheets, FAB
 * columns, search bars, glass overlays, etc. This component owns only the
 * map tiles, markers, central indicator, loading state and camera animation.
 *
 * @param onSpotClick fires with the plain spot ID (extra title-encoded data
 *   is stripped before calling). Cluster taps are silently ignored.
 * @param onMyCarClick fires when the parked-car marker is tapped. Hosts wire
 *   this to select the parking peek modal (same affordance as the parked-car
 *   FAB on Home).
 * @param onCameraMove suppressed when [PaparcarMapConfig.interactionMode] is
 *   `READ_ONLY` — read-only callers should not need to react to camera moves
 *   they cannot trigger.
 * @param onMapReady fires once, when the underlying Google Map finishes its
 *   first load. Useful to dismiss loading overlays in callers.
 */
@Composable
fun PaparcarMapView(
    config: PaparcarMapConfig,
    spots: List<Spot>,
    userLocation: GpsPoint?,
    parkingLocation: GpsPoint?,
    modifier: Modifier = Modifier,
    /**
     * Live driving puck — when non-null the user's own car is drawn top-down at this location,
     * rotated to the heading, and the native location dot is suppressed. Non-null only while
     * detection is actively monitoring a trip. [MAP-ICONS-V2]
     */
    drivingPuck: DrivingPuck? = null,
    /**
     * Enriched parking sessions from [ObserveParkedVehiclesUseCase].
     * When non-empty, badge markers are rendered instead of the legacy
     * teardrop for the home screen. [parkingLocation] still drives
     * ParkingLocationScreen which does not have vehicle context.
     */
    parkedVehicles: List<ParkedVehicleSummary> = emptyList(),
    /** Vehicle size for the fallback single-parking marker (used by ParkingLocationScreen). */
    parkingVehicleSize: VehicleSize? = null,
    /** Vehicle body shape for the fallback single-parking marker — preferred over [parkingVehicleSize] when present. */
    parkingVehicleCarbody: io.apptolast.paparcar.domain.model.CarbodyType? = null,
    /** When false the fallback parking marker renders in the inactive/history palette. */
    parkingIsActive: Boolean = true,
    cameraTarget: CameraTarget? = null,
    /** User's saved habitual places. Rendered as circular markers with the chosen icon. */
    zones: List<Zone> = emptyList(),
    /** Camera lat/lon of the zone being added/edited — for the live radius preview circle. */
    previewZoneLat: Double? = null,
    previewZoneLon: Double? = null,
    previewZoneRadius: Float = Zone.DEFAULT_RADIUS_METERS,
    previewZoneIsPrivate: Boolean = false,
    selectedSpotId: String? = null,
    /**
     * Session ID of the parked vehicle currently selected, or null when no parking
     * is selected. Per-vehicle: only the marker whose `sessionId` matches this
     * value renders with the selected style (bypassing the [dimSpots] pass).
     * Other parked vehicles in [parkedVehicles] remain in their normal/dim state.
     * Replaces the legacy global `isMyCarSelected` boolean which incorrectly marked
     * every parked vehicle as selected when ANY one was selected. [MULTI-PARKING-001]
     */
    selectedSessionId: String? = null,
    reportMode: Boolean = false,
    isAnyItemSelected: Boolean = false,
    isLoading: Boolean = false,
    /**
     * When true, every non-focus marker (anything not routed to a `_sel`
     * contentId via [selectedSpotId] or [selectedSessionId]) renders with
     * [DIM_MARKER_ALPHA] opacity. Dim is baked into the marker's contentId
     * by the list builder, so kmpmaps caches NRM and DIM as separate
     * bitmaps and the visual flips reliably whenever dimSpots toggles.
     * Used by the host to subordinate non-focus markers while the user has
     * a selection or is positioning a new spot (Home's Reporting mode +
     * selection states). [MAP-MARKERS-DIM-002]
     */
    dimSpots: Boolean = false,
    onSpotClick: (String) -> Unit = {},
    onMyCarClick: (sessionId: String) -> Unit = {},
    onZoneClick: (zoneId: String) -> Unit = {},
    onCameraMove: (lat: Double, lon: Double) -> Unit = { _, _ -> },
    onMapReady: () -> Unit = {},
) {
    val isReadOnly = config.interactionMode == MapInteractionMode.READ_ONLY

    // ── Clustering ───────────────────────────────────────────────────────────
    var currentZoom by remember { mutableStateOf(ZOOM_DEFAULT) }

    val clusters = remember(spots, currentZoom, config.showFreeSpotOverlays) {
        if (config.showFreeSpotOverlays) {
            clusterSpots(spots, clusterThresholdDeg(currentZoom))
        } else {
            emptyList()
        }
    }

    // ── Build markers list ───────────────────────────────────────────────────
    // Coords-keyed side maps replace the per-marker title encoding. Each is
    // remembered against the same keys as the markers list so a stale entry
    // can't leak: any marker still in `markers` is guaranteed to have a meta
    // entry, and removed markers' meta is also dropped.
    val spotMetaByCoords: Map<Coordinates, SpotMeta> = remember(clusters) {
        buildMap {
            clusters.forEach { cluster ->
                if (cluster.spots.size == 1) {
                    val spot = cluster.spots.first()
                    put(
                        Coordinates(spot.location.latitude, spot.location.longitude),
                        SpotMeta(
                            spot.id,
                            spot.sizeCategory,
                            spot.enRouteCount,
                        ),
                    )
                }
            }
        }
    }
    val clusterCountByCoords: Map<Coordinates, Int> = remember(clusters) {
        clusters.filter { it.spots.size > 1 }
            .associate { Coordinates(it.lat, it.lon) to it.spots.size }
    }
    // Distinct en-route count buckets currently on the map — drives which
    // blue "reserved" pill bitmaps get registered as marker handlers. [BOLT-MARKERS-001]
    val enRouteBuckets: Set<Int> = remember(clusters) {
        buildSet {
            clusters.forEach { cluster ->
                if (cluster.spots.size == 1) {
                    val n = cluster.spots.first().enRouteCount
                    if (n > 0) add(n.coerceAtMost(EN_ROUTE_BUCKET_MAX))
                }
            }
        }
    }
    val zoneIdByCoords: Map<Coordinates, String> = remember(zones) {
        zones.associate { Coordinates(it.lat, it.lon) to it.id }
    }
    val sessionIdByCoords: Map<Coordinates, String> = remember(parkedVehicles) {
        parkedVehicles.associate { Coordinates(it.location.latitude, it.location.longitude) to it.sessionId }
    }

    // dim is encoded in the contentId of every non-selected marker (selected
    // markers always render at full opacity regardless of dimSpots). When
    // dimSpots flips, every non-selected marker gets a NEW contentId and
    // kmpmaps fetches the pre-dimmed bitmap from cache — that is the only
    // reliable way to refresh opacity, since kmpmaps caches the rasterised
    // bitmap by contentId. [MAP-MARKERS-DIM-002]
    val markers = remember(
        clusters, parkingLocation, parkedVehicles, selectedSpotId, selectedSessionId, zones, dimSpots,
        drivingPuck,
    ) {
        buildList {
            // Zone markers — added FIRST (lowest zIndex) so spot/parking markers
            // always render on top. Zones are never the selected marker, so they
            // route through the dim suffix uniformly.
            val zoneDimSuffix = if (dimSpots) "_dim" else "_nrm"
            zones.forEach { zone ->
                add(
                    Marker(
                        coordinates = Coordinates(zone.lat, zone.lon),
                        // title = null suppresses the Google Maps default info-window
                        // balloon. Zone ID is recovered via zoneIdByCoords in the
                        // click handler instead.
                        title = null,
                        // Per-zone × dim contentId: each zone has two cached bitmaps. [MAP-MARKERS-DIM-002]
                        contentId = "$MARKER_ZONE_PREFIX${zone.id}_${if (zone.isPrivate) "prv" else "pub"}$zoneDimSuffix",
                        androidMarkerOptions = ZONE_MARKER_OPTIONS,
                    ),
                )
            }
            // Non-selected parking markers first; selected parking appended last so it renders on top.
            // Selection is per-vehicle: only the marker whose sessionId matches
            // [selectedSessionId] gets the `_sel` contentId. The previous global
            // boolean made every parked vehicle render selected as soon as one was
            // tapped. [MULTI-PARKING-001]
            if (parkedVehicles.isNotEmpty()) {
                parkedVehicles.forEach { v ->
                    val selected = v.sessionId == selectedSessionId
                    add(
                        Marker(
                            coordinates = Coordinates(v.location.latitude, v.location.longitude),
                            title = null,
                            contentId = vehicleBadgeContentId(
                                v,
                                selected = selected,
                                dim = !selected && dimSpots,
                            ),
                            androidMarkerOptions = if (selected) SELECTED_MARKER_OPTIONS else NORMAL_MARKER_OPTIONS,
                        ),
                    )
                }
            } else {
                // Fallback: legacy teardrop (used by ParkingLocationScreen which
                // does not supply parkedVehicles). Only one possible marker here,
                // so any non-null selectedSessionId means "the single parking is selected".
                parkingLocation?.let {
                    val isSelected = selectedSessionId != null
                    val contentId = when {
                        isSelected -> MARKER_MY_CAR_SELECTED
                        dimSpots   -> MARKER_MY_CAR_DIM
                        else       -> MARKER_MY_CAR
                    }
                    add(
                        Marker(
                            coordinates = Coordinates(it.latitude, it.longitude),
                            title = null,
                            contentId = contentId,
                            androidMarkerOptions = if (isSelected) SELECTED_MARKER_OPTIONS else NORMAL_MARKER_OPTIONS,
                        ),
                    )
                }
            }
            clusters.forEach { cluster ->
                if (cluster.spots.size == 1) {
                    val spot = cluster.spots.first()
                    val selected = spot.id == selectedSpotId
                    val contentId = freeSpotContentId(
                        tier = spot.toReliabilityUiState(),
                        selected = selected,
                        dim = dimSpots,
                        enRouteCount = spot.enRouteCount,
                    )
                    add(
                        Marker(
                            coordinates = Coordinates(spot.location.latitude, spot.location.longitude),
                            title = null,
                            contentId = contentId,
                            androidMarkerOptions = if (selected) SELECTED_MARKER_OPTIONS else NORMAL_MARKER_OPTIONS,
                        ),
                    )
                } else {
                    add(
                        Marker(
                            coordinates = Coordinates(cluster.lat, cluster.lon),
                            title = null,
                            contentId = if (dimSpots) MARKER_CLUSTER_DIM else MARKER_CLUSTER,
                            androidMarkerOptions = NORMAL_MARKER_OPTIONS,
                        ),
                    )
                }
            }
            // Driving puck — own car, top-down, rotated to heading; added last (top zIndex). The
            // native location dot is suppressed while this is shown. [MAP-ICONS-V2]
            drivingPuck?.let { puck ->
                add(
                    Marker(
                        coordinates = Coordinates(puck.latitude, puck.longitude),
                        title = null,
                        contentId = locationActiveContentId(puck.carbodyType, headingBucket(puck.bearingDegrees)),
                        androidMarkerOptions = LOCATION_MARKER_OPTIONS,
                    ),
                )
            }
        }
    }

    // ── Custom marker composables ─────────────────────────────────────────────
    // Marker family [MAP-MARKERS-REDESIGN-001] / Bolt-green restyle [BOLT-MARKERS-001]:
    //   - VehicleBadgeMarker: light "tag" disc + full-colour vehicle silhouette, status ring (per vehicle×state×dim)
    //   - FreeSpotMarker: Bolt-green teardrop pin, colour+ring+badge per reliability tier
    //     (4 tiers × nrm/dim/sel), plus a blue en-route pill bitmap per count bucket
    //   - ZoneMarker: blue hexagon + 3-char zone code (per zone×dim)
    // Each non-selected content type registers TWO handlers — `_nrm` at full
    // alpha and `_dim` wrapped in [DimWrapper] — and the list builder picks
    // the contentId that matches the current dimSpots. This means the dim
    // state is baked into the rasterised bitmap (kmpmaps caches by contentId)
    // instead of being applied as a post-rasterisation Modifier.alpha, which
    // never refreshed reliably. [MAP-MARKERS-DIM-002]
    val customMarkerContent = remember(
        clusterCountByCoords, enRouteBuckets, parkedVehicles, zones,
        parkingVehicleSize, parkingVehicleCarbody, parkingIsActive, drivingPuck,
    ) {
        val baseHandlers: Map<String, @Composable (Marker) -> Unit> = buildMap {
            // ── Fallback single-parking marker (ParkingLocationScreen) ──
            put(MARKER_MY_CAR) { _ ->
                VehicleBadgeMarker(
                    sizeCategory = parkingVehicleSize,
                    carbodyType = parkingVehicleCarbody,
                    isActive = parkingIsActive,
                )
            }
            put(MARKER_MY_CAR_DIM) { _ ->
                DimWrapper {
                    VehicleBadgeMarker(
                        sizeCategory = parkingVehicleSize,
                        carbodyType = parkingVehicleCarbody,
                        isActive = parkingIsActive,
                    )
                }
            }
            put(MARKER_MY_CAR_SELECTED) { _ ->
                VehicleBadgeMarker(
                    selected = true,
                    sizeCategory = parkingVehicleSize,
                    carbodyType = parkingVehicleCarbody,
                    isActive = parkingIsActive,
                )
            }
            // ── Free-spot bitmaps — 12 cached variants (4 tiers × nrm/dim/sel) ──
            // Each (tier, state) pair gets its own contentId so kmpmaps caches a distinct
            // bitmap and the on-map color matches the peek modal badge. [MAP-MARKERS-RELIABILITY-001]
            SpotReliabilityUiState.entries.forEach { tier ->
                put(freeSpotContentId(tier, selected = false, dim = false)) { _ ->
                    FreeSpotWithOverlays(reliability = tier, selected = false)
                }
                put(freeSpotContentId(tier, selected = false, dim = true)) { _ ->
                    DimWrapper { FreeSpotWithOverlays(reliability = tier, selected = false) }
                }
                put(freeSpotContentId(tier, selected = true, dim = false)) { _ ->
                    FreeSpotWithOverlays(reliability = tier, selected = true)
                }
            }
            // ── En-route ("reserved") pills — one bitmap per present count bucket ──
            // Blue override carrying the people count; tier is irrelevant here so the
            // key is keyed by bucket only. [BOLT-MARKERS-001]
            enRouteBuckets.forEach { bucket ->
                put(freeSpotContentId(SpotReliabilityUiState.HIGH, selected = false, dim = false, enRouteCount = bucket)) { _ ->
                    FreeSpotWithOverlays(selected = false, enRouteCount = bucket)
                }
                put(freeSpotContentId(SpotReliabilityUiState.HIGH, selected = false, dim = true, enRouteCount = bucket)) { _ ->
                    DimWrapper { FreeSpotWithOverlays(selected = false, enRouteCount = bucket) }
                }
                put(freeSpotContentId(SpotReliabilityUiState.HIGH, selected = true, dim = false, enRouteCount = bucket)) { _ ->
                    FreeSpotWithOverlays(selected = true, enRouteCount = bucket)
                }
            }
            // ── Spot clusters ──
            put(MARKER_CLUSTER) { marker ->
                FreeSpotClusterMarker(count = clusterCountByCoords[marker.coordinates] ?: 0)
            }
            put(MARKER_CLUSTER_DIM) { marker ->
                DimWrapper {
                    FreeSpotClusterMarker(count = clusterCountByCoords[marker.coordinates] ?: 0)
                }
            }
        }

        // ── Per-vehicle badge bitmaps (one per vehicle × selected × dim) ──
        val vehicleHandlers: Map<String, @Composable (Marker) -> Unit> =
            parkedVehicles.flatMap { v ->
                listOf<Pair<String, @Composable (Marker) -> Unit>>(
                    vehicleBadgeContentId(v, selected = false, dim = false) to { _: Marker ->
                        VehicleBadgeMarker(
                            sizeCategory = v.sizeCategory,
                            carbodyType = v.carbodyType,
                            isActive = true,
                            stableRank = v.stableRank,
                            isBluetoothPaired = v.isBluetoothPaired,
                        )
                    },
                    vehicleBadgeContentId(v, selected = false, dim = true) to { _: Marker ->
                        DimWrapper {
                            VehicleBadgeMarker(
                                sizeCategory = v.sizeCategory,
                                carbodyType = v.carbodyType,
                                isActive = true,
                                stableRank = v.stableRank,
                                isBluetoothPaired = v.isBluetoothPaired,
                            )
                        }
                    },
                    vehicleBadgeContentId(v, selected = true) to { _: Marker ->
                        VehicleBadgeMarker(
                            selected = true,
                            sizeCategory = v.sizeCategory,
                            carbodyType = v.carbodyType,
                            isActive = true,
                            stableRank = v.stableRank,
                            isBluetoothPaired = v.isBluetoothPaired,
                        )
                    },
                )
            }.toMap()

        // ── Per-zone marker bitmaps (zone code is zone-specific × dim) ──
        val zoneHandlers: Map<String, @Composable (Marker) -> Unit> =
            zones.flatMap { zone ->
                val base = "$MARKER_ZONE_PREFIX${zone.id}_${if (zone.isPrivate) "prv" else "pub"}"
                listOf<Pair<String, @Composable (Marker) -> Unit>>(
                    "${base}_nrm" to { _: Marker ->
                        ZoneMarker(name = zone.name, icon = zoneIconFor(zone.iconKey), isPrivate = zone.isPrivate)
                    },
                    "${base}_dim" to { _: Marker ->
                        DimWrapper {
                            ZoneMarker(name = zone.name, icon = zoneIconFor(zone.iconKey), isPrivate = zone.isPrivate)
                        }
                    },
                )
            }.toMap()

        // ── Driving puck handler (one bitmap per carbody × heading bucket) ──
        val drivingHandler: Map<String, @Composable (Marker) -> Unit> = drivingPuck?.let { puck ->
            val bucket = headingBucket(puck.bearingDegrees)
            val angle = if (bucket == NO_HEADING_BUCKET) 0f else bucket.toFloat()
            mapOf(
                locationActiveContentId(puck.carbodyType, bucket) to { _: Marker ->
                    LocationActiveMarker(
                        carbody = puck.carbodyType,
                        size = puck.sizeCategory,
                        headingDegrees = angle,
                    )
                },
            )
        } ?: emptyMap()

        baseHandlers + vehicleHandlers + zoneHandlers + drivingHandler
    }

    // ── Track real camera center (set by the map, not by us) ──────────────
    var actualCamLat by remember { mutableStateOf<Float?>(null) }
    var actualCamLon by remember { mutableStateOf<Float?>(null) }

    // ── Camera movement detection (debounced 280ms) ──────────────────────
    var cameraMoving by remember { mutableStateOf(false) }
    LaunchedEffect(actualCamLat, actualCamLon) {
        if (actualCamLat != null) {
            cameraMoving = true
            delay(CAMERA_MOVING_DEBOUNCE_MS.milliseconds)
            cameraMoving = false
        }
    }

    // ── Loading state ────────────────────────────────────────────────────
    val backgroundColor = MaterialTheme.colorScheme.background
    val isThemeDark = backgroundColor.luminance() < 0.5f
    val resolvedDark = when (config.styleMode) {
        MapStyleMode.AUTO -> isThemeDark
        MapStyleMode.DARK -> true
        MapStyleMode.LIGHT -> false
    }
    var mapLoaded by remember { mutableStateOf(false) }
    val showLoading = !mapLoaded || isLoading

    // ── Crosshair animations (used when config.centerPin == null) ────────
    val crosshairScale by animateFloatAsState(
        targetValue = when {
            isAnyItemSelected -> CROSSHAIR_SCALE_HIDDEN   // hide when any item is focused
            cameraMoving      -> CROSSHAIR_SCALE_AIMING   // enlarge while aiming
            else              -> CROSSHAIR_SCALE_NORMAL
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "crosshair_scale",
    )
    // Pulse ring: fires once when camera settles
    val pulseAlpha = remember { Animatable(0f) }
    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(cameraMoving, showLoading) {
        if (!cameraMoving && actualCamLat != null && !isAnyItemSelected && !showLoading) {
            pulseAlpha.snapTo(PULSE_INITIAL_ALPHA)
            pulseScale.snapTo(PULSE_INITIAL_SCALE)
            launch { pulseAlpha.animateTo(0f, tween(PULSE_ANIM_MS, easing = FastOutSlowInEasing)) }
            launch { pulseScale.animateTo(PULSE_MAX_SCALE, tween(PULSE_ANIM_MS, easing = FastOutSlowInEasing)) }
        }
    }
    // Loading arc: spins only while content is being fetched; stops automatically when done
    val loadingAngle = remember { Animatable(0f) }
    LaunchedEffect(showLoading) {
        if (showLoading) {
            loadingAngle.snapTo(0f)
            loadingAngle.animateTo(
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(LOADING_ARC_ANIM_MS, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            )
        } else {
            loadingAngle.stop()
        }
    }

    val cameraPosition = rememberCameraAnimationState(
        cameraTarget = cameraTarget,
        userLocation = userLocation,
        initialCamera = config.initialCamera,
        actualCamLat = actualCamLat,
        actualCamLon = actualCamLon,
        actualCamZoom = currentZoom,
    )

    val loadingAlpha by animateFloatAsState(
        targetValue = if (showLoading && !isAnyItemSelected) 1f else 0f,
        animationSpec = tween(LOADING_FADE_MS),
        label = "loading_alpha",
    )

    // Zone radius rings as NATIVE map circles, rendered BELOW markers in the map's
    // own shape layer. Saved zones AND the live AddingZone preview use the SAME
    // native Circle, so the ring the user positions is pixel-identical to the one
    // that appears once saved (no Web-Mercator Canvas approximation that drifts from
    // Google's geodesic circle). [ZONE-AREA-001]
    val zoneRingPrimary = MaterialTheme.colorScheme.primary
    val zoneRingTertiary = MaterialTheme.colorScheme.tertiary
    val zoneRingStrokePx = with(LocalDensity.current) { ZONE_CIRCLE_STROKE_DP.dp.toPx() }
    val zoneRingDimFactor = if (dimSpots) ZONE_CIRCLE_DIM_FACTOR else 1f
    fun zoneCircle(lat: Double, lon: Double, radius: Float, isPrivate: Boolean, dim: Float): Circle {
        val base = if (isPrivate) zoneRingTertiary else zoneRingPrimary
        return Circle(
            center = Coordinates(lat, lon),
            radius = radius,
            color = base.copy(alpha = ZONE_CIRCLE_FILL_ALPHA * dim),
            lineColor = base.copy(alpha = ZONE_CIRCLE_STROKE_ALPHA * dim),
            lineWidth = zoneRingStrokePx,
        )
    }
    val zoneCircles = buildList {
        zones.forEach { add(zoneCircle(it.lat, it.lon, it.radiusMeters, it.isPrivate, zoneRingDimFactor)) }
        // Live preview while positioning a new/edited zone — identical native circle
        // so it coincides exactly with the saved result.
        if (previewZoneLat != null && previewZoneLon != null) {
            add(zoneCircle(previewZoneLat, previewZoneLon, previewZoneRadius, previewZoneIsPrivate, 1f))
        }
    }

    Box(modifier = modifier) {
        Map(
            modifier = Modifier.fillMaxSize(),
            cameraPosition = cameraPosition,
            properties = MapProperties(
                // Native location dot off while the custom driving puck owns the user's position.
                isMyLocationEnabled = drivingPuck == null,
                isTrafficEnabled = false,
                mapTheme = if (resolvedDark) MapTheme.DARK else MapTheme.LIGHT,
                mapType = config.mapType,
                androidMapProperties = AndroidMapProperties(
                    mapStyleOptions = GoogleMapsMapStyleOptions(
                        if (resolvedDark) DARK_MAP_STYLE else LIGHT_MAP_STYLE
                    ),
                ),
            ),
            uiSettings = MapUISettings(
                myLocationButtonEnabled = false,
                compassEnabled = false,
                scrollEnabled = !isReadOnly,
                zoomEnabled = !isReadOnly,
                rotateEnabled = !isReadOnly,
                togglePitchEnabled = !isReadOnly,
                androidUISettings = AndroidUISettings(
                    zoomControlsEnabled = false,
                    mapToolbarEnabled = false,
                ),
            ),
            markers = markers,
            circles = zoneCircles,
            customMarkerContent = customMarkerContent,
            onCircleClick = { circle ->
                // Circles carry no id, so we recover the zone by its centre coords
                // (the same coords-keyed lookup the marker tap uses). Marker taps
                // take native precedence over circle taps, so a tap that lands on a
                // spot/vehicle puck inside the ring still selects that puck, not the
                // zone — "spot gana". [ZONE-AREA-001]
                zoneIdByCoords[circle.center]?.let(onZoneClick)
            },
            onCameraMove = { pos ->
                actualCamLat = pos.coordinates.latitude.toFloat()
                actualCamLon = pos.coordinates.longitude.toFloat()
                currentZoom = pos.zoom
                if (!isReadOnly) {
                    onCameraMove(pos.coordinates.latitude, pos.coordinates.longitude)
                }
            },
            onMarkerClick = { marker ->
                // Marker.title is null for every marker we render (so Google
                // Maps suppresses the default info-window balloon), so we
                // route clicks purely off contentId + a coords-keyed lookup.
                val cid = marker.contentId
                when {
                    cid == MARKER_MY_CAR ||
                        cid == MARKER_MY_CAR_DIM ||
                        cid == MARKER_MY_CAR_SELECTED ||
                        cid?.startsWith("vehicle_badge_") == true ->
                        sessionIdByCoords[marker.coordinates]?.let(onMyCarClick)
                    cid?.startsWith(MARKER_ZONE_PREFIX) == true ->
                        zoneIdByCoords[marker.coordinates]?.let(onZoneClick)
                    cid == MARKER_CLUSTER || cid == MARKER_CLUSTER_DIM -> Unit // cluster taps are inert
                    cid?.startsWith(MARKER_FREE_SPOT_PREFIX) == true ->
                        spotMetaByCoords[marker.coordinates]?.let { onSpotClick(it.spotId) }
                    else -> Unit
                }
            },
            onMapLoaded = {
                if (!mapLoaded) {
                    mapLoaded = true
                    onMapReady()
                }
            },
        )

        // ── Loading overlay — hides partial map tiles; central pointer is the indicator ──
        AnimatedVisibility(
            visible = !mapLoaded,
            exit = fadeOut(animationSpec = tween(600)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor),
            )
        }

        // ── Center indicator: animated pin (pin-drop) OR crosshair ───────────
        // Report / Parking pins are positioned with `mapCenterPinAnchor` so the
        // pin's BOTTOM-centre — not its geometric centre — sits on the camera
        // target. That matches the (0.5, 1.0) anchor of placed markers, so the
        // dropped marker appears exactly where the centre pin was floating.
        //
        // Zone uses plain Center: its glyph indicates the centre of a radius
        // circle (an area), not a ground-anchored pin tip.
        val pinKind = config.centerPin
        if (pinKind != null) {
            when (pinKind) {
                CenterPinKind.Report -> ReportCenterPin(
                    cameraMoving = cameraMoving,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .mapCenterPinAnchor(),
                )
                CenterPinKind.Parking -> ParkingCenterPin(
                    cameraMoving = cameraMoving,
                    carbodyType = parkingVehicleCarbody,
                    sizeCategory = parkingVehicleSize,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .mapCenterPinAnchor(),
                )
                is CenterPinKind.Zone -> ZoneCenterPin(
                    icon = pinKind.icon,
                    cameraMoving = cameraMoving,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        } else {
            // Crosshair tracks the theme so it contrasts with the map tiles, but uses the softer
            // onSurfaceVariant (light: blue-dark grey #374460 / dark: cool grey #8EA0B4) rather than
            // the near-black onSurface, which read as invasive on light maps. [MAP-ICONS-V2]
            val indicatorColor = if (reportMode) PapGreen else MaterialTheme.colorScheme.onSurfaceVariant
            val shadowAlpha = if (reportMode) REPORT_MODE_SHADOW_ALPHA else NORMAL_MODE_SHADOW_ALPHA

            Box(
                modifier = Modifier
                    .size(LOCATION_INDICATOR_BOX_SIZE)
                    .align(Alignment.Center)
                    .scale(crosshairScale),
                contentAlignment = Alignment.Center,
            ) {
                // Pulse ring (expands outward on settle)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val r = (size.minDimension / 2f) * pulseScale.value
                    drawCircle(
                        color = indicatorColor.copy(alpha = pulseAlpha.value),
                        radius = r,
                        center = Offset(size.width / 2f, size.height / 2f),
                        style = Stroke(width = PULSE_STROKE_DP.dp.toPx()),
                    )
                }
                // Ring + center dot (+ loading arc when fetching content; only the default crosshair branch)
                Canvas(modifier = Modifier.size(RING_CANVAS_SIZE)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val ringRadius = size.minDimension * RING_RADIUS_FACTOR
                    val ringStroke = RING_STROKE_DP.dp.toPx()

                    // Shadow (subtle drop, offset down-right)
                    drawCircle(
                        color = Color.Black.copy(alpha = shadowAlpha),
                        radius = ringRadius + SHADOW_RADIUS_OFFSET,
                        center = Offset(cx + SHADOW_OFFSET_X, cy + SHADOW_OFFSET_Y),
                        style = Stroke(width = ringStroke + SHADOW_EXTRA_STROKE),
                    )
                    // Main ring
                    drawCircle(
                        color = indicatorColor,
                        radius = ringRadius,
                        center = Offset(cx, cy),
                        style = Stroke(width = ringStroke),
                    )
                    // Loading comet: gradient arc spinning while content loads.
                    // HEAD (opaque, fraction ≈0.721) leads clockwise; TAIL fades to transparent.
                    // Replaces CircularProgressIndicator — fades out when loading completes,
                    // then the pulse ring fires to signal "ready".
                    if (loadingAlpha > 0f) {
                        withTransform({ rotate(loadingAngle.value, pivot = Offset(cx, cy)) }) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    colorStops = arrayOf(
                                        0f                         to Color.Transparent,
                                        LOADING_GRADIENT_TAIL_START to indicatorColor.copy(alpha = 0.04f),
                                        LOADING_GRADIENT_HEAD       to indicatorColor,
                                        LOADING_GRADIENT_CUTOFF     to Color.Transparent,
                                        1f                         to Color.Transparent,
                                    ),
                                    center = Offset(cx, cy),
                                ),
                                startAngle = 0f,
                                sweepAngle = LOADING_ARC_SWEEP_ANGLE,
                                useCenter = false,
                                topLeft = Offset(cx - ringRadius, cy - ringRadius),
                                size = Size(ringRadius * 2, ringRadius * 2),
                                alpha = loadingAlpha,
                                style = Stroke(width = ringStroke + SHADOW_EXTRA_STROKE, cap = StrokeCap.Round),
                            )
                        }
                    }
                    // Center dot shadow
                    drawCircle(
                        color = Color.Black.copy(alpha = shadowAlpha),
                        radius = CENTER_DOT_SHADOW_RADIUS_DP.dp.toPx(),
                        center = Offset(cx + SHADOW_OFFSET_X / 2, cy + SHADOW_OFFSET_Y / 2),
                    )
                    // Center dot
                    drawCircle(
                        color = indicatorColor,
                        radius = CENTER_DOT_RADIUS_DP.dp.toPx(),
                        center = Offset(cx, cy),
                    )
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Camera animation
// ─────────────────────────────────────────────────────────────────────────────

private const val CAMERA_ANIM_MS = 700

@Composable
private fun rememberCameraAnimationState(
    cameraTarget: CameraTarget?,
    userLocation: GpsPoint?,
    initialCamera: CameraTarget?,
    actualCamLat: Float?,
    actualCamLon: Float?,
    actualCamZoom: Float,
): CameraPosition {
    // Seed priority: explicit cameraTarget > config.initialCamera > live userLocation > origin.
    val initCoords = cameraTarget?.let { Coordinates(it.lat, it.lon) }
        ?: initialCamera?.let { Coordinates(it.lat, it.lon) }
        ?: userLocation?.let { Coordinates(it.latitude, it.longitude) }
        ?: Coordinates(0.0, 0.0)
    val animLat = remember { Animatable(initCoords.latitude.toFloat()) }
    val animLon = remember { Animatable(initCoords.longitude.toFloat()) }
    val animZoom = remember {
        Animatable(cameraTarget?.zoom ?: initialCamera?.zoom ?: ZOOM_DEFAULT)
    }

    // Snap Animatables to the actual map position only right before launching
    // a programmatic animation. This ensures animations start from wherever
    // the user left the camera (after dragging/flinging/pinching) without
    // creating a feedback loop that would interrupt the native gesture each frame.
    LaunchedEffect(cameraTarget) {
        val target = cameraTarget ?: return@LaunchedEffect
        // Sync all three axes to the real camera so the animation never jumps.
        actualCamLat?.let { animLat.snapTo(it) }
        actualCamLon?.let { animLon.snapTo(it) }
        animZoom.snapTo(actualCamZoom)
        val targetLat: Float
        val targetLon: Float
        val targetZoom: Float?
        if (target.boundsLat2 != null && target.boundsLon2 != null) {
            val maxDelta = maxOf(
                abs(target.lat - target.boundsLat2),
                abs(target.lon - target.boundsLon2),
            ).toFloat()
            targetZoom = when {
                maxDelta < BOUNDS_DELTA_220M  -> ZOOM_STREET        // ~220 m
                maxDelta < BOUNDS_DELTA_550M  -> ZOOM_CLOSE         // ~550 m
                maxDelta < BOUNDS_DELTA_1100M -> ZOOM_DEFAULT       // ~1.1 km
                maxDelta < BOUNDS_DELTA_2200M -> ZOOM_NEIGHBORHOOD  // ~2.2 km
                maxDelta < BOUNDS_DELTA_4500M -> ZOOM_DISTRICT      // ~4.5 km
                else                          -> ZOOM_WIDE
            }
            targetLat = ((target.lat + target.boundsLat2) / 2.0).toFloat()
            targetLon = ((target.lon + target.boundsLon2) / 2.0).toFloat()
        } else {
            targetLat = target.lat.toFloat()
            targetLon = target.lon.toFloat()
            targetZoom = target.zoom  // null → preserve current zoom
        }
        val spec = tween<Float>(CAMERA_ANIM_MS, easing = FastOutSlowInEasing)
        launch { animLat.animateTo(targetLat, spec) }
        launch { animLon.animateTo(targetLon, spec) }
        targetZoom?.let { launch { animZoom.animateTo(it, spec) } }
    }

    return CameraPosition(
        coordinates = Coordinates(animLat.value.toDouble(), animLon.value.toDouble()),
        zoom = animZoom.value,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Custom marker composables — overlays on top of the Design System markers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Wraps [FreeSpotMarker] — spot markers are cached bitmaps keyed by contentId
 * (tier × nrm/dim/sel, plus one per en-route count bucket) so this simply
 * forwards the tier/selection and the optional en-route count. [BOLT-MARKERS-001]
 */
@Composable
private fun FreeSpotWithOverlays(
    reliability: SpotReliabilityUiState = SpotReliabilityUiState.HIGH,
    selected: Boolean = false,
    enRouteCount: Int = 0,
) {
    FreeSpotMarker(reliability = reliability, selected = selected, enRouteCount = enRouteCount)
}


