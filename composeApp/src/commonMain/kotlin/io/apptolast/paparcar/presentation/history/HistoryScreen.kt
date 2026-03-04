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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.presentation.util.formatCoords
import io.apptolast.paparcar.presentation.util.formatRelativeTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_active_section
import paparcar.composeapp.generated.resources.history_active_since
import paparcar.composeapp.generated.resources.history_cd_back
import paparcar.composeapp.generated.resources.history_date_pattern
import paparcar.composeapp.generated.resources.history_empty_subtitle
import paparcar.composeapp.generated.resources.history_empty_title
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
import paparcar.composeapp.generated.resources.history_precision
import paparcar.composeapp.generated.resources.history_status_active
import paparcar.composeapp.generated.resources.history_title
import paparcar.composeapp.generated.resources.history_view_map
import kotlin.time.Instant

// ─── Palette ─────────────────────────────────────────────────────────────────

private val EcoForestDark = Color(0xFF0D3D2E)
private val EcoForestMedium = Color(0xFF1A5C40)
private val EcoAccent = Color(0xFF25F48C)
private val EcoAccentDim = Color(0x2625F48C)
private val SurfaceDark = Color(0xFF0D1F17)

// ─── Typography ───────────────────────────────────────────────────────────────
//
// Syne  → TopAppBar title  (geometric, display weight — matches the React version)
// Jost  → everything else  (Futura-like, clean geometric sans)
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
//
// Then:
//   private val TopBarFont = SyneFontFamily
//   private val BodyFont   = JostFontFamily

private val TopBarFont = FontFamily.Default   // replace with SyneFontFamily
private val BodyFont = FontFamily.SansSerif // replace with JostFontFamily

private val BodyMedium = TextStyle(
    fontFamily = BodyFont,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    letterSpacing = 0.1.sp,
)
private val BodySmall = TextStyle(
    fontFamily = BodyFont,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    letterSpacing = 0.1.sp,
)
private val LabelBold = TextStyle(
    fontFamily = BodyFont,
    fontWeight = FontWeight.Bold,
    fontSize = 11.sp,
    letterSpacing = 0.5.sp,
)
private val TitleBody = TextStyle(
    fontFamily = BodyFont,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    letterSpacing = 0.sp,
)

// ─── Month resources ──────────────────────────────────────────────────────────

private val MONTH_RES: List<StringResource> = listOf(
    Res.string.history_month_1, Res.string.history_month_2,
    Res.string.history_month_3, Res.string.history_month_4,
    Res.string.history_month_5, Res.string.history_month_6,
    Res.string.history_month_7, Res.string.history_month_8,
    Res.string.history_month_9, Res.string.history_month_10,
    Res.string.history_month_11, Res.string.history_month_12,
)

// ─── Weekly chart data model ───────────────────────────────────────────────────

data class WeekDayStats(val label: String, val sessions: Int, val minutes: Int)

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

    // rememberUpdatedState ensures the LaunchedEffect always calls the *current*
    // version of these callbacks, even though the effect runs only once (Unit key).
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

    // ── ViewOnMap handler — stable reference, captures only viewModel ──────────
    // BUG FIX: cards now emit (lat, lon) themselves; this lambda only needs
    // to capture `viewModel`, which is stable across recompositions.
    val onViewOnMap: (Double, Double) -> Unit = remember(viewModel) {
        { lat, lon ->
            viewModel.handleIntent(HistoryIntent.ViewOnMap(lat, lon))
        }
    }

    Scaffold(
        // TopAppBar keeps the default Material typography (unchanged).
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
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.sessions.isEmpty() -> {
                    EmptyHistoryState(modifier = Modifier.align(Alignment.Center))
                }

                else -> {
                    val active = state.sessions.firstOrNull { it.isActive }
                    val ended = state.sessions.filter { !it.isActive }

                    // Build a simple weekly dataset from ended sessions
                    val weeklyStats = buildWeeklyStats(ended)

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // ── Weekly Activity Chart ───────────────────────────
                        item(key = "chart") {
                            WeeklyActivityCard(data = weeklyStats)
                        }

                        // ── Active session ──────────────────────────────────
                        if (active != null) {
                            item(key = "active_header") {
                                HistorySectionHeader(
                                    text = stringResource(Res.string.history_active_section),
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            item(key = "active_${active.id}") {
                                ActiveSessionHeroCard(
                                    session = active,
                                    // BUG FIX: onViewOnMap receives (lat, lon) from the card itself
                                    onViewOnMap = onViewOnMap,
                                )
                            }
                        }

                        // ── Ended sessions ──────────────────────────────────
                        if (ended.isNotEmpty()) {
                            item(key = "ended_header") {
                                HistorySectionHeader(
                                    text = stringResource(Res.string.history_ended_section),
                                    modifier = if (active != null) Modifier.padding(top = 8.dp) else Modifier,
                                )
                            }
                            items(items = ended, key = { it.id }) { session ->
                                EndedSessionCard(
                                    session = session,
                                    // BUG FIX: onViewOnMap is (lat, lon) -> Unit.
                                    // The card calls onViewOnMap(session.location.latitude, session.location.longitude)
                                    // internally, so this lambda captures ONLY viewModel (stable).
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

// ─── Weekly chart ─────────────────────────────────────────────────────────────

@Composable
private fun WeeklyActivityCard(data: List<WeekDayStats>) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        )
    }

    val anim = progress.value
    val maxSessions = (data.maxOfOrNull { it.sessions } ?: 1).coerceAtLeast(1)
    // TextMeasurer is the KMP-safe way to draw text on Canvas (no android.graphics.Paint needed)
    val textMeasurer = rememberTextMeasurer()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "Actividad semanal",
                        style = TitleBody,
                        color = Color.White,
                    )
                    Text(
                        "${data.sumOf { it.sessions }} aparcamientos esta semana",
                        style = BodySmall,
                        color = Color.White.copy(alpha = 0.45f),
                    )
                }
                Surface(
                    color = EcoAccentDim,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        "${data.sumOf { it.minutes }} min",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = LabelBold,
                        color = EcoAccent,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            ) {
                drawBarChart(
                    data = data,
                    maxValue = maxSessions,
                    progress = anim,
                    textMeasurer = textMeasurer,
                )
            }
        }
    }
}

