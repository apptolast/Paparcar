package com.rndeveloper.paparcar.presentation.vehicles.components

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.presentation.vehicles.WeekDayStats
import kotlin.math.roundToInt
import com.rndeveloper.paparcar.ui.components.rememberCountUp
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
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
) {
    // Brand green, on EVERY vehicle's page — the chart is app chrome, not identity anatomy. An
    // identity-tinted chart (blue for BT, neutral for unwatched) was built and REVOKED by the
    // user on device (28-08): the theme stays green everywhere and the car's identity lives only
    // in its method glyphs (border/badge/dot/pill). See COLOR-SYSTEM §8. [UI-COLOR-DOCTRINE-001]
    ActivityCardShell {
        // ⛔ No separate "low activity" body any more. It existed because a chart with few buckets
        // used to render "a full-width blob" — the symptom of bars taking a FRACTION of the space
        // each bucket owned. Since the gap became the constant (STATS-UI-001), a week with one
        // parking draws one short bar among seven and reads fine, so the branch was buying nothing
        // and costing plenty: a second implementation of the "N parkings" phrase, an inline
        // FontWeight.Bold and a decorative arrow. [UI-IDENTITY-IS-SAID-ONCE-001]
        ActivityCardTitle(total = total, distanceText = distanceText)
        Spacer(Modifier.height(CARD_TITLE_GAP_DP.dp))
        ActivityBarChart(data = data)
    }
}

@Composable
private fun ActivityCardTitle(total: Int, distanceText: String?) {
    // Two ends, not one sentence: the count is what the card is ABOUT and the distance is a
    // qualifier. Run together ("12 aparcamientos · 84,2 km") they read as equals and the eye has to
    // parse a middle dot to tell them apart; pushed to opposite edges, the hierarchy is the layout.
    // (User, 08-09-2026.) [ACTIVITY-CARD-TITLE-001]
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TITLE_GAP_DP.dp),
    ) {
        // ⛔ No glyph in front of the count, and the reason is worth keeping: there WAS one for a
        // few hours on 08-09-2026 — a fixed `TrendingUp`, copied straight out of the HTML mock this
        // layout came from. A permanently-up arrow has the SHAPE of information, so it reads as a
        // trend, and half the time it says the opposite of the truth: a month with fewer parkings
        // than the last still pointed up.
        //
        // Making it a real trend was the obvious repair and it is worse. This ticket's own rule for
        // the figures below is that direction is a shape and never a colour, because up/down is a
        // judgement the app has no business making — and on parkings it is nakedly false: parking
        // MORE is not an achievement. It would also put a second comparison language (a tinted
        // arrow) on a screen that already speaks one (neutral ▲/▼ + percent, in the tiles).
        //
        // And the colour settles it: a green glyph in front of a green number spends the accent
        // twice on one fact. The number IS the fact; a glyph would only be labelling it.
        // The count climbs to its new value when the period changes, with the same movement the
        // figures below use — one screen, one speed. The NOUN comes from the target count, not from
        // the number mid-flight, so it cannot flicker between singular and plural on the way there.
        val shownTotal = rememberCountUp(total.toFloat()).roundToInt()
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = PapColor.actionText)) { append("$shownTotal ") }
                append(pluralStringResource(Res.plurals.history_activity_noun, total))
            },
            style = PaparcarType.current.cardTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (distanceText != null) {
            // A sentence ("56,0 km in 14 of them") cannot be interpolated, so it changes the way a
            // sentence can. [STATS-PANEL-001]
            Crossfade(targetState = distanceText, label = "activity_distance") { shown ->
                Text(
                    text = shown,
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
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
    val primaryColor = PapColor.brandData
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

private const val CARD_CORNER_DP = 16
private const val CARD_INNER_PAD_DP = 16
private const val CARD_TITLE_GAP_DP = 16     // gap from the card title down to the bars
// ≤ this many sessions in the selected window → compact summary instead of the full chart. [Task 3]
private const val LOW_DATA_THRESHOLD = 2
private const val LOW_ICON_CIRCLE_DP = 44
private const val LOW_ICON_DP = 22
private const val BORDER_ALPHA = 0.5f
private val SUBTITLE_ALPHA = PapAlpha.subtitle
/** Fixed breathing room between bars, whatever the bucket count. */
private const val TITLE_GAP_DP = 7
