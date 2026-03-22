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
import com.swmansion.kmpmaps.core.MapUISettings
import com.swmansion.kmpmaps.core.Marker
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.presentation.map.CameraTarget
import io.apptolast.paparcar.ui.theme.PapForestDark
import io.apptolast.paparcar.ui.theme.PapGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// ── Marker content IDs ──────────────────────────────────────────────────────
private const val MARKER_MY_CAR = "my_car"
private const val MARKER_FREE_SPOT = "free_spot"
private const val MARKER_FREE_SPOT_SELECTED = "free_spot_selected"

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
) {
    // ── Build markers list ───────────────────────────────────────────────────
    val markers = remember(spots, parkingLocation, selectedSpotId) {
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
            spots.forEach { spot ->
                add(
                    Marker(
                        coordinates = Coordinates(
                            spot.location.latitude,
                            spot.location.longitude,
                        ),
                        title = spot.id,
                        contentId = if (spot.id == selectedSpotId) MARKER_FREE_SPOT_SELECTED
                        else MARKER_FREE_SPOT,
                    ),
                )
            }
        }
    }

    // ── Custom marker composables ────────────────────────────────────────────
    val customMarkerContent = remember {
        mapOf<String, @Composable (Marker) -> Unit>(
            MARKER_MY_CAR to { _ -> MyCarMarkerContent() },
            MARKER_FREE_SPOT to { _ -> SpotMarkerContent(isSelected = false) },
            MARKER_FREE_SPOT_SELECTED to { _ -> SpotMarkerContent(isSelected = true) },
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
            delay(280L)
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
            isAnyItemSelected -> 0f         // hide when any item (spot or parking) is focused
            cameraMoving -> 1.25f           // enlarge while aiming
            else -> 1f
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
            pulseAlpha.snapTo(0.55f)
            pulseScale.snapTo(0.5f)
            launch { pulseAlpha.animateTo(0f, tween(600, easing = FastOutSlowInEasing)) }
            launch { pulseScale.animateTo(2.4f, tween(600, easing = FastOutSlowInEasing)) }
        }
    }
    // Loading arc: spins while content is being fetched
    val loadingTransition = rememberInfiniteTransition(label = "loading")
    val loadingAngle by loadingTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
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
        animationSpec = tween(300),
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
                onCameraMove(pos.coordinates.latitude, pos.coordinates.longitude)
            },
            onMarkerClick = { marker ->
                val id = marker.title ?: return@Map
                if (id != MARKER_MY_CAR) {
                    onSpotClick(id)
                }
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
        val shadowAlpha = if (reportMode) 0.35f else 0.22f

        Box(
            modifier = Modifier
                .size(56.dp)
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
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            // Ring + center dot (+ loading arc when fetching content)
            Canvas(modifier = Modifier.size(36.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val ringRadius = size.minDimension * 0.38f
                val ringStroke = 1.8.dp.toPx()

                // Shadow (subtle drop, offset down-right)
                drawCircle(
                    color = Color.Black.copy(alpha = shadowAlpha),
                    radius = ringRadius + 0.5f,
                    center = Offset(cx + 1f, cy + 1.5f),
                    style = Stroke(width = ringStroke + 1.5f),
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
                                    0f to Color.Transparent,                            // gap
                                    0.716f to indicatorColor.copy(alpha = 0.04f),          // tail start
                                    0.721f to indicatorColor,                              // HEAD (bright)
                                    0.726f to Color.Transparent,                           // cutoff
                                    1f to Color.Transparent,                            // gap wraps
                                ),
                                center = Offset(cx, cy),
                            ),
                            startAngle = 0f,
                            sweepAngle = 260f,
                            useCenter = false,
                            topLeft = Offset(cx - ringRadius, cy - ringRadius),
                            size = Size(ringRadius * 2, ringRadius * 2),
                            alpha = loadingAlpha,
                            style = Stroke(width = ringStroke + 1.5f, cap = StrokeCap.Round),
                        )
                    }
                }
                // Center dot shadow
                drawCircle(
                    color = Color.Black.copy(alpha = shadowAlpha),
                    radius = 3.dp.toPx(),
                    center = Offset(cx + 0.5f, cy + 1f),
                )
                // Center dot
                drawCircle(
                    color = indicatorColor,
                    radius = 2.5.dp.toPx(),
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
    val animZoom = remember { Animatable(cameraTarget?.zoom ?: 15f) }

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
                maxDelta < 0.002f -> 17f   // ~220 m
                maxDelta < 0.005f -> 16f   // ~550 m
                maxDelta < 0.010f -> 15f   // ~1.1 km
                maxDelta < 0.020f -> 14f   // ~2.2 km
                maxDelta < 0.040f -> 13f   // ~4.5 km
                else -> 12f
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
    val borderWidth = 2.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(-borderWidth), // 👈 solapa el triángulo bajo el borde
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(PapForestDark, CircleShape)
                .border(3.5.dp, PapGreen, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.DirectionsCar,
                contentDescription = null,
                tint = PapGreen,
                modifier = Modifier.size(24.dp),
            )
        }
        PinTail(color = PapGreen, width = 12.dp, height = 10.dp)
    }
}

@Composable
private fun SpotMarkerContent(isSelected: Boolean = false) {
    val size = if (isSelected) 46.dp else 38.dp
    val bg = if (isSelected) PapForestDark else PapGreen
    val border = if (isSelected) PapGreen else PapForestDark
    val iconTint = if (isSelected) PapGreen else PapForestDark
    val tailW = if (isSelected) 12.dp else 10.dp
    val tailH = if (isSelected) 10.dp else 8.dp
    val borderWidth = 2.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(-borderWidth), // 👈 solapa el triángulo bajo el borde
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(bg, CircleShape)
                .border(borderWidth, border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "P",
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (isSelected) 18.sp else 15.sp,
                lineHeight = if (isSelected) 20.sp else 17.sp,
                color = iconTint,
            )
        }
        PinTail(color = border, width = tailW, height = tailH)
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
