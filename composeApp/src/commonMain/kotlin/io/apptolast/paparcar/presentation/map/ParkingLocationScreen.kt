package io.apptolast.paparcar.presentation.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.presentation.home.components.PlatformMap
import io.apptolast.paparcar.presentation.map.components.MapControlButtons
import io.apptolast.paparcar.domain.error.PaparcarError
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.error_unknown
import paparcar.composeapp.generated.resources.map_cd_back
import paparcar.composeapp.generated.resources.map_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkingLocationScreen(
    onNavigateBack: () -> Unit = {},
    initialFocus: Pair<Double, Double>? = null,
    viewModel: ParkingLocationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Turn initialFocus into a CameraTarget on first composition.
    var cameraTarget by remember {
        mutableStateOf(
            initialFocus?.let { (lat, lon) -> CameraTarget(lat = lat, lon = lon, zoom = 16f) }
        )
    }

    fun moveCamera(lat: Double, lon: Double, zoom: Float = 17f) {
        cameraTarget = CameraTarget(lat, lon, zoom, token = (cameraTarget?.token ?: 0) + 1)
    }

    // Pre-resolve strings in Composable scope — cannot use stringResource inside LaunchedEffect
    val msgErrorUnknown = stringResource(Res.string.error_unknown)

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ParkingLocationEffect.NavigateToSpotDetails -> { /* future */ }
                is ParkingLocationEffect.ShowError -> snackbarHostState.showSnackbar(msgErrorUnknown)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.map_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.map_cd_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            PlatformMap(
                spots = state.spots,
                userLocation = state.userLocation,
                parkingLocation = initialFocus
                    ?.let { (lat, lon) ->
                        GpsPoint(
                            lat,
                            lon,
                            accuracy = 0f,
                            timestamp = 0L,
                            speed = 0f
                        )
                    }
                    ?: state.userParking?.location,
                onSpotClick = { spotId -> viewModel.handleIntent(ParkingLocationIntent.OnSpotSelected(spotId)) },
                onCameraMove = { _, _ -> },
                cameraTarget = cameraTarget,
                modifier = Modifier.fillMaxSize(),
            )

            MapControlButtons(
                userLocation = state.userLocation,
                userParking = state.userParking,
                sheetBottomPadding = 0.dp,
                onMyLocation = {
                    state.userLocation?.let { moveCamera(it.latitude, it.longitude, zoom = 16f) }
                },
                onParkedCar = {
                    state.userParking?.let { moveCamera(it.location.latitude, it.location.longitude) }
                },
                onMidpoint = {
                    val loc = state.userLocation
                    val parking = state.userParking
                    if (loc != null && parking != null) {
                        cameraTarget = CameraTarget(
                            lat = parking.location.latitude,
                            lon = parking.location.longitude,
                            boundsLat2 = loc.latitude,
                            boundsLon2 = loc.longitude,
                            token = (cameraTarget?.token ?: 0) + 1,
                        )
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd),
            )

            if (state.isLoading) {
                CircularProgressIndicator()
            }
        }
    }
}
