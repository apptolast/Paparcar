@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.history

// ─────────────────────────────────────────────────────────────────────────────
// FONTS SETUP (two families)
//
// 1. Syne  — geometric display font for the TopAppBar title
//    Download from https://fonts.google.com/specimen/Syne
//    Place in:  composeResources/font/syne_bold.ttf
//               composeResources/font/syne_extrabold.ttf
//
// 2. Jost  — clean geometric sans (closest free Futura substitute) for body UI
//    Download from https://fonts.google.com/specimen/Jost
//    Place in:  composeResources/font/jost_regular.ttf
//               composeResources/font/jost_medium.ttf
//               composeResources/font/jost_semibold.ttf
//               composeResources/font/jost_bold.ttf
//
// Then uncomment the FontFamily declarations below and remove the fallback lines.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.presentation.history.components.ActiveSessionHeroCard
import io.apptolast.paparcar.presentation.history.components.DayHeaderRow
import io.apptolast.paparcar.presentation.history.components.EmptyHistoryState
import io.apptolast.paparcar.presentation.history.components.EndedSessionTimelineNode
import io.apptolast.paparcar.presentation.history.components.HistorySectionHeader
import io.apptolast.paparcar.presentation.history.components.StatsRow
import io.apptolast.paparcar.presentation.history.components.WeeklyActivityCard
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_active_section
import paparcar.composeapp.generated.resources.history_cd_back
import paparcar.composeapp.generated.resources.history_ended_section
import paparcar.composeapp.generated.resources.history_month_1
import paparcar.composeapp.generated.resources.history_month_10
import paparcar.composeapp.generated.resources.history_month_11
import paparcar.composeapp.generated.resources.history_month_12
import paparcar.composeapp.generated.resources.history_month_2
import paparcar.composeapp.generated.resources.history_month_3
import paparcar.composeapp.generated.resources.history_month_4
import paparcar.composeapp.generated.resources.history_month_5
import paparcar.composeapp.generated.resources.history_month_6
import paparcar.composeapp.generated.resources.history_month_7
import paparcar.composeapp.generated.resources.history_month_8
import paparcar.composeapp.generated.resources.history_month_9
import paparcar.composeapp.generated.resources.history_title
import paparcar.composeapp.generated.resources.history_today
import paparcar.composeapp.generated.resources.history_yesterday
import kotlin.time.Instant

// ─── Typography ───────────────────────────────────────────────────────────────
//
// Uncomment once font files are in composeResources/font/:
//
// val SyneFontFamily = FontFamily(
//     Font(Res.font.syne_bold,      FontWeight.Bold),
//     Font(Res.font.syne_extrabold, FontWeight.ExtraBold),
// )
// val JostFontFamily = FontFamily(
//     Font(Res.font.jost_regular,  FontWeight.Normal),
//     Font(Res.font.jost_medium,   FontWeight.Medium),
//     Font(Res.font.jost_semibold, FontWeight.SemiBold),
//     Font(Res.font.jost_bold,     FontWeight.Bold),
// )

internal val TopBarFont = FontFamily.Default   // replace with SyneFontFamily
internal val BodyFont = FontFamily.SansSerif // replace with JostFontFamily

internal val BodyMedium = TextStyle(
    fontFamily = BodyFont,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    letterSpacing = 0.1.sp,
)
internal val BodySmall = TextStyle(
    fontFamily = BodyFont,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    letterSpacing = 0.1.sp,
)
internal val LabelBold = TextStyle(
    fontFamily = BodyFont,
    fontWeight = FontWeight.Bold,
    fontSize = 11.sp,
    letterSpacing = 0.5.sp,
)
internal val TitleBody = TextStyle(
    fontFamily = BodyFont,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    letterSpacing = 0.sp,
)

// ─── Month resources ──────────────────────────────────────────────────────────

internal val MONTH_RES: List<StringResource> = listOf(
    Res.string.history_month_1, Res.string.history_month_2,
    Res.string.history_month_3, Res.string.history_month_4,
    Res.string.history_month_5, Res.string.history_month_6,
    Res.string.history_month_7, Res.string.history_month_8,
    Res.string.history_month_9, Res.string.history_month_10,
    Res.string.history_month_11, Res.string.history_month_12,
)

// Short month names for non-composable helpers (buildTimeline day headers)
internal val MONTH_NAMES_SHORT = listOf(
    "ene", "feb", "mar", "abr", "may", "jun",
    "jul", "ago", "sep", "oct", "nov", "dic",
)

// ─── Weekly chart data model ───────────────────────────────────────────────────