private fun DrawScope.drawBarChart(
    data: List<WeekDayStats>,
    maxValue: Int,
    progress: Float,
    textMeasurer: TextMeasurer,
) {
    if (data.isEmpty()) return

    val labelHeight = 22.dp.toPx()
    val chartHeight = size.height - labelHeight
    val barAreaWidth = size.width / data.size
    val barWidth = barAreaWidth * 0.45f
    val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)

    // Grid lines
    repeat(3) { i ->
        val y = chartHeight * (1f - (i + 1).toFloat() / 3)
        drawLine(
            color = Color.White.copy(alpha = 0.07f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    val labelStyle = TextStyle(
        fontFamily = BodyFont,
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal,
        color = Color.White.copy(alpha = 0.4f),
    )
    val countStyle = TextStyle(
        fontFamily = BodyFont,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = EcoAccent,
    )

    data.forEachIndexed { index, item ->
        val centerX = barAreaWidth * index + barAreaWidth / 2f
        val barLeft = centerX - barWidth / 2f
        val fillRatio = if (maxValue > 0) item.sessions.toFloat() / maxValue else 0f
        val barHeight = chartHeight * fillRatio * progress

        // Background track
        drawRoundRect(
            color = Color.White.copy(alpha = 0.06f),
            topLeft = Offset(barLeft, 0f),
            size = Size(barWidth, chartHeight),
            cornerRadius = cornerRadius,
        )

        // Filled bar
        if (barHeight > 0f) {
            val isToday = index == data.size - 1
            drawRoundRect(
                color = if (isToday) EcoAccent else EcoAccent.copy(alpha = 0.55f),
                topLeft = Offset(barLeft, chartHeight - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius,
            )
        }

        // Day label (KMP-safe: drawText with TextMeasurer)
        val labelResult = textMeasurer.measure(item.label, labelStyle)
        drawText(
            textLayoutResult = labelResult,
            topLeft = Offset(
                x = centerX - labelResult.size.width / 2f,
                y = size.height - labelResult.size.height,
            ),
        )

        // Session count above bar (fade in at end of animation)
        if (item.sessions > 0 && progress > 0.8f) {
            val fadeAlpha = ((progress - 0.8f) / 0.2f).coerceIn(0f, 1f)
            val countResult = textMeasurer.measure(
                item.sessions.toString(),
                countStyle.copy(color = EcoAccent.copy(alpha = fadeAlpha * 0.9f)),
            )
            drawText(
                textLayoutResult = countResult,
                topLeft = Offset(
                    x = centerX - countResult.size.width / 2f,
                    y = chartHeight - barHeight - countResult.size.height - 4.dp.toPx(),
                ),
            )
        }
    }
}

// ─── Active session card ──────────────────────────────────────────────────────

@Composable
private fun ActiveSessionHeroCard(
    session: UserParking,
    // BUG FIX: callback is (lat, lon) -> Unit instead of () -> Unit
    onViewOnMap: (Double, Double) -> Unit,
) {
    val relativeTime = remember(session.location.timestamp) { formatRelativeTime(session.location.timestamp) }
    val dateTime = remember(session.location.timestamp) {
        Instant.fromEpochMilliseconds(session.location.timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
    }
    val monthRes = MONTH_RES.getOrNull(dateTime.month.number - 1)
    val monthName = monthRes?.let { stringResource(it) }
        ?: dateTime.month.number.toString().padStart(2, '0')
    val dateStr = stringResource(
        Res.string.history_date_pattern,
        dateTime.day, monthName,
        dateTime.hour.toString().padStart(2, '0'),
        dateTime.minute.toString().padStart(2, '0'),
    )
    val precisionStr = stringResource(Res.string.history_precision, session.location.accuracy.toInt())
    val activeSinceStr = stringResource(Res.string.history_active_since, relativeTime)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EcoForestDark),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(EcoForestMedium, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.DirectionsCar,
                        contentDescription = null,
                        tint = EcoAccent,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeSinceStr,
                        style = TitleBody,
                        color = Color.White,
                    )
                    Text(
                        text = dateStr,
                        style = BodySmall,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
                Surface(
                    color = EcoAccentDim,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        stringResource(Res.string.history_status_active),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = LabelBold,
                        color = EcoAccent,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Coords + precision
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatCoords(session.location.latitude, session.location.longitude),
                    style = BodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    color = EcoForestMedium,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = precisionStr,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = LabelBold,
                        color = EcoAccent,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // BUG FIX: card passes its own session coordinates
            Button(
                onClick = { onViewOnMap(session.location.latitude, session.location.longitude) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EcoAccent,
                    contentColor = EcoForestDark,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.history_view_map), style = LabelBold)
            }
        }
    }
}

// ─── Ended session card ───────────────────────────────────────────────────────

@Composable
private fun EndedSessionCard(
    session: UserParking,
    // BUG FIX: callback is (lat, lon) -> Unit instead of () -> Unit
    onViewOnMap: (Double, Double) -> Unit,
) {
    val dateTime = remember(session.location.timestamp) {
        Instant.fromEpochMilliseconds(session.location.timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
    }
    val monthRes = MONTH_RES.getOrNull(dateTime.month.number - 1)
    val monthName = monthRes?.let { stringResource(it) }
        ?: dateTime.month.number.toString().padStart(2, '0')
    val dateStr = stringResource(
        Res.string.history_date_pattern,
        dateTime.day, monthName,
        dateTime.hour.toString().padStart(2, '0'),
        dateTime.minute.toString().padStart(2, '0'),
    )
    val precisionStr = stringResource(Res.string.history_precision, session.location.accuracy.toInt())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.DirectionsCar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = dateStr, style = BodyMedium)
                Text(
                    text = formatCoords(session.location.latitude, session.location.longitude),
                    style = BodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        precisionStr,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        style = LabelBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // BUG FIX: card emits its own session's coordinates
                TextButton(
                    onClick = { onViewOnMap(session.location.latitude, session.location.longitude) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Icon(
                        Icons.Filled.Map,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(Res.string.history_view_map),
                        style = BodySmall,
                    )
                }
            }
        }
    }
}

// ─── Section header ───────────────────────────────────────────────────────────

@Composable
private fun HistorySectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LabelBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

// ─── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.DirectionsCar,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(64.dp),
        )
        Text(
            stringResource(Res.string.history_empty_title),
            style = TitleBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(Res.string.history_empty_subtitle),
            style = BodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Groups [sessions] by day-of-week for the last 7 days and returns
 * a [WeekDayStats] list ready for the bar chart.
 */
private fun buildWeeklyStats(sessions: List<UserParking>): List<WeekDayStats> {
    val labels = listOf("L", "M", "X", "J", "V", "S", "D")

    val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val sevenDaysMs = 7L * 24 * 60 * 60 * 1000

    // Only sessions from the last 7 days
    val recentSessions = sessions.filter { it.location.timestamp >= nowMs - sevenDaysMs }

    val grouped: Map<Int, List<UserParking>> = recentSessions.groupBy { session ->
        Instant.fromEpochMilliseconds(session.location.timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .dayOfWeek.isoDayNumber   // 1=Mon … 7=Sun
    }
    return labels.mapIndexed { i, label ->
        val daySessions: List<UserParking> = grouped[i + 1] ?: emptyList()
        WeekDayStats(
            label = label,
            sessions = daySessions.size,
            minutes = 0,
        )
    }
}