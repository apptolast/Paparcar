package io.apptolast.paparcar.presentation.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.GpsPoint

@Composable
actual fun PlatformMap(
    spots: List<Spot>,
    userLocation: GpsPoint?,
    userParking: UserParking?,
    onSpotClick: (String) -> Unit,
    modifier: Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Map not available on iOS yet")
    }
}
