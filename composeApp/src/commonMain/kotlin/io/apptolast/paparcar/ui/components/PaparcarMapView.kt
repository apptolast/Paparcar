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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.swmansion.kmpmaps.core.AndroidMapProperties
import com.swmansion.kmpmaps.core.AndroidMarkerOptions
import com.swmansion.kmpmaps.core.AndroidUISettings
import com.swmansion.kmpmaps.core.CameraPosition
import com.swmansion.kmpmaps.core.Circle
import com.swmansion.kmpmaps.core.Coordinates
import com.swmansion.kmpmaps.core.Polyline
import com.swmansion.kmpmaps.core.GoogleMapsAnchor
import com.swmansion.kmpmaps.core.GoogleMapsMapStyleOptions
import com.swmansion.kmpmaps.core.Map
import com.swmansion.kmpmaps.core.MapProperties
import com.swmansion.kmpmaps.core.MapTheme
import com.swmansion.kmpmaps.core.MapType
import com.swmansion.kmpmaps.core.MapUISettings
import com.swmansion.kmpmaps.core.Marker
import io.apptolast.paparcar.domain.detection.DetectionPhase
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.DrivingPuck
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkedVehicleSummary
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.presentation.map.CameraTarget
import io.apptolast.paparcar.presentation.util.SpotReliabilityUiState
import io.apptolast.paparcar.presentation.util.toReliabilityUiState
import io.apptolast.paparcar.presentation.util.zoneIconFor
import io.apptolast.paparcar.ui.theme.PapDriveBlue
import io.apptolast.paparcar.ui.theme.PapGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
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
private const val REPORT_MODE_SHADOW_ALPHA = 0.35f
private const val NORMAL_MODE_SHADOW_ALPHA = 0.22f

// ── Radar (default center indicator) ────────────────────────────────────────
private const val RING_AIMING_SCALE     = 0.82f   // the ring shrinks while aiming
private const val RING_LOADING_ALPHA    = 0.50f   // ring dimmed while loading
private const val RADAR_SWEEP_DEG       = 62f     // angular width of the wedge
private const val RADAR_HEAD_ALPHA      = 0.55f   // opacity of the leading edge
private const val RADAR_OUTSET_DP       = 6f      // how far the sweep extends past the ring
private const val CENTER_DOT_AIMING_RADIUS_DP = 1.4f // precision dot while aiming

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
    // Monitoring tone is baked into the key so the marker bitmap regenerates when a car goes
    // active↔inactive (kmpmaps caches by contentId). BT > active > inactive. [MAP-ICONS-V2]
    val tone = when {
        v.isBluetoothPaired -> "bt"
        v.isActive          -> "act"
        else                -> "ina"
    }
    // Colour is baked into the key so a recoloured car regenerates its cached bitmap. [VEH-COLOR-001]
    return "vehicle_badge_${v.vehicleId.take(8)}_${v.sizeCategory?.name ?: "def"}_${v.color?.name ?: "def"}_${state}_$tone"
}

private const val MARKER_MY_CAR          = "my_car"
private const val MARKER_MY_CAR_DIM      = "my_car_dim"
private const val MARKER_MY_CAR_SELECTED = "my_car_selected"
private const val MARKER_DEPARTURE       = "departure" // trip origin (blue dot) during a trip [DEPART-CONSISTENCY-001]
// Trip breadcrumb polyline width (screen px in Google Maps). [TRIP-TRAIL-001]
private const val TRIP_TRAIL_WIDTH = 14f
// Interpolated points inserted per original span when smoothing the trail. Higher = rounder curves,
// heavier polyline. [ROUTE-SMOOTH-002]
private const val TRAIL_SMOOTH_SEGMENTS = 8

/**
 * Catmull-Rom spline through the raw breadcrumb points: the curve passes through every original GPS
 * point and inserts [TRAIL_SMOOTH_SEGMENTS] interpolated points per span, so sparse fixes around a
 * roundabout render as a curve instead of a chord polygon. Endpoints duplicate the terminal point as
 * the virtual control point. Returns the input unchanged when there are too few points to interpolate.
 * [ROUTE-SMOOTH-002]
 */
