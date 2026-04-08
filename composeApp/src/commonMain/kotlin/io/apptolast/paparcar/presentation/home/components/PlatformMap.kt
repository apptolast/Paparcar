package io.apptolast.paparcar.presentation.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.presentation.map.CameraTarget
import io.apptolast.paparcar.ui.theme.PapAmber
import io.apptolast.paparcar.ui.theme.PapBlue
import io.apptolast.paparcar.ui.theme.PapForestDark
import io.apptolast.paparcar.ui.theme.PapGreen
import io.apptolast.paparcar.ui.theme.PapRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// ── Crosshair / pulse animations ─────────────────────────────────────────────
private const val CROSSHAIR_SCALE_HIDDEN  = 0f
private const val CROSSHAIR_SCALE_AIMING  = 1.25f
private const val CROSSHAIR_SCALE_NORMAL  = 1f
private const val PULSE_INITIAL_ALPHA     = 0.55f
private const val PULSE_INITIAL_SCALE     = 0.5f
private const val PULSE_MAX_SCALE         = 2.4f
private const val PULSE_ANIM_MS           = 600
private const val LOADING_ARC_ANIM_MS     = 1100
private const val LOADING_FADE_MS         = 300

// ── Location indicator (canvas drawing) ──────────────────────────────────────
private val   LOCATION_INDICATOR_BOX_SIZE = 56.dp
private val   RING_CANVAS_SIZE            = 36.dp
private const val RING_RADIUS_FACTOR      = 0.38f
private const val RING_STROKE_DP          = 1.8f
private const val PULSE_STROKE_DP         = 1.5f
private const val SHADOW_EXTRA_STROKE     = 1.5f
private const val SHADOW_OFFSET_X         = 1f
private const val SHADOW_OFFSET_Y         = 1.5f
private const val SHADOW_RADIUS_OFFSET    = 0.5f
private const val CENTER_DOT_SHADOW_RADIUS_DP = 3f
private const val CENTER_DOT_RADIUS_DP    = 2.5f
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

// ── Marker dimensions ─────────────────────────────────────────────────────────
private val MY_CAR_MARKER_SIZE        = 44.dp
private val MY_CAR_MARKER_BORDER      = 3.5.dp
private val MY_CAR_MARKER_ICON_SIZE   = 24.dp
private val MY_CAR_TAIL_WIDTH         = 12.dp
private val MY_CAR_TAIL_HEIGHT        = 10.dp
private val SPOT_MARKER_SELECTED_SIZE = 46.dp
private val SPOT_MARKER_DEFAULT_SIZE  = 38.dp
private val SPOT_MARKER_BORDER_WIDTH  = 2.dp
private val SPOT_TAIL_SELECTED_WIDTH  = 12.dp
private val SPOT_TAIL_SELECTED_HEIGHT = 10.dp
private val SPOT_TAIL_DEFAULT_WIDTH   = 10.dp
private val SPOT_TAIL_DEFAULT_HEIGHT  = 8.dp
private val CLUSTER_MARKER_SIZE       = 48.dp
private val CLUSTER_MARKER_BORDER     = 2.5.dp
private const val CLUSTER_MAX_DISPLAY = 99

// ── Marker typography ─────────────────────────────────────────────────────────
private val SPOT_FONT_SELECTED        = 18.sp
private val SPOT_FONT_DEFAULT         = 15.sp
private val SPOT_LINE_HEIGHT_SELECTED = 20.sp
private val SPOT_LINE_HEIGHT_DEFAULT  = 17.sp
private val CLUSTER_FONT_LARGE        = 13.sp  // count > 9
private val CLUSTER_FONT_SMALL        = 16.sp  // count <= 9
private val CLUSTER_LINE_HEIGHT_LARGE = 15.sp
private val CLUSTER_LINE_HEIGHT_SMALL = 18.sp

// ── Marker content IDs ──────────────────────────────────────────────────────
private const val MARKER_MY_CAR = "my_car"
private const val MARKER_FREE_SPOT = "free_spot"
private const val MARKER_FREE_SPOT_SELECTED = "free_spot_selected"
// Reliability variants — used once Spot.confidence is available in the domain model
private const val MARKER_FREE_SPOT_MEDIUM = "free_spot_medium"
private const val MARKER_FREE_SPOT_LOW = "free_spot_low"
private const val MARKER_FREE_SPOT_MANUAL = "free_spot_manual"
private const val MARKER_CLUSTER = "cluster"
private const val CAMERA_MOVING_DEBOUNCE_MS = 280L

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

