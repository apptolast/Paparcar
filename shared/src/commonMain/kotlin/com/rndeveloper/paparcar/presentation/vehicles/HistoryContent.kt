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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WorkspacePremium
import com.rndeveloper.paparcar.domain.detection.DetectionPause
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.presentation.util.distanceString
import com.rndeveloper.paparcar.presentation.vehicles.components.ActiveSectionHeader
import com.rndeveloper.paparcar.presentation.vehicles.components.DayHeaderRow
import com.rndeveloper.paparcar.presentation.vehicles.components.EmptyHistoryState
import com.rndeveloper.paparcar.presentation.vehicles.components.EndedSessionTimelineNode
import com.rndeveloper.paparcar.presentation.vehicles.components.StatsPeriodBar
import com.rndeveloper.paparcar.presentation.vehicles.components.ActivityCard
import com.rndeveloper.paparcar.presentation.vehicles.components.chartQualifierText
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.components.PapPremiumEmphasis
import com.rndeveloper.paparcar.ui.components.PapNavChevron
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.components.PapPremiumSurface
import com.rndeveloper.paparcar.ui.components.PapScrollToTopButton
import com.rndeveloper.paparcar.ui.components.PapShimmerBox
import com.rndeveloper.paparcar.ui.components.PapShimmerBlockScale
import com.rndeveloper.paparcar.ui.theme.PapAlpha
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_active_section
import paparcar.composeapp.generated.resources.history_activity_title
import paparcar.composeapp.generated.resources.history_plus_footer_cta
import paparcar.composeapp.generated.resources.history_plus_hidden_count
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
import paparcar.composeapp.generated.resources.history_activity_measure_partial
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

/**
 * One bar of the activity chart: what it is called, what it is WORTH on the selected metric, and
 * whether it is the bucket the calendar is standing in. [STATS-A-PERIOD-IS-A-SERIES-NOT-A-NUMBER-001]
 *
 * It was `WeekDayStats(label, sessions: Int, isCurrent)`, and both halves of that name had stopped
 * being true: the buckets have been weeks and months since the filter got five options, and the
 * value stopped being a count of sessions the moment a bar could hold metres or milliseconds. The
 * unit lives in the [ChartMetric] that produced the value, which is also where its scale floor and
 * its formatter come from.
 */
