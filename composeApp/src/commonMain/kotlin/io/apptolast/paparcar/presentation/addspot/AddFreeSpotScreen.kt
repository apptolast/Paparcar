package io.apptolast.paparcar.presentation.addspot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.presentation.util.formatCoords
import io.apptolast.paparcar.presentation.util.locationDisplayText
import io.apptolast.paparcar.ui.components.PaparcarMapConfig
import io.apptolast.paparcar.ui.components.PaparcarMapView
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.add_free_spot_action
import paparcar.composeapp.generated.resources.add_free_spot_locating_address
import paparcar.composeapp.generated.resources.add_free_spot_subtitle
import paparcar.composeapp.generated.resources.add_free_spot_title
import paparcar.composeapp.generated.resources.error_gps_unavailable
import paparcar.composeapp.generated.resources.error_unknown
import paparcar.composeapp.generated.resources.home_manual_spot_reported

@Composable
fun AddFreeSpotScreen(
    onSpotReported: () -> Unit = {},
    bottomPadding: Dp = 0.dp,
    viewModel: AddFreeSpotViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val msgReported = stringResource(Res.string.home_manual_spot_reported)
    val msgErrorUnknown = stringResource(Res.string.error_unknown)
    val msgErrorGps = stringResource(Res.string.error_gps_unavailable)

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                AddFreeSpotEffect.SpotReported -> {
                    snackbarHostState.showSnackbar(msgReported)
                    onSpotReported()
                }
                is AddFreeSpotEffect.ShowError -> {
                    val msg = when (effect.error) {
                        is PaparcarError.Location.ProviderDisabled -> msgErrorGps
                        else -> msgErrorUnknown
                    }
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    AddFreeSpotContent(
        state = state,
        snackbarHostState = snackbarHostState,
        bottomPadding = bottomPadding,
        onCameraMove = { lat, lon ->
            viewModel.handleIntent(AddFreeSpotIntent.CameraPositionChanged(lat, lon))
        },
        onConfirmReport = { viewModel.handleIntent(AddFreeSpotIntent.ConfirmReport) },
    )
}

@Composable
internal fun AddFreeSpotContent(
    state: AddFreeSpotState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    bottomPadding: Dp = 0.dp,
    onCameraMove: (lat: Double, lon: Double) -> Unit = { _, _ -> },
    onConfirmReport: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomPadding),
    ) {
        // Map + sheet stacked vertically so the map fills only the area above
        // the sheet — the animated center pin stays centred on the visible
        // map, not hidden behind the sheet.
        Column(modifier = Modifier.fillMaxSize()) {
            PaparcarMapView(
                config = PaparcarMapConfig(showAnimatedCenterPin = true),
                spots = state.nearbySpots,
                userLocation = state.userGpsPoint,
                parkingLocation = null,
                cameraTarget = state.initialCameraTarget,
                onSpotClick = {},
                onCameraMove = onCameraMove,
                reportMode = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            ReportSheet(
                state = state,
                onConfirmReport = onConfirmReport,
            )
        }
        // Snackbar floats above the status bar so the success/error message is
        // visible regardless of where the sheet is positioned.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = SNACKBAR_TOP_OFFSET_DP.dp),
        )
    }
}

@Composable
internal fun ReportSheet(
    state: AddFreeSpotState,
    onConfirmReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = SHEET_CORNER_DP.dp, topEnd = SHEET_CORNER_DP.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        shadowElevation = SHEET_ELEVATION_DP.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(Res.string.add_free_spot_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.add_free_spot_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBTITLE_ALPHA),
            )
            AddressRow(state = state)
            Spacer(modifier = Modifier.height(4.dp))
            ConfirmButton(
                isLoading = state.isReporting,
                onClick = onConfirmReport,
            )
        }
    }
}

@Composable
private fun AddressRow(state: AddFreeSpotState) {
    val displayed = when {
        state.pinLocation != null -> locationDisplayText(
            placeInfo = state.pinLocation.placeInfo,
            address = state.pinLocation.address,
            lat = state.cameraLat ?: state.userGpsPoint?.latitude ?: 0.0,
            lon = state.cameraLon ?: state.userGpsPoint?.longitude ?: 0.0,
        )
        state.cameraLat != null && state.cameraLon != null ->
            stringResource(Res.string.add_free_spot_locating_address)
        else -> formatCoords(
            state.userGpsPoint?.latitude ?: 0.0,
            state.userGpsPoint?.longitude ?: 0.0,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.PinDrop,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = displayed,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ConfirmButton(
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CTA_CORNER_DP.dp),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Campaign,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = stringResource(Res.string.add_free_spot_action),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

private const val SHEET_CORNER_DP = 24
private const val SHEET_ELEVATION_DP = 8
private const val SUBTITLE_ALPHA = 0.72f
private const val CTA_CORNER_DP = 16
private const val SNACKBAR_TOP_OFFSET_DP = 8
