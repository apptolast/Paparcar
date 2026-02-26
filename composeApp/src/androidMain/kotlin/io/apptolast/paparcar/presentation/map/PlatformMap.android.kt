package io.apptolast.paparcar.presentation.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import io.apptolast.paparcar.domain.model.UserParkingSession
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotLocation

@Composable
actual fun PlatformMap(
    spots: List<Spot>,
    userLocation: SpotLocation?,
    userParking: UserParkingSession?,
    onSpotClick: (String) -> Unit,
    modifier: Modifier,
) {
    val defaultLatLng = LatLng(40.4168, -3.7038) // Madrid fallback

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            userLocation?.let { LatLng(it.latitude, it.longitude) } ?: defaultLatLng,
            15f,
        )
    }

    // Animate to user location the first time it becomes available (e.g. after GPS cold start).
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

    val uiSettings by remember {
        mutableStateOf(MapUiSettings(myLocationButtonEnabled = true))
    }

    val mapProperties by remember {
        mutableStateOf(MapProperties(isMyLocationEnabled = true))
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = uiSettings,
        properties = mapProperties,
    ) {
        // User's current location is shown by Google Maps' built-in blue dot
        // (MapProperties.isMyLocationEnabled = true). No custom marker needed.

        // User's parked car marker (blue-violet)
        userParking?.let { session ->
            Marker(
                state = MarkerState(position = LatLng(session.latitude, session.longitude)),
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
}
