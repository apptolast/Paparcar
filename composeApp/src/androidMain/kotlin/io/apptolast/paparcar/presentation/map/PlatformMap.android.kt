package io.apptolast.paparcar.presentation.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import kotlinx.coroutines.launch

// ── Local design tokens (mirror values from EcoHomeScreen) ────────────────────
private val EcoGreen = Color(0xFF25F48C)
private val EcoGreenMuted = Color(0xFF133D28)

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
) {
    val defaultLatLng = LatLng(40.4168, -3.7038) // Madrid fallback

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            userLocation?.let { LatLng(it.latitude, it.longitude) } ?: defaultLatLng,
            15f,
        )
    }

    // Animate to user location the first time it becomes available.
    // Only fires once — does not follow the user while they move.
    var centeredOnUser by remember { mutableStateOf(userLocation != null) }
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

    // Animate to externally requested target (banner click, spot click, etc.)
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

    // Track map tile loading to avoid the initial flash.
    var mapLoaded by remember { mutableStateOf(false) }

    val isDark = isSystemInDarkTheme()
    val coroutineScope = rememberCoroutineScope()

    // All default Google Maps controls are hidden; replaced by custom FABs below.
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

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = uiSettings,
            properties = mapProperties,
            contentPadding = contentPadding,
            onMapLoaded = { mapLoaded = true },
        ) {
            // User's current location is shown by Google Maps' built-in blue dot
            // (MapProperties.isMyLocationEnabled = true). No custom marker needed.

            // User's parked car marker (violet)
            userParking?.let { session ->
                Marker(
                    state = MarkerState(position = LatLng(session.location.latitude, session.location.longitude)),
                    title = "Tu coche aparcado",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET),
                )
            }

            // Nearby available spots (green = free, red = taken)
            spots.forEach { spot ->
                val hue = if (spot.isActive)
                    BitmapDescriptorFactory.HUE_GREEN
                else
                    BitmapDescriptorFactory.HUE_RED
                Marker(
                    state = MarkerState(
                        position = LatLng(spot.location.latitude, spot.location.longitude)
                    ),
                    title = if (spot.isActive) "Plaza libre" else "Plaza ocupada",
                    snippet = "Reportado por ${spot.reportedBy}",
                    icon = BitmapDescriptorFactory.defaultMarker(hue),
                    onClick = {
                        onSpotClick(spot.id)
                        false
                    },
                )
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
                    .background(Color(0xFF0D1C14)), // EcoForest — matches app background
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF25F48C), // EcoGreen
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // ── Custom map control buttons — slide in/out from the right edge ──
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
// Map control buttons column — replaces all default Google Maps UI controls
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MapControlButtons(
    userLocation: GpsPoint?,
    userParking: UserParking?,
    sheetBottomPadding: Dp,
    onMyLocation: () -> Unit,
    onParkedCar: () -> Unit,
    onMidpoint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasParking = userParking != null
    val hasBothPoints = userLocation != null && userParking != null

    Column(
        modifier = modifier.padding(end = 12.dp, bottom = sheetBottomPadding + 12.dp),
        horizontalAlignment = Alignment.End,
    ) {
        // ── Midpoint — only when both positions are known ─────────────────
        AnimatedVisibility(
            visible = hasBothPoints,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            MapControlFab(
                icon = Icons.Outlined.Route,
                contentDescription = "Punto medio entre mi posición y el coche",
                onClick = onMidpoint,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }

        // ── Parked car — only when an active parking session exists ───────
        AnimatedVisibility(
            visible = hasParking,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            MapControlFab(
                icon = Icons.Outlined.DirectionsCar,
                contentDescription = "Ir a mi vehículo aparcado",
                iconTint = EcoGreen,
                surfaceColor = EcoGreenMuted,
                onClick = onParkedCar,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }

        // ── My location — always visible ──────────────────────────────────
        MapControlFab(
            icon = Icons.Outlined.MyLocation,
            contentDescription = "Centrar en mi ubicación",
            onClick = onMyLocation,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Individual FAB — consistent style with EcoFloatingHeader action buttons
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MapControlFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
) {
    val resolvedSurface = if (surfaceColor == Color.Unspecified)
        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    else
        surfaceColor
    val resolvedTint = if (iconTint == Color.Unspecified)
        MaterialTheme.colorScheme.onSurface
    else
        iconTint

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = resolvedSurface,
        shadowElevation = 4.dp,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = resolvedTint,
            modifier = Modifier
                .padding(10.dp)
                .size(22.dp),
        )
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