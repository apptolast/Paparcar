@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.vehicles

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.presentation.vehicles.components.ActiveSectionHeader
import io.apptolast.paparcar.presentation.vehicles.components.DayHeaderRow
import io.apptolast.paparcar.presentation.vehicles.components.EmptyHistoryState
import io.apptolast.paparcar.presentation.vehicles.components.EndedSessionTimelineNode
import io.apptolast.paparcar.presentation.vehicles.components.HistoryFilterBar
import io.apptolast.paparcar.presentation.vehicles.components.HistoryInsightsCard
import io.apptolast.paparcar.presentation.vehicles.components.StatsRow
import io.apptolast.paparcar.presentation.vehicles.components.WeeklyActivityCard
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_active_section
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

data class WeekDayStats(val label: String, val sessions: Int)

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
internal fun HistoryContent(
    state: HistoryState,
    contentPadding: PaddingValues,
    onViewOnMap: (lat: Double, lon: Double, sessionId: String) -> Unit,
    onFilterSelected: (HistoryFilter) -> Unit = {},
    modifier: Modifier = Modifier,
    showInternalStats: Boolean = true,
    headerSlot: (LazyListScope.() -> Unit)? = null,
) {
    val todayLabel = stringResource(Res.string.history_today)
    val yesterdayLabel = stringResource(Res.string.history_yesterday)
    val monthNamesShort = MONTH_SHORT_RES.map { stringResource(it) }
    val dayLabels = DAY_SHORT_RES.map { stringResource(it) }
    val dayFullLabels = DAY_FULL_RES.map { stringResource(it) }

    val allEnded = remember(state.sessions) { state.sessions.filter { !it.isActive } }
    val weeklyStats = remember(state.sessions, dayLabels) { buildWeeklyStats(state.sessions, dayLabels) }
    val activeSession =
        remember(state.filteredSessions) { state.filteredSessions.firstOrNull { it.isActive } }
    val ended = remember(state.filteredSessions) { state.filteredSessions.filter { !it.isActive } }
    val timelineItems =
        remember(ended, todayLabel, yesterdayLabel, monthNamesShort, dayFullLabels) {
            buildTimeline(ended, todayLabel, yesterdayLabel, monthNamesShort, dayFullLabels)
        }

    Box(modifier = modifier.padding(contentPadding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = if (headerSlot != null) 0.dp else 8.dp,
                bottom = 8.dp,
            ),
        ) {
            headerSlot?.invoke(this)

            if (state.isLoading) {
                item(key = "sk_section") {
                    HistorySkeletonSection(fillMaxSize = headerSlot == null)
                }
            } else if (state.sessions.isEmpty()) {
                item(key = "empty") {
                    EmptyHistoryState(
                        modifier = if (headerSlot == null) Modifier.fillParentMaxSize()
                        else Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    )
                }
            } else {
                item(key = "chart_spacer") { Spacer(Modifier.height(8.dp)) }
                item(key = "chart") {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        WeeklyActivityCard(data = weeklyStats)
                    }
                }

                if (showInternalStats) {
                    item(key = "stats") {
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            StatsRow(sessions = state.sessions)
                        }
                    }
                    if (state.statsData != null) {
                        item(key = "insights") {
                            Spacer(Modifier.height(8.dp))
                            HistoryInsightsCard(
                                stats = state.statsData,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }

                if (!showInternalStats) {
                    item(key = "chart_filter_gap") { Spacer(Modifier.height(8.dp)) }
                }

                stickyHeader(key = "filter_bar") {
                    HistoryFilterBar(
                        activeFilter = state.activeFilter,
                        onFilterSelected = onFilterSelected,
                    )
                }

                val hasTimeline = activeSession != null || timelineItems.isNotEmpty()
                if (hasTimeline) {
                    item(key = "timeline_spacer") { Spacer(Modifier.height(4.dp)) }
                }

                if (activeSession != null) {
                    item(key = "active_label") {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            ActiveSectionHeader(stringResource(Res.string.history_active_section))
                        }
                    }
                    item(key = "active_session") {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            EndedSessionTimelineNode(
                                session = activeSession,
                                isLast = timelineItems.isEmpty(),
                                isActive = true,
                                onViewOnMap = onViewOnMap,
                            )
                        }
                    }
                }

                items(items = timelineItems, key = { it.key }) { timelineItem ->
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        when (timelineItem) {
                            is TimelineItem.Header -> DayHeaderRow(label = timelineItem.label)
                            is TimelineItem.Session -> EndedSessionTimelineNode(
                                session = timelineItem.parking,
                                isLast = timelineItem.isLast,
                                isActive = false,
                                onViewOnMap = onViewOnMap,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Skeleton loading section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistorySkeletonSection(fillMaxSize: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val skAlpha by transition.animateFloat(
        initialValue = SKELETON_ALPHA_MIN,
        targetValue = SKELETON_ALPHA_MAX,
        animationSpec = infiniteRepeatable(
            animation = tween(SKELETON_ANIM_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillMaxSize) Modifier.fillMaxSize() else Modifier)
            .padding(top = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(SKELETON_CHART_HEIGHT_DP.dp)
                .clip(RoundedCornerShape(SKELETON_CORNER_DP.dp))
                .background(cs.onSurface.copy(alpha = skAlpha)),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(SKELETON_FILTER_COUNT) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(SKELETON_CHIP_HEIGHT_DP.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(cs.onSurface.copy(alpha = skAlpha * SKELETON_CHIP_ALPHA_FACTOR)),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .padding(start = 16.dp)
                .width(SKELETON_HEADER_WIDTH_DP.dp)
                .height(SKELETON_HEADER_HEIGHT_DP.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(cs.onSurface.copy(alpha = skAlpha * SKELETON_HEADER_ALPHA_FACTOR)),
        )
        Spacer(Modifier.height(8.dp))
        repeat(SKELETON_ROW_COUNT) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .height(SKELETON_SESSION_HEIGHT_DP.dp)
                    .clip(RoundedCornerShape(SKELETON_CORNER_DP.dp))
                    .background(cs.onSurface.copy(alpha = skAlpha)),
            )
        }
    }
}

private const val SKELETON_CHART_HEIGHT_DP = 148
private const val SKELETON_CHIP_HEIGHT_DP = 32
private const val SKELETON_SESSION_HEIGHT_DP = 72
private const val SKELETON_HEADER_WIDTH_DP = 80
private const val SKELETON_HEADER_HEIGHT_DP = 12
private const val SKELETON_CORNER_DP = 16
private const val SKELETON_ANIM_MS = 700
private const val SKELETON_FILTER_COUNT = 4
private const val SKELETON_ROW_COUNT = 3
private const val SKELETON_ALPHA_MIN = 0.06f
private const val SKELETON_ALPHA_MAX = 0.14f
private const val SKELETON_CHIP_ALPHA_FACTOR = 0.85f
private const val SKELETON_HEADER_ALPHA_FACTOR = 0.7f

private const val DAY_MS = 86_400_000L

private fun buildWeeklyStats(
    sessions: List<UserParking>,
    dayLabels: List<String>
): List<WeekDayStats> {
    val tz = TimeZone.currentSystemDefault()
    val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()

    val grouped = sessions
        .filter { it.location.timestamp >= nowMs - 7 * DAY_MS }
        .groupBy { Instant.fromEpochMilliseconds(it.location.timestamp).toLocalDateTime(tz).date }

    return (6 downTo 0).map { daysAgo ->
        val date = Instant.fromEpochMilliseconds(nowMs - daysAgo * DAY_MS).toLocalDateTime(tz).date
        WeekDayStats(
            label = dayLabels[date.dayOfWeek.isoDayNumber - 1],
            sessions = grouped[date]?.size ?: 0,
        )
    }
}

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
