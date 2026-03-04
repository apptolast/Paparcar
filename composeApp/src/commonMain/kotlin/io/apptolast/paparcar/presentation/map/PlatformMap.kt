package io.apptolast.paparcar.presentation.map

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.GpsPoint

@Composable
expect fun PlatformMap(
    spots: List<Spot>,
    userLocation: GpsPoint?,
    userParking: UserParking?,
    onSpotClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    showMapControls: Boolean = true,
    cameraTarget: CameraTarget? = null,
)