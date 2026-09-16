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
import androidx.compose.runtime.remember
import kotlin.math.roundToInt
import kotlin.time.Clock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import com.rndeveloper.paparcar.domain.stats.HeatMeasure
import com.rndeveloper.paparcar.domain.stats.projectPeriod
import com.rndeveloper.paparcar.domain.stats.projectYear
import com.rndeveloper.paparcar.presentation.util.durationText
import com.rndeveloper.paparcar.domain.stats.buildParkingHeatmap
import com.rndeveloper.paparcar.domain.model.displayName
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rndeveloper.paparcar.presentation.util.distanceString
import com.rndeveloper.paparcar.presentation.vehicles.DAY_SHORT_RES
import com.rndeveloper.paparcar.presentation.vehicles.HistoryFilter
import com.rndeveloper.paparcar.presentation.vehicles.MONTH_SHORT_RES
import com.rndeveloper.paparcar.presentation.vehicles.VehicleHistoryCalculator
import com.rndeveloper.paparcar.presentation.vehicles.availableChartMetrics
import com.rndeveloper.paparcar.presentation.vehicles.resolveChartMetric
import com.rndeveloper.paparcar.presentation.vehicles.components.ActivityCard
import com.rndeveloper.paparcar.presentation.vehicles.components.chartQualifierText
import com.rndeveloper.paparcar.presentation.vehicles.VehicleStatsBlock
import com.rndeveloper.paparcar.presentation.vehicles.components.HistoryFilterBar
import com.rndeveloper.paparcar.presentation.vehicles.vehicleStatsPeriodLabel
import com.rndeveloper.paparcar.ui.components.PapShimmerBlockScale
import com.rndeveloper.paparcar.ui.components.PapShimmerBox
import com.rndeveloper.paparcar.ui.components.chips.PaparcarFilterChip
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_activity_measure_partial
import paparcar.composeapp.generated.resources.my_car_unnamed_vehicle
import paparcar.composeapp.generated.resources.stats_panel_empty_body
import paparcar.composeapp.generated.resources.stats_pace_close
import paparcar.composeapp.generated.resources.stats_pace_distance
import paparcar.composeapp.generated.resources.stats_pace_search
import paparcar.composeapp.generated.resources.stats_pace_year
import paparcar.composeapp.generated.resources.stats_panel_title
import paparcar.composeapp.generated.resources.licenses_cd_back

