package io.apptolast.paparcar.presentation.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import androidx.compose.ui.platform.LocalContext
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.presentation.map.components.MapControlButtons
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.map_marker_car_here
import paparcar.composeapp.generated.resources.map_marker_free
import paparcar.composeapp.generated.resources.map_marker_my_car
import paparcar.composeapp.generated.resources.map_marker_occupied
import paparcar.composeapp.generated.resources.map_marker_reported_by

// ─────────────────────────────────────────────────────────────────────────────

@Composable
actual fun PlatformMap(
    spots: List<Spot>,
    userLocation: GpsPoint?,
    userParking: UserParking?,
    onSpotClick: (String) -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
    showMapControls: Boolean,
    cameraTarget: CameraTarget?,
    focusMarker: Pair<Double, Double>?,
) {
    val defaultLatLng = LatLng(40.4168, -3.7038) // Madrid fallback

    val context = LocalContext.current
    var myCarIcon    by remember { mutableStateOf<com.google.android.gms.maps.model.BitmapDescriptor?>(null) }
    var freeSpotIcon by remember { mutableStateOf<com.google.android.gms.maps.model.BitmapDescriptor?>(null) }
    var occupiedIcon by remember { mutableStateOf<com.google.android.gms.maps.model.BitmapDescriptor?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            cameraTarget?.let { LatLng(it.lat, it.lon) }
                ?: userLocation?.let { LatLng(it.latitude, it.longitude) }
                ?: defaultLatLng,
            cameraTarget?.zoom ?: 15f,
        )
    }

    var centeredOnUser by remember { mutableStateOf(userLocation != null || cameraTarget != null) }
    LaunchedEffect(userLocation) {
        if (userLocation != null && !centeredOnUser) {
            centeredOnUser = true
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(userLocation.latitude, userLocation.longitude),
                    15f,
                )
            )
        }
    }

    LaunchedEffect(cameraTarget) {
        if (cameraTarget != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(cameraTarget.lat, cameraTarget.lon),
                    cameraTarget.zoom,
                )
            )
        }
    }

    var mapLoaded by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()
    val coroutineScope = rememberCoroutineScope()

    // Capture theme colors before entering GoogleMap composable
    val backgroundColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary

    val uiSettings by remember {
        mutableStateOf(
            MapUiSettings(
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
                compassEnabled = false,
                mapToolbarEnabled = false,
            )
        )
    }

    val mapProperties by remember(isDark) {
        mutableStateOf(
            MapProperties(
                isMyLocationEnabled = true,
                mapStyleOptions = if (isDark) MapStyleOptions(DARK_MAP_STYLE) else null,
            )
        )
    }

    // Marker strings resolved outside GoogleMap composable
    val markerMyCar     = stringResource(Res.string.map_marker_my_car)
    val markerCarHere   = stringResource(Res.string.map_marker_car_here)
    val markerFree      = stringResource(Res.string.map_marker_free)
    val markerOccupied  = stringResource(Res.string.map_marker_occupied)

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = uiSettings,
            properties = mapProperties,
            contentPadding = contentPadding,
            onMapLoaded = {
                mapLoaded = true
                myCarIcon    = context.createMyCarMarkerIcon()
                freeSpotIcon = context.createFreeSpotMarkerIcon()
                occupiedIcon = context.createOccupiedSpotMarkerIcon()
            },
        ) {
            userParking?.let { session ->
                val icon = myCarIcon ?: return@let
                Marker(
                    state = MarkerState(position = LatLng(session.location.latitude, session.location.longitude)),
                    title = markerMyCar,
                    icon = icon,
                )
            }

            focusMarker?.let { (lat, lon) ->
                val icon = myCarIcon ?: return@let
                val sameAsActive = userParking?.let { p ->
                    p.location.latitude == lat && p.location.longitude == lon
                } ?: false
                if (!sameAsActive) {
                    Marker(
                        state = MarkerState(position = LatLng(lat, lon)),
                        title = markerCarHere,
                        icon = icon,
                    )
                }
            }

            val freeIcon = freeSpotIcon
            val occIcon  = occupiedIcon
            if (freeIcon != null && occIcon != null) {
                spots.forEach { spot ->
                    Marker(
                        state = MarkerState(
                            position = LatLng(spot.location.latitude, spot.location.longitude)
                        ),
                        title = if (spot.isActive) markerFree else markerOccupied,
                        snippet = stringResource(Res.string.map_marker_reported_by, spot.reportedBy),
                        icon = if (spot.isActive) freeIcon else occIcon,
                        onClick = {
                            onSpotClick(spot.id)
                            false
                        },
                    )
                }
            }
        }

        // ── Loading overlay — fades out once map tiles are ready ─────────
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

        // ── Custom map control buttons ─────────────────────────────────────
        AnimatedVisibility(
            visible = showMapControls,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            MapControlButtons(
                userLocation = userLocation,
                userParking = userParking,
                sheetBottomPadding = contentPadding.calculateBottomPadding(),
                onMyLocation = {
                    userLocation?.let { loc ->
                        coroutineScope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(loc.latitude, loc.longitude), 16f,
                                )
                            )
                        }
                    }
                },
                onParkedCar = {
                    userParking?.let { session ->
                        coroutineScope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(session.location.latitude, session.location.longitude), 17f,
                                )
                            )
                        }
                    }
                },
                onMidpoint = {
                    if (userLocation != null && userParking != null) {
                        val midLat = (userLocation.latitude + userParking.location.latitude) / 2.0
                        val midLon = (userLocation.longitude + userParking.location.longitude) / 2.0
                        coroutineScope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(LatLng(midLat, midLon), 15f)
                            )
                        }
                    }
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Google Maps "Night Mode" style — applied when the system is in dark theme.
// Source: Google Maps Platform Styling Wizard — Night preset.
// ─────────────────────────────────────────────────────────────────────────────
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
