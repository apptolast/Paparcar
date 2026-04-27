package io.apptolast.paparcar.presentation.vehicles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import io.apptolast.paparcar.presentation.history.HistoryScreen
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.vehicle_detail_cd_back
import paparcar.composeapp.generated.resources.vehicle_detail_tab_details
import paparcar.composeapp.generated.resources.vehicle_detail_tab_history

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailScreen(
    vehicleId: String,
    onNavigateBack: () -> Unit = {},
    onNavigateToMap: (lat: Double, lon: Double) -> Unit = { _, _ -> },
    onEditVehicle: (vehicleId: String) -> Unit = {},
    onConfigureBluetooth: (vehicleId: String) -> Unit = {},
    vehiclesViewModel: VehiclesViewModel = koinViewModel(),
) {
    val state by vehiclesViewModel.state.collectAsState()
    val vehicleWithStats = state.vehicles.firstOrNull { it.vehicle.id == vehicleId }

    val pagerState = rememberPagerState(pageCount = { TAB_COUNT })
    val scope = rememberCoroutineScope()
    val tabLabels = listOf(
        stringResource(Res.string.vehicle_detail_tab_details),
        stringResource(Res.string.vehicle_detail_tab_history),
    )

    val title = vehicleWithStats?.let {
        listOfNotNull(it.vehicle.brand, it.vehicle.model).joinToString(" ").ifBlank { null }
    } ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.vehicle_detail_cd_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                tabLabels.forEachIndexed { index, label ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(label) },
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    TAB_DETAILS -> VehicleDetailsTab(
                        vehicleId = vehicleId,
                        vehicleWithStats = vehicleWithStats,
                        onEditVehicle = onEditVehicle,
                        onConfigureBluetooth = onConfigureBluetooth,
                    )
                    TAB_HISTORY -> Box(modifier = Modifier.fillMaxSize()) {
                        HistoryScreen(
                            vehicleId = vehicleId,
                            onNavigateBack = onNavigateBack,
                            onNavigateToMap = onNavigateToMap,
                        )
                    }
                }
            }
        }
    }
}

private const val TAB_COUNT   = 2
private const val TAB_DETAILS = 0
private const val TAB_HISTORY = 1
