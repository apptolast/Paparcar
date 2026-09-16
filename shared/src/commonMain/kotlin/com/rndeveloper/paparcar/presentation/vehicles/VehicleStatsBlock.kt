package com.rndeveloper.paparcar.presentation.vehicles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SensorsOff
import androidx.compose.material.icons.rounded.TravelExplore
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
import com.rndeveloper.paparcar.domain.detection.DetectionPause
import com.rndeveloper.paparcar.domain.entitlement.FREE_HISTORY_WINDOW_DAYS
import com.rndeveloper.paparcar.domain.fuel.FuelPrice
import com.rndeveloper.paparcar.domain.stats.PeriodStats
import com.rndeveloper.paparcar.domain.stats.ScopedMeasure
import com.rndeveloper.paparcar.domain.stats.coverageAllowsComparison
import com.rndeveloper.paparcar.localeGallonKind
import com.rndeveloper.paparcar.presentation.util.DistanceUnit
import com.rndeveloper.paparcar.presentation.util.LocalDistanceUnit
import com.rndeveloper.paparcar.presentation.util.burnedVolumeToDisplay
import com.rndeveloper.paparcar.presentation.util.durationText
import com.rndeveloper.paparcar.presentation.util.emittedMassToDisplay
import com.rndeveloper.paparcar.presentation.util.formatHourBucket
import com.rndeveloper.paparcar.presentation.util.fuelPriceNoteText
import com.rndeveloper.paparcar.presentation.util.statDelta
import com.rndeveloper.paparcar.ui.components.PapButtonSize
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.ui.components.PapIconTile
import com.rndeveloper.paparcar.ui.components.PapIconTileSize
import com.rndeveloper.paparcar.ui.components.PapListItem
import com.rndeveloper.paparcar.ui.components.PapListItemGap
import com.rndeveloper.paparcar.ui.components.PapNavChevron
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.components.PapPrimaryButton
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
import paparcar.composeapp.generated.resources.stats_detection_blocked_body
import paparcar.composeapp.generated.resources.stats_detection_blocked_cta
import paparcar.composeapp.generated.resources.stats_detection_blocked_title
import paparcar.composeapp.generated.resources.stats_detection_off_body
import paparcar.composeapp.generated.resources.stats_detection_off_cta
import paparcar.composeapp.generated.resources.stats_detection_off_title
import paparcar.composeapp.generated.resources.stats_empty_sub
import paparcar.composeapp.generated.resources.stats_empty_period
import paparcar.composeapp.generated.resources.stats_empty_first_trip
import paparcar.composeapp.generated.resources.stats_empty_no_measured_trip
import paparcar.composeapp.generated.resources.history_filter_last_7_days
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_day_full_fri
import paparcar.composeapp.generated.resources.history_day_full_mon
import paparcar.composeapp.generated.resources.history_day_full_sat
import paparcar.composeapp.generated.resources.history_day_full_sun
import paparcar.composeapp.generated.resources.history_day_full_thu
import paparcar.composeapp.generated.resources.history_day_full_tue
import paparcar.composeapp.generated.resources.history_day_full_wed
import paparcar.composeapp.generated.resources.history_filter_last_3_months
import paparcar.composeapp.generated.resources.history_filter_last_month
import paparcar.composeapp.generated.resources.history_filter_this_month
import paparcar.composeapp.generated.resources.history_filter_this_year
import paparcar.composeapp.generated.resources.history_filter_this_week
import paparcar.composeapp.generated.resources.stats_lifetime
import paparcar.composeapp.generated.resources.stats_open_panel
import paparcar.composeapp.generated.resources.stats_period_all
import paparcar.composeapp.generated.resources.stats_period_free_window
import paparcar.composeapp.generated.resources.stats_plus_row_subtitle
import paparcar.composeapp.generated.resources.stats_plus_row_title
import paparcar.composeapp.generated.resources.stats_tile_auto_detected
import paparcar.composeapp.generated.resources.stats_tile_avg_duration
import paparcar.composeapp.generated.resources.stats_tile_search_time
import paparcar.composeapp.generated.resources.stats_tile_co2
import paparcar.composeapp.generated.resources.stats_tile_energy
import paparcar.composeapp.generated.resources.stats_tile_fuel
import paparcar.composeapp.generated.resources.stats_tile_cost
import paparcar.composeapp.generated.resources.stats_tile_coverage
import paparcar.composeapp.generated.resources.stats_tile_usual_place
import paparcar.composeapp.generated.resources.stats_tile_peak_hour
import paparcar.composeapp.generated.resources.stats_tile_spots_shared
import paparcar.composeapp.generated.resources.stats_tile_usual_day
import paparcar.composeapp.generated.resources.stats_value_hours_minutes
import paparcar.composeapp.generated.resources.stats_value_kg
import paparcar.composeapp.generated.resources.stats_value_lb
import paparcar.composeapp.generated.resources.stats_value_gallons
import paparcar.composeapp.generated.resources.stats_value_kwh
import paparcar.composeapp.generated.resources.stats_value_liters
import paparcar.composeapp.generated.resources.stats_value_minutes
import paparcar.composeapp.generated.resources.stats_value_share
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
    /** Where the spend figure's price came from. Null = there is no spend figure to caveat.
     *  [FUEL-PRICE-RESOLVER-001] */
    costPrice: FuelPrice? = null,
    isPlus: Boolean,
    onOpenPaywall: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the full panel. Null where the block IS the panel — a card does not link to itself.
     *  [STATS-PANEL-001] */
    onOpenPanel: (() -> Unit)? = null,
    /** Why the next trip will not be measured, or null when it will. The notice above the figures.
     *  [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001] */
    detectionPause: DetectionPause? = null,
    /** The notice's CTA. [DET-TOGGLE-001] */
    onEnableDetection: () -> Unit = {},
) {
    // [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001] A period with nothing MEASURED in it has no
    // figures to show — only "0 spots shared", which reads as a verdict on a car that simply has not
    // been measured yet. The card says what is true instead: waiting for the first trip (or for one
    // in this period), and how to get there when detection is off.
    //
    // ⛔ The gate is the FIGURES, not the session count (user, 14-09). Counting parkings let the
    // tutorial's own parking — one manual session, no route, nothing ended — open the card on the
    // very zero this ticket exists to remove, because SPOTS_SHARED is added unconditionally. What
    // makes a card is a figure somebody measured; a lone "0 spots shared" is not one.
    if (!hasMeasuredFigures(stats)) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(PLUS_ROW_GAP_DP.dp),
        ) {
            DetectionPausedCard(pause = detectionPause, onEnable = onEnableDetection)
            EmptyStatsCard(
                periodLabel = periodLabel,
                neverParked = lifetimeParkings == 0,
                parkedInPeriod = stats.parkings > 0,
            )
        }
        return
    }
    val tiles = statTiles(stats)

    Column(modifier = modifier.fillMaxWidth()) {
        // ⛔ ABOVE the figures and OUTSIDE their card, never over them. Whoever is reading this has
        // figures worth reading — they were measured back when detection ran — so covering them
        // would take away the very thing they opened the screen for. What the notice adds is the
        // one fact the numbers cannot carry: that they have stopped growing.
        // [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001]
        DetectionPausedCard(pause = detectionPause, onEnable = onEnableDetection)
        if (detectionPause != null) Spacer(Modifier.height(PLUS_ROW_GAP_DP.dp))
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
                // Every figure, for everyone. What Plus changes is the PERIOD, not which figures you
                // may read: free sees all of them over its last FREE_HISTORY_WINDOW_DAYS days (the
                // figures are already computed over the gated history), Plus over any period.
                // [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001]
                StatTileGrid(tiles = tiles)
                // With the "all time" period selected the tiles ARE the car's whole life, so the
                // footer would repeat the card in smaller type.
                // ⛔ A spend figure has to say what price it used. An average and the user's own
                // price are not the same claim, and only one of them is theirs. Same family as the
                // partial-sum rule: a figure that cannot describe itself gets described by whoever
                // reads it. [FUEL-PRICE-RESOLVER-001]
                costPrice?.let { price ->
                    PapDivider()
                    Text(
                        // The wording lives in `fuelPriceNoteText`: the closed-month recap prints a
                        // spend too, and both have to qualify it the same way.
                        // [STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001]
                        text = fuelPriceNoteText(price),
                        style = PaparcarType.current.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = LIFETIME_H_PAD_DP.dp,
                            vertical = LIFETIME_V_PAD_DP.dp,
                        ),
                    )
                }
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
                // ⛔ **For everyone with figures, not for Plus.** [MONEY-001]
                // [STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001]
                //
                // This used to read `isPlus && …`, on the argument that the breakdown is where any
                // PERIOD can be explored and free cannot choose one. But the panel is not a period
                // chooser: it is where the chart, the places, the day×band grid and the conclusions
                // live, and every one of them is free over the last seven days — the panel was
                // already built for a free account, with its own honest title and its own Plus row
                // inside it. Hiding the way in was a HARD gate on a button, which the plan doctrine
                // forbids; and gating VER is the one thing the tiers never do.
                //
                // Both rows now, and that is not overload: they answer different things. This one
                // opens what is already yours; the Plus surface below sells the period.
                if (onOpenPanel != null) {
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
private fun StatTileGrid(tiles: List<StatTileData>) {
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
                        emphasized = absoluteIndex == 0,
                        animatedNumber = tile.animated,
                        icon = tile.icon,
                        note = tile.note,
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

/**
 * The figures card of a period with nothing measured in it.
 * [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001]
 *
 * Same card and title as the figures would have, so the section does not jump when the first figure
 * lands. It never shows a zero.
 *
 * Three different truths, three sentences — the card says the one that actually holds:
 * - nothing ever ended → waiting for the first trip;
 * - history exists but this period is empty → no parkings in this period;
 * - parked in this period, nothing measured yet → no measured trips yet. ⛔ This third one is why
 *   the sentence cannot be picked by `neverParked` alone: telling someone who parked yesterday that
 *   there are «no parkings in this period» is a lie the card would be making about its own data.
 */
/**
 * The notice that says the figures have stopped growing, and offers the one action that restarts
 * them. Renders nothing while detection is measuring. [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001]
 *
 * ⛔ **Two texts, because there are two populations with opposite remedies** — see [DetectionPause].
 * "Turn it on" said to somebody whose switch is already on, and whose background location is what
 * lapsed, is an instruction that cannot be followed.
 *
 * ⛔ **Not a danger card.** Nothing is broken or being destroyed: red is this app's destructive
 * tone and never a CTA's [UI-COLOR-DOCTRINE-001]. The same outlined card the figures use, with the
 * glyph Settings already spends on a detection switch that is off.
 *
 * ⛔ The CTA gets its OWN line rather than the row's trailing slot: it is a two-to-three word phrase
 * in all nine locales, and a trailing child measures BEFORE the weighted text column — it would
 * starve the copy instead of shrinking. Measured in [UI-LIST-ITEM-001]; the first-steps card
 * carries the same shape, and the indent is READ from the tile so both stay lined up.
 */
@Composable
private fun DetectionPausedCard(pause: DetectionPause?, onEnable: () -> Unit) {
    if (pause == null) return
    PapOutlinedCard(modifier = Modifier.fillMaxWidth(), shape = PapShapes.card) {
        Column {
            PapListItem(
                title = stringResource(
                    when (pause) {
                        DetectionPause.TURNED_OFF -> Res.string.stats_detection_off_title
                        DetectionPause.MISSING_PERMISSIONS -> Res.string.stats_detection_blocked_title
                    },
                ),
                subtitle = stringResource(
                    when (pause) {
                        DetectionPause.TURNED_OFF -> Res.string.stats_detection_off_body
                        DetectionPause.MISSING_PERMISSIONS -> Res.string.stats_detection_blocked_body
                    },
                ),
                leading = { PapIconTile(icon = Icons.Rounded.SensorsOff) },
                contentPadding = PaddingValues(
                    horizontal = PAUSE_H_PAD_DP.dp,
                    vertical = PAUSE_V_PAD_DP.dp,
                ),
            )
            PapPrimaryButton(
                label = stringResource(
                    when (pause) {
                        DetectionPause.TURNED_OFF -> Res.string.stats_detection_off_cta
                        DetectionPause.MISSING_PERMISSIONS -> Res.string.stats_detection_blocked_cta
                    },
                ),
                onClick = onEnable,
                size = PapButtonSize.Compact,
                modifier = Modifier.padding(
                    start = PAUSE_H_PAD_DP.dp + PapIconTileSize + PapListItemGap,
                    end = PAUSE_H_PAD_DP.dp,
                    bottom = PAUSE_V_PAD_DP.dp,
                ),
            )
        }
    }
}

@Composable
private fun EmptyStatsCard(
    periodLabel: String,
    neverParked: Boolean,
    /** At least one session falls in this period, measured or not. */
    parkedInPeriod: Boolean,
    modifier: Modifier = Modifier,
) {
    PapOutlinedCard(modifier = modifier.fillMaxWidth(), shape = PapShapes.card) {
        Column {
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
            Column(
                modifier = Modifier.padding(horizontal = LIFETIME_H_PAD_DP.dp, vertical = EMPTY_V_PAD_DP.dp),
                verticalArrangement = Arrangement.spacedBy(EMPTY_LINE_GAP_DP.dp),
            ) {
                Text(
                    text = stringResource(
                        when {
                            neverParked -> Res.string.stats_empty_first_trip
                            parkedInPeriod -> Res.string.stats_empty_no_measured_trip
                            else -> Res.string.stats_empty_period
                        },
                    ),
                    style = PaparcarType.current.rowTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // ⛔ One line, not two. It used to swap itself for "activa la detección automática"
                // when the switch was off — a remedy written as prose, on the one card that could
                // never show it to somebody who already HAS figures. The remedy now lives above,
                // in a notice with a button, for everyone; this line went back to saying the one
                // thing it is for. [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001]
                Text(
                    text = stringResource(Res.string.stats_empty_sub),
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
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
 *  sells. It sells the one thing free lacks: choosing the PERIOD. [UI-PLUS-EMPHASIS-MATCHES-THE-MOMENT-001]
 *  [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001] */
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
                    text = stringResource(Res.string.stats_plus_row_subtitle, FREE_HISTORY_WINDOW_DAYS),
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
 * printed as a total. So free names its real window, and the Plus row under the card is where a
 * longer period is offered. [STATS-UI-001] [MONEY-001] [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001]
 */
@Composable
internal fun vehicleStatsPeriodLabel(filter: StatsPeriod, isPlus: Boolean): String =
    if (!isPlus) {
        stringResource(Res.string.stats_period_free_window, FREE_HISTORY_WINDOW_DAYS)
    } else {
        when (filter) {
            // The chip says "Todos" because it qualifies a plural of parkings; as this card's title
            // the same window needs its own words.
            StatsPeriod.All -> stringResource(Res.string.stats_period_all)
            StatsPeriod.ThisWeek -> stringResource(Res.string.history_filter_this_week)
            StatsPeriod.Last7Days -> stringResource(Res.string.history_filter_last_7_days)
            StatsPeriod.ThisMonth -> stringResource(Res.string.history_filter_this_month)
            StatsPeriod.Last3Months -> stringResource(Res.string.history_filter_last_3_months)
            StatsPeriod.LastMonth -> stringResource(Res.string.history_filter_last_month)
            StatsPeriod.ThisYear -> stringResource(Res.string.history_filter_this_year)
            // A year names itself. [STATS-A-FILTER-THAT-CANNOT-ANSWER-IS-NOT-OFFERED-001]
            is StatsPeriod.Year -> filter.value.toString()
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// PeriodStats → tiles. Order IS the product decision: the first tile is the
// emphasized lead figure, so it must be the most concrete figure available.
// ─────────────────────────────────────────────────────────────────────────────

internal data class StatTileData(
    val value: String,
    val label: String,
    val icon: ImageVector,
    val delta: PapStatDelta? = null,
    /** Set only on figures that COUNT — spend, CO2, spots shared. A usual street does not climb. */
    val animated: StatTileNumber? = null,
    /** What the figure covers, on the MEASURED figures that do not cover the whole period.
     *  [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001] */
    val note: String? = null,
)

/**
 * The figures a period can show, in reading order. A key is present only when its figure EXISTS —
 * the aggregate already returned null below every significance threshold, and cost stays null
 * until a price does. [STATS-CORE-001]
 *
 * Pure and separate from the copy on purpose: presence and order are the product rules (the first
 * key is the emphasized lead figure), and a rule that lives inside a @Composable is a rule no test
 * can reach.
 */
internal enum class StatTileKey {
    COST, FUEL, ENERGY, CO2, SEARCH_TIME, AVG_DURATION, PEAK_HOUR, USUAL_DAY, USUAL_PLACE,
    AUTO_DETECTED, SPOTS_SHARED
}

/**
 * The glyph that says what a figure is about. Deliberately the SAME icons the app already spends on
 * these ideas — the calendar that headed "día habitual", the pin that headed "calle habitual", the
 * sparkle for automatic detection, the hands for a shared spot — so the grid inherits a vocabulary
 * instead of inventing one. Level 2: Material Rounded, tinted. [STATS-UI-001]
 */
private fun StatTileKey.icon(): ImageVector = when (this) {
    StatTileKey.COST -> Icons.Rounded.Payments
    StatTileKey.FUEL -> Icons.Rounded.LocalGasStation
    StatTileKey.ENERGY -> Icons.Rounded.ElectricBolt
    StatTileKey.CO2 -> Icons.Rounded.Cloud
    // The roaming magnifier: time spent hunting the spot. [TRIP-THE-SEARCH-FOR-A-SPOT-IS-MEASURED-001]
    StatTileKey.SEARCH_TIME -> Icons.Rounded.TravelExplore
    StatTileKey.AVG_DURATION -> Icons.Rounded.HourglassBottom
    StatTileKey.PEAK_HOUR -> Icons.Rounded.Schedule
    StatTileKey.USUAL_DAY -> Icons.Rounded.Event
    StatTileKey.USUAL_PLACE -> Icons.Rounded.Place
    StatTileKey.AUTO_DETECTED -> Icons.Rounded.AutoAwesome
    StatTileKey.SPOTS_SHARED -> Icons.Rounded.VolunteerActivism
}

internal fun statTileKeys(stats: PeriodStats): List<StatTileKey> = buildList {
    // Spend leads when it exists — it is the figure a person can act on, so it is the lead tile.
    if (stats.costEstimate != null) add(StatTileKey.COST)
    // El combustible va DETRAS del coste y DELANTE de las emisiones: es la magnitud de la que sale
    // el coste, así que se lee antes que el CO₂. Dos claves y no una porque un híbrido enchufable
    // consume las DOS energías y el agregado ya las lleva separadas — fundirlas obligaría a
    // inventar una unidad común. [STATS-A-COMPUTED-LITRE-WITH-NO-READER-001]
    if (stats.fuelLiters != null) add(StatTileKey.FUEL)
    if (stats.energyKwh != null) add(StatTileKey.ENERGY)
    if (stats.co2Kg != null) add(StatTileKey.CO2)
    // Time LOST hunting is a cost of the period, like fuel — it reads with the money block.
    // [TRIP-THE-SEARCH-FOR-A-SPOT-IS-MEASURED-001]
    if (stats.searchTime != null) add(StatTileKey.SEARCH_TIME)
    if (stats.avgParkedDurationMs != null) add(StatTileKey.AVG_DURATION)
    if (stats.peakHourBucket != null) add(StatTileKey.PEAK_HOUR)
    if (stats.mostActiveDayOfWeek != null) add(StatTileKey.USUAL_DAY)
    // The place you repeat at — named by your own zone when you have one there.
    // [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001]
    if (stats.usualPlaceName != null) add(StatTileKey.USUAL_PLACE)
    // Zero known-provenance sessions means "below the threshold" upstream, where a share is noise.
    if (stats.detectionKnownCount > 0) add(StatTileKey.AUTO_DETECTED)
    // Spots shared: 0 IS information (you never freed one), so it shows whenever the block does.
    add(StatTileKey.SPOTS_SHARED)
}

/**
 * Whether the period holds a figure somebody MEASURED — the one condition that earns the figures
 * card. [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001]
 *
 * `SPOTS_SHARED` does not count: it is added unconditionally precisely because its zero is an answer
 * when it stands NEXT to other figures. Alone in the grid it stops being an answer and becomes the
 * verdict «0 plazas cedidas» on a car that has not driven anywhere yet — which is what the tutorial's
 * manual parking produced (user, 14-09): one session, no route, nothing ended.
 */
internal fun hasMeasuredFigures(stats: PeriodStats): Boolean =
    statTileKeys(stats).any { it != StatTileKey.SPOTS_SHARED }

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
    // ⛔ Y la unidad se elige aquí, en el mismo sitio y por el mismo motivo. El eje es el del
    // CONSUMO —el ajuste imperial, no el locale del surtidor—: los litros quemados son el consumo
    // integrado sobre la distancia, así que las tres cifras de la pantalla tienen que CERRAR
    // (distancia ÷ consumo = volumen). La masa no tiene galón: sólo depende del ajuste.
    // [STATS-A-VOLUME-BURNED-IS-QUOTED-IN-LITRES-EVERYWHERE-001]
    val imperial = LocalDistanceUnit.current == DistanceUnit.IMPERIAL
    val gallon = localeGallonKind()
    val kgTemplate = stringResource(
        if (imperial) Res.string.stats_value_lb else Res.string.stats_value_kg, NUMBER_SLOT,
    )
    val litersTemplate = stringResource(
        if (imperial) Res.string.stats_value_gallons else Res.string.stats_value_liters, NUMBER_SLOT,
    )
    val kwhTemplate = stringResource(Res.string.stats_value_kwh, NUMBER_SLOT)

    // Spend, litres, kWh and CO₂ are all computed over the SAME measured distance, so they all
    // wear the SAME coverage — one caveat resolved once, not four that could drift apart.
    // [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001]
    val distanceNote = coverageNote(stats.distance)
    val distanceComparable = coverageAllowsComparison(stats.distance, stats.previous?.distance)
    val searchNote = coverageNote(stats.searchTime)
    val searchComparable = coverageAllowsComparison(stats.searchTime, stats.previous?.searchTime)

    return statTileKeys(stats).map { key ->
        val icon = key.icon()
        when (key) {
            StatTileKey.COST -> {
                val cost = requireNotNull(stats.costEstimate)
                StatTileData(
                    icon = icon,
                    value = "${formatMoney(cost.amount)} ${cost.currency}",
                    label = stringResource(Res.string.stats_tile_cost),
                    delta = statDelta(cost.amount, stats.previous?.costEstimate?.amount)
                        .takeIf { distanceComparable },
                    animated = StatTileNumber(cost.amount) { v -> "${formatMoney(v)} ${cost.currency}" },
                    note = distanceNote,
                )
            }
            // ⛔ LITROS, no «consumo medio en l/100 km». Los litros se derivan del consumo
            // declarado (`km * consumptionPer100 / 100`), así que litros/km × 100 devolvería
            // EXACTAMENTE la cifra que el usuario tecleó — y que ya está escrita en la ficha, dos
            // dedos más arriba. Sería una constante disfrazada de medición. Los litros del periodo
            // sí se mueven con los kilómetros. [STATS-A-COMPUTED-LITRE-WITH-NO-READER-001]
            StatTileKey.FUEL -> {
                // ⛔ El dominio sigue en LITROS: `PeriodStats.fuelLiters` no cambia de unidad, y la
                // delta tampoco se convierte — es una RAZÓN, y una razón no tiene unidad.
                val burned = burnedVolumeToDisplay(
                    requireNotNull(stats.fuelLiters), LocalDistanceUnit.current, gallon,
                )
                StatTileData(
                    icon = icon,
                    value = litersTemplate.replace(NUMBER_SLOT, formatDecimal(burned)),
                    label = stringResource(Res.string.stats_tile_fuel),
                    delta = statDelta(requireNotNull(stats.fuelLiters), stats.previous?.fuelLiters)
                        .takeIf { distanceComparable },
                    animated = StatTileNumber(burned) { v ->
                        litersTemplate.replace(NUMBER_SLOT, formatDecimal(v))
                    },
                    note = distanceNote,
                )
            }
            StatTileKey.ENERGY -> StatTileData(
                icon = icon,
                value = kwhTemplate.replace(NUMBER_SLOT, formatDecimal(requireNotNull(stats.energyKwh))),
                label = stringResource(Res.string.stats_tile_energy),
                delta = statDelta(requireNotNull(stats.energyKwh), stats.previous?.energyKwh)
                    .takeIf { distanceComparable },
                animated = StatTileNumber(requireNotNull(stats.energyKwh)) { v ->
                    kwhTemplate.replace(NUMBER_SLOT, formatDecimal(v))
                },
                note = distanceNote,
            )
            StatTileKey.CO2 -> {
                val emitted = emittedMassToDisplay(requireNotNull(stats.co2Kg), LocalDistanceUnit.current)
                StatTileData(
                    icon = icon,
                    value = kgTemplate.replace(NUMBER_SLOT, formatDecimal(emitted)),
                    label = stringResource(Res.string.stats_tile_co2),
                    delta = statDelta(requireNotNull(stats.co2Kg), stats.previous?.co2Kg)
                        .takeIf { distanceComparable },
                    animated = StatTileNumber(emitted) { v ->
                        kgTemplate.replace(NUMBER_SLOT, formatDecimal(v))
                    },
                    note = distanceNote,
                )
            }
            StatTileKey.SEARCH_TIME -> StatTileData(
                icon = icon,
                value = durationText(requireNotNull(stats.searchTime).value),
                label = stringResource(Res.string.stats_tile_search_time),
                delta = statDelta(
                    requireNotNull(stats.searchTime).value.toDouble(),
                    stats.previous?.searchTime?.value?.toDouble(),
                ).takeIf { searchComparable },
                note = searchNote,
            )
            StatTileKey.AVG_DURATION -> StatTileData(
                icon = icon,
                value = durationText(requireNotNull(stats.avgParkedDurationMs)),
                label = stringResource(Res.string.stats_tile_avg_duration),
                delta = statDelta(
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
            StatTileKey.USUAL_PLACE -> StatTileData(
                icon = icon,
                value = requireNotNull(stats.usualPlaceName),
                label = stringResource(Res.string.stats_tile_usual_place),
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
                delta = statDelta(
                    stats.spotsReleased.toDouble(),
                    stats.previous?.spotsReleased?.toDouble(),
                ),
            )
        }
    }
}

/**
 * Money always keeps its two decimals. "5.6 EUR" reads as a broken number to anyone who has seen a
 * price before — cents are not trailing noise to be trimmed, they are part of what a price IS.
 * [FUEL-PRICE-RESOLVER-001]
 */
private fun formatMoney(value: Double): String {
    val cents = (value * CENTS).roundToInt()
    return "${cents / CENTS.toInt()}.${(cents % CENTS.toInt()).toString().padStart(2, '0')}"
}

/** One decimal, and no trailing ".0" — the same shape the fuel line already uses. */
private fun formatDecimal(value: Double): String {
    val rounded = (value * DECIMAL_SCALE).roundToInt() / DECIMAL_SCALE
    return if (rounded == rounded.toInt().toDouble()) rounded.toInt().toString() else rounded.toString()
}

/**
 * What a measured figure covers, or null when it covers everything — the caveat exists to stop a
 * subtotal passing for a total, so a complete figure gets no line at all rather than a reassuring
 * one nobody needs to read. [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001]
 *
 * "Trips", the same word the chart's kilometre line already uses for the same idea: what is
 * measured is the DRIVE that ended in a parking, and a period holds more parkings than measured
 * drives.
 */
@Composable
private fun coverageNote(measure: ScopedMeasure<*>?): String? {
    if (measure == null || measure.isComplete) return null
    return stringResource(
        Res.string.stats_tile_coverage,
        measure.fromParkings,
        measure.ofParkings,
    )
}

/**
 * Stands in for the figure inside a resolved unit template while it is being animated. A
 * control character, so no translation can contain it by accident.
 *
 * ⛔ Written as the ESCAPE `\u0000`, never as a raw NUL byte in the source.
 * [STATS-A-COMPUTED-LITRE-WITH-NO-READER-001]
 *
 * The sentinel is a good idea; embedding the byte itself was not. A NUL makes every tool that
 * sniffs for one treat the file as binary — `grep` refuses to print matches without `-a`, which
 * is how this was found: reading this file needed a flag no other file in the repo needs. Git's
 * own check only sniffs the first 8 KB, so it kept diffing this file and the damage stayed
 * invisible. Same value at runtime, and the file is ordinary text again.
 */
// Vertical air inside the empty figures card, and between its two lines.
private const val EMPTY_V_PAD_DP = 16
private const val EMPTY_LINE_GAP_DP = 4
private const val NUMBER_SLOT = "\u0000"
private const val COLUMNS = 2
private const val HEAD_H_PAD_DP = 14
private const val HEAD_TOP_PAD_DP = 13
private const val HEAD_BOTTOM_PAD_DP = 12
private const val PERCENT = 100
private const val DECIMAL_SCALE = 10.0
private const val CENTS = 100.0
private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60
private const val LIFETIME_H_PAD_DP = 14
private const val LIFETIME_V_PAD_DP = 11
private const val PANEL_ROW_H_PAD_DP = 14
private const val PANEL_ROW_V_PAD_DP = 13
private const val PLUS_ROW_GAP_DP = 12
// The paused-detection notice keeps the figures card's own rhythm — it is a sibling card, not a
// banner bolted on top.
private const val PAUSE_H_PAD_DP = 14
private const val PAUSE_V_PAD_DP = 12
private const val PLUS_ROW_PAD_DP = 14
private const val PLUS_MARK_DP = 24