@Composable
fun PlatformMap(
    spots: List<Spot>,
    userLocation: GpsPoint?,
    parkingLocation: GpsPoint?,
    onSpotClick: (String) -> Unit,
    onCameraMove: (lat: Double, lon: Double) -> Unit,
    modifier: Modifier = Modifier,
    cameraTarget: CameraTarget? = null,
    selectedSpotId: String? = null,
    reportMode: Boolean = false,
    isAnyItemSelected: Boolean = false,
    isLoading: Boolean = false,
    mapType: MapType = MapType.NORMAL,
) {
    // ── Clustering ───────────────────────────────────────────────────────────
    var currentZoom by remember { mutableStateOf(ZOOM_DEFAULT) }

    val clusters = remember(spots, currentZoom) {
        clusterSpots(spots, clusterThresholdDeg(currentZoom))
    }

    // ── Build markers list ───────────────────────────────────────────────────
    val markers = remember(clusters, parkingLocation, selectedSpotId) {
        buildList {
            parkingLocation?.let {
                add(
                    Marker(
                        coordinates = Coordinates(it.latitude, it.longitude),
                        title = MARKER_MY_CAR,
                        contentId = MARKER_MY_CAR,
                    ),
                )
            }
            clusters.forEach { cluster ->
                if (cluster.spots.size == 1) {
                    val spot = cluster.spots.first()
                    add(
                        Marker(
                            coordinates = Coordinates(spot.location.latitude, spot.location.longitude),
                            title = spot.id,
                            contentId = if (spot.id == selectedSpotId) MARKER_FREE_SPOT_SELECTED
                            else MARKER_FREE_SPOT,
                        ),
                    )
                } else {
                    add(
                        Marker(
                            coordinates = Coordinates(cluster.lat, cluster.lon),
                            // Encode count in title so ClusterMarkerContent can read it
                            title = "$MARKER_CLUSTER:${cluster.spots.size}",
                            contentId = MARKER_CLUSTER,
                        ),
                    )
                }
            }
        }
    }

    // ── Custom marker composables ────────────────────────────────────────────
    val customMarkerContent = remember {
        mapOf<String, @Composable (Marker) -> Unit>(
            MARKER_MY_CAR to { _ -> MyCarMarkerContent() },
            MARKER_FREE_SPOT to { _ -> SpotMarkerContent(isSelected = false) },
            MARKER_FREE_SPOT_SELECTED to { _ -> SpotMarkerContent(isSelected = true) },
            // Reliability variants — visually identical for now; will differentiate
            // when Spot.confidence is added to the domain model (Phase 4)
            MARKER_FREE_SPOT_MEDIUM to { _ -> SpotMarkerContent(isSelected = false, ringColor = PapAmber) },
            MARKER_FREE_SPOT_LOW to { _ -> SpotMarkerContent(isSelected = false, ringColor = PapRed) },
            MARKER_FREE_SPOT_MANUAL to { _ -> SpotMarkerContent(isSelected = false, ringColor = PapBlue) },
            MARKER_CLUSTER to { marker ->
                val count = marker.title
                    ?.removePrefix("$MARKER_CLUSTER:")
                    ?.toIntOrNull() ?: 0
                ClusterMarkerContent(count = count)
            },
        )
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
    val isDark = isSystemInDarkTheme()
    var mapLoaded by remember { mutableStateOf(false) }
    val showLoading = !mapLoaded || isLoading

    // ── Crosshair animations ─────────────────────────────────────────────
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
    // Loading arc: spins while content is being fetched
    val loadingTransition = rememberInfiniteTransition(label = "loading")
    val loadingAngle by loadingTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(LOADING_ARC_ANIM_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "loading_angle",
    )

    val cameraPosition = rememberCameraAnimationState(
        cameraTarget = cameraTarget,
        userLocation = userLocation,
        actualCamLat = actualCamLat,
        actualCamLon = actualCamLon,
    )

    // ── Map styling ──────────────────────────────────────────────────────────
    val backgroundColor = MaterialTheme.colorScheme.background
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
                mapType = mapType,
                androidMapProperties = AndroidMapProperties(
                    mapStyleOptions = if (isDark) GoogleMapsMapStyleOptions(DARK_MAP_STYLE) else null,
                ),
            ),
            uiSettings = MapUISettings(
                myLocationButtonEnabled = false,
                compassEnabled = false,
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
                onCameraMove(pos.coordinates.latitude, pos.coordinates.longitude)
            },
            onMarkerClick = { marker ->
                val id = marker.title ?: return@Map
                if (id == MARKER_MY_CAR || id.startsWith("$MARKER_CLUSTER:")) return@Map
                onSpotClick(id)
            },
            onMapLoaded = { mapLoaded = true },
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

        // ── Location indicator ───────────────────────────────────────────────
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
            // Ring + center dot (+ loading arc when fetching content)
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
                    withTransform({ rotate(loadingAngle, pivot = Offset(cx, cy)) }) {
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

// ── Camera animation ─────────────────────────────────────────────────────────

private const val CAMERA_ANIM_MS = 700

@Composable
private fun rememberCameraAnimationState(
    cameraTarget: CameraTarget?,
    userLocation: GpsPoint?,
    actualCamLat: Float?,
    actualCamLon: Float?,
): CameraPosition {
    val initCoords = cameraTarget?.let { Coordinates(it.lat, it.lon) }
        ?: userLocation?.let { Coordinates(it.latitude, it.longitude) }
        ?: Coordinates(0.0, 0.0)
    val animLat = remember { Animatable(initCoords.latitude.toFloat()) }
    val animLon = remember { Animatable(initCoords.longitude.toFloat()) }
    val animZoom = remember { Animatable(cameraTarget?.zoom ?: ZOOM_DEFAULT) }

    // Snap Animatables to the actual map position only right before launching
    // a programmatic animation. This ensures animations start from wherever
    // the user left the camera (after dragging/flinging) without creating a
    // feedback loop that would interrupt the native fling each frame.
    LaunchedEffect(cameraTarget) {
        val target = cameraTarget ?: return@LaunchedEffect
        // Sync to real camera position so animation starts from the correct point.
        actualCamLat?.let { animLat.snapTo(it) }
        actualCamLon?.let { animLon.snapTo(it) }
        val (targetLat, targetLon, targetZoom) = if (target.boundsLat2 != null && target.boundsLon2 != null) {
            val maxDelta = maxOf(
                abs(target.lat - target.boundsLat2),
                abs(target.lon - target.boundsLon2),
            ).toFloat()
            val zoom = when {
                maxDelta < BOUNDS_DELTA_220M  -> ZOOM_STREET        // ~220 m
                maxDelta < BOUNDS_DELTA_550M  -> ZOOM_CLOSE         // ~550 m
                maxDelta < BOUNDS_DELTA_1100M -> ZOOM_DEFAULT       // ~1.1 km
                maxDelta < BOUNDS_DELTA_2200M -> ZOOM_NEIGHBORHOOD  // ~2.2 km
                maxDelta < BOUNDS_DELTA_4500M -> ZOOM_DISTRICT      // ~4.5 km
                else                          -> ZOOM_WIDE
            }
            Triple(
                ((target.lat + target.boundsLat2) / 2.0).toFloat(),
                ((target.lon + target.boundsLon2) / 2.0).toFloat(),
                zoom,
            )
        } else {
            Triple(target.lat.toFloat(), target.lon.toFloat(), target.zoom)
        }
        val spec = tween<Float>(CAMERA_ANIM_MS, easing = FastOutSlowInEasing)
        launch { animLat.animateTo(targetLat, spec) }
        launch { animLon.animateTo(targetLon, spec) }
        launch { animZoom.animateTo(targetZoom, spec) }
    }

    return CameraPosition(
        coordinates = Coordinates(animLat.value.toDouble(), animLon.value.toDouble()),
        zoom = animZoom.value,
    )
}

// ── Custom marker composables ────────────────────────────────────────────────

@Composable
private fun MyCarMarkerContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(-SPOT_MARKER_BORDER_WIDTH),
    ) {
        Box(
            modifier = Modifier
                .size(MY_CAR_MARKER_SIZE)
                .background(PapForestDark, CircleShape)
                .border(MY_CAR_MARKER_BORDER, PapGreen, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.DirectionsCar,
                contentDescription = null,
                tint = PapGreen,
                modifier = Modifier.size(MY_CAR_MARKER_ICON_SIZE),
            )
        }
        PinTail(color = PapGreen, width = MY_CAR_TAIL_WIDTH, height = MY_CAR_TAIL_HEIGHT)
    }
}

