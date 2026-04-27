package io.apptolast.paparcar.presentation.addspot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Campaign
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.ui.components.PaparcarMapConfig
import io.apptolast.paparcar.ui.components.PaparcarMapView
import io.apptolast.paparcar.ui.components.PaparcarBottomActionBar
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.add_free_spot_action
import paparcar.composeapp.generated.resources.add_free_spot_cd_back
import paparcar.composeapp.generated.resources.add_free_spot_title
import paparcar.composeapp.generated.resources.error_gps_unavailable
import paparcar.composeapp.generated.resources.error_unknown
import paparcar.composeapp.generated.resources.home_manual_spot_reported

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFreeSpotScreen(
    onNavigateBack: () -> Unit = {},
    onSpotReported: () -> Unit = {},
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.add_free_spot_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.add_free_spot_cd_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                PaparcarMapView(
                    config = PaparcarMapConfig(showAnimatedCenterPin = true),
                    spots = emptyList(),
                    userLocation = state.userGpsPoint,
                    parkingLocation = null,
                    onSpotClick = {},
                    onCameraMove = { lat, lon ->
                        viewModel.handleIntent(AddFreeSpotIntent.CameraPositionChanged(lat, lon))
                    },
                    reportMode = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            PaparcarBottomActionBar(
                label = stringResource(Res.string.add_free_spot_action),
                icon = Icons.Outlined.Campaign,
                onClick = { viewModel.handleIntent(AddFreeSpotIntent.ConfirmReport) },
                isLoading = state.isReporting,
            )
        }
    }
}