data class WeekDayStats(val label: String, val sessions: Int, val minutes: Int)

// ─── Timeline data model ──────────────────────────────────────────────────────

internal sealed class TimelineItem {
    abstract val key: String

    data class Header(val label: String) : TimelineItem() {
        override val key: String = "hdr_$label"
    }

    data class Session(val parking: UserParking, val isLast: Boolean) : TimelineItem() {
        override val key: String = "ses_${parking.id}"
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToMap: (lat: Double, lon: Double) -> Unit = { _, _ -> },
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)
    val currentOnNavigateToMap by rememberUpdatedState(onNavigateToMap)

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is HistoryEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                is HistoryEffect.NavigateBack -> currentOnNavigateBack()
                is HistoryEffect.NavigateToMap -> currentOnNavigateToMap(effect.lat, effect.lon)
            }
        }
    }

    val onViewOnMap: (Double, Double) -> Unit = remember(viewModel) {
        { lat, lon -> viewModel.handleIntent(HistoryIntent.ViewOnMap(lat, lon)) }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.history_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = TopBarFont,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.history_cd_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        HistoryContent(
            state = state,
            contentPadding = padding,
            onViewOnMap = onViewOnMap,
        )
    }
}

@Composable
internal fun HistoryContent(
    state: HistoryState,
    contentPadding: PaddingValues,
    onViewOnMap: (Double, Double) -> Unit,
) {
    val todayLabel = stringResource(Res.string.history_today)
    val yesterdayLabel = stringResource(Res.string.history_yesterday)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            state.sessions.isEmpty() -> {
                EmptyHistoryState(modifier = Modifier.align(Alignment.Center))
            }

            else -> {
                val active = remember(state.sessions) { state.sessions.firstOrNull { it.isActive } }
                val ended = remember(state.sessions) { state.sessions.filter { !it.isActive } }
                val weeklyStats = remember(ended) { buildWeeklyStats(ended) }
                val timelineItems = remember(ended, todayLabel, yesterdayLabel) {
                    buildTimeline(ended, todayLabel, yesterdayLabel)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    item(key = "chart") {
                        WeeklyActivityCard(data = weeklyStats)
                    }

                    item(key = "stats") {
                        Spacer(Modifier.height(8.dp))
                        StatsRow(sessions = state.sessions)
                    }

                    if (active != null) {
                        item(key = "active_header") {
                            Spacer(Modifier.height(8.dp))
                            HistorySectionHeader(
                                text = stringResource(Res.string.history_active_section),
                            )
                        }
                        item(key = "active_${active.id}") {
                            Spacer(Modifier.height(4.dp))
                            ActiveSessionHeroCard(
                                session = active,
                                onViewOnMap = onViewOnMap,
                            )
                        }
                    }

                    if (ended.isNotEmpty()) {
                        item(key = "ended_header") {
                            Spacer(Modifier.height(8.dp))
                            HistorySectionHeader(
                                text = stringResource(Res.string.history_ended_section),
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        items(
                            items = timelineItems,
                            key = { it.key },
                        ) { timelineItem ->
                            when (timelineItem) {
                                is TimelineItem.Header -> DayHeaderRow(label = timelineItem.label)
                                is TimelineItem.Session -> EndedSessionTimelineNode(
                                    session = timelineItem.parking,
                                    isLast = timelineItem.isLast,
                                    onViewOnMap = onViewOnMap,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun buildWeeklyStats(sessions: List<UserParking>): List<WeekDayStats> {
    val labels = listOf("L", "M", "X", "J", "V", "S", "D")
    val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
    val recentSessions = sessions.filter { it.location.timestamp >= nowMs - sevenDaysMs }
    val grouped: Map<Int, List<UserParking>> = recentSessions.groupBy { session ->
        Instant.fromEpochMilliseconds(session.location.timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .dayOfWeek.isoDayNumber
    }
    return labels.mapIndexed { i, label ->
        val daySessions: List<UserParking> = grouped[i + 1] ?: emptyList()
        WeekDayStats(label = label, sessions = daySessions.size, minutes = 0)
    }
}

private fun buildTimeline(
    sessions: List<UserParking>,
    todayLabel: String,
    yesterdayLabel: String,
): List<TimelineItem> {
    val tz = TimeZone.currentSystemDefault()
    val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz).date
    val yesterday = Instant.fromEpochMilliseconds(nowMs - 86_400_000L).toLocalDateTime(tz).date

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
                else -> "${date.dayOfMonth} ${MONTH_NAMES_SHORT[date.monthNumber - 1]} ${date.year}"
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
