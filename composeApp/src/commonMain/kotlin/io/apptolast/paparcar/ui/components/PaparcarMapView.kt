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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swmansion.kmpmaps.core.AndroidMapProperties
import com.swmansion.kmpmaps.core.AndroidUISettings
import com.swmansion.kmpmaps.core.CameraPosition
import com.swmansion.kmpmaps.core.Coordinates
import com.swmansion.kmpmaps.core.GoogleMapsMapStyleOptions
import com.swmansion.kmpmaps.core.Map
import com.swmansion.kmpmaps.core.MapProperties
import com.swmansion.kmpmaps.core.MapTheme
import com.swmansion.kmpmaps.core.MapType
import com.swmansion.kmpmaps.core.MapUISettings
import com.swmansion.kmpmaps.core.Marker
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkedVehicleView
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.domain.model.ZoneIcon

import io.apptolast.paparcar.presentation.map.CameraTarget

import io.apptolast.paparcar.presentation.util.zoneIconFor
import io.apptolast.paparcar.presentation.util.SpotReliabilityLevel
import io.apptolast.paparcar.presentation.util.toSpotReliabilityLevel
import io.apptolast.paparcar.ui.theme.PapBlue
import io.apptolast.paparcar.ui.theme.PapForestDark
import io.apptolast.paparcar.ui.theme.PapGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    val mapType: MapType = MapType.NORMAL,
    val styleMode: MapStyleMode = MapStyleMode.AUTO,
)

/**
 * Variant of the animated centre pin. Null in [PaparcarMapConfig.centerPin]
 * means "show the default crosshair indicator". Each subtype maps 1:1 to a
 * composable in [PaparcarMapMarkers.kt].
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
 * Selects which Google Maps style JSON is applied (see [MapStyles.kt]).
 *
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

// ── Center pin drop (config.centerPin != null) ───────────────────────────────
private const val CENTER_PIN_DROP_OFFSET_DP = -8f
private const val CENTER_PIN_DROP_REST_DP   = 0f
private const val CENTER_PIN_DROP_SCALE_FROM = 1.1f
private const val CENTER_PIN_DROP_SCALE_TO   = 1.0f

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
private const val CENTER_DOT_SHADOW_RADIUS_DP = 2.2f
private const val CENTER_DOT_RADIUS_DP    = 2.0f
private const val LOADING_ARC_SWEEP_ANGLE = 260f
private const val REPORT_MODE_SHADOW_ALPHA = 0.35f
private const val NORMAL_MODE_SHADOW_ALPHA = 0.22f

// ── Loading arc gradient stops ────────────────────────────────────────────────
private const val LOADING_GRADIENT_TAIL_START = 0.716f
private const val LOADING_GRADIENT_HEAD       = 0.721f
private const val LOADING_GRADIENT_CUTOFF     = 0.726f

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

// ── Size badge (corner overlay on spot marker, preserved from legacy) ────────
private val   BADGE_SIZE          = 14.dp
private val   BADGE_BORDER_WIDTH  = 1.5.dp
private val   BADGE_OFFSET        = 2.dp   // overflow beyond marker corner
private val   BADGE_FONT_SIZE     = 7.sp
private val   BADGE_LINE_HEIGHT   = 9.sp

// Per-marker metadata that travelled in Marker.title before — we now hold it
// off-marker so we can set Marker.title = null on every marker and suppress
// Google Maps' default info window (the "title + snippet" balloon shown on
// tap). Coordinates is a stable, value-equal key since we pass spot/zone lats
// and lons through unchanged.
private data class SpotMeta(
    val spotId: String,
    val sizeCategory: VehicleSize?,
    val enRouteCount: Int,
    val reliability: SpotReliabilityLevel,
)

// ── Marker content IDs ──────────────────────────────────────────────────────
// Badge markers: contentId encodes vehicleId + state suffix so the bitmap
// cache stores one entry per vehicle×state.
private fun vehicleBadgeContentId(v: ParkedVehicleView, selected: Boolean, dim: Boolean): String {
    val suffix = when {
        selected -> "sel"
        dim      -> "dim"
        else     -> "nrm"
    }
    return "vehicle_badge_${v.vehicleId.take(8)}_$suffix"
}

private const val MARKER_MY_CAR          = "my_car"
private const val MARKER_MY_CAR_SELECTED = "my_car_selected"
private const val MARKER_MY_CAR_DIM      = "my_car_dim"

// Free-spot markers — various variants (reliability / selected / dimmed).
private fun spotContentId(spot: Spot, selected: Boolean, dim: Boolean): String {
    val reliability = spot.toSpotReliabilityLevel().name.lowercase()
    val suffix = when {
        selected -> "sel"
        dim      -> "dim"
        else     -> "nrm"
    }
    return "free_spot_${reliability}_$suffix"
}

// Alpha applied to dimmed markers — visible enough to deter duplicate
// reports, subordinate enough that the centre pin dominates.
private const val DIM_MARKER_ALPHA = 0.35f
private const val MARKER_CLUSTER   = "cluster"
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
            if (kotlin.math.abs(other.location.latitude - seed.location.latitude) < thresholdDeg &&
                kotlin.math.abs(other.location.longitude - seed.location.longitude) < thresholdDeg
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

// ── Marker badge helpers ──────────────────────────────────────────────────────

/**
 * Single-character label shown on the size badge.
 * MEDIUM returns null → no badge (it is the most common size, no annotation needed).
 */