private fun smoothTrail(pts: List<Coordinates>): List<Coordinates> {
    if (pts.size < 3) return pts
    val out = ArrayList<Coordinates>((pts.size - 1) * TRAIL_SMOOTH_SEGMENTS + 1)
    out.add(pts.first())
    for (i in 0 until pts.size - 1) {
        val p0 = pts[if (i == 0) 0 else i - 1]
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val p3 = pts[if (i + 2 > pts.lastIndex) pts.lastIndex else i + 2]
        for (s in 1..TRAIL_SMOOTH_SEGMENTS) {
            val t = s.toDouble() / TRAIL_SMOOTH_SEGMENTS
            val t2 = t * t
            val t3 = t2 * t
            val lat = 0.5 * (2 * p1.latitude +
                (-p0.latitude + p2.latitude) * t +
                (2 * p0.latitude - 5 * p1.latitude + 4 * p2.latitude - p3.latitude) * t2 +
                (-p0.latitude + 3 * p1.latitude - 3 * p2.latitude + p3.latitude) * t3)
            val lon = 0.5 * (2 * p1.longitude +
                (-p0.longitude + p2.longitude) * t +
                (2 * p0.longitude - 5 * p1.longitude + 4 * p2.longitude - p3.longitude) * t2 +
                (-p0.longitude + 3 * p1.longitude - 3 * p2.longitude + p3.longitude) * t3)
            out.add(Coordinates(lat, lon))
        }
    }
    return out
}

// Google Maps renders billboard markers by screen-Y when zIndex is equal,
// so the selected marker must carry an explicit higher zIndex to always
// render on top regardless of geographic position.
private const val MARKER_Z_INDEX_SELECTED = 1f
private const val MARKER_Z_INDEX_NORMAL   = 0f
private val SELECTED_MARKER_OPTIONS = AndroidMarkerOptions(zIndex = MARKER_Z_INDEX_SELECTED)
private val NORMAL_MARKER_OPTIONS   = AndroidMarkerOptions(zIndex = MARKER_Z_INDEX_NORMAL)
// Trip origin dot is centred on its coordinate (0.5, 0.5) — the dot IS the point — and kept under
// the trail + puck (normal zIndex). [DEPART-CONSISTENCY-001]
private val DEPARTURE_MARKER_OPTIONS = AndroidMarkerOptions(
    anchor = GoogleMapsAnchor(0.5f, 0.5f),
    zIndex = MARKER_Z_INDEX_NORMAL,
)
// Zone label is centred on the circle centre (0.5, 0.5) — not bottom-anchored — so
// it sits IN the middle of the radius ring instead of floating above it. Lowest
// zIndex so spot/vehicle markers always render on top. [ZONE-AREA-001]
private val ZONE_MARKER_OPTIONS = AndroidMarkerOptions(
    anchor = GoogleMapsAnchor(0.5f, 0.5f),
    zIndex = MARKER_Z_INDEX_NORMAL,
)
// Driving puck — centred on its coordinate (the car pivots around the point), drawn flat so its
// native `rotation` is relative to north (the map is always north-up) and above every other marker
// (the moving car owns the screen). Rotation is applied per-fix via `.copy(rotation = …)`; the
// bitmap itself is north-up. [DRIVE-PUCK-NATIVE-001]
private const val MARKER_Z_INDEX_PUCK = 2f
private const val MARKER_PUCK_ID = "driving-puck"
private val PUCK_MARKER_OPTIONS = AndroidMarkerOptions(
    anchor = GoogleMapsAnchor(0.5f, 0.5f),
    zIndex = MARKER_Z_INDEX_PUCK,
    flat = true,
)