data class ActivityBucket(val label: String, val value: Float, val isCurrent: Boolean = false)

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
    onFilterSelected: (StatsPeriod) -> Unit = {},
    /** Which series the activity chart draws. [STATS-A-PERIOD-IS-A-SERIES-NOT-A-NUMBER-001] */
    onMetricSelected: (ChartMetric) -> Unit = {},
    /** The "Ver todo con Plus" footer under a gated timeline. [MONEY-001] */
    onOpenPlusPaywall: () -> Unit = {},
    /** Live entitlement: decides how the figures card's period is named and whether the Plus row shows.
     *  Defaults to free — the safe answer, the one that cannot hand Plus away by omission. */
    isPlus: Boolean = false,
    /** Why the next trip will not be measured — the notice over the figures. Null = it will.
     *  [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001] */
    detectionPause: DetectionPause? = null,
    /** The notice's CTA. [DET-TOGGLE-001] */
    onEnableDetection: () -> Unit = {},
    /** Tapping the figures card's Plus row. A DIFFERENT reason from the timeline footer's, so the
     *  funnel can tell which surface sold the upgrade. [STATS-UI-001] */
    onOpenStatsPaywall: () -> Unit = {},
    /** Plus only: the figures card's link to the full panel. Null = no link. [STATS-PANEL-001] */
    onOpenStatsPanel: (() -> Unit)? = null,
    /** Cómo se firma cada fila cuando el historial es de un coche compartido; null = es el tuyo.
     *  [VEH-SHARED-001] */
    sharedNote: ((UserParking) -> SharedRowNote?)? = null,
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
    // What the entitlement lets this screen SHOW — the full history stays intact underneath,
    // and the footer below the timeline accounts for the rest. [MONEY-001]
    val sessions = resolved?.visibleSessions.orEmpty()
    val hiddenOlderCount = resolved?.hiddenOlderCount ?: 0
    val filteredSessions = resolved?.filteredSessions.orEmpty()

    val allEnded = remember(sessions) { sessions.filter { !it.isActive } }
    // The activity chart follows the selected time filter: buckets are built from the SCOPED
    // sessions with a granularity that matches the window (daily for a week, weekly for a month,
    // monthly for longer). Its total/scope label come from the same filter. [VEHICLES-REDESIGN-001]
    // The bars now span the SAME window that scoped those sessions — both read
    // VehicleHistoryCalculator.windowStartMs, so neither can drift from the other. That claim used
    // to be a comment here while the code did something else.
    // [UI-HISTORY-THE-CHART-SPANS-WHAT-THE-FILTER-SPANS-001]
    // Which series the bars draw, and which ones this period can offer at all. The choice is
    // re-resolved here rather than trusted, because the filter can take the data out from under it
    // between two emissions. [STATS-A-PERIOD-IS-A-SERIES-NOT-A-NUMBER-001]
    val chartMetrics = remember(filteredSessions) { availableChartMetrics(filteredSessions) }
    val chartMetric = remember(filteredSessions, state.chartMetric) {
        resolveChartMetric(state.chartMetric, filteredSessions)
    }
    val activityBuckets = remember(
        filteredSessions, state.activePeriod, chartMetric, dayLabels, monthNamesShort,
    ) {
        VehicleHistoryCalculator.buildActivityBuckets(
            sessions = filteredSessions,
            filter = state.activePeriod,
            metric = chartMetric,
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

    // The header only re-expands from a PROGRAMMATIC jump via `expandKey` (the scroll-to-top
    // button) — a normal drag/fling that lands the list back at its very first pixel never told
    // it to. Anything that decouples the header's offset from the list's real position (a
    // fling settle-animation racing a new gesture, a jump that isn't the button) then leaves the
    // title and tabs retracted over a list sitting at its own top, forever, since nothing else
    // asks the header to expand. Watching the list's OWN top — not the button — closes that gap
    // structurally: "list at its first pixel" always means "header shown", regardless of how it
    // got there. [UI-A-HEADER-THAT-COLLAPSES-MUST-COME-BACK-001]
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
            .distinctUntilChanged()
            .collect { atTop -> if (atTop) onScrolledToTop() }
    }

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
                            // A history that is only HIDDEN must not claim to be empty: when the
                            // free window covers nothing recent, the honest message is how much
                            // exists beyond it, not "you never parked". [MONEY-001]
                            if (hiddenOlderCount > 0) {
                                PlusHistoryFooter(
                                    hiddenCount = hiddenOlderCount,
                                    onSeeAll = onOpenPlusPaywall,
                                )
                            } else {
                                EmptyHistoryState(modifier = Modifier.fillMaxWidth())
                            }
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
                    StatsPeriodBar(
                        filters = state.periodFilters,
                        activePeriod = state.activePeriod,
                        onFilterSelected = onFilterSelected,
                        // The tone of THIS page, said out loud instead of assumed.
                        // [STATS-A-FILTER-THAT-CANNOT-ANSWER-IS-NOT-OFFERED-001]
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    )
                }

                item(key = "chart_spacer") { Spacer(Modifier.height(8.dp)) }
                item(key = "chart") {
                    // Scoped km follow the same filter as the bars. The three "facts" that used to
                    // footer this card (day, street, auto share) moved to the figures card below:
                    // they were computed over the WHOLE visible history while living inside a card
                    // titled by the period's count, so changing the filter left them untouched.
                    // [STATS-UI-001]
                    // [UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001] A sum that does not cover
                    // every parking in scope says so — for whichever measure the bars are drawing.
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        ActivityCard(
                            data = activityBuckets,
                            total = scopeTotal,
                            qualifierText = chartQualifierText(chartMetric, filteredSessions),
                            metric = chartMetric,
                            metrics = chartMetrics,
                            onMetricSelected = onMetricSelected,
                        )
                    }
                }

                // The Activity section's SECOND card: same filter, same scope, titled by the
                // period instead of by the count. Under one heading so the filter's authority over
                // both is legible without saying it. (User, 08-09.) [STATS-UI-001]
                resolved.periodStats?.let { periodStats ->
                    item(key = "period_stats") {
                        Spacer(Modifier.height(STATS_CARD_GAP.dp))
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            VehicleStatsBlock(
                                stats = periodStats,
                                periodLabel = vehicleStatsPeriodLabel(state.activePeriod, isPlus),
                                lifetimeParkings = resolved.lifetimeParkings,
                                lifetimeSpotsReleased = resolved.lifetimeSpotsReleased,
                                // "All time" selected means the tiles ARE the whole life already — for
                                // Plus. Free figures are always seven days, so the line always helps.
                                showLifetime = !isPlus || state.activePeriod != StatsPeriod.All,
                                costPrice = resolved.costPrice,
                                isPlus = isPlus,
                                onOpenPaywall = onOpenStatsPaywall,
                                onOpenPanel = onOpenStatsPanel,
                                detectionPause = detectionPause,
                                onEnableDetection = onEnableDetection,
                            )
                        }
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
                                accent = PapColor.brandData,
                            )
                        }
                    }
                    item(key = "active_session") {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            EndedSessionTimelineNode(
                                session = activeSession,
                                isLast = timelineItems.isEmpty(),
                                isActive = true,
                                onViewOnMap = onViewOnMap,
                                sharedNote = sharedNote?.invoke(activeSession),
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
                                isActive = false,
                                onViewOnMap = onViewOnMap,
                                sharedNote = sharedNote?.invoke(timelineItem.parking),
                            )
                        }
                    }
                }

                // [MONEY-001] Soft gate, not a wall: the timeline ends where the free window ends,
                // and the footer SAYS how much lies beyond instead of pretending nothing does.
                if (hiddenOlderCount > 0) {
                    item(key = "plus_history_footer") {
                        PlusHistoryFooter(
                            hiddenCount = hiddenOlderCount,
                            onSeeAll = onOpenPlusPaywall,
                        )
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
// Plus footer — what lies beyond the free window [MONEY-001]
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlusHistoryFooter(
    hiddenCount: Int,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The same metal as the paywall, at the SUBDUED weight — because this one interrupts rather
    // than sells. [UI-PLUS-EMPHASIS-MATCHES-THE-MOMENT-001]
    //
    // ⚠️ It used to be a FILLED slab composed as three full-width bands (header · explanation ·
    // full-width CTA) and it was revoked on device as garish and oversized. The problem was never
    // the colour — the same green is fine on the paywall. It was that a card whose job is to tap
    // you on the shoulder while you read a list of parkings was wearing the weight of a card whose
    // job is to close a sale, and taking a third of the screen to do it.
    //
    // So: one row, list-sized, on a tint. The WHOLE card is the tap target, which is what lets the
    // CTA shrink from a full-width filled button to a line of text with a chevron — the card
    // already says "this is pressable", so a button inside it was saying it twice.
    PapPremiumSurface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = PapShapes.card,
        emphasis = PapPremiumEmphasis.Subdued,
        onClick = onSeeAll,
    ) {
        Row(
            modifier = Modifier.padding(FOOTER_PAD_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FOOTER_GAP_DP.dp),
        ) {
            // On a TINT the medal goes back to brand green: there is no filled bed to disappear
            // against here, unlike the Settings row. [UI-PLUS-WEARS-THE-BRANDS-OWN-MARK-001]
            Icon(
                imageVector = Icons.Rounded.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(FOOTER_MARK_DP.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(
                        Res.plurals.history_plus_hidden_count,
                        hiddenCount,
                        hiddenCount,
                    ),
                    style = PaparcarType.current.rowTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.history_plus_footer_cta),
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            PapNavChevron()
        }
    }
}

/** Inner padding of the Plus footer. Matched to the history rows around it, NOT roomier: this
 *  card sits inside the list and should belong to it. */
private const val FOOTER_PAD_DP = 14

/** Gap between the footer's glyph, its text and its chevron. */
private const val FOOTER_GAP_DP = 12

/** Sized to the two text lines beside it, like any other list-row glyph. */
private const val FOOTER_MARK_DP = 24

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

/** Between the Activity section's two cards: more air than between a card and its own filter row
 *  (8), less than between sections (24). They are siblings under one heading, and a sibling still
 *  needs room to be its own object. (User, 08-09-2026.) [STATS-UI-001] */
private const val STATS_CARD_GAP = 16

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