private fun VehicleSize.badgeLabel(): String? = when (this) {
    VehicleSize.MOTO   -> "M"
    VehicleSize.SMALL  -> "S"
    VehicleSize.MEDIUM -> null
    VehicleSize.LARGE  -> "L"
    VehicleSize.VAN    -> "V"
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
     * Enriched parking sessions from [ObserveParkedVehiclesUseCase].
     * When non-empty, badge markers are rendered instead of the legacy
     * teardrop for the home screen. [parkingLocation] still drives
     * ParkingLocationScreen which does not have vehicle context.
     */
    parkedVehicles: List<ParkedVehicleView> = emptyList(),
    cameraTarget: CameraTarget? = null,
    /** User's saved habitual places. Rendered as circular markers with the chosen icon. */
    zones: List<Zone> = emptyList(),
    selectedSpotId: String? = null,
    /** When true, the parked-car marker bypasses the [dimSpots] pass and renders at full opacity. */
    isMyCarSelected: Boolean = false,
    reportMode: Boolean = false,
    isAnyItemSelected: Boolean = false,
    isLoading: Boolean = false,
    /**
     * When true, every marker EXCEPT the currently-focused one (the
     * selected spot via [selectedSpotId] or the parked car via
     * [isMyCarSelected]) renders with [DIM_MARKER_ALPHA] opacity via a
     * distinct "_dim" contentId. The kmpmaps bitmap cache keys on
     * contentId, so flipping this flag re-rasterises the affected
     * markers — flipping a Modifier.alpha inside the existing lambda
     * would reuse the cached bitmap and the dim would never appear.
     * Used by the host to subordinate non-focus markers while the user
     * has a selection or is positioning a new spot (Home's Reporting
     * mode + selection states).
     */
    dimSpots: Boolean = false,
    onSpotClick: (String) -> Unit = {},
    onMyCarClick: () -> Unit = {},
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
    // dimSpots + isMyCarSelected are part of the cache key because both
    // flip per-marker contentIds (full-opacity ↔ "_dim" variants, plus the
    // parking-selected exception). Without them, the remembered list would
    // hold the original Markers and kmpmaps would keep showing the cached
    // full-opacity bitmaps after the host enters a focus state.
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
                            spot.toSpotReliabilityLevel()
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
    val zoneIdByCoords: Map<Coordinates, String> = remember(zones) {
        zones.associate { Coordinates(it.lat, it.lon) to it.id }
    }

    val markers = remember(clusters, parkingLocation, parkedVehicles, selectedSpotId, dimSpots, isMyCarSelected, zones) {
        buildList {
            // Zone markers — added FIRST so spot/parking markers render on top
            // (kmpmaps draws in list order; later entries z-index above earlier).
            // Zones are background context, not actionable like spots.
            zones.forEach { zone ->
                add(
                    Marker(
                        coordinates = Coordinates(zone.lat, zone.lon),
                        // title = null suppresses the Google Maps default info-window
                        // balloon. Zone ID is recovered via zoneIdByCoords in the
                        // click handler instead.
                        title = null,
                        // Per-iconKey contentId: zones with the same icon share a bitmap.
                        contentId = "$MARKER_ZONE_PREFIX${zone.iconKey}",
                    ),
                )
            }
            if (parkedVehicles.isNotEmpty()) {
                // Name-tag markers: amber plate per active parking session.
                parkedVehicles.forEach { v ->
                    add(
                        Marker(
                            coordinates = Coordinates(v.location.latitude, v.location.longitude),
                            title = null,
                            contentId = vehicleBadgeContentId(v, selected = isMyCarSelected, dim = dimSpots && !isMyCarSelected),
                        ),
                    )
                }
            } else {
                // Fallback: legacy teardrop (used by ParkingLocationScreen which
                // does not supply parkedVehicles).
                parkingLocation?.let {
                    add(
                        Marker(
                            coordinates = Coordinates(it.latitude, it.longitude),
                            title = null,
                            contentId = when {
                                isMyCarSelected -> MARKER_MY_CAR_SELECTED
                                dimSpots -> MARKER_MY_CAR_DIM
                                else -> MARKER_MY_CAR
                            },
                        ),
                    )
                }
            }
            clusters.forEach { cluster ->
                if (cluster.spots.size == 1) {
                    val spot = cluster.spots.first()
                    add(
                        Marker(
                            coordinates = Coordinates(spot.location.latitude, spot.location.longitude),
                            title = null,
                            contentId = spotContentId(
                                spot = spot,
                                selected = spot.id == selectedSpotId,
                                dim = dimSpots && spot.id != selectedSpotId
                            ),
                        ),
                    )
                } else {
                    add(
                        Marker(
                            coordinates = Coordinates(cluster.lat, cluster.lon),
                            title = null,
                            contentId = MARKER_CLUSTER,
                        ),
                    )
                }
            }
        }
    }

    // ── Custom marker composables ────────────────────────────────────────────
    // Three-marker system (MAP-MARKERS-REDESIGN-001):
    //   - VehicleBadgeMarker: amber round badge + car icon (larger than spot)
    //   - FreeSpotMarker: green teardrop + "P" (unified, no reliability tiers)
    //   - ZoneMarker: blue teardrop + darker-blue disc + zone icon
    // FreeSpot size/en-route overlays are preserved via FreeSpotWithOverlays.
    val customMarkerContent = remember(spotMetaByCoords, clusterCountByCoords, parkedVehicles, zones, isMyCarSelected, dimSpots) {
        val spotContentHandlers: List<Pair<String, @Composable (Marker) -> Unit>> = spotMetaByCoords.values.flatMap { meta ->
            val reliability = meta.reliability
            listOf(
                "free_spot_${reliability.name.lowercase()}_nrm" to { _: Marker ->
                    FreeSpotWithOverlays(meta)
                },
                "free_spot_${reliability.name.lowercase()}_sel" to { _: Marker ->
                    FreeSpotWithOverlays(meta, selected = true)
                },
                "free_spot_${reliability.name.lowercase()}_dim" to { _: Marker ->
                    Box(modifier = Modifier.alpha(DIM_MARKER_ALPHA)) {
                        FreeSpotWithOverlays(meta)
                    }
                }
            )
        }

        val baseHandlers: Map<String, @Composable (Marker) -> Unit> = mapOf(
            // ── Legacy fallback teardrop (ParkingLocationScreen) ──
            MARKER_MY_CAR to { _ -> MyVehicleMarker() },
            MARKER_MY_CAR_SELECTED to { _ -> MyVehicleMarker(selected = true) },
            MARKER_MY_CAR_DIM to { _ ->
                Box(modifier = Modifier.alpha(DIM_MARKER_ALPHA)) { MyVehicleMarker() }
            },
            MARKER_CLUSTER to { marker ->
                FreeSpotClusterMarker(count = clusterCountByCoords[marker.coordinates] ?: 0)
            },
        )

        baseHandlers + spotContentHandlers.toMap() + parkedVehicles.flatMap { v ->
            // Explicit type annotation preserves @Composable on each lambda through flatMap.
            val entries: List<Pair<String, @Composable (Marker) -> Unit>> = listOf(
                vehicleBadgeContentId(v, selected = false, dim = false) to { _: Marker ->
                    VehicleBadgeMarker()
                },
                vehicleBadgeContentId(v, selected = true, dim = false) to { _: Marker ->
                    VehicleBadgeMarker(selected = true)
                },
                vehicleBadgeContentId(v, selected = false, dim = true) to { _: Marker ->
                    Box(modifier = Modifier.alpha(DIM_MARKER_ALPHA)) { VehicleBadgeMarker() }
                },
            )
            entries
        }.toMap() + zones.associate { zone ->
            // Zones with the same iconKey share a cached bitmap (identical visual).
            "$MARKER_ZONE_PREFIX${zone.iconKey}" to { _: Marker ->
                ZoneMarker(icon = zoneIconFor(zone.iconKey))
            }
        }
    }

    // ── Track real camera center (set by the map, not by us) ──────────────
    var actualCamLat by remember { mutableStateOf<Float?>(null) }
    var actualCamLon by remember { mutableStateOf<Float?>(null) }

    // ── Camera movement detection (debounced 280ms) ──────────────────────
    var cameraMoving by remember { mutableStateOf(false) }
    LaunchedEffect(actualCamLat, actualCamLon) {
        if (actualCamLat != null) {
            cameraMoving = true
            delay(CAMERA_MOVING_DEBOUNCE_MS)
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

    Box(modifier = modifier) {
        Map(
            modifier = Modifier.fillMaxSize(),
            cameraPosition = cameraPosition,
            properties = MapProperties(
                isMyLocationEnabled = true,
                isTrafficEnabled = false,
                mapTheme = MapTheme.SYSTEM,
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
            customMarkerContent = customMarkerContent,
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
                        cid == MARKER_MY_CAR_SELECTED ||
                        cid == MARKER_MY_CAR_DIM ||
                        cid?.startsWith("vehicle_badge_") == true -> onMyCarClick()
                    cid?.startsWith(MARKER_ZONE_PREFIX) == true ->
                        zoneIdByCoords[marker.coordinates]?.let(onZoneClick)
                    cid == MARKER_CLUSTER -> Unit // cluster taps are inert
                    else ->
                        spotMetaByCoords[marker.coordinates]?.let { onSpotClick(it.spotId) }
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
        val pinKind = config.centerPin
        if (pinKind != null) {
            when (pinKind) {
                CenterPinKind.Report -> ReportCenterPin(
                    cameraMoving = cameraMoving,
                    modifier = Modifier.align(Alignment.Center),
                )
                CenterPinKind.Parking -> ParkingCenterPin(
                    cameraMoving = cameraMoving,
                    modifier = Modifier.align(Alignment.Center),
                )
                is CenterPinKind.Zone -> ZoneCenterPin(
                    icon = pinKind.icon,
                    cameraMoving = cameraMoving,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        } else {
            val indicatorColor = if (reportMode) PapGreen else Color.White
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
 * Wraps [FreeSpotMarker] with the legacy overlays that the new Design System
 * pin does not include by itself:
 *  - corner [SpotSizeBadge] when the spot has a non-MEDIUM vehicle size
 *  - top-left [SpotEnRouteDot] when other users are currently driving toward it
 *
 * Metadata (size + en-route count) arrives via [meta], looked up by the caller
 * from the coords-keyed side map (spotMetaByCoords). Keeping the overlays here
 * lets [FreeSpotMarker] stay generic (reliability + ttl + selected only) while
 * preserving the existing product affordances.
 */
@Composable
private fun FreeSpotWithOverlays(
    meta: SpotMeta?,
    selected: Boolean = false,
) {
    val sizeCategory = meta?.sizeCategory
    val enRouteCount = meta?.enRouteCount ?: 0
    val reliability = meta?.reliability ?: SpotReliabilityLevel.HIGH
    Box {
        FreeSpotMarker(selected = selected, reliability = reliability)
        val label = sizeCategory?.badgeLabel()
        if (label != null) {
            SpotSizeBadge(
                label = label,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = BADGE_OFFSET, y = BADGE_OFFSET),
            )
        }
        if (enRouteCount > 0) {
            SpotEnRouteDot(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = -BADGE_OFFSET, y = -BADGE_OFFSET),
            )
        }
    }
}

/**
 * Small circular badge overlaid on the bottom-end corner of the free-spot
 * marker. Shows a single letter for the [VehicleSize] that freed the spot.
 * Forest-on-green styling reads consistently across all four reliability
 * pin colours — the previous variant-coupled colouring was lost in the swap
 * but the badge itself remains a useful product affordance.
 */
@Composable
private fun SpotSizeBadge(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(BADGE_SIZE)
            .background(PapForestDark, CircleShape)
            .border(BADGE_BORDER_WIDTH, PapGreen, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = BADGE_FONT_SIZE,
            lineHeight = BADGE_LINE_HEIGHT,
            fontWeight = FontWeight.ExtraBold,
            color = PapGreen,
        )
    }
}

// ── en-route dot (top-left corner of spot marker) ────────────────────────────
private val EN_ROUTE_DOT_SIZE = 8.dp

/**
 * Small blue dot shown on the top-left corner of a free-spot marker when at
 * least one user is currently navigating to the spot.
 */
@Composable
private fun SpotEnRouteDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(EN_ROUTE_DOT_SIZE)
            .background(PapBlue, CircleShape),
    )
}