/**
 * The stats panel — every figure of one car and one period, with its comparison. [STATS-PANEL-001]
 *
 * Free is not turned away at the door: it enters and sees every figure over its last 7 days, with
 * the Plus row underneath offering any period. [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001]
 */
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    onOpenPaywall: (PaywallReason) -> Unit,
    vehicleId: String? = null,
    viewModel: StatsViewModel = koinViewModel { parametersOf(vehicleId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
                    filters = state.periodFilters,
                    activeFilter = state.filter,
                    onFilterSelected = { onIntent(StatsIntent.SetFilter(it)) },
                    // This screen's Scaffold is `surface`; the bar used to assume the tone of the
                    // OTHER screen it lives on and painted a band across this one.
                    // [STATS-A-FILTER-THAT-CANNOT-ANSWER-IS-NOT-OFFERED-001]
                    containerColor = MaterialTheme.colorScheme.surface,
                )
                Spacer(Modifier.height(SECTION_GAP_DP.dp))
            }

            // The month that just closed — above everything, because it is the only thing here
            // nobody asked for and it is only here because it just happened.
            // [STATS-PLUS-REMEMBERS-AND-REPORTS-001]
            state.closedMonth?.let { recap ->
                item(key = "recap") {
                    Box(Modifier.padding(horizontal = H_PAD_DP.dp)) {
                        StatsMonthRecapCard(stats = recap)
                    }
                    Spacer(Modifier.height(SECTION_GAP_DP.dp))
                }
            }

            // The conclusion BEFORE the proof: everything below this card — the chart, the
            // figures, the places, the grid — is what backs it up.
            // [STATS-AN-INSIGHT-IS-A-VERDICT-WITH-ITS-EVIDENCE-001]
            if (state.insights.isNotEmpty()) {
                item(key = "insights") {
                    Box(Modifier.padding(horizontal = H_PAD_DP.dp)) {
                        StatsInsightsCard(insights = state.insights)
                    }
                    Spacer(Modifier.height(SECTION_GAP_DP.dp))
                }
            }

            val stats = state.periodStats
            if (stats != null) {
                item(key = "chart") {
                    // The same bars the ficha draws, from the same builder and the same window —
                    // the panel is a bigger view of one car, not a second interpretation of it.
                    // [UI-HISTORY-THE-CHART-SPANS-WHAT-THE-FILTER-SPANS-001]
                    // The panel offers the SAME metrics as the ficha, resolved against the same
                    // sessions: a bigger view of one car, never a second interpretation of it.
                    // [STATS-A-PERIOD-IS-A-SERIES-NOT-A-NUMBER-001]
                    val metric = resolveChartMetric(state.chartMetric, state.filteredSessions)
                    Box(Modifier.padding(horizontal = H_PAD_DP.dp)) {
                        ActivityCard(
                            data = VehicleHistoryCalculator.buildActivityBuckets(
                                sessions = state.filteredSessions,
                                filter = state.filter,
                                dayLabels = DAY_SHORT_RES.map { stringResource(it) },
                                monthNamesShort = MONTH_SHORT_RES.map { stringResource(it) },
                                metric = metric,
                            ),
                            total = stats.parkings,
                            qualifierText = chartQualifierText(metric, state.filteredSessions),
                            metric = metric,
                            metrics = availableChartMetrics(state.filteredSessions),
                            onMetricSelected = { onIntent(StatsIntent.SetChartMetric(it)) },
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
                            showLifetime = !state.isPlus || state.filter != HistoryFilter.All,
                            costPrice = state.costPrice,
                            isPlus = state.isPlus,
                            onOpenPaywall = { onIntent(StatsIntent.OpenPaywall) },
                            isAutoDetectionOn = state.isAutoDetectionOn,
                        )
                    }
                }
                // Where the period is HEADING — an estimate, so it keeps its own card and its own
                // tense, right after the figures it extrapolates. [STATS-AN-ESTIMATE-SAYS-IT-IS-ONE-001]
                item(key = "pace") {
                    val nowMs = remember { Clock.System.now().toEpochMilliseconds() }
                    val (rows, elapsedPercent) = paceRows(state, nowMs)
                    if (rows.isNotEmpty()) {
                        Spacer(Modifier.height(SECTION_GAP_DP.dp))
                        Box(Modifier.padding(horizontal = H_PAD_DP.dp)) {
                            StatsPaceCard(rows = rows, elapsedPercent = elapsedPercent)
                        }
                    }
                }
                // Where this car repeats, and what parking costs at each place — UNDER the figures,
                // because it answers the same question with more room: the grid names one place in
                // a tile, this names five and prices each one.
                // [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001]
                item(key = "places") {
                    Spacer(Modifier.height(SECTION_GAP_DP.dp))
                    Box(Modifier.padding(horizontal = H_PAD_DP.dp)) {
                        StatsPlacesCard(places = stats.places)
                    }
                }
                // WHEN you park, and when it costs you — the two dimensions the peak-hour and
                // usual-day tiles can only answer one at a time.
                // [STATS-THE-EXPENSIVE-HOUR-IS-MEASURED-001]
                item(key = "when") {
                    val searchGrid = remember(state.filteredSessions) {
                        buildParkingHeatmap(state.filteredSessions, HeatMeasure.SEARCH_TIME)
                    }
                    // The hunting grid is offered only when it HAS something: a chip that leads to
                    // "not enough parkings yet" is a chip that wasted a tap.
                    val measures = buildList {
                        add(HeatMeasure.PARKINGS)
                        if (!searchGrid.isEmpty) add(HeatMeasure.SEARCH_TIME)
                    }
                    val measure = state.heatMeasure.takeIf { it in measures } ?: HeatMeasure.PARKINGS
                    val grid = if (measure == HeatMeasure.SEARCH_TIME) {
                        searchGrid
                    } else {
                        remember(state.filteredSessions) {
                            buildParkingHeatmap(state.filteredSessions, HeatMeasure.PARKINGS)
                        }
                    }
                    Spacer(Modifier.height(SECTION_GAP_DP.dp))
                    Box(Modifier.padding(horizontal = H_PAD_DP.dp)) {
                        StatsWhenCard(
                            heatmap = grid,
                            measures = measures,
                            onMeasureSelected = { onIntent(StatsIntent.SetHeatMeasure(it)) },
                        )
                    }
                }
            }
        }
    }
}


