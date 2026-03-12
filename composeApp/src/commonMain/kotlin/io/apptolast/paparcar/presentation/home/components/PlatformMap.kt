package io.apptolast.paparcar.presentation.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import io.apptolast.paparcar.ui.theme.PapForest
import io.apptolast.paparcar.ui.theme.PapForestDark
import io.apptolast.paparcar.ui.theme.PapGreen

// ── Marker content IDs ──────────────────────────────────────────────────────
private const val MARKER_MY_CAR = "my_car"
private const val MARKER_FREE_SPOT = "free_spot"

@Composable
fun PlatformMap(
    spots: List<Spot>,
    userLocation: GpsPoint?,
    parkingLocation: GpsPoint?,
    onSpotClick: (String) -> Unit,
    onCameraMove: (lat: Double, lon: Double) -> Unit,
    modifier: Modifier = Modifier,
    cameraTarget: CameraTarget? = null,
) {
    // ── Build markers list ───────────────────────────────────────────────────
    val markers = remember(spots, parkingLocation) {
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
                        contentId = MARKER_FREE_SPOT,
                    ),
                )
            }
        }
    }

    // ── Custom marker composables ────────────────────────────────────────────
    val customMarkerContent = remember {
        mapOf<String, @Composable (Marker) -> Unit>(
            MARKER_MY_CAR to { _ -> MyCarMarkerContent() },
            MARKER_FREE_SPOT to { _ -> SpotMarkerContent() },
        )
    }

    // ── Track real camera center+zoom (set by the map, not by us) ──────────
    var actualCamLat by remember { mutableStateOf<Float?>(null) }
    var actualCamLon by remember { mutableStateOf<Float?>(null) }
    var actualCamZoom by remember { mutableStateOf<Float?>(null) }

    val cameraPosition = rememberCameraAnimationState(
        cameraTarget = cameraTarget,
        userLocation = userLocation,
        actualCamLat = actualCamLat,
        actualCamLon = actualCamLon,
        actualCamZoom = actualCamZoom,
    )

    // ── Map styling ──────────────────────────────────────────────────────────
    val isDark = isSystemInDarkTheme()
    var mapLoaded by remember { mutableStateOf(false) }
    val backgroundColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary

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
                actualCamZoom = pos.zoom
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

        // ── Loading overlay ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible = !mapLoaded,
            exit = fadeOut(animationSpec = tween(600)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = primaryColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp),
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
    actualCamZoom: Float?,
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
        actualCamZoom?.let { animZoom.snapTo(it) }
        val (targetLat, targetLon, targetZoom) = if (target.boundsLat2 != null && target.boundsLon2 != null) {
            Triple(
                ((target.lat + target.boundsLat2) / 2.0).toFloat(),
                ((target.lon + target.boundsLon2) / 2.0).toFloat(),
                14f,
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
private fun SpotMarkerContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(PapGreen, CircleShape)
                .border(3.dp, PapForestDark, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.DirectionsCar,
                contentDescription = null,
                tint = PapForestDark,
                modifier = Modifier.size(20.dp),
            )
        }
        PinTail(color = PapForestDark, width = 10.dp, height = 8.dp)
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
