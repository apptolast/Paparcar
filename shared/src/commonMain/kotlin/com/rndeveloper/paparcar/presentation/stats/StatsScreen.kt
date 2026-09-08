@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rndeveloper.paparcar.presentation.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import com.rndeveloper.paparcar.domain.model.displayName
import com.rndeveloper.paparcar.presentation.util.collectAsStateLifecycleAware
import com.rndeveloper.paparcar.presentation.util.distanceString
import com.rndeveloper.paparcar.presentation.vehicles.DAY_SHORT_RES
import com.rndeveloper.paparcar.presentation.vehicles.HistoryFilter
import com.rndeveloper.paparcar.presentation.vehicles.MONTH_SHORT_RES
import com.rndeveloper.paparcar.presentation.vehicles.VehicleHistoryCalculator
import com.rndeveloper.paparcar.presentation.vehicles.components.ActivityCard
import com.rndeveloper.paparcar.presentation.vehicles.VehicleStatsBlock
import com.rndeveloper.paparcar.presentation.vehicles.components.HistoryFilterBar
import com.rndeveloper.paparcar.presentation.vehicles.vehicleStatsPeriodLabel
import com.rndeveloper.paparcar.ui.components.PapShimmerBlockScale
import com.rndeveloper.paparcar.ui.components.PapShimmerBox
import com.rndeveloper.paparcar.ui.components.chips.PaparcarFilterChip
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_activity_distance_partial
import paparcar.composeapp.generated.resources.my_car_unnamed_vehicle
import paparcar.composeapp.generated.resources.stats_panel_empty_body
import paparcar.composeapp.generated.resources.stats_panel_title
import paparcar.composeapp.generated.resources.licenses_cd_back

/**
 * The stats panel — every figure of one car and one period, with its comparison. [STATS-PANEL-001]
 *
 * Free is not turned away at the door: it enters, sees the first figure whole and the rest
 * redacted, and the Plus row underneath. Showing the room is the pitch; a locked door is not.
 */
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    onOpenPaywall: (PaywallReason) -> Unit,
    vehicleId: String? = null,
    viewModel: StatsViewModel = koinViewModel { parametersOf(vehicleId) },
) {
    val state by viewModel.state.collectAsStateLifecycleAware()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is StatsEffect.NavigateToPaywall -> onOpenPaywall(effect.reason)
            }
        }
    }

    StatsContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onIntent = viewModel::handleIntent,
    )
}

@Composable
fun StatsContent(
    state: StatsState,
    onNavigateBack: () -> Unit,
    onIntent: (StatsIntent) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.stats_panel_title),
                        style = PaparcarType.current.screenTitle,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(Res.string.licenses_cd_back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = LIST_V_PAD_DP.dp),
        ) {
            // A panel that has not resolved yet must not claim the car has no figures — same rule
            // the history timeline lives by. [UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001]
            if (state.isLoading) {
                item(key = "skeleton") { StatsSkeleton() }
                return@LazyColumn
            }

            if (state.showsVehicleSelector) {
                item(key = "vehicles") {
                    VehicleChips(state = state, onIntent = onIntent)
                    Spacer(Modifier.height(SECTION_GAP_DP.dp))
                }
            }

            item(key = "filters") {
                HistoryFilterBar(
                    activeFilter = state.filter,
                    onFilterSelected = { onIntent(StatsIntent.SetFilter(it)) },
                )
                Spacer(Modifier.height(SECTION_GAP_DP.dp))
            }

            val stats = state.periodStats
            if (stats != null) {
                item(key = "chart") {
                    // The same bars the ficha draws, from the same builder and the same window —
                    // the panel is a bigger view of one car, not a second interpretation of it.
                    // [UI-HISTORY-THE-CHART-SPANS-WHAT-THE-FILTER-SPANS-001]
                    Box(Modifier.padding(horizontal = H_PAD_DP.dp)) {
                        ActivityCard(
                            data = VehicleHistoryCalculator.buildActivityBuckets(
                                sessions = state.filteredSessions,
                                filter = state.filter,
                                dayLabels = DAY_SHORT_RES.map { stringResource(it) },
                                monthNamesShort = MONTH_SHORT_RES.map { stringResource(it) },
                            ),
                            total = stats.parkings,
                            distanceText = stats.distance?.let { distance ->
                                val km = distanceString(distance.meters)
                                if (distance.isComplete) km
                                else stringResource(
                                    Res.string.history_activity_distance_partial,
                                    km,
                                    distance.fromParkings,
                                )
                            },
                        )
                    }
                    Spacer(Modifier.height(SECTION_GAP_DP.dp))
                }
            }
            if (stats == null) {
                item(key = "empty") {
                    Text(
                        text = stringResource(Res.string.stats_panel_empty_body),
                        style = PaparcarType.current.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = H_PAD_DP.dp, vertical = EMPTY_V_PAD_DP.dp),
                    )
                }
            } else {
                item(key = "figures") {
                    Box(Modifier.padding(horizontal = H_PAD_DP.dp)) {
                        VehicleStatsBlock(
                            stats = stats,
                            periodLabel = vehicleStatsPeriodLabel(state.filter, state.isPlus),
                            lifetimeParkings = state.lifetimeParkings,
                            lifetimeSpotsReleased = state.lifetimeSpotsReleased,
                            showLifetime = state.filter != HistoryFilter.All,
                            costOrigin = state.costOrigin,
                            isPlus = state.isPlus,
                            onOpenPaywall = { onIntent(StatsIntent.OpenPaywall) },
                        )
                    }
                }
            }
        }
    }
}

/** Which car the figures belong to. Only rendered when there is more than one to choose. */
@Composable
private fun VehicleChips(state: StatsState, onIntent: (StatsIntent) -> Unit) {
    val fallback = stringResource(Res.string.my_car_unnamed_vehicle)
    LazyRow(
        contentPadding = PaddingValues(horizontal = H_PAD_DP.dp),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP_DP.dp),
    ) {
        items(state.vehicles.orEmpty()) { vws ->
            val index = state.vehicles.orEmpty().indexOf(vws)
            PaparcarFilterChip(
                label = vws.vehicle.displayName(fallback = fallback),
                selected = index == state.selectedVehicleIndex,
                onClick = { onIntent(StatsIntent.SelectVehicle(index)) },
            )
        }
    }
}

@Composable
private fun StatsSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = H_PAD_DP.dp),
        verticalArrangement = Arrangement.spacedBy(SKELETON_GAP_DP.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CHIP_GAP_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(SKELETON_CHIPS) {
                PapShimmerBox(
                    modifier = Modifier.height(SKELETON_CHIP_H_DP.dp).fillMaxWidth(SKELETON_CHIP_FRACTION),
                    alphaScale = PapShimmerBlockScale,
                )
            }
        }
        PapShimmerBox(
            modifier = Modifier.fillMaxWidth().height(SKELETON_CARD_H_DP.dp),
            alphaScale = PapShimmerBlockScale,
        )
    }
}

private const val H_PAD_DP = 16
private const val LIST_V_PAD_DP = 8
private const val SECTION_GAP_DP = 12
private const val EMPTY_V_PAD_DP = 24
private const val CHIP_GAP_DP = 8
private const val SKELETON_GAP_DP = 12
private const val SKELETON_CHIPS = 3
private const val SKELETON_CHIP_H_DP = 32
private const val SKELETON_CHIP_FRACTION = 0.28f
private const val SKELETON_CARD_H_DP = 220
