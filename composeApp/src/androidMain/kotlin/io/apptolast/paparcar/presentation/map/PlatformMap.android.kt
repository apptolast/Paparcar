package io.apptolast.paparcar.presentation.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import io.apptolast.paparcar.domain.model.ParkingSession
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotLocation

@Composable
actual fun PlatformMap(
    spots: List<Spot>,
    userLocation: SpotLocation?,
    userParking: ParkingSession?,
    onSpotClick: (String) -> Unit,
    modifier: Modifier,
) {
    val defaultLatLng = LatLng(40.4168, -3.7038) // Madrid fallback
    val cameraTarget = userLocation
        ?.let { LatLng(it.latitude, it.longitude) }
        ?: defaultLatLng

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(cameraTarget, 15f)
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
    ) {
        // User's current location marker (blue)
        userLocation?.let { loc ->
            Marker(
                state = MarkerState(position = LatLng(loc.latitude, loc.longitude)),
                title = "Tu posición",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
            )
        }

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
