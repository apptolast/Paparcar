@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.vehicles

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Place
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.presentation.util.distanceString
import com.rndeveloper.paparcar.presentation.vehicles.components.ActiveSectionHeader
import com.rndeveloper.paparcar.presentation.vehicles.components.DayHeaderRow
import com.rndeveloper.paparcar.presentation.vehicles.components.EmptyHistoryState
import com.rndeveloper.paparcar.presentation.vehicles.components.EndedSessionTimelineNode
import com.rndeveloper.paparcar.presentation.vehicles.components.HistoryFilterBar
import com.rndeveloper.paparcar.presentation.vehicles.components.ActivityCard
import com.rndeveloper.paparcar.presentation.vehicles.components.ActivityFact
import com.rndeveloper.paparcar.ui.components.PapScrollToTopButton
import com.rndeveloper.paparcar.ui.components.PapShimmerBox
import com.rndeveloper.paparcar.ui.components.PapShimmerBlockScale
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import com.rndeveloper.paparcar.ui.theme.vehicleIdentityColor
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_active_section
import paparcar.composeapp.generated.resources.history_activity_title
import paparcar.composeapp.generated.resources.history_section_label
import paparcar.composeapp.generated.resources.history_day_full_fri
import paparcar.composeapp.generated.resources.history_day_full_mon
import paparcar.composeapp.generated.resources.history_day_full_sat
import paparcar.composeapp.generated.resources.history_day_full_sun
import paparcar.composeapp.generated.resources.history_day_full_thu
import paparcar.composeapp.generated.resources.history_day_full_tue
import paparcar.composeapp.generated.resources.history_day_full_wed
import paparcar.composeapp.generated.resources.history_day_short_fri
import paparcar.composeapp.generated.resources.history_day_short_mon
import paparcar.composeapp.generated.resources.history_day_short_sat
import paparcar.composeapp.generated.resources.history_day_short_sun
import paparcar.composeapp.generated.resources.history_day_short_thu
import paparcar.composeapp.generated.resources.history_day_short_tue
import paparcar.composeapp.generated.resources.history_day_short_wed
import paparcar.composeapp.generated.resources.history_activity_distance_partial
import paparcar.composeapp.generated.resources.history_fact_active_day
import paparcar.composeapp.generated.resources.history_fact_auto_detected
import paparcar.composeapp.generated.resources.history_fact_favorite_street
import paparcar.composeapp.generated.resources.history_month_short_1
import paparcar.composeapp.generated.resources.history_month_short_10
import paparcar.composeapp.generated.resources.history_month_short_11
import paparcar.composeapp.generated.resources.history_month_short_12
import paparcar.composeapp.generated.resources.history_month_short_2
import paparcar.composeapp.generated.resources.history_month_short_3
import paparcar.composeapp.generated.resources.history_month_short_4
import paparcar.composeapp.generated.resources.history_month_short_5
import paparcar.composeapp.generated.resources.history_month_short_6
import paparcar.composeapp.generated.resources.history_month_short_7
import paparcar.composeapp.generated.resources.history_month_short_8
import paparcar.composeapp.generated.resources.history_month_short_9
import paparcar.composeapp.generated.resources.history_today
import paparcar.composeapp.generated.resources.history_yesterday
import kotlin.time.Instant

internal val MONTH_SHORT_RES: List<StringResource> = listOf(
    Res.string.history_month_short_1, Res.string.history_month_short_2,
    Res.string.history_month_short_3, Res.string.history_month_short_4,
    Res.string.history_month_short_5, Res.string.history_month_short_6,
    Res.string.history_month_short_7, Res.string.history_month_short_8,
    Res.string.history_month_short_9, Res.string.history_month_short_10,
    Res.string.history_month_short_11, Res.string.history_month_short_12,
)

internal val DAY_SHORT_RES: List<StringResource> = listOf(
    Res.string.history_day_short_mon, Res.string.history_day_short_tue,
    Res.string.history_day_short_wed, Res.string.history_day_short_thu,
    Res.string.history_day_short_fri, Res.string.history_day_short_sat,
    Res.string.history_day_short_sun,
)

internal val DAY_FULL_RES: List<StringResource> = listOf(
    Res.string.history_day_full_mon, Res.string.history_day_full_tue,
    Res.string.history_day_full_wed, Res.string.history_day_full_thu,
    Res.string.history_day_full_fri, Res.string.history_day_full_sat,
    Res.string.history_day_full_sun,
)

data class WeekDayStats(val label: String, val sessions: Int, val isCurrent: Boolean = false)

internal sealed class TimelineItem {
    abstract val key: String

    data class Header(val label: String) : TimelineItem() {
        override val key: String = "hdr_$label"
    }

    data class Session(val parking: UserParking, val isLast: Boolean) : TimelineItem() {
        override val key: String = "ses_${parking.id}"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryContent(
    state: HistoryState,
    contentPadding: PaddingValues,
    onViewOnMap: (lat: Double, lon: Double, sessionId: String) -> Unit,
    // How the vehicle owning this timeline is monitored. Vehículos is a per-vehicle pager, so the
    // whole list belongs to one car and carries that car's identity colour — brand green is left to
    // the chrome (day headers, the "view on map" action, the activity chart).
    // [UI-COLOR-DOCTRINE-001][UI-HISTORY-IDENTITY-AND-SOURCE-001]
    watch: VehicleWatch = VehicleWatch.Off,
    onFilterSelected: (HistoryFilter) -> Unit = {},
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    onScrolledToTop: () -> Unit = {},
) {
    val todayLabel = stringResource(Res.string.history_today)
    val yesterdayLabel = stringResource(Res.string.history_yesterday)
    val monthNamesShort = MONTH_SHORT_RES.map { stringResource(it) }
    val dayLabels = DAY_SHORT_RES.map { stringResource(it) }
    val dayFullLabels = DAY_FULL_RES.map { stringResource(it) }

    // Unresolved means "Room hasn't spoken yet", NOT "this car has no history": it renders the
    // skeleton, and the empty state stays reserved for a history that really is empty.
    // [UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001]
    val resolved = state.timeline as? HistoryTimeline.Resolved
    val sessions = resolved?.sessions.orEmpty()
    val filteredSessions = resolved?.filteredSessions.orEmpty()

    val allEnded = remember(sessions) { sessions.filter { !it.isActive } }
    // The activity chart follows the selected time filter: buckets are built from the SCOPED
    // sessions with a granularity that matches the window (daily for a week, weekly for a month,
    // monthly for longer). Its total/scope label come from the same filter. [VEHICLES-REDESIGN-001]
    // The bars now span the SAME window that scoped those sessions — both read
    // VehicleHistoryCalculator.windowStartMs, so neither can drift from the other. That claim used
    // to be a comment here while the code did something else.
    // [UI-HISTORY-THE-CHART-SPANS-WHAT-THE-FILTER-SPANS-001]
    val activityBuckets = remember(filteredSessions, state.activeFilter, dayLabels, monthNamesShort) {
        VehicleHistoryCalculator.buildActivityBuckets(
            sessions = filteredSessions,
            filter = state.activeFilter,
            dayLabels = dayLabels,
            monthNamesShort = monthNamesShort,
        )
    }
    val scopeTotal = filteredSessions.size
    // The History timeline is ALWAYS the complete list — the time filter scopes only the Activity
    // chart, never the timeline below it. So it reads from every session, not the filtered set.
    // [HOME-VEH-REFINE-001 · Task 4]
    val activeSession =
        remember(sessions) { sessions.firstOrNull { it.isActive } }
    val timelineItems =
        remember(allEnded, todayLabel, yesterdayLabel, monthNamesShort, dayFullLabels) {
            buildTimeline(allEnded, todayLabel, yesterdayLabel, monthNamesShort, dayFullLabels)
        }

    val layoutDirection = LocalLayoutDirection.current
    // El padding del llamante entra como padding de CONTENIDO, no de layout: así la lista arranca
    // bajo la cabecera pero puede pasar por debajo de ella al scrollear. [UI-TOPBAR-COLLAPSE-001]
    val listPadding = PaddingValues(
        top = contentPadding.calculateTopPadding() + if (header != null) 0.dp else LIST_V_PADDING,
        bottom = contentPadding.calculateBottomPadding() + LIST_V_PADDING,
        start = contentPadding.calculateStartPadding(layoutDirection),
        end = contentPadding.calculateEndPadding(layoutDirection),
    )

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = listPadding,
        ) {
            if (resolved == null) {
                if (header != null) item(key = "header") { header() }
                item(key = "sk_section") {
                    HistorySkeletonSection(fillMaxSize = header == null)
                }
            } else if (sessions.isEmpty()) {
                item(key = "empty") {
                    // Un único item a viewport completo: la hero card arriba y el bloque vacío
                    // centrado en el hueco restante con weight(1f), así queda encuadrado en el
                    // centro del espacio bajo la card (no del viewport entero). [empty-records]
                    // fillParentMaxSize mide el viewport ENTERO (ignora el contentPadding), así que
                    // se le descuenta la cabecera: si no, el bloque vacío quedaría medio hueco de
                    // cabecera por debajo del centro real. [UI-TOPBAR-COLLAPSE-001]
                    Column(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(bottom = listPadding.calculateTopPadding()),
                    ) {
                        header?.invoke()
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyHistoryState(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            } else {
                if (header != null) item(key = "header") { header() }

                // "Activity" owns the chart + time filters. Its heading carries the scoped count as a
                // green aside; the filters below re-bucket ONLY the chart. [HOME-VEH-REFINE-001 · Task 4]
                item(key = "activity_section_label") {
                    Spacer(Modifier.height(SECTION_TITLE_TOP_GAP.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        // Just the section title; the scoped count moved INTO the chart card as its
                        // own header so the card isn't a bare graph. [ACTIVITY-CARD-TITLE-001]
                        Text(
                            text = stringResource(Res.string.history_activity_title),
                            style = PaparcarType.current.sectionTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(SECTION_TITLE_BOTTOM_GAP.dp))
                }

                item(key = "filter_bar") {
                    HistoryFilterBar(
                        activeFilter = state.activeFilter,
                        onFilterSelected = onFilterSelected,
                    )
                }

                item(key = "chart_spacer") { Spacer(Modifier.height(8.dp)) }
                item(key = "chart") {
                    // Scoped km follow the same filter as the bars; all-history facts (day, street,
                    // auto share) appear only above their significance thresholds — a metric
                    // without data renders as nothing. [VEH-STATS-SAY-SOMETHING-USEFUL-001]
                    val scopeDistance = remember(filteredSessions) {
                        VehicleHistoryCalculator.sumDistanceMeters(filteredSessions)
                    }
                    // [UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001] A sum that does not cover every
                    // parking in scope says so. Only the BT lane and legacy rows lack a route, so
                    // the plain figure is still the common case; what it can no longer do is pass
                    // for a total while describing three parkings out of twelve.
                    val distanceText = scopeDistance?.let { distance ->
                        val km = distanceString(distance.meters)
                        if (distance.isComplete) km
                        else stringResource(
                            Res.string.history_activity_distance_partial,
                            km,
                            distance.fromParkings,
                        )
                    }
                    val facts = buildList {
                        resolved.statsData?.mostActiveDayOfWeek?.let { day ->
                            add(ActivityFact(
                                icon = Icons.Rounded.Event,
                                text = stringResource(Res.string.history_fact_active_day, dayFullLabels[day - 1]),
                            ))
                        }
                        resolved.statsData?.favoriteStreet?.let { street ->
                            add(ActivityFact(
                                icon = Icons.Rounded.Place,
                                text = stringResource(Res.string.history_fact_favorite_street, street),
                            ))
                        }
                        resolved.statsData?.autoDetected?.let { share ->
                            add(ActivityFact(
                                icon = Icons.Rounded.AutoAwesome,
                                text = stringResource(Res.string.history_fact_auto_detected, share.auto, share.known),
                            ))
                        }
                    }
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        ActivityCard(
                            data = activityBuckets,
                            total = scopeTotal,
                            distanceText = distanceText,
                            facts = facts,
                        )
                    }
                }

                val hasTimeline = activeSession != null || timelineItems.isNotEmpty()
                if (hasTimeline) {
                    // "History" heads the timeline as its own section — always the full list, never
                    // scoped by the Activity filter. [Task 4]
                    item(key = "history_section_label") {
                        Spacer(Modifier.height(SECTION_TITLE_TOP_GAP.dp))
                        Text(
                            text = stringResource(Res.string.history_section_label),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = PaparcarType.current.sectionTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(SECTION_TITLE_BOTTOM_GAP.dp))
                    }
                }

                if (activeSession != null) {
                    item(key = "active_label") {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            ActiveSectionHeader(
                                text = stringResource(Res.string.history_active_section),
                                accent = vehicleIdentityColor(watch),
                            )
                        }
                    }
                    item(key = "active_session") {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            EndedSessionTimelineNode(
                                session = activeSession,
                                isLast = timelineItems.isEmpty(),
                                watch = watch,
                                isActive = true,
                                onViewOnMap = onViewOnMap,
                            )
                        }
                    }
                }

                items(items = timelineItems, key = { it.key }) { timelineItem ->
                    // Filter changes swap the timeline set — animate item insert/remove/move
                    // so rows glide instead of snapping. [MOTION-POLISH-001]
                    Box(Modifier.animateItem().padding(horizontal = 16.dp)) {
                        when (timelineItem) {
                            is TimelineItem.Header -> DayHeaderRow(label = timelineItem.label)
                            is TimelineItem.Session -> EndedSessionTimelineNode(
                                session = timelineItem.parking,
                                isLast = timelineItem.isLast,
                                watch = watch,
                                isActive = false,
                                onViewOnMap = onViewOnMap,
                            )
                        }
                    }
                }
            }
        }

        PapScrollToTopButton(
            listState = listState,
            bottomPadding = contentPadding.calculateBottomPadding(),
            onClick = {
                scope.launch { listState.animateScrollToItem(0) }
                onScrolledToTop()
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Skeleton loading section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistorySkeletonSection(fillMaxSize: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillMaxSize) Modifier.fillMaxSize() else Modifier)
            .padding(top = 6.dp),
    ) {
        PapShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(SKELETON_CHART_HEIGHT_DP.dp),
            shape = RoundedCornerShape(SKELETON_CORNER_DP.dp),
            alphaScale = PapShimmerBlockScale,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(SKELETON_FILTER_COUNT) {
                PapShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(SKELETON_CHIP_HEIGHT_DP.dp),
                    shape = RoundedCornerShape(999.dp),
                    alphaScale = PapShimmerBlockScale * SKELETON_CHIP_ALPHA_FACTOR,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        PapShimmerBox(
            modifier = Modifier
                .padding(start = 16.dp)
                .width(SKELETON_HEADER_WIDTH_DP.dp)
                .height(SKELETON_HEADER_HEIGHT_DP.dp),
            shape = RoundedCornerShape(4.dp),
            alphaScale = PapShimmerBlockScale * SKELETON_HEADER_ALPHA_FACTOR,
        )
        Spacer(Modifier.height(8.dp))
        repeat(SKELETON_ROW_COUNT) {
            PapShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .height(SKELETON_SESSION_HEIGHT_DP.dp),
                shape = RoundedCornerShape(SKELETON_CORNER_DP.dp),
                alphaScale = PapShimmerBlockScale,
            )
        }
    }
}

// Section titles ("Activity" / "History") sit closer to their OWN content below than to the section
// above — a generous top gap separates from the previous block, a tight bottom gap attaches the
// title to what it heads. [HOME-VEH-REFINE-001]
private const val SECTION_TITLE_TOP_GAP = 24
private const val SECTION_TITLE_BOTTOM_GAP = 8

private const val SKELETON_CHART_HEIGHT_DP = 148
private const val SKELETON_CHIP_HEIGHT_DP = 32
private const val SKELETON_SESSION_HEIGHT_DP = 72
private const val SKELETON_HEADER_WIDTH_DP = 80
private const val SKELETON_HEADER_HEIGHT_DP = 12
private const val SKELETON_CORNER_DP = 16
private const val SKELETON_FILTER_COUNT = 4
private const val SKELETON_ROW_COUNT = 3
/** Holgura vertical de la lista con sus extremos. */
private val LIST_V_PADDING = 8.dp

private const val SKELETON_CHIP_ALPHA_FACTOR = 0.85f
private const val SKELETON_HEADER_ALPHA_FACTOR = 0.7f

private const val DAY_MS = 86_400_000L

private fun buildTimeline(
    sessions: List<UserParking>,
    todayLabel: String,
    yesterdayLabel: String,
    monthNamesShort: List<String>,
    dayFullLabels: List<String>,
): List<TimelineItem> {
    val tz = TimeZone.currentSystemDefault()
    val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz).date
    val yesterday = Instant.fromEpochMilliseconds(nowMs - DAY_MS).toLocalDateTime(tz).date

    val flat = mutableListOf<TimelineItem>()

    sessions
        .sortedByDescending { it.location.timestamp }
        .groupBy { session ->
            Instant.fromEpochMilliseconds(session.location.timestamp)
                .toLocalDateTime(tz).date
        }
        .forEach { (date, daySessions) ->
            val label = when (date) {
                today -> todayLabel
                yesterday -> yesterdayLabel
                else -> "${dayFullLabels[date.dayOfWeek.isoDayNumber - 1]}, ${date.day} ${monthNamesShort[date.month.number - 1]} ${date.year}"
            }
            flat += TimelineItem.Header(label)
            daySessions.forEach { flat += TimelineItem.Session(it, isLast = false) }
        }

    val lastIdx = flat.indexOfLast { it is TimelineItem.Session }
    if (lastIdx >= 0) {
        flat[lastIdx] = (flat[lastIdx] as TimelineItem.Session).copy(isLast = true)
    }

    return flat
}
