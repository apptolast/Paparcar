package io.apptolast.paparcar.presentation.history.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.presentation.history.BodyFont
import io.apptolast.paparcar.presentation.history.BodySmall
import io.apptolast.paparcar.presentation.history.LabelBold
import io.apptolast.paparcar.presentation.history.TitleBody
import io.apptolast.paparcar.presentation.history.WeekDayStats
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_minutes_suffix
import paparcar.composeapp.generated.resources.history_weekly_subtitle
import paparcar.composeapp.generated.resources.history_weekly_title

private const val CHART_ENTER_DURATION = 800

@Composable
internal fun WeeklyActivityCard(data: List<WeekDayStats>) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = CHART_ENTER_DURATION, easing = FastOutSlowInEasing),
        )
    }

    val anim = progress.value
    val maxSessions = (data.maxOfOrNull { it.sessions } ?: 1).coerceAtLeast(1)
    val textMeasurer = rememberTextMeasurer()

    // Capture theme colors before entering Canvas
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        stringResource(Res.string.history_weekly_title),
                        style = TitleBody,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${data.sumOf { it.sessions }} ${stringResource(Res.string.history_weekly_subtitle)}",
                        style = BodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        "${data.sumOf { it.minutes }} ${stringResource(Res.string.history_minutes_suffix)}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = LabelBold,
                        color = MaterialTheme.colorScheme.primary,
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
                    primaryColor = primaryColor,
                    onSurfaceColor = onSurfaceColor,
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
) {
    if (data.isEmpty()) return

    val labelHeight = 22.dp.toPx()
    val chartHeight = size.height - labelHeight
    val barAreaWidth = size.width / data.size
    val barWidth = barAreaWidth * 0.45f
    val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)

    repeat(3) { i ->
        val y = chartHeight * (1f - (i + 1).toFloat() / 3)
        drawLine(
            color = onSurfaceColor.copy(alpha = 0.07f),
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
        color = onSurfaceColor.copy(alpha = 0.4f),
    )
    val countStyle = TextStyle(
        fontFamily = BodyFont,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = primaryColor,
    )

    data.forEachIndexed { index, item ->
        val centerX = barAreaWidth * index + barAreaWidth / 2f
        val barLeft = centerX - barWidth / 2f
        val fillRatio = if (maxValue > 0) item.sessions.toFloat() / maxValue else 0f
        val barHeight = chartHeight * fillRatio * progress

        drawRoundRect(
            color = onSurfaceColor.copy(alpha = 0.06f),
            topLeft = Offset(barLeft, 0f),
            size = Size(barWidth, chartHeight),
            cornerRadius = cornerRadius,
        )

        if (barHeight > 0f) {
            val isToday = index == data.size - 1
            drawRoundRect(
                color = if (isToday) primaryColor else primaryColor.copy(alpha = 0.55f),
                topLeft = Offset(barLeft, chartHeight - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius,
            )
        }

        val labelResult = textMeasurer.measure(item.label, labelStyle)
        drawText(
            textLayoutResult = labelResult,
            topLeft = Offset(
                x = centerX - labelResult.size.width / 2f,
                y = size.height - labelResult.size.height,
            ),
        )

        if (item.sessions > 0 && progress > 0.8f) {
            val fadeAlpha = ((progress - 0.8f) / 0.2f).coerceIn(0f, 1f)
            val countResult = textMeasurer.measure(
                item.sessions.toString(),
                countStyle.copy(color = primaryColor.copy(alpha = fadeAlpha * 0.9f)),
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
