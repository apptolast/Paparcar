package com.rndeveloper.paparcar.presentation.vehicles.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.presentation.vehicles.WeekDayStats
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_activity_low_hint
import paparcar.composeapp.generated.resources.history_activity_noun
import com.rndeveloper.paparcar.ui.theme.PapAlpha
import com.rndeveloper.paparcar.ui.theme.PapColor

private const val CHART_ENTER_DURATION = 800

/**
 * Activity chart (v1 redesign) — reflects the currently-selected time filter (its buckets are built
 * per-scope: daily for a week, weekly for a month, monthly for longer windows). [VEHICLES-REDESIGN-001]
 *  - 20dp card, 1px subtle outline.
 *  - Title lives OUTSIDE the card: the "Activity" section header in HistoryContent owns it, so the
 *    card renders only the chart (or its compact low-data summary). [HOME-VEH-REFINE-001]
 *  - Newest bucket = primary; the rest = primary @ 50% alpha.
 *  - When the selected window holds ≤2 sessions it collapses to a compact summary (Task 3) instead of
 *    a near-empty full-height chart; the bar scale also has a floor so a lone bar never fills the top.
 */
@Composable
internal fun ActivityCard(
    data: List<WeekDayStats>,
    total: Int,
    // Pre-formatted, locale-correct "8,4 km" of the SCOPED sessions — null hides it (no distance
    // data must render as nothing, never as "0 km"). [VEH-STATS-SAY-SOMETHING-USEFUL-001]
    distanceText: String? = null,
    // All-history facts (most active day · usual street · auto-detected share), already resolved
    // to user copy by the caller; each one only exists above its significance threshold.
    facts: List<ActivityFact> = emptyList(),
) {
    // Brand green, on EVERY vehicle's page — the chart is app chrome, not identity anatomy. An
    // identity-tinted chart (blue for BT, neutral for unwatched) was built and REVOKED by the
    // user on device (28-08): the theme stays green everywhere and the car's identity lives only
    // in its method glyphs (border/badge/dot/pill). See COLOR-SYSTEM §8. [UI-COLOR-DOCTRINE-001]
    ActivityCardShell {
        if (total <= LOW_DATA_THRESHOLD) {
            LowActivitySummary(total = total)
        } else {
            // The scoped count is the card's title (icon + "N parkings"), so the graph card reads as
            // a titled block, not a bare chart. [ACTIVITY-CARD-TITLE-001]
            ActivityCardTitle(total = total, distanceText = distanceText)
            Spacer(Modifier.height(CARD_TITLE_GAP_DP.dp))
            ActivityBarChart(data = data)
        }
        if (facts.isNotEmpty()) {
            Spacer(Modifier.height(FACTS_TOP_GAP_DP.dp))
            ActivityFactsColumn(facts)
        }
    }
}

/** One secondary line under the activity chart — icon + resolved copy. */
internal data class ActivityFact(val icon: ImageVector, val text: String)

@Composable
private fun ActivityFactsColumn(facts: List<ActivityFact>) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(FACT_ROW_GAP_DP.dp)) {
        facts.forEach { fact ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FACT_ICON_GAP_DP.dp),
            ) {
                Icon(
                    imageVector = fact.icon,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(FACT_ICON_DP.dp),
                )
                Text(
                    text = fact.text,
                    // The `meta` role, kin to the chart labels above — the user weighed the
                    // `caption` alternative on device (28-08) and preferred this. The DATA
                    // reading: each line is icon + token + payload, kin to the chart it footers.
                    style = PaparcarType.current.meta,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ActivityCardTitle(total: Int, distanceText: String?) {
    // No icon — the count in primary already carries the visual hierarchy. [CARD-META-POLISH-001]
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = PapColor.actionText)) { append("$total ") }
            append(pluralStringResource(Res.plurals.history_activity_noun, total))
            if (distanceText != null) {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    append(" · $distanceText")
                }
            }
        },
        style = PaparcarType.current.cardTitle,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ActivityCardShell(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER_DP.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = BORDER_ALPHA),
        ),
    ) {
        // 16dp, matching the sibling cards and the external "Activity" section header so the chart
        // content doesn't sit 4dp deeper than the title above it. [UI-REGRESSION]
        Column(modifier = Modifier.padding(CARD_INNER_PAD_DP.dp)) { content() }
    }
}

