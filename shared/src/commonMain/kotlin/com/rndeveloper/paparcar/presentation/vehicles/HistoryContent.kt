@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.vehicles

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
import com.rndeveloper.paparcar.ui.theme.PapControlHeight
import com.rndeveloper.paparcar.ui.theme.PapGap
import com.rndeveloper.paparcar.ui.theme.PapPageRhythm
import com.rndeveloper.paparcar.ui.theme.PaparcarSpacing
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
        // ⛔ Sin condicional. El `0.dp` existía porque la hero card traía su propio margen superior
        // dentro del componente: el hueco con la cabecera de pantalla estaba, pero escondido en un
        // bloque. Ahora lo pone la lista, que es de quien es.
        // [UI-A-PAGE-KEEPS-ONE-VERTICAL-RHYTHM-001]
        top = contentPadding.calculateTopPadding() + LIST_V_PADDING,
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

    // El alto del viewport se lee aquí —no cuesta un layout nuevo— porque lo necesita el estado
    // vacío para saber CUÁNTO sobra bajo el header. Dentro de un `item` de `LazyColumn` esa cifra
    // no existe: las constraints verticales llegan con `maxHeight = Infinity`.
    // [UI-AN-EMPTY-HISTORY-MUST-NOT-CROP-ITS-HEADER-001]
    BoxWithConstraints(modifier = modifier) {
        val viewportHeight = maxHeight
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
                    // El header arriba y el bloque vacío centrado en LO QUE SOBRE del viewport, que
                    // es lo que lo encuadra bajo la hero card en vez de en el centro de la pantalla.
                    // [empty-records]
                    //
                    // ⛔ Esto era una caja de altura FIJA (`fillParentMaxSize`) con el header
                    // dentro, y recortaba en silencio en cuanto el header dejaba de caber: con un
                    // coche compartido la sección del grupo se cortaba a media card y el propio
                    // bloque vacío se quedaba a cero px, porque su `weight(1f)` repartía un sobrante
                    // negativo. Ahora la altura es un MÍNIMO, así que un header grande hace crecer
                    // la lista —y scrollear— en vez de perder lo que no cabe.
                    // [UI-AN-EMPTY-HISTORY-MUST-NOT-CROP-ITS-HEADER-001]
                    //
                    // Al viewport se le descuenta la cabecera de la pantalla porque el alto medido
                    // la ignora (es `contentPadding`, no layout): si no, el bloque vacío quedaría
                    // medio hueco de cabecera por debajo del centro real. [UI-TOPBAR-COLLAPSE-001]
                    HeaderThenFillRest(
                        restHeight = viewportHeight - listPadding.calculateTopPadding(),
                        header = { header?.invoke() },
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
            } else {
                if (header != null) item(key = "header") { header() }

                // "Activity" owns the chart + time filters. Its heading carries the scoped count as a
                // green aside; the filters below re-bucket ONLY the chart. [HOME-VEH-REFINE-001 · Task 4]
                item(key = "activity_section_label") {
                    Spacer(Modifier.height(SECTION_TITLE_TOP_GAP))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = PapPageRhythm.sideInset),
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
                    Spacer(Modifier.height(SECTION_TITLE_BOTTOM_GAP_TO_CHIPS))
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

                item(key = "chart_spacer") { Spacer(Modifier.height(FILTER_BAR_CHART_GAP)) }
                item(key = "chart") {
                    // Scoped km follow the same filter as the bars. The three "facts" that used to
                    // footer this card (day, street, auto share) moved to the figures card below:
                    // they were computed over the WHOLE visible history while living inside a card
                    // titled by the period's count, so changing the filter left them untouched.
                    // [STATS-UI-001]
                    // [UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001] A sum that does not cover
                    // every parking in scope says so — for whichever measure the bars are drawing.
                    Box(Modifier.padding(horizontal = PapPageRhythm.sideInset)) {
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
                        Spacer(Modifier.height(STATS_CARD_GAP))
                        Box(Modifier.padding(horizontal = PapPageRhythm.sideInset)) {
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
                        Spacer(Modifier.height(SECTION_TITLE_TOP_GAP))
                        Text(
                            text = stringResource(Res.string.history_section_label),
                            modifier = Modifier.padding(horizontal = PapPageRhythm.sideInset),
                            style = PaparcarType.current.sectionTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(SECTION_TITLE_BOTTOM_GAP))
                    }
                }

                if (activeSession != null) {
                    item(key = "active_label") {
                        Box(Modifier.padding(horizontal = PapPageRhythm.sideInset)) {
                            ActiveSectionHeader(
                                text = stringResource(Res.string.history_active_section),
                                accent = PapColor.brandData,
                            )
                        }
                    }
                    item(key = "active_session") {
                        Box(Modifier.padding(horizontal = PapPageRhythm.sideInset)) {
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
                    Box(Modifier.animateItem().padding(horizontal = PapPageRhythm.sideInset)) {
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
// Header + resto del viewport [UI-AN-EMPTY-HISTORY-MUST-NOT-CROP-ITS-HEADER-001]
// ─────────────────────────────────────────────────────────────────────────────

/**
 * El [header] con su altura natural, y debajo [content] ocupando **lo que sobre** de [restHeight]
 * — o su propia altura cuando no sobra nada.
 *
 * Existe porque la caja de altura fija que hacía esto antes (`fillParentMaxSize` con el header
 * dentro) no tenía forma de crecer: cuando el header pasó a llevar también la sección de coche
 * compartido dejó de caber, y lo que no cabía se perdía fuera de la caja —incluido el estado vacío,
 * cuyo `weight(1f)` recibía un sobrante negativo y lo dejaba a cero px—.
 *
 * Una sola pasada de medida resuelve los dos casos sin número mágico ni estado intermedio: el
 * header se mide **suelto** (nunca recortado) y el resto se mide con `minHeight` = lo que queda,
 * acotado a ≥ 0, y `maxHeight` libre. Si queda sitio, [content] se estira hasta ahí y se centra en
 * él; si no queda, toma su altura natural y quien lo aloje crecerá y scrolleará.
 *
 * ⛔ No vale `heightIn(min = …)` sobre una `Column` con `weight`: dentro de un `item` de
 * `LazyColumn` las constraints verticales llegan con `maxHeight = Infinity`, y un `weight()` sin
 * máximo acotado no tiene espacio que repartir. Quitaría el recorte a costa del centrado.
 */
@Composable
private fun HeaderThenFillRest(
    restHeight: Dp,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    // El centrado vive AQUÍ y no en el call site: el hueco es lo que este layout acaba de calcular,
    // así que centrarse en él no es algo que el llamante pueda pedir con un modifier.
    val centeredContent: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
    }
    Layout(contents = listOf(header, centeredContent)) { (headerMeasurables, contentMeasurables), constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val headerPlaceables = headerMeasurables.map { it.measure(loose) }
        val headerHeight = headerPlaceables.sumOf { it.height }
        // Lo que sobra es un SUELO, no un techo: de ahí que el máximo siga libre.
        val rest = (restHeight.roundToPx() - headerHeight).coerceAtLeast(0)
        val contentPlaceables = contentMeasurables.map { it.measure(loose.copy(minHeight = rest)) }
        val contentHeight = contentPlaceables.maxOfOrNull { it.height } ?: 0

        layout(constraints.maxWidth, headerHeight + contentHeight) {
            var y = 0
            headerPlaceables.forEach { placeable ->
                placeable.place(0, y)
                y += placeable.height
            }
            contentPlaceables.forEach { it.place(0, y) }
        }
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
        modifier = modifier.fillMaxWidth().padding(horizontal = PapPageRhythm.sideInset, vertical = PaparcarSpacing.sm),
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
            .padding(top = SKELETON_SECTION_TOP_PAD),
    ) {
        PapShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PapPageRhythm.sideInset)
                .height(SKELETON_CHART_HEIGHT_DP.dp),
            shape = RoundedCornerShape(SKELETON_CORNER_DP.dp),
            alphaScale = PapShimmerBlockScale,
        )
        Spacer(Modifier.height(PaparcarSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = PapPageRhythm.sideInset),
            horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm),
        ) {
            repeat(SKELETON_FILTER_COUNT) {
                PapShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(PapControlHeight.chip),
                    shape = PapShapes.chip,
                    alphaScale = PapShimmerBlockScale * SKELETON_CHIP_ALPHA_FACTOR,
                )
            }
        }
        Spacer(Modifier.height(PaparcarSpacing.md))
        PapShimmerBox(
            modifier = Modifier
                .padding(start = PapPageRhythm.sideInset)
                .width(SKELETON_HEADER_WIDTH_DP.dp)
                .height(SKELETON_HEADER_HEIGHT_DP.dp),
            shape = RoundedCornerShape(SKELETON_HEADER_CORNER_DP),
            alphaScale = PapShimmerBlockScale * SKELETON_HEADER_ALPHA_FACTOR,
        )
        Spacer(Modifier.height(PaparcarSpacing.sm))
        repeat(SKELETON_ROW_COUNT) {
            PapShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PapPageRhythm.sideInset, vertical = PaparcarSpacing.xs)
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
//
// ⛔ Esa asimetría ya no la elige esta pantalla: es la ley de [PapPageRhythm], medida en Ajustes.
// Eran 24/8 contra los 20/10 de allí, y la diferencia se leía como «en vehicles el espaciado
// vertical es más alto» (user, 19-10). [UI-A-PAGE-KEEPS-ONE-VERTICAL-RHYTHM-001]
//
// ⚠️ El hueco de ARRIBA se mide contra el último bloque del `header`, y ese bloque cambia con el
// estado: sin grupo termina en una card (20 dp exactos), con grupo termina en la fila de chips de
// alcance, que arrastra sus 7 dp de mínimo táctil y deja 27 a la vista. Descontarlo aquí a ciegas
// dejaría el caso común en 13. Queda MEDIDO y escrito: arreglarlo exige que la cabecera declare en
// qué termina, y eso es acoplar la lista a su cabecera por 7 dp.
private val SECTION_TITLE_TOP_GAP = PapPageRhythm.beforeSection

/** Bajo «Actividad» hay una fila de CHIPS —los filtros de periodo—, así que el hueco se pide por
 *  lo que se VE: el chip arrastra su overhang de mínimo táctil y con los 10 dp de la ley escritos
 *  a pelo se verían 17. [UI-THE-SPACING-SWEEP-IS-SCREEN-BY-SCREEN-001] */
private val SECTION_TITLE_BOTTOM_GAP_TO_CHIPS = PapGap.betweenControls(
    visible = PapPageRhythm.afterSection,
    controlHeight = PapControlHeight.chip,
    sides = 1,
)

/** Bajo «Historial» hay filas, no controles: el hueco de la ley se escribe tal cual. */
private val SECTION_TITLE_BOTTOM_GAP = PapPageRhythm.afterSection

/** Las dos cards de Actividad son hermanas bajo una misma cabecera, o sea el mismo hueco que
 *  cualquier par de bloques hermanos de una página. Eran 16 — *"más aire que entre una card y su
 *  fila de filtros, menos que entre secciones"* (user, 08-09-2026) —, un tercer escalón inventado
 *  entre dos que ya existían. [UI-A-PAGE-KEEPS-ONE-VERTICAL-RHYTHM-001] [STATS-UI-001] */
private val STATS_CARD_GAP = PapPageRhythm.block

/** El chart y la fila de filtros que lo manda: hermanos pegados, con el overhang del chip
 *  descontado para que se vean los mismos dp que entre dos cards. */
private val FILTER_BAR_CHART_GAP = PapGap.betweenControls(
    visible = PapPageRhythm.block,
    controlHeight = PapControlHeight.chip,
    sides = 1,
)

private const val SKELETON_CHART_HEIGHT_DP = 148
private const val SKELETON_SESSION_HEIGHT_DP = 72
private const val SKELETON_HEADER_WIDTH_DP = 80
private const val SKELETON_HEADER_HEIGHT_DP = 12
private val SKELETON_SECTION_TOP_PAD = 6.dp
private val SKELETON_HEADER_CORNER_DP = 4.dp
private const val SKELETON_CORNER_DP = 16
private const val SKELETON_FILTER_COUNT = 4
private const val SKELETON_ROW_COUNT = 3
/** Holgura vertical de la lista con sus extremos. */
private val LIST_V_PADDING = PapPageRhythm.edgeInset

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
