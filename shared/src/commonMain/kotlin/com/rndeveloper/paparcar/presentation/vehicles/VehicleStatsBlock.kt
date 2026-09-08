package com.rndeveloper.paparcar.presentation.vehicles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.entitlement.FREE_HISTORY_WINDOW_DAYS
import com.rndeveloper.paparcar.domain.stats.PeriodStats
import com.rndeveloper.paparcar.presentation.util.formatHourBucket
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.ui.components.PapNavChevron
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.components.PapPremiumEmphasis
import com.rndeveloper.paparcar.ui.components.PapPremiumSurface
import com.rndeveloper.paparcar.ui.components.PapStatDelta
import com.rndeveloper.paparcar.ui.components.PapStatTile
import com.rndeveloper.paparcar.ui.components.StatTileNumber
import com.rndeveloper.paparcar.ui.components.PapVerticalDivider
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_day_full_fri
import paparcar.composeapp.generated.resources.history_day_full_mon
import paparcar.composeapp.generated.resources.history_day_full_sat
import paparcar.composeapp.generated.resources.history_day_full_sun
import paparcar.composeapp.generated.resources.history_day_full_thu
import paparcar.composeapp.generated.resources.history_day_full_tue
import paparcar.composeapp.generated.resources.history_day_full_wed
import paparcar.composeapp.generated.resources.history_filter_last_3_months
import paparcar.composeapp.generated.resources.history_filter_this_month
import paparcar.composeapp.generated.resources.history_filter_this_week
import paparcar.composeapp.generated.resources.stats_lifetime
import paparcar.composeapp.generated.resources.stats_open_panel
import paparcar.composeapp.generated.resources.stats_period_all
import paparcar.composeapp.generated.resources.stats_period_free_window
import paparcar.composeapp.generated.resources.stats_plus_row_subtitle
import paparcar.composeapp.generated.resources.stats_plus_row_title
import paparcar.composeapp.generated.resources.stats_tile_auto_detected
import paparcar.composeapp.generated.resources.stats_tile_avg_duration
import paparcar.composeapp.generated.resources.stats_tile_co2
import paparcar.composeapp.generated.resources.stats_tile_cost
import paparcar.composeapp.generated.resources.stats_tile_favorite_street
import paparcar.composeapp.generated.resources.stats_tile_peak_hour
import paparcar.composeapp.generated.resources.stats_tile_spots_shared
import paparcar.composeapp.generated.resources.stats_tile_usual_day
import paparcar.composeapp.generated.resources.stats_value_hours_minutes
import paparcar.composeapp.generated.resources.stats_value_kg
import paparcar.composeapp.generated.resources.stats_value_minutes
import paparcar.composeapp.generated.resources.stats_value_share
import paparcar.composeapp.generated.resources.stats_veiled_value
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The vehicle ficha's figures block — zone 03 of the ficha layout signed off on 08-09-2026.
 * [STATS-UI-001]
 *
 * Everything here belongs to ONE scope: the period the Activity filter selects, the same one the
 * chart above spans. That is the whole point of the block existing — before it, the ficha carried
 * all-time figures in the identity card and period figures in the Activity card, plus three
 * "facts" that were all-time INSIDE the period card, and none of the three said which was which.
 *
 * It is a SECOND CARD OF THE ACTIVITY SECTION, not a section of its own (user, 08-09): the chart
 * card is titled by its count ("12 aparcamientos") and this one by its period ("Este mes"), in the
 * same `cardTitle` voice. Siblings under one heading is what makes the filter's authority over both
 * legible without a word of explanation — the Historial below stays a separate section because the
 * filter genuinely does NOT govern it.
 *
 * The only figures that speak for the car's whole life are in the footer line, and they say so.
 */
@Composable
internal fun VehicleStatsBlock(
    stats: PeriodStats,
    /** The period this card covers, already resolved: the filter's own words for Plus, the free
     *  window's for free — see [vehicleStatsPeriodLabel]. */
    periodLabel: String,
    /** Lifetime totals — the two figures that outlived the identity card's stats row. */
    lifetimeParkings: Int,
    lifetimeSpotsReleased: Int,
    /** False when the selected period already IS the car's whole life. */
    showLifetime: Boolean,
    isPlus: Boolean,
    onOpenPaywall: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the full panel. Null where the block IS the panel — a card does not link to itself.
     *  [STATS-PANEL-001] */
    onOpenPanel: (() -> Unit)? = null,
) {
    val tiles = statTiles(stats)
    if (tiles.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        PapOutlinedCard(modifier = Modifier.fillMaxWidth(), shape = PapShapes.card) {
            Column {
                // Same role and same rhythm as the chart card's title — the two cards are siblings.
                // [ACTIVITY-CARD-TITLE-001]
                Text(
                    text = periodLabel,
                    style = PaparcarType.current.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(
                        start = HEAD_H_PAD_DP.dp, end = HEAD_H_PAD_DP.dp,
                        top = HEAD_TOP_PAD_DP.dp, bottom = HEAD_BOTTOM_PAD_DP.dp,
                    ),
                )
                PapDivider()
                // Free sees the FIRST figure whole and the rest redacted: enough to know the block
                // is real and about their own car, never enough to have the answer. [MONEY-001]
                StatTileGrid(tiles = tiles, veilFromIndex = if (isPlus) null else 1)
                // With the "all time" period selected the tiles ARE the car's whole life, so the
                // footer would repeat the card in smaller type.
                if (showLifetime) {
                    PapDivider()
                    Text(
                        text = stringResource(
                            Res.string.stats_lifetime,
                            lifetimeParkings.toString(),
                            lifetimeSpotsReleased.toString(),
                        ),
                        style = PaparcarType.current.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = LIFETIME_H_PAD_DP.dp,
                            vertical = LIFETIME_V_PAD_DP.dp,
                        ),
                    )
                }
                // Plus has nothing to be sold, so the card ends with where to go rather than what
                // to buy. Free gets the metal row below instead — never both.
                if (isPlus && onOpenPanel != null) {
                    PapDivider()
                    OpenPanelRow(onClick = onOpenPanel)
                }
            }
        }
        if (!isPlus) {
            PlusStatsRow(onClick = onOpenPaywall, modifier = Modifier.padding(top = PLUS_ROW_GAP_DP.dp))
        }
    }
}

/** Two-column grid of tiles, hairline-divided like the ficha's other data surfaces. */
@Composable
private fun StatTileGrid(tiles: List<StatTileData>, veilFromIndex: Int?) {
    val veiledLabel = stringResource(Res.string.stats_veiled_value)
    tiles.chunked(COLUMNS).forEachIndexed { rowIndex, rowTiles ->
        if (rowIndex > 0) PapDivider()
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            rowTiles.forEachIndexed { columnIndex, tile ->
                if (columnIndex > 0) PapVerticalDivider()
                val absoluteIndex = rowIndex * COLUMNS + columnIndex
                // ⛔ Keyed by the FIGURE, not by its position. The count-up remembers where a number
                // is coming from, and the grid's contents change with the period: a tile that goes
                // from "gasto" to "CO₂" because the first figure vanished would inherit the other
                // one's animation and climb from 9,40 to 14,2 as if the same number had moved.
                // Identity is the label; position is an accident of how many figures survived.
                key(tile.label) {
                    PapStatTile(
                        value = tile.value,
                        label = tile.label,
                        delta = tile.delta,
                        veiled = veilFromIndex != null && absoluteIndex >= veilFromIndex,
                        emphasized = absoluteIndex == 0,
                        veiledDescription = veiledLabel,
                        animatedNumber = tile.animated,
                        icon = tile.icon,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // A lone tile on the last row keeps its column width instead of stretching across the
            // card — but WITHOUT the divider: measured on device, a rule with nothing on its right
            // reads as a tile that failed to load, and a car whose only significant figure is
            // "spots shared" would open on exactly that. The spacer holds the column; the missing
            // line says the row simply ends here.
            if (rowTiles.size < COLUMNS) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Where a Plus user goes for the whole picture. A plain row: the card is already theirs. */
@Composable
private fun OpenPanelRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PANEL_ROW_H_PAD_DP.dp, vertical = PANEL_ROW_V_PAD_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.stats_open_panel),
            style = PaparcarType.current.cta,
            color = PapColor.actionText,
            modifier = Modifier.weight(1f),
        )
        PapNavChevron()
    }
}

/** The Plus touchpoint under the block — the metal at the weight that interrupts, not the one that
 *  sells. [UI-PLUS-EMPHASIS-MATCHES-THE-MOMENT-001] */
@Composable
private fun PlusStatsRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    PapPremiumSurface(
        modifier = modifier.fillMaxWidth(),
        shape = PapShapes.card,
        emphasis = PapPremiumEmphasis.Subdued,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(PLUS_ROW_PAD_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PLUS_ROW_GAP_DP.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(PLUS_MARK_DP.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.stats_plus_row_title),
                    style = PaparcarType.current.rowTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.stats_plus_row_subtitle),
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            PapNavChevron()
        }
    }
}

/**
 * What this card's period is CALLED — and for a free user that is not the filter's word.
 *
 * ⛔ Since STATS-CORE-001 the figures are computed over what the entitlement lets the user SEE, so
 * a free account cannot have a real "this month": there are only [FREE_HISTORY_WINDOW_DAYS] days of
 * history behind it, and the longer filters would all yield the week's figures. Titling this card
 * "Este mes" for them would be a label the numbers cannot back — the same failure as a partial sum
 * printed as a total. So free names its real window, and reaching for a longer period is exactly
 * what opens the paywall. [STATS-UI-001] [MONEY-001]
 */
@Composable
internal fun vehicleStatsPeriodLabel(filter: HistoryFilter, isPlus: Boolean): String =
    if (!isPlus) {
        stringResource(Res.string.stats_period_free_window, FREE_HISTORY_WINDOW_DAYS)
    } else {
        when (filter) {
            // The chip says "Todos" because it qualifies a plural of parkings; as this card's title
            // the same window needs its own words.
            HistoryFilter.All -> stringResource(Res.string.stats_period_all)
            HistoryFilter.ThisWeek -> stringResource(Res.string.history_filter_this_week)
            HistoryFilter.ThisMonth -> stringResource(Res.string.history_filter_this_month)
            HistoryFilter.Last3Months -> stringResource(Res.string.history_filter_last_3_months)
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// PeriodStats → tiles. Order IS the product decision: the first tile is what a
// free user gets to see, so it must be the most concrete figure available.
// ─────────────────────────────────────────────────────────────────────────────

internal data class StatTileData(
    val value: String,
    val label: String,
    val icon: ImageVector,
    val delta: PapStatDelta? = null,
    /** Set only on figures that COUNT — spend, CO2, spots shared. A usual street does not climb. */
    val animated: StatTileNumber? = null,
)

/**
 * The figures a period can show, in reading order. A key is present only when its figure EXISTS —
 * the aggregate already returned null below every significance threshold, and cost stays null
 * until a price does. [STATS-CORE-001]
 *
 * Pure and separate from the copy on purpose: presence and order are the product rules (the first
 * key is the one figure a free user sees whole), and a rule that lives inside a @Composable is a
 * rule no test can reach.
 */
internal enum class StatTileKey {
    COST, CO2, AVG_DURATION, PEAK_HOUR, USUAL_DAY, USUAL_STREET, AUTO_DETECTED, SPOTS_SHARED
}

/**
 * The glyph that says what a figure is about. Deliberately the SAME icons the app already spends on
 * these ideas — the calendar that headed "día habitual", the pin that headed "calle habitual", the
 * sparkle for automatic detection, the hands for a shared spot — so the grid inherits a vocabulary
 * instead of inventing one. Level 2: Material Rounded, tinted. [STATS-UI-001]
 */
private fun StatTileKey.icon(): ImageVector = when (this) {
    StatTileKey.COST -> Icons.Rounded.Payments
    StatTileKey.CO2 -> Icons.Rounded.Cloud
    StatTileKey.AVG_DURATION -> Icons.Rounded.HourglassBottom
    StatTileKey.PEAK_HOUR -> Icons.Rounded.Schedule
    StatTileKey.USUAL_DAY -> Icons.Rounded.Event
    StatTileKey.USUAL_STREET -> Icons.Rounded.Place
    StatTileKey.AUTO_DETECTED -> Icons.Rounded.AutoAwesome
    StatTileKey.SPOTS_SHARED -> Icons.Rounded.VolunteerActivism
}

internal fun statTileKeys(stats: PeriodStats): List<StatTileKey> = buildList {
    // Spend leads when it exists — it is the figure a person can act on, so it is what free sees.
    if (stats.costEstimate != null) add(StatTileKey.COST)
    if (stats.co2Kg != null) add(StatTileKey.CO2)
    if (stats.avgParkedDurationMs != null) add(StatTileKey.AVG_DURATION)
    if (stats.peakHourBucket != null) add(StatTileKey.PEAK_HOUR)
    if (stats.mostActiveDayOfWeek != null) add(StatTileKey.USUAL_DAY)
    if (stats.favoriteStreet != null) add(StatTileKey.USUAL_STREET)
    // Zero known-provenance sessions means "below the threshold" upstream, where a share is noise.
    if (stats.detectionKnownCount > 0) add(StatTileKey.AUTO_DETECTED)
    // Spots shared: 0 IS information (you never freed one), so it shows whenever the block does.
    add(StatTileKey.SPOTS_SHARED)
}

@Composable
internal fun statTiles(stats: PeriodStats): List<StatTileData> {
    val dayLabels = listOf(
        Res.string.history_day_full_mon, Res.string.history_day_full_tue,
        Res.string.history_day_full_wed, Res.string.history_day_full_thu,
        Res.string.history_day_full_fri, Res.string.history_day_full_sat,
        Res.string.history_day_full_sun,
    ).map { stringResource(it) }

    // A number that moves has to be re-written on every frame, and `stringResource` is a
    // composable — it cannot be called from inside the animation. So each unit template is
    // resolved ONCE here, with a sentinel where the figure goes, and the substitution that runs
    // per frame is pure. The locale still decides where the unit sits relative to the number,
    // which a hardcoded "$value kg" would have quietly taken away from it.
    val kgTemplate = stringResource(Res.string.stats_value_kg, NUMBER_SLOT)

    return statTileKeys(stats).map { key ->
        val icon = key.icon()
        when (key) {
            StatTileKey.COST -> {
                val cost = requireNotNull(stats.costEstimate)
                StatTileData(
                    icon = icon,
                    value = "${formatDecimal(cost.amount)} ${cost.currency}",
                    label = stringResource(Res.string.stats_tile_cost),
                    delta = percentDelta(cost.amount, stats.previous?.costEstimate?.amount),
                    animated = StatTileNumber(cost.amount) { v -> "${formatDecimal(v)} ${cost.currency}" },
                )
            }
            StatTileKey.CO2 -> StatTileData(
                icon = icon,
                value = kgTemplate.replace(NUMBER_SLOT, formatDecimal(requireNotNull(stats.co2Kg))),
                label = stringResource(Res.string.stats_tile_co2),
                delta = percentDelta(requireNotNull(stats.co2Kg), stats.previous?.co2Kg),
                animated = StatTileNumber(requireNotNull(stats.co2Kg)) { v ->
                    kgTemplate.replace(NUMBER_SLOT, formatDecimal(v))
                },
            )
            StatTileKey.AVG_DURATION -> StatTileData(
                icon = icon,
                value = durationText(requireNotNull(stats.avgParkedDurationMs)),
                label = stringResource(Res.string.stats_tile_avg_duration),
                delta = percentDelta(
                    requireNotNull(stats.avgParkedDurationMs).toDouble(),
                    stats.previous?.avgParkedDurationMs?.toDouble(),
                ),
            )
            StatTileKey.PEAK_HOUR -> StatTileData(
                icon = icon,
                value = formatHourBucket(requireNotNull(stats.peakHourBucket)),
                label = stringResource(Res.string.stats_tile_peak_hour),
            )
            StatTileKey.USUAL_DAY -> StatTileData(
                icon = icon,
                value = dayLabels[requireNotNull(stats.mostActiveDayOfWeek) - 1],
                label = stringResource(Res.string.stats_tile_usual_day),
            )
            StatTileKey.USUAL_STREET -> StatTileData(
                icon = icon,
                value = requireNotNull(stats.favoriteStreet),
                label = stringResource(Res.string.stats_tile_favorite_street),
            )
            StatTileKey.AUTO_DETECTED -> StatTileData(
                icon = icon,
                value = stringResource(
                    Res.string.stats_value_share,
                    stats.autoDetectedCount,
                    stats.detectionKnownCount,
                ),
                label = stringResource(Res.string.stats_tile_auto_detected),
            )
            StatTileKey.SPOTS_SHARED -> StatTileData(
                icon = icon,
                value = stats.spotsReleased.toString(),
                label = stringResource(Res.string.stats_tile_spots_shared),
                animated = StatTileNumber(stats.spotsReleased.toDouble()) { v ->
                    v.roundToInt().toString()
                },
                delta = percentDelta(
                    stats.spotsReleased.toDouble(),
                    stats.previous?.spotsReleased?.toDouble(),
                ),
            )
        }
    }
}

/**
 * Movement against the same figure last period, as a whole percentage.
 *
 * Null when there is nothing to compare against, when the previous figure was zero (any change
 * from zero is "infinitely more", which is not a statement), or when the move rounds to nothing —
 * a "▲ 0%" is noise wearing the clothes of a finding.
 */
private fun percentDelta(current: Double, previous: Double?): PapStatDelta? {
    if (previous == null || previous == 0.0) return null
    val changePercent = ((current - previous) / previous * PERCENT).roundToInt()
    if (changePercent == 0) return null
    return PapStatDelta(amountText = "${abs(changePercent)}%", isUp = changePercent > 0)
}

/** One decimal, and no trailing ".0" — the same shape the fuel line already uses. */
private fun formatDecimal(value: Double): String {
    val rounded = (value * DECIMAL_SCALE).roundToInt() / DECIMAL_SCALE
    return if (rounded == rounded.toInt().toDouble()) rounded.toInt().toString() else rounded.toString()
}

@Composable
private fun durationText(durationMs: Long): String {
    val totalMinutes = (durationMs / MS_PER_MINUTE).toInt()
    return if (totalMinutes < MINUTES_PER_HOUR) {
        stringResource(Res.string.stats_value_minutes, totalMinutes)
    } else {
        stringResource(
            Res.string.stats_value_hours_minutes,
            totalMinutes / MINUTES_PER_HOUR,
            totalMinutes % MINUTES_PER_HOUR,
        )
    }
}

/** Stands in for the figure inside a resolved unit template while it is being animated. A
 *  control character, so no translation can contain it by accident. */
private const val NUMBER_SLOT = " "
private const val COLUMNS = 2
private const val HEAD_H_PAD_DP = 14
private const val HEAD_TOP_PAD_DP = 13
private const val HEAD_BOTTOM_PAD_DP = 12
private const val PERCENT = 100
private const val DECIMAL_SCALE = 10.0
private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60
private const val LIFETIME_H_PAD_DP = 14
private const val LIFETIME_V_PAD_DP = 11
private const val PANEL_ROW_H_PAD_DP = 14
private const val PANEL_ROW_V_PAD_DP = 13
private const val PLUS_ROW_GAP_DP = 12
private const val PLUS_ROW_PAD_DP = 14
private const val PLUS_MARK_DP = 24
