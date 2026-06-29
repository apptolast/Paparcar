package io.apptolast.paparcar.presentation.vehicles.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.vehicles.WeekDayStats
import io.apptolast.paparcar.ui.theme.rememberDataTypography
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_weekly_subtitle
import paparcar.composeapp.generated.resources.history_weekly_title

private const val CHART_ENTER_DURATION = 800

/**
 * Weekly activity bar chart (v1 redesign).
 *  - 20dp card, 1px subtle outline.
 *  - Subtitle: "<count> sessions · last 7 days" with count in primary bold.
 *  - Today's bar = primary; other bars = primary @ 50% alpha.
 *  - 3 grid lines @ 7% alpha and per-bar count callout on fade-in.
 */
@Composable
internal fun WeeklyActivityCard(data: List<WeekDayStats>) {
    val maxSessions = (data.maxOfOrNull { it.sessions } ?: 1).coerceAtLeast(1)
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
    val dataType = rememberDataTypography()

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

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
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(Res.string.history_weekly_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) {
                        append("$total ")
                    }
                    append(pluralStringResource(Res.plurals.history_weekly_subtitle, total))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBTITLE_ALPHA),
            )

            Spacer(Modifier.height(18.dp))

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
                    labelStyle = dataType.chartDayLabel,
                    countStyle = dataType.chartCountBadge,
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
    primaryColor: Color,
    onSurfaceColor: Color,
    labelStyle: TextStyle,
    countStyle: TextStyle,
) {
    if (data.isEmpty()) return

    val labelHeight = 22.dp.toPx()
    val chartHeight = size.height - labelHeight
    val barAreaWidth = size.width / data.size
    val barWidth = barAreaWidth * BAR_WIDTH_FRACTION
    val corner = CornerRadius(barWidth / 2f, barWidth / 2f)

    drawGridLines(chartHeight, onSurfaceColor)

    data.forEachIndexed { index, item ->
        val centerX = barAreaWidth * index + barAreaWidth / 2f
        val barLeft = centerX - barWidth / 2f
        val fillRatio = if (maxValue > 0) item.sessions.toFloat() / maxValue else 0f
        val barHeight = chartHeight * fillRatio * progress
        val isToday = index == data.size - 1

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

private const val CARD_CORNER_DP = 20
private const val CHART_HEIGHT_DP = 120
private const val BORDER_ALPHA = 0.5f
private const val SUBTITLE_ALPHA = 0.55f
private const val BAR_WIDTH_FRACTION = 0.45f
private const val GRID_LINE_COUNT = 3
private const val GRID_LINE_ALPHA = 0.07f
private const val PLACEHOLDER_BAR_ALPHA = 0.06f
private const val INACTIVE_BAR_ALPHA = 0.5f
private const val LABEL_ALPHA_MUTED = 0.45f
private const val LABEL_ALPHA_TODAY = 0.8f
private const val COUNT_FADE_START = 0.8f