/**
 * Compact low-data variant — a short row instead of a 120dp chart so the section never looks empty
 * when the selected window has 0–2 sessions. [VEHICLES-REDESIGN-001]
 */
@Composable
private fun LowActivitySummary(total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(LOW_ICON_CIRCLE_DP.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(LOW_ICON_DP.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = PapColor.actionText,
                            fontWeight = FontWeight.Bold,
                        ),
                    ) { append("$total ") }
                    append(pluralStringResource(Res.plurals.history_activity_noun, total))
                },
                style = PaparcarType.current.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.history_activity_low_hint),
                style = PaparcarType.current.caption,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBTITLE_ALPHA),
            )
        }
    }
}

@Composable
private fun ActivityBarChart(data: List<WeekDayStats>) {
    // Scale floor: a lone bar (value 1) shouldn't fill the whole chart height and leave the rest blank,
    // so the max is clamped up to MIN_SCALE_MAX. [VEHICLES-REDESIGN-001]
    val maxSessions = (data.maxOfOrNull { it.sessions } ?: 1).coerceAtLeast(MIN_SCALE_MAX)
    val total = data.sumOf { it.sessions }

    // Re-grow the bars whenever the dataset changes (e.g. a new session lands)
    // instead of animating only on first composition.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(total, maxSessions) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = CHART_ENTER_DURATION, easing = FastOutSlowInEasing),
        )
    }

    val anim = progress.value
    val textMeasurer = rememberTextMeasurer()
    val type = PaparcarType.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT_DP.dp),
    ) {
        drawBarChart(
            data = data,
            maxValue = maxSessions,
            progress = anim,
            textMeasurer = textMeasurer,
            primaryColor = primaryColor,
            onSurfaceColor = onSurfaceColor,
            labelStyle = type.chartLabel,
            countStyle = type.chartValue,
        )
    }
}

private fun DrawScope.drawBarChart(
    data: List<WeekDayStats>,
    maxValue: Int,
    progress: Float,
    textMeasurer: TextMeasurer,
    primaryColor: Color,
    onSurfaceColor: Color,
    labelStyle: TextStyle,
    countStyle: TextStyle,
) {
    if (data.isEmpty()) return

    val labelHeight = 22.dp.toPx()
    val chartHeight = size.height - labelHeight
    val barAreaWidth = size.width / data.size
    // Cap the bar width so a chart with few buckets (e.g. one week into the month) renders a normal,
    // centred bar instead of a full-width blob. [VEHICLES-REDESIGN-001]
    val barWidth = (barAreaWidth * BAR_WIDTH_FRACTION).coerceAtMost(MAX_BAR_WIDTH_DP.dp.toPx())
    val corner = CornerRadius(barWidth / 2f, barWidth / 2f)

    drawGridLines(chartHeight, onSurfaceColor)

    data.forEachIndexed { index, item ->
        val centerX = barAreaWidth * index + barAreaWidth / 2f
        val barLeft = centerX - barWidth / 2f
        val fillRatio = if (maxValue > 0) item.sessions.toFloat() / maxValue else 0f
        // Reserve headroom above the tallest bar so the per-bar count label never clips off the top of
        // the canvas (a full-height bar would push its number to a negative y). [VEHICLES-REDESIGN-001]
        val barHeight = chartHeight * BAR_MAX_HEIGHT_FRACTION * fillRatio * progress
        val isToday = item.isCurrent

        drawBar(barLeft, barWidth, barHeight, chartHeight, corner, isToday, primaryColor, onSurfaceColor)
        drawDayLabel(centerX, item.label, isToday, textMeasurer, labelStyle, onSurfaceColor)
        if (item.sessions > 0 && progress > COUNT_FADE_START) {
            drawCountLabel(centerX, barHeight, chartHeight, item.sessions, progress, textMeasurer, countStyle, primaryColor)
        }
    }
}

