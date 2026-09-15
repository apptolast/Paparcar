package com.rndeveloper.paparcar.presentation.vehicles.components

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
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.presentation.vehicles.ActivityBucket

/**
 * The activity chart's actual drawing — every `DrawScope` routine the Canvas calls, and nothing else.
 *
 * ⛔ It lives apart from its composables ON PURPOSE. Measuring text on a Canvas is the one place the
 * app legitimately sets `fontWeight` by hand (there is no role to ask: `TextMeasurer` takes a
 * `TextStyle`, not a token), so this file is the ONE entry the typography guardrail exempts. While
 * the drawing shared a file with the card, that exemption covered the composables too — and it was
 * quietly covering a plain `FontWeight.Bold` in the low-activity summary, which is exactly the
 * failure `TypographyGuardrailTest` documents for ConnectivityBanner: the allowlist hid the bug
 * instead of documenting it. Split like this, the exemption is as small as the reason for it.
 * [UI-IDENTITY-IS-SAID-ONCE-001] [UI-TYPE-SYSTEM-HYGIENE-001]
 */

internal fun DrawScope.drawBarChart(
    data: List<ActivityBucket>,
    maxValue: Float,
    progress: Float,
    textMeasurer: TextMeasurer,
    primaryColor: Color,
    onSurfaceColor: Color,
    labelStyle: TextStyle,
    countStyle: TextStyle,
    /** The value above a bar, already in the reader's units and locale. Resolved in composition and
     *  passed in pure, because a `DrawScope` cannot call a composable.
     *  [STATS-A-PERIOD-IS-A-SERIES-NOT-A-NUMBER-001] */
    formatValue: (Float) -> String,
) {
    if (data.isEmpty()) return

    val labelHeight = 22.dp.toPx()
    val chartHeight = size.height - labelHeight
    // ⛔ The GAP is the constant, not the bar. Before, each bucket owned width/n and the bar took a
    // FRACTION of it, so the spacing between bars grew and shrank with the number of buckets: seven
    // days sat tight and four weeks floated far apart, and neither reading was the one the chart
    // meant. Now the breathing room is fixed and the bars share whatever is left — a month renders
    // wide bars, a week narrow ones, and the rhythm between them never changes.
    // (User, 08-09-2026.) [VEHICLES-REDESIGN-001]
    val gap = BAR_GAP_DP.dp.toPx()
    val barWidth = ((size.width - gap * (data.size - 1)) / data.size).coerceAtLeast(1f)
    // Rounded like the rest of the app rather than a pill: at a month's width a half-width radius
    // turns a bar into a capsule that no longer reads as a column. Thin bars still round fully,
    // because the cap can never exceed half the width. [UI-SHEET-001]
    val cornerPx = BAR_CORNER_DP.dp.toPx().coerceAtMost(barWidth / 2f)
    val corner = CornerRadius(cornerPx, cornerPx)

    drawGridLines(chartHeight, onSurfaceColor)

    data.forEachIndexed { index, item ->
        val barLeft = index * (barWidth + gap)
        val centerX = barLeft + barWidth / 2f
        val fillRatio = if (maxValue > 0f) item.value / maxValue else 0f
        // Reserve headroom above the tallest bar so the per-bar count label never clips off the top of
        // the canvas (a full-height bar would push its number to a negative y). [VEHICLES-REDESIGN-001]
        val barHeight = chartHeight * BAR_MAX_HEIGHT_FRACTION * fillRatio * progress
        val isToday = item.isCurrent

        drawBar(barLeft, barWidth, barHeight, chartHeight, corner, isToday, primaryColor, onSurfaceColor)
        // ⛔ Con doce barras las etiquetas se pisan: a 360 dp cada bucket son ~15 dp y un «sep»
        // ocupa el doble. Se pintan ALTERNAS —y la última siempre, que es el mes en curso y la
        // referencia de todo el gráfico—, así el año cabe sin que el eje se vuelva ilegible.
        // [STATS-A-YEAR-IS-THE-SPAN-MEMORY-SELLS-001]
        val crowded = data.size > LABELS_FIT_UP_TO
        val labelled = !crowded || index % 2 == data.lastIndex % 2
        if (labelled) {
            drawDayLabel(centerX, item.label, isToday, textMeasurer, labelStyle, onSurfaceColor)
        }
        if (item.value > 0f && progress > COUNT_FADE_START) {
            drawValueLabel(
                centerX, barHeight, chartHeight, formatValue(item.value), progress, textMeasurer,
                countStyle, primaryColor,
            )
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
    // The empty track stays. It was tried without (08-09-2026, at the user's request) and reverted
    // the same evening: an unfilled bucket that draws NOTHING leaves the chart floating over its
    // labels, and the eye loses the scale it is comparing the green column against. The track is
    // the ruler; the fill is the reading.
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

private fun DrawScope.drawValueLabel(
    centerX: Float,
    barHeight: Float,
    chartHeight: Float,
    valueText: String,
    progress: Float,
    textMeasurer: TextMeasurer,
    countStyle: TextStyle,
    primaryColor: Color,
) {
    val fade = ((progress - COUNT_FADE_START) / (1f - COUNT_FADE_START)).coerceIn(0f, 1f)
    val result = textMeasurer.measure(
        valueText,
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

// Facts footer — secondary lines under the chart. [VEH-STATS-SAY-SOMETHING-USEFUL-001]
internal const val CHART_HEIGHT_DP = 120
private const val BAR_GAP_DP = 10
/** Corner of a bar — the app's small-card rounding, capped at half the width for thin bars. */
private const val BAR_CORNER_DP = 8
// Max bar width so few-bucket charts don't render one giant full-width bar. [VEHICLES-REDESIGN-001]
// Tallest bar fills this fraction of the chart height, leaving room for the count label above it.
private const val BAR_MAX_HEIGHT_FRACTION = 0.82f
/** Hasta aquí caben todas las etiquetas; a partir de aquí se alternan. */
private const val LABELS_FIT_UP_TO = 7
private const val GRID_LINE_COUNT = 3
private const val GRID_LINE_ALPHA = 0.07f
private const val PLACEHOLDER_BAR_ALPHA = 0.06f
private const val INACTIVE_BAR_ALPHA = 0.5f
private const val LABEL_ALPHA_MUTED = 0.45f
private const val LABEL_ALPHA_TODAY = 0.8f
private const val COUNT_FADE_START = 0.8f
// ⛔ The bar-scale floor used to live HERE, as `MIN_SCALE_MAX = 3`. It is a floor of three
// PARKINGS, which means nothing once the same bar can hold metres or milliseconds, so it moved to
// `ChartMetric.scaleFloor` — with its unit, like every other magnitude in this app.
// [STATS-A-PERIOD-IS-A-SERIES-NOT-A-NUMBER-001]