@Composable
private fun SpotMarkerContent(
    isSelected: Boolean = false,
    ringColor: Color = if (isSelected) PapGreen else PapForestDark,
) {
    val size      = if (isSelected) SPOT_MARKER_SELECTED_SIZE  else SPOT_MARKER_DEFAULT_SIZE
    val bg        = if (isSelected) PapForestDark               else PapGreen
    val iconTint  = if (isSelected) PapGreen                    else PapForestDark
    val tailW     = if (isSelected) SPOT_TAIL_SELECTED_WIDTH    else SPOT_TAIL_DEFAULT_WIDTH
    val tailH     = if (isSelected) SPOT_TAIL_SELECTED_HEIGHT   else SPOT_TAIL_DEFAULT_HEIGHT
    val borderWidth = SPOT_MARKER_BORDER_WIDTH

    // Spring pop-in on first composition
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(-borderWidth),
        modifier = Modifier.scale(scale.value),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(bg, CircleShape)
                .border(borderWidth, ringColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "P",
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (isSelected) SPOT_FONT_SELECTED else SPOT_FONT_DEFAULT,
                lineHeight = if (isSelected) SPOT_LINE_HEIGHT_SELECTED else SPOT_LINE_HEIGHT_DEFAULT,
                color = iconTint,
            )
        }
        PinTail(color = ringColor, width = tailW, height = tailH)
    }
}