// User-location dot — the "you" marker when not driving. Migrated from a Web Mercator Compose overlay
// to a native Marker with a stable id: that overlay only existed to dodge the kmpmaps moving-marker
// flicker the fork now fixes, so the projection is gone. Centred on its coordinate. [DRIVE-PUCK-NATIVE-001]
private const val MARKER_USER_DOT = "user_location_dot"
private const val MARKER_USER_DOT_ID = "user-location-dot"
private val USER_DOT_MARKER_OPTIONS = AndroidMarkerOptions(
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
// ONE north-up bitmap per carbody × colour: the GPS heading is applied as native marker rotation
// now (see [PUCK_MARKER_OPTIONS]), not baked into the bitmap, so the contentId no longer carries a
// heading bucket. Anchored centre so the car pivots around the coordinate. [DRIVE-PUCK-NATIVE-001]
private const val LOCATION_ACTIVE_PREFIX = "loc_active_"
private fun locationActiveContentId(
    carbody: CarbodyType?,
    color: io.apptolast.paparcar.domain.model.VehicleColor? = null,
): String =
    "$LOCATION_ACTIVE_PREFIX${carbody?.name ?: "def"}_${color?.name ?: "def"}"

/**
 * Brand user-position dot: green core + light ring + 15% green halo — the "you"
 * marker in the same palette as the rest of the map. [UI-SHEET-003]
 */
@Composable
private fun UserLocationDot(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(USER_DOT_HALO_DP.dp)
            .clip(CircleShape)
            .background(cs.primary.copy(alpha = USER_DOT_HALO_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(USER_DOT_RING_DP.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(USER_DOT_CORE_DP.dp)
                    .clip(CircleShape)
                    .background(cs.primary),
            )
        }
    }
}

private const val USER_DOT_HALO_DP = 44
private const val USER_DOT_RING_DP = 16
private const val USER_DOT_CORE_DP = 12
private const val USER_DOT_HALO_ALPHA = 0.15f
private const val USER_DOT_GLIDE_MS = 600

/** Interpolated render pose of the driving puck (geo position + heading). [FOLLOW-001] */
private data class PuckPose(val latitude: Double, val longitude: Double, val headingDegrees: Float)

private fun lerpDouble(a: Double, b: Double, t: Float): Double = a + (b - a) * t

/** Shortest-path angular lerp so the car turns the short way (e.g. 350°→10° via 0, not backwards). */
private fun lerpAngle(a: Float, b: Float, t: Float): Float {
    var d = (b - a) % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return a + d * t
}

/** GPS fixes land ~1/s; glide over roughly that window so motion reads continuous, not stepped. */
private const val PUCK_INTERP_MS = 1000

/**
 * Smoothly glides the puck between discrete GPS fixes. Each new fix animates a 0→1 progress and the
 * pose is lerp'd from the previously-rendered pose to the new fix, so the car flows continuously
 * (like a transport app) instead of jumping fix-to-fix — including its heading (shortest-path). The
 * car trails real position by up to one fix, the usual trade for smoothness. Returns null when idle.
 * [FOLLOW-001]
 */
@Composable
private fun rememberInterpolatedPuck(puck: DrivingPuck?): PuckPose? {
    if (puck == null) return null
    val progress = remember { Animatable(1f) }
    var prev by remember { mutableStateOf(PuckPose(puck.latitude, puck.longitude, puck.bearingDegrees ?: 0f)) }
    var target by remember { mutableStateOf(PuckPose(puck.latitude, puck.longitude, puck.bearingDegrees ?: 0f)) }

    LaunchedEffect(puck.latitude, puck.longitude, puck.bearingDegrees) {
        // Restart the glide from wherever we are RIGHT NOW so there's no jump when a fix arrives mid-glide.
        val t = progress.value
        prev = PuckPose(
            lerpDouble(prev.latitude, target.latitude, t),
            lerpDouble(prev.longitude, target.longitude, t),
            lerpAngle(prev.headingDegrees, target.headingDegrees, t),
        )
        // A null bearing (momentarily stopped) keeps the last heading rather than snapping to north.
        target = PuckPose(puck.latitude, puck.longitude, puck.bearingDegrees ?: target.headingDegrees)
        progress.snapTo(0f)
        progress.animateTo(1f, tween(PUCK_INTERP_MS, easing = LinearEasing))
    }

    val t = progress.value
    return PuckPose(
        lerpDouble(prev.latitude, target.latitude, t),
        lerpDouble(prev.longitude, target.longitude, t),
        lerpAngle(prev.headingDegrees, target.headingDegrees, t),
    )
}

/**
 * Glided coordinates for the native user-location dot — animates lat/lon between fixes so the dot
 * slides instead of stepping, like the old overlay did. Returns null when there's no location (so the
 * marker is simply absent), and re-seeds at the real position when a location reappears rather than
 * gliding from null-island. [DRIVE-PUCK-NATIVE-001]
 */
@Composable
private fun rememberGlidedLatLon(point: GpsPoint?): Coordinates? {
    if (point == null) return null
    val lat by animateFloatAsState(
        targetValue = point.latitude.toFloat(),
        animationSpec = tween(USER_DOT_GLIDE_MS),
        label = "user_dot_lat",
    )
    val lon by animateFloatAsState(
        targetValue = point.longitude.toFloat(),
        animationSpec = tween(USER_DOT_GLIDE_MS),
        label = "user_dot_lon",
    )
    return Coordinates(lat.toDouble(), lon.toDouble())
}

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
    /** Breadcrumb of the current trip — drawn as a navigation-style polyline behind the puck. [TRIP-TRAIL-001] */
    tripTrail: List<GpsPoint> = emptyList(),
    /** Trip trail snapped onto OSM streets; preferred over [tripTrail] when non-empty (follows the
     *  road). Already includes the departure origin as its first point. [ROUTE-SNAP-001] */
    matchedTrail: List<GpsPoint> = emptyList(),
    /** Faded "departure" point — where the car left from, shown while a trip runs. [TRIP-TRAIL-001] */
    departurePoint: GpsPoint? = null,
    /**
     * When true (camera is following the trip), the driving puck is drawn as a CENTERED overlay rather
     * than a moving marker. kmpmaps keys markers by hashCode (incl. coordinates), so a marker that moves
     * every GPS frame is torn down + recreated each frame → flicker. Centered + camera-follow is the
     * nav-app pattern: the car stays put, the map slides under it — smooth, no flicker. [FOLLOW-001]
     */
    centerDrivingPuck: Boolean = false,
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
    /** Paint colour for the fallback single-parking marker. Null = default green. [VEH-COLOR-001] */
    parkingVehicleColor: io.apptolast.paparcar.domain.model.VehicleColor? = null,
    /** When false the fallback parking marker renders in the inactive/history palette. */
    parkingIsActive: Boolean = true,
    /** When true the add-parking centre pin reads as a Bluetooth-tracked car (blue border). */
    parkingIsBluetoothPaired: Boolean = false,
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
        departurePoint,
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
            // Trip origin — a blue dot where the car left from, under the trail + puck. [DEPART-CONSISTENCY-001]
            departurePoint?.let { dp ->
                add(
                    Marker(
                        coordinates = Coordinates(dp.latitude, dp.longitude),
                        title = null,
                        contentId = MARKER_DEPARTURE,
                        androidMarkerOptions = DEPARTURE_MARKER_OPTIONS,
                    ),
                )
            }
            // The driving puck is NOT added here. It moves every frame (interpolated between GPS
            // fixes), so it lives outside this memoised list as its own native Marker with a stable
            // [Marker.id] + native rotation — see [drivingPuckMarker] below. The fork keys markers by
            // that stable id, so the moving puck repositions in place instead of being torn down and
            // recreated (the old flicker), which is why it no longer needs a Compose overlay.
            // [DRIVE-PUCK-NATIVE-001]
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
        parkingVehicleSize, parkingVehicleCarbody, parkingVehicleColor, parkingIsActive,
        // Key only on the puck's VISUAL identity, not the whole drivingPuck: its handler bitmap depends
        // solely on carbody/size/colour (heading is native rotation, position is the marker coordinate).
        // Keying on the full object rebuilt this map every GPS fix (~1 Hz), which re-rasterised the puck
        // MarkerComposable and flashed a doubled halo — the periodic blue flicker. [DRIVE-PUCK-NATIVE-001]
        drivingPuck?.carbodyType, drivingPuck?.sizeCategory, drivingPuck?.color,
    ) {
        val baseHandlers: Map<String, @Composable (Marker) -> Unit> = buildMap {
            // ── Fallback single-parking marker (ParkingLocationScreen) ──
            put(MARKER_MY_CAR) { _ ->
                VehicleBadgeMarker(
                    sizeCategory = parkingVehicleSize,
                    carbodyType = parkingVehicleCarbody,
                    isActive = parkingIsActive,
                    color = parkingVehicleColor,
                )
            }
            put(MARKER_MY_CAR_DIM) { _ ->
                DimWrapper {
                    VehicleBadgeMarker(
                        sizeCategory = parkingVehicleSize,
                        carbodyType = parkingVehicleCarbody,
                        isActive = parkingIsActive,
                        color = parkingVehicleColor,
                    )
                }
            }
            put(MARKER_MY_CAR_SELECTED) { _ ->
                VehicleBadgeMarker(
                    selected = true,
                    sizeCategory = parkingVehicleSize,
                    carbodyType = parkingVehicleCarbody,
                    color = parkingVehicleColor,
                    isActive = parkingIsActive,
                )
            }
            // Departure point: a small blue dot with a white halo — a clean "you left from here"
            // origin where the breadcrumb trail starts. [DEPART-CONSISTENCY-001] [TRIP-TRAIL-001]
            put(MARKER_DEPARTURE) { _ ->
                DepartureDotMarker()
            }
            // User-location "you" dot — one stable bitmap, no per-fix churn. [DRIVE-PUCK-NATIVE-001]
            put(MARKER_USER_DOT) { _ ->
                UserLocationDot()
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
                            isActive = v.isActive,
                            stableRank = v.stableRank,
                            isBluetoothPaired = v.isBluetoothPaired,
                            color = v.color,
                        )
                    },
                    vehicleBadgeContentId(v, selected = false, dim = true) to { _: Marker ->
                        DimWrapper {
                            VehicleBadgeMarker(
                                sizeCategory = v.sizeCategory,
                                carbodyType = v.carbodyType,
                                isActive = v.isActive,
                                stableRank = v.stableRank,
                                isBluetoothPaired = v.isBluetoothPaired,
                                color = v.color,
                            )
                        }
                    },
                    vehicleBadgeContentId(v, selected = true) to { _: Marker ->
                        VehicleBadgeMarker(
                            selected = true,
                            sizeCategory = v.sizeCategory,
                            carbodyType = v.carbodyType,
                            isActive = v.isActive,
                            stableRank = v.stableRank,
                            isBluetoothPaired = v.isBluetoothPaired,
                            color = v.color,
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

        // ── Driving puck handler — ONE north-up bitmap per carbody × colour ──
        // Heading is applied as native marker rotation now (see [PUCK_MARKER_OPTIONS]), so the bitmap
        // is drawn north-up (headingDegrees = 0) and no longer multiplied per heading bucket.
        // [DRIVE-PUCK-NATIVE-001]
        val drivingHandler: Map<String, @Composable (Marker) -> Unit> = drivingPuck?.let { puck ->
            mapOf(
                locationActiveContentId(puck.carbodyType, puck.color) to { _: Marker ->
                    LocationActiveMarker(
                        carbody = puck.carbodyType,
                        size = puck.sizeCategory,
                        headingDegrees = 0f,
                        color = puck.color,
                    )
                },
            )
        } ?: emptyMap()

        baseHandlers + vehicleHandlers + zoneHandlers + drivingHandler
    }

    // ── Track real camera center (set by the map, not by us) ──────────────
    var actualCamLat by remember { mutableStateOf<Float?>(null) }
    var actualCamLon by remember { mutableStateOf<Float?>(null) }

    // True while a finger is down on the map. Releases the driver-follow camera lock the instant the
    // user touches (zero latency), so the FIRST drag pans freely instead of the lock snapping the
    // camera back to the car — the VM's gesture-based follow-pause has round-trip latency, which is
    // why it took two drags to break free. [DRIVE-PUCK-NATIVE-001]
    var userTouchingMap by remember { mutableStateOf(false) }

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
    // Radar ring: shrinks slightly while aiming so the ring appears to "close in" on the target
    val ringScale by animateFloatAsState(
        targetValue = if (cameraMoving && !isAnyItemSelected) RING_AIMING_SCALE else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "radar_ring_scale",
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

    // Interpolated render pose so the driving car glides between GPS fixes instead of stepping. Also
    // drives the follow camera (below) so the puck marker stays pinned to screen-centre. [FOLLOW-001]
    val puckPose = rememberInterpolatedPuck(drivingPuck)

    val cameraPosition = rememberCameraAnimationState(
        cameraTarget = cameraTarget,
        userLocation = userLocation,
        initialCamera = config.initialCamera,
        actualCamLat = actualCamLat,
        actualCamLon = actualCamLon,
        actualCamZoom = currentZoom,
        // While actively following the driver, lock the camera centre to the SAME interpolated pose
        // that positions the puck marker, with no tween lag — so the marker sits rock-steady at centre
        // (what the old centered overlay did) instead of drifting as a lagging camera catches up.
        // A real pan pauses follow (centerDrivingPuck → false), handing back to the tween. Touching the
        // map suspends the lock immediately so the first drag isn't fought. [DRIVE-PUCK-NATIVE-001]
        followPose = if (centerDrivingPuck && drivingPuck != null && !userTouchingMap) puckPose else null,
        // While a finger is down, freeze all programmatic camera moves so nothing fights the gesture.
        userInteracting = userTouchingMap,
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

    // Live trip breadcrumb as a native polyline (en-route blue), rendered below the markers. Prefer the
    // OSM-matched trail (follows the road, includes the origin); otherwise start at the departure point
    // and trace the raw puck path. [TRIP-TRAIL-001] [ROUTE-SNAP-001]
    val tripPolylines = remember(tripTrail, departurePoint, matchedTrail) {
        if (matchedTrail.size >= 2) {
            // Matched points are dense and already on the road — connect them directly. Do NOT spline:
            // Catmull-Rom overshoots into loops where consecutive snaps jump between lanes. [ROUTE-SNAP-001]
            listOf(Polyline(
                coordinates = matchedTrail.map { Coordinates(it.latitude, it.longitude) },
                width = TRIP_TRAIL_WIDTH,
                lineColor = PapDriveBlue,
            ))
        } else {
            // Raw fallback (no roads matched): sparse GPS, so smooth it into a flowing curve so tight
            // turns read as curves rather than chord polygons. [ROUTE-SMOOTH-002]
            val coords = buildList {
                departurePoint?.let { add(Coordinates(it.latitude, it.longitude)) }
                tripTrail.forEach { add(Coordinates(it.latitude, it.longitude)) }
            }
            if (coords.size < 2) emptyList()
            else listOf(Polyline(coordinates = smoothTrail(coords), width = TRIP_TRAIL_WIDTH, lineColor = PapDriveBlue))
        }
    }

    // The driving puck as a single native Marker with a STABLE id (so it repositions in place, never
    // torn down → no flicker) + native rotation (one north-up bitmap turned to the heading, instead of
    // a bitmap per heading bucket). Built outside the memoised [markers] because it moves every frame:
    // its coordinates + rotation follow the interpolated pose. Present in EVERY phase (moving or the
    // frozen Candidate car), so the car's visibility no longer depends on camera-follow. Appended each
    // recomposition; the fork keeps every other marker's identity stable so only the puck moves.
    // [DRIVE-PUCK-NATIVE-001]
    // The user-location dot is likewise a native Marker now (was a Web Mercator Compose overlay). Shown
    // when not driving — or the frozen Candidate car, where it marks the pedestrian walking away. Its
    // coordinates glide between fixes. [DRIVE-PUCK-NATIVE-001]
    val userDotCoords = rememberGlidedLatLon(userLocation)
    val showUserDot = userDotCoords != null &&
        (drivingPuck == null || drivingPuck.phase == DetectionPhase.Candidate) && mapLoaded
    val allMarkers = remember(markers, drivingPuck, puckPose, showUserDot, userDotCoords) {
        var result = markers
        drivingPuck?.let { puck ->
            val heading = puckPose?.headingDegrees ?: puck.bearingDegrees ?: 0f
            result = result + Marker(
                coordinates = Coordinates(
                    puckPose?.latitude ?: puck.latitude,
                    puckPose?.longitude ?: puck.longitude,
                ),
                title = null,
                contentId = locationActiveContentId(puck.carbodyType, puck.color),
                androidMarkerOptions = PUCK_MARKER_OPTIONS.copy(rotation = heading),
                id = MARKER_PUCK_ID,
            )
        }
        if (showUserDot && userDotCoords != null) {
            result = result + Marker(
                coordinates = userDotCoords,
                title = null,
                contentId = MARKER_USER_DOT,
                androidMarkerOptions = USER_DOT_MARKER_OPTIONS,
                id = MARKER_USER_DOT_ID,
            )
        }
        result
    }

    Box(
        modifier = modifier.pointerInput(Unit) {
            // Observe-only (Initial pass, never consumed) press tracking — the map still pans; we only
            // learn whether a finger is down so the follow lock can step aside instantly. [DRIVE-PUCK-NATIVE-001]
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    userTouchingMap = event.changes.any { it.pressed }
                }
            }
        },
    ) {
        Map(
            modifier = Modifier.fillMaxSize(),
            cameraPosition = cameraPosition,
            properties = MapProperties(
                // The native Google blue dot is OFF for good: the user position is painted by us
                // (brand-green projected overlay below) so the map speaks ONE palette — three
                // different marker languages (car, blue dot, pin) read as three apps. [UI-SHEET-003]
                isMyLocationEnabled = false,
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
                // Rotation & tilt stay OFF everywhere: the whole map is top-down (top-down car
                // silhouettes + markers) so they add nothing, and — critically — the driving-car
                // overlay is placed by a north-up/flat Web Mercator projection, which a rotated or
                // tilted camera would break (the car would drift off its point). Keeping the map
                // north-up/flat guarantees the car stays exactly glued to its coordinate. [FOLLOW-001]
                rotateEnabled = false,
                togglePitchEnabled = false,
                androidUISettings = AndroidUISettings(
                    zoomControlsEnabled = false,
                    mapToolbarEnabled = false,
                ),
            ),
            markers = allMarkers,
            circles = zoneCircles,
            polylines = tripPolylines,
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
                // kmp-maps 0.9.1 made CameraPosition.coordinates nullable (it can be absent
                // briefly mid-gesture); zoom always updates, lat/lon only when coords are present.
                pos.zoom?.let { currentZoom = it }
                pos.coordinates?.let { coords ->
                    actualCamLat = coords.latitude.toFloat()
                    actualCamLon = coords.longitude.toFloat()
                    if (!isReadOnly) {
                        onCameraMove(coords.latitude, coords.longitude)
                    }
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

        // The brand user-location "you" dot is now a native Marker (see [allMarkers] / MARKER_USER_DOT),
        // not a Web Mercator Compose overlay — the fork's stable-id fix removed the flicker that overlay
        // worked around. [DRIVE-PUCK-NATIVE-001]

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
                    color = parkingVehicleColor,
                    isActive = parkingIsActive,
                    isBluetoothPaired = parkingIsBluetoothPaired,
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
        } else if (drivingPuck == null) {
            // The centre crosshair marks "the map centre" for picking a location in Browse. During a
            // detected trip the centre IS the moving car (the driving puck), so the crosshair would
            // just sit on top of it — visual noise with no meaning. Hide it while driving. [FOLLOW-001]
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
                // Fine ring + center dot + a radar sweep while loading. No cardinal ticks/cross.
                // Only the default crosshair branch. [CENTER-SIGHT-001]
                Canvas(modifier = Modifier.size(RING_CANVAS_SIZE)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val baseRadius = size.minDimension * RING_RADIUS_FACTOR
                    val ringRadius = baseRadius * ringScale
                    val ringStroke = RING_STROKE_DP.dp.toPx()
                    val center = Offset(cx, cy)

                    // Ring shadow (subtle drop, offset down-right)
                    drawCircle(
                        color = Color.Black.copy(alpha = shadowAlpha),
                        radius = ringRadius + SHADOW_RADIUS_OFFSET,
                        center = Offset(cx + SHADOW_OFFSET_X, cy + SHADOW_OFFSET_Y),
                        style = Stroke(width = ringStroke + SHADOW_EXTRA_STROKE),
                    )

                    // Radar sweep (only while loading) — drawn under the ring. A wedge whose
                    // leading edge sits at loadingAngle and fades toward the tail, replacing the
                    // old loading comet. Fades out when loading completes, then the pulse fires.
                    if (loadingAlpha > 0f) {
                        val sweepRadius = ringRadius + RADAR_OUTSET_DP.dp.toPx()
                        val head = loadingAngle.value            // leading edge (degrees)
                        val sweepBrush = Brush.sweepGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                (1f - RADAR_SWEEP_DEG / 360f) to indicatorColor.copy(alpha = 0f),
                                1f to indicatorColor.copy(alpha = RADAR_HEAD_ALPHA * loadingAlpha),
                            ),
                            center = center,
                        )
                        // Rotate so the wedge head lands at `head`.
                        withTransform({ rotate(head, center) }) {
                            drawArc(
                                brush = sweepBrush,
                                startAngle = -RADAR_SWEEP_DEG,   // relative to the applied rotation
                                sweepAngle = RADAR_SWEEP_DEG,
                                useCenter = true,
                                topLeft = Offset(cx - sweepRadius, cy - sweepRadius),
                                size = Size(sweepRadius * 2, sweepRadius * 2),
                            )
                        }
                    }

                    // Main ring (dimmed while loading)
                    val ringAlpha = if (loadingAlpha > 0f) RING_LOADING_ALPHA else 1f
                    drawCircle(
                        color = indicatorColor.copy(alpha = ringAlpha),
                        radius = ringRadius,
                        center = center,
                        style = Stroke(width = ringStroke),
                    )

                    // Center dot shadow
                    drawCircle(
                        color = Color.Black.copy(alpha = shadowAlpha),
                        radius = CENTER_DOT_SHADOW_RADIUS_DP.dp.toPx(),
                        center = Offset(cx + SHADOW_OFFSET_X / 2, cy + SHADOW_OFFSET_Y / 2),
                    )
                    // Center dot (tightens to a precision dot while aiming)
                    val dotRadius = if (cameraMoving) CENTER_DOT_AIMING_RADIUS_DP.dp.toPx()
                        else CENTER_DOT_RADIUS_DP.dp.toPx()
                    drawCircle(color = indicatorColor, radius = dotRadius, center = center)
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
    followPose: PuckPose? = null,
    userInteracting: Boolean = false,
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
    val following = followPose != null

    // Camera position captured SYNCHRONOUSLY the instant a finger touches the map, held static for the
    // gesture so nothing programmatic fights the native pan. Synchronous (not via a post-composition
    // LaunchedEffect) so the first touched frame renders the real position, not a stale flash. The
    // tracker advances in a SideEffect after composition. [DRIVE-PUCK-NATIVE-001]
    var frozenLat by remember { mutableStateOf(0f) }
    var frozenLon by remember { mutableStateOf(0f) }
    var frozenZoom by remember { mutableStateOf(actualCamZoom) }
    var prevInteracting by remember { mutableStateOf(false) }
    if (userInteracting && !prevInteracting) {
        frozenLat = actualCamLat ?: animLat.value
        frozenLon = actualCamLon ?: animLon.value
        frozenZoom = actualCamZoom
    }
    SideEffect { prevInteracting = userInteracting }

    // Follow-engage glide progress: 0 = at the camera's current spot on engage, 1 = locked on the puck.
    // Reset to 0 on disengage so a later re-engage starts from the camera's real position instead of
    // flashing to the puck for one frame (the loc-button flicker). [DRIVE-PUCK-NATIVE-001]
    val followProgress = remember { Animatable(0f) }
    LaunchedEffect(following) {
        if (following) followProgress.animateTo(1f, tween(CAMERA_ANIM_MS, easing = FastOutSlowInEasing))
        else followProgress.snapTo(0f)
    }

    // Keyed on the TARGET (and follow lock), deliberately NOT on userInteracting: releasing a finger
    // must not re-run the tween toward a stale cameraTarget (the last followed puck position) — that
    // was re-centring the map on the car after every pan. The tween only fires on a genuine new target.
    LaunchedEffect(cameraTarget, following) {
        // Don't run the programmatic tween while locked to the driver (it would fight the lock) or
        // while the user is touching the map (it would yank the camera back mid-gesture).
        if (following || userInteracting) return@LaunchedEffect
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

    // While touching: the frozen position (constant, captured above) so cameraPosition stops changing
    // and native pan/zoom own the camera. Checked FIRST so a touch always wins. [DRIVE-PUCK-NATIVE-001]
    if (userInteracting) {
        return CameraPosition(
            coordinates = Coordinates(frozenLat.toDouble(), frozenLon.toDouble()),
            zoom = frozenZoom,
        )
    }

    // Driver-follow: glide from the camera's CURRENT (live) position to the live puck by [followProgress]
    // so engage catches up instead of snapping; at progress = 1 it is exactly the puck each frame (zero-
    // lag lock). Using the live camera as the anchor means no stale value ever renders. [DRIVE-PUCK-NATIVE-001]
    if (followPose != null) {
        val t = followProgress.value
        val anchorLat = actualCamLat?.toDouble() ?: followPose.latitude
        val anchorLon = actualCamLon?.toDouble() ?: followPose.longitude
        return CameraPosition(
            coordinates = Coordinates(
                lerpDouble(anchorLat, followPose.latitude, t),
                lerpDouble(anchorLon, followPose.longitude, t),
            ),
            zoom = actualCamZoom,
        )
    }

    // Idle vs programmatic tween: while a tween is running, follow the Animatable that drives it;
    // otherwise reflect the REAL camera so a prior manual pan isn't yanked back to a stale Animatable.
    return if (animLat.isRunning || animLon.isRunning || animZoom.isRunning) {
        CameraPosition(
            coordinates = Coordinates(animLat.value.toDouble(), animLon.value.toDouble()),
            zoom = animZoom.value,
        )
    } else {
        CameraPosition(
            coordinates = Coordinates(
                (actualCamLat ?: animLat.value).toDouble(),
                (actualCamLon ?: animLon.value).toDouble(),
            ),
            zoom = actualCamZoom,
        )
    }
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