private fun DrawScope.drawGridLines(chartHeight: Float, onSurfaceColor: Color) {
    repeat(GRID_LINE_COUNT) { i ->
        val y = chartHeight * (1f - (i + 1).toFloat() / GRID_LINE_COUNT)
        drawLine(
            color = onSurfaceColor.copy(alpha = GRID_LINE_ALPHA),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawBar(
    barLeft: Float,
    barWidth: Float,
    barHeight: Float,
    chartHeight: Float,
    corner: CornerRadius,
    isToday: Boolean,
    primaryColor: Color,
    onSurfaceColor: Color,
) {
    drawRoundRect(
        color = onSurfaceColor.copy(alpha = PLACEHOLDER_BAR_ALPHA),
        topLeft = Offset(barLeft, 0f),
        size = Size(barWidth, chartHeight),
        cornerRadius = corner,
    )
    if (barHeight > 0f) {
        drawRoundRect(
            color = if (isToday) primaryColor else primaryColor.copy(alpha = INACTIVE_BAR_ALPHA),
            topLeft = Offset(barLeft, chartHeight - barHeight),
            size = Size(barWidth, barHeight),
            cornerRadius = corner,
        )
    }
}

private fun DrawScope.drawDayLabel(
    centerX: Float,
    label: String,
    isToday: Boolean,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    onSurfaceColor: Color,
) {
    val result = textMeasurer.measure(
        label,
        labelStyle.copy(
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) onSurfaceColor.copy(alpha = LABEL_ALPHA_TODAY)
                    else onSurfaceColor.copy(alpha = LABEL_ALPHA_MUTED),
        ),
    )
    drawText(
        textLayoutResult = result,
        topLeft = Offset(x = centerX - result.size.width / 2f, y = size.height - result.size.height),
    )
}

private fun DrawScope.drawCountLabel(
    centerX: Float,
    barHeight: Float,
    chartHeight: Float,
    sessionCount: Int,
    progress: Float,
    textMeasurer: TextMeasurer,
    countStyle: TextStyle,
    primaryColor: Color,
) {
    val fade = ((progress - COUNT_FADE_START) / (1f - COUNT_FADE_START)).coerceIn(0f, 1f)
    val result = textMeasurer.measure(
        sessionCount.toString(),
        countStyle.copy(color = primaryColor.copy(alpha = fade)),
    )
    drawText(
        textLayoutResult = result,
        topLeft = Offset(
            x = centerX - result.size.width / 2f,
            y = chartHeight - barHeight - result.size.height - 4.dp.toPx(),
        ),
    )
}

private const val CARD_CORNER_DP = 16
private const val CARD_INNER_PAD_DP = 16
private const val CARD_TITLE_GAP_DP = 16     // gap from the card title down to the bars
// Facts footer — secondary lines under the chart. [VEH-STATS-SAY-SOMETHING-USEFUL-001]
private const val FACTS_TOP_GAP_DP = 12
private const val FACT_ROW_GAP_DP = 6
private const val FACT_ICON_GAP_DP = 6
private const val FACT_ICON_DP = 14
private const val CHART_HEIGHT_DP = 120
// ≤ this many sessions in the selected window → compact summary instead of the full chart. [Task 3]
private const val LOW_DATA_THRESHOLD = 2
// Bar-scale floor: the max value used for bar heights never drops below this, so a single session
// doesn't render as a full-height bar. [Task 3]
private const val MIN_SCALE_MAX = 3
private const val LOW_ICON_CIRCLE_DP = 44
private const val LOW_ICON_DP = 22
private const val BORDER_ALPHA = 0.5f
private val SUBTITLE_ALPHA = PapAlpha.subtitle
private const val BAR_WIDTH_FRACTION = 0.45f
// Max bar width so few-bucket charts don't render one giant full-width bar. [VEHICLES-REDESIGN-001]
private const val MAX_BAR_WIDTH_DP = 32
// Tallest bar fills this fraction of the chart height, leaving room for the count label above it.
private const val BAR_MAX_HEIGHT_FRACTION = 0.82f
private const val GRID_LINE_COUNT = 3
private const val GRID_LINE_ALPHA = 0.07f
private const val PLACEHOLDER_BAR_ALPHA = 0.06f
private const val INACTIVE_BAR_ALPHA = 0.5f
private const val LABEL_ALPHA_MUTED = 0.45f
private const val LABEL_ALPHA_TODAY = 0.8f
private const val COUNT_FADE_START = 0.8f