@Composable
private fun ClusterMarkerContent(count: Int) {
    val label = if (count > CLUSTER_MAX_DISPLAY) "$CLUSTER_MAX_DISPLAY+" else count.toString()
    Box(
        modifier = Modifier
            .size(CLUSTER_MARKER_SIZE)
            .background(PapGreen, CircleShape)
            .border(CLUSTER_MARKER_BORDER, PapForestDark, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (count > 9) CLUSTER_FONT_LARGE else CLUSTER_FONT_SMALL,
            lineHeight = if (count > 9) CLUSTER_LINE_HEIGHT_LARGE else CLUSTER_LINE_HEIGHT_SMALL,
            color = PapForestDark,
        )
    }
}

@Composable
private fun PinTail(color: Color, width: Dp, height: Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width / 2f, size.height)
            close()
        }
        drawPath(path, color = color, style = Fill)
    }
}

// ── Google Maps "Night Mode" style (applied on Android when system is dark) ──
private const val DARK_MAP_STYLE = """[
  {"elementType":"geometry","stylers":[{"color":"#212121"}]},
  {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#757575"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#212121"}]},
  {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#757575"}]},
  {"featureType":"administrative.country","elementType":"labels.text.fill","stylers":[{"color":"#9e9e9e"}]},
  {"featureType":"administrative.land_parcel","stylers":[{"visibility":"off"}]},
  {"featureType":"administrative.locality","elementType":"labels.text.fill","stylers":[{"color":"#bdbdbd"}]},
  {"featureType":"poi","elementType":"labels.text.fill","stylers":[{"color":"#757575"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#181818"}]},
  {"featureType":"poi.park","elementType":"labels.text.fill","stylers":[{"color":"#616161"}]},
  {"featureType":"poi.park","elementType":"labels.text.stroke","stylers":[{"color":"#1b1b1b"}]},
  {"featureType":"road","elementType":"geometry.fill","stylers":[{"color":"#2c2c2c"}]},
  {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#8a8a8a"}]},
  {"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#373737"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#3c3c3c"}]},
  {"featureType":"road.highway.controlled_access","elementType":"geometry","stylers":[{"color":"#4e4e4e"}]},
  {"featureType":"road.local","elementType":"labels.text.fill","stylers":[{"color":"#616161"}]},
  {"featureType":"transit","elementType":"labels.text.fill","stylers":[{"color":"#757575"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#000000"}]},
  {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#3d3d3d"}]}
]"""
