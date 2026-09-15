package com.rndeveloper.paparcar.presentation.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.stats.HeatMeasure
import com.rndeveloper.paparcar.domain.stats.ParkingHeatmap
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.components.chips.PaparcarFilterChip
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.presentation.vehicles.DAY_SHORT_RES
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.stats_metric_parkings
import paparcar.composeapp.generated.resources.stats_tile_search_time
import paparcar.composeapp.generated.resources.stats_when_empty
import paparcar.composeapp.generated.resources.stats_when_more
import paparcar.composeapp.generated.resources.stats_when_less
import paparcar.composeapp.generated.resources.stats_when_title

/**
 * "When you park" — the week as a grid of days × four-hour bands.
 * [STATS-THE-EXPENSIVE-HOUR-IS-MEASURED-001]
 *
 * ⛔ **Intensity, never a new hue.** The cells are `PapColor.brandData` at a varying alpha, which is
 * exactly what the activity bars already do to say "less" — a heat map is that idea in two
 * dimensions. A second sequential RAMP would have to compete with the one that already means
 * something in this app (a spot's freshness), and that ramp is exclusive to it.
 * [UI-COLOR-DOCTRINE-001] [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]
 *
 * ⛔ **Boxes, not a Canvas.** The grid needs its hour and day labels, and text on a Canvas means a
 * `TextMeasurer` and a `TextStyle` built by hand — the one thing the typography guardrail exempts
 * for `ActivityBarCanvas` "and nothing else". Forty-two composables cost less than an exemption.
 * [UI-TYPE-SYSTEM-HYGIENE-001]
 */
@Composable
internal fun StatsWhenCard(
    heatmap: ParkingHeatmap,
    measures: List<HeatMeasure>,
    onMeasureSelected: (HeatMeasure) -> Unit,
) {
    PapOutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CARD_PAD_DP.dp)) {
            Text(
                text = stringResource(Res.string.stats_when_title),
                style = PaparcarType.current.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (measures.size > 1) {
                Spacer(Modifier.height(TITLE_GAP_DP.dp))
                // Scrolls for the same reason the chart's row does: a chip that wraps its own
                // label has stopped being a label. (Measured on device, 15-09.)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(CHIP_GAP_DP.dp)) {
                    items(measures) { measure ->
                        PaparcarFilterChip(
                            label = measure.label(),
                            selected = measure == heatmap.measure,
                            onClick = { onMeasureSelected(measure) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(GRID_GAP_DP.dp))
            if (heatmap.isEmpty) {
                // A grid of empty cells would look like a chart that failed to load. The card says
                // what is missing instead.
                Text(
                    text = stringResource(Res.string.stats_when_empty),
                    style = PaparcarType.current.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                HeatGrid(heatmap)
                Spacer(Modifier.height(GRID_GAP_DP.dp))
                IntensityLegend()
            }
        }
    }
}

@Composable
private fun HeatGrid(heatmap: ParkingHeatmap) {
    val dayLabels = DAY_SHORT_RES.map { stringResource(it) }
    val peak = heatmap.peak ?: return
    Column(verticalArrangement = Arrangement.spacedBy(CELL_GAP_DP.dp)) {
        // Hour axis. Bare numbers: this is a clock reading, not copy anyone translates — the same
        // reasoning `formatClockTime` already wrote down.
        Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP_DP.dp)) {
            Spacer(Modifier.width(DAY_LABEL_W_DP.dp))
            repeat(ParkingHeatmap.BANDS) { band ->
                Text(
                    text = (band * ParkingHeatmap.BAND_HOURS).toString().padStart(2, '0'),
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        repeat(ParkingHeatmap.DAYS) { day ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(CELL_GAP_DP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dayLabels[day],
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.width(DAY_LABEL_W_DP.dp),
                )
                repeat(ParkingHeatmap.BANDS) { band ->
                    val cell = heatmap.cell(day, band)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(PapShapes.cell)
                            .background(cellColor(cell?.value, peak)),
                    )
                }
            }
        }
    }
}

/**
 * How full a cell is drawn. A cell with NO data keeps the empty track the bars use — the ruler the
 * filled ones are read against — and never a pale version of the colour, which would read as "a
 * little" where the honest answer is "nothing".
 */
@Composable
private fun cellColor(value: Double?, peak: Double) = if (value == null || peak <= 0.0) {
    MaterialTheme.colorScheme.onSurface.copy(alpha = EMPTY_CELL_ALPHA)
} else {
    PapColor.brandData.copy(
        alpha = (MIN_CELL_ALPHA + (value / peak).toFloat() * (1f - MIN_CELL_ALPHA)).coerceIn(0f, 1f),
    )
}

/** Three steps of the same green, so "more" and "less" are readable without a number on each cell. */
@Composable
private fun IntensityLegend() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CELL_GAP_DP.dp),
    ) {
        Text(
            text = stringResource(Res.string.stats_when_less),
            style = PaparcarType.current.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LEGEND_STEPS.forEach { step ->
            Box(
                modifier = Modifier
                    .size(LEGEND_SWATCH_DP.dp)
                    .clip(PapShapes.cell)
                    .background(PapColor.brandData.copy(alpha = step)),
            )
        }
        Text(
            text = stringResource(Res.string.stats_when_more),
            style = PaparcarType.current.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The grid borrows the chart's words: one idea, one wording. */
@Composable
private fun HeatMeasure.label(): String = stringResource(
    when (this) {
        HeatMeasure.PARKINGS -> Res.string.stats_metric_parkings
        HeatMeasure.SEARCH_TIME -> Res.string.stats_tile_search_time
    },
)

private const val CARD_PAD_DP = 16
private const val TITLE_GAP_DP = 10
private const val GRID_GAP_DP = 12
private const val CELL_GAP_DP = 4
private const val CHIP_GAP_DP = 8
private const val DAY_LABEL_W_DP = 26
private const val LEGEND_SWATCH_DP = 12
/** A cell with the least data still has to be visible against the empty track. */
private const val MIN_CELL_ALPHA = 0.22f
private const val EMPTY_CELL_ALPHA = 0.06f
private val LEGEND_STEPS = listOf(0.22f, 0.5f, 0.78f, 1f)