/**
 * Where the period is heading — money first, because it is the figure someone can act on.
 * [STATS-AN-ESTIMATE-SAYS-IT-IS-ONE-001]
 *
 * ⛔ Only the three magnitudes that move independently: CO₂ and litres are constant multiples of the
 * kilometres for one car, so projecting them would print the same forecast three times in different
 * units — the finding F1 already made about the chart's series.
 */
@Composable
private fun paceRows(state: StatsState, nowMs: Long): Pair<List<PaceRow>, Int> {
    val stats = state.periodStats ?: return emptyList<PaceRow>() to 0
    val start = VehicleHistoryCalculator.windowStartMs(state.filter, nowMs)
    val end = VehicleHistoryCalculator.windowEndMs(state.filter, nowMs)
    if (start == null || end == null) return emptyList<PaceRow>() to 0
    val elapsed = nowMs - start
    val window = end - start

    val cost = stats.costEstimate?.let { money ->
        projectPeriod(money.amount, elapsed, window)?.let { it to money.currency }
    }
    val distance = stats.distance?.let { projectPeriod(it.value.toDouble(), elapsed, window) }
    val search = stats.searchTime?.let { projectPeriod(it.value.toDouble(), elapsed, window) }
    val fraction = cost?.first?.elapsedFraction ?: distance?.elapsedFraction ?: search?.elapsedFraction
        ?: return emptyList<PaceRow>() to 0

    val rows = buildList {
        cost?.let { (projection, currency) ->
            add(
                PaceRow(
                    label = stringResource(Res.string.stats_pace_close),
                    value = "${formatPaceMoney(projection.value)} $currency",
                ),
            )
            // A year is not one of the filter's periods — it is a PACE extrapolated, and only from a
            // month: seven days of somebody's life sample their week, not their year.
            projectYear(money(projection.value, elapsed, window), elapsed)?.let { yearly ->
                add(
                    PaceRow(
                        label = stringResource(Res.string.stats_pace_year),
                        value = "${formatPaceMoney(yearly)} $currency",
                    ),
                )
            }
        }
        distance?.let {
            add(PaceRow(stringResource(Res.string.stats_pace_distance), distanceString(it.value.toFloat())))
        }
        search?.let {
            add(PaceRow(stringResource(Res.string.stats_pace_search), durationText(it.value.toLong())))
        }
    }
    return rows to (fraction * PERCENT).roundToInt()
}

/** The measured part of the period, back out of its projection — what the yearly pace runs on. */
private fun money(projected: Double, elapsed: Long, window: Long): Double =
    projected * (elapsed.toDouble() / window)

/** Two decimals, like every other price on this screen. [FUEL-PRICE-RESOLVER-001] */
private fun formatPaceMoney(value: Double): String {
    val cents = (value * CENTS).roundToInt()
    return "${cents / CENTS.toInt()}.${(cents % CENTS.toInt()).toString().padStart(2, '0')}"
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

private const val PERCENT = 100
private const val CENTS = 100.0
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
