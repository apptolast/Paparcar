package io.apptolast.paparcar.presentation.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.apptolast.paparcar.domain.model.UserParkingSession
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotLocation

@Composable
expect fun PlatformMap(
    spots: List<Spot>,
    userLocation: SpotLocation?,
    userParking: UserParkingSession?,
    onSpotClick: (String) -> Unit,
    modifier: Modifier = Modifier,
)
