package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.fuel.FuelStandoutBadge
import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.fuel.fuelStandouts
import com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation
import com.rndeveloper.paparcar.presentation.home.model.isAttendedService
import com.rndeveloper.paparcar.presentation.home.model.pumpPriceLabel
import com.rndeveloper.paparcar.presentation.util.LocalPumpGallon
import com.rndeveloper.paparcar.presentation.util.formatDistance
import com.rndeveloper.paparcar.ui.components.PapBadge
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.ui.components.PapIconTile
import com.rndeveloper.paparcar.ui.components.PapListItem
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_fuel_attended_price
import paparcar.composeapp.generated.resources.home_fuel_badge_cheapest
import paparcar.composeapp.generated.resources.home_fuel_badge_cheapest_nearest
import paparcar.composeapp.generated.resources.home_fuel_badge_nearest
import paparcar.composeapp.generated.resources.location_fallback_station

/**
 * One forecourt: what it is called, how far, and what it charges for THIS car's fuel.
 * [FUEL-STATIONS-LIST-001] [UI-LIST-ITEM-001]
 *
 * The canonical row anatomy rather than a hand-rolled twin: glyph = leading, price = trailing.
 */
@Composable
fun HomeFuelStationRow(
    station: NearbyFuelStation,
    modifier: Modifier = Modifier,
    /** Non-null → the row opens the station's peek, the same one its map capsule opens.
     *  [FUEL-STATIONS-MAP-AND-PEEK-001] */
    onClick: (() -> Unit)? = null,
    /** Whether that distance is a distance TO THE USER. See [subtitleFor].
     *  [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001] */
    hasFix: Boolean = true,
    /** What this forecourt can claim over the others in the same answer, if anything.
     *  [FUEL-THE-CHEAPEST-AND-THE-NEAREST-WEAR-A-BADGE-001] */
    badge: FuelStandoutBadge? = null,
) {
    // A source can hold a forecourt with no name — France's official feed carries none at all, and
    // 1 in 25 OSM entries has neither name nor brand. Where it cannot name itself it is placed
    // instead of being given an invented name. [UI-LOCATION-FALLBACK-SPEAKS-HUMAN-001]
    val title = station.station.name
        ?: station.station.address
        ?: station.station.town
        ?: stringResource(Res.string.location_fallback_station)

    PapListItem(
        title = title,
        // Clickable OUTSIDE the caller's padding, so the ripple and the touch target are the
        // whole row, not the inset text block.
        modifier = (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(modifier),
        contentPadding = PaddingValues(0.dp),
        titleStyle = PaparcarType.current.rowName,
        titleMaxLines = 1,
        subtitle = subtitleFor(station, title, hasFix).takeIf { badge == null },
        subtitleMaxLines = 1,
        // The badge rides the subtitle line, not the trailing slot: the price lives there and a
        // forecourt's claim is about the forecourt, not about the number. One badge per row — a row
        // is a name, never a scoreboard. [FUEL-THE-CHEAPEST-AND-THE-NEAREST-WEAR-A-BADGE-001]
        subtitleSlot = badge?.let {
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FuelStandoutBadgeChip(it)
                    // A forecourt can have no subtitle at all (no fix, no address): then the badge
                    // is the whole line rather than a badge next to an empty string.
                    subtitleFor(station, title, hasFix)?.let { line ->
                        Text(
                            text = line,
                            style = PaparcarType.current.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        leading = {
            // ⛔ VERDE, no neutro (user, 10-09, viéndolo en el Oppo). El barrido de
            // `UI-A-NEUTRAL-TILE-MUST-NOT-MUTE-AN-ACTION-001` dejó neutro todo tile que ETIQUETA su
            // fila, y por regla esto lo es. En mano la conclusión fue otra: una lista entera de
            // glifos grises no se lee como «esto es una etiqueta», se lee como una lista apagada.
            // El verde aquí no compite con ninguna acción — en estas filas no hay ninguna.
            // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
            PapIconTile(icon = PaparcarIcons.Fuel, tint = PapColor.actionText)
        },
        trailing = station.price?.let { price -> { FuelPriceLabel(price) } },
    )
}

/**
 * Distance first — it is what decides between two forecourts before the price does.
 *
 * ⛔ The place is compared against the **title actually shown**, not against the raw name. A station
 * with no name is titled by its address, and comparing to the null name let that same address come
 * back as its own subtitle — "Carretera de Burgos km 14 / 3,1 km · Carretera de Burgos km 14",
 * caught on device. Whatever the title spent, the subtitle cannot spend again.
 *
 * ⛔ **Sin fix no hay distancia que rotular**, por la misma razón que en la fila de parking:
 * `distanceMeters` se mide desde el punto por el que se PREGUNTÓ — tú con ubicación, el centro del
 * mapa sin ella — y desde que el carril responde también sin permiso eso dejaría de ser una ventana
 * de dos segundos para ser el estado normal. El precio y el sitio no dependen de dónde estés.
 * [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
 */
@Composable
private fun subtitleFor(station: NearbyFuelStation, title: String, hasFix: Boolean): String? {
    val distance = if (hasFix) formatDistance(station.distanceMeters.toFloat()) else null
    val where = listOfNotNull(station.station.address, station.station.town)
        .firstOrNull { it != title }
    return when {
        distance != null && where != null -> "$distance · $where"
        else -> distance ?: where
    }
}

/**
 * The price, at the pump's own precision — and who pumps it, when that is a question.
 *
 * ⛔ **Three decimals, not two.** Fuel is quoted to a tenth of a cent, and that tenth is frequently
 * the entire difference between two forecourts — rounding 1,879 and 1,875 both to "1,88" would erase
 * exactly the comparison this list exists to make. Per LITRE almost everywhere and per gallon where
 * that is what the pump charges, which `pumpPriceLabel` settles once for every surface.
 * [I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001]
 *
 * ⛔ And that same comparison is what the second line protects. In Italy the same pump quotes `self`
 * and `servito` up to **0,35 €/L** apart, so an unqualified figure here could be either, and two
 * rows side by side could be quoting two different products.
 * [FUEL-ITALY-A-PRICE-DEPENDS-ON-WHO-PUMPS-IT-001]
 *
 * LECTURA, not CIFRA: it shares a line box with a name in MARCA, and a figure inside a ROW stays in
 * the reading voice. [UI-TYPE-TWO-VOICES-ONE-ROW-001]
 */
@Composable
private fun FuelPriceLabel(price: StationPrice) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            // The shared formatter: row, map capsule and peek must quote one price the same way.
            // [FUEL-STATIONS-MAP-AND-PEEK-001]
            text = price.perUnit.pumpPriceLabel(),
            style = PaparcarType.current.subtitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (price.isAttendedService()) {
            Text(
                text = stringResource(Res.string.home_fuel_attended_price),
                style = PaparcarType.current.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The claim itself. Brand container for anything involving the price — the cheapest forecourt is the
 * useful answer to the question that opened this lane — and a neutral one for distance alone, which
 * the row already states in metres two words later.
 * [FUEL-THE-CHEAPEST-AND-THE-NEAREST-WEAR-A-BADGE-001]
 */
@Composable
private fun FuelStandoutBadgeChip(badge: FuelStandoutBadge) {
    val label = stringResource(
        when (badge) {
            FuelStandoutBadge.CHEAPEST -> Res.string.home_fuel_badge_cheapest
            FuelStandoutBadge.NEAREST -> Res.string.home_fuel_badge_nearest
            FuelStandoutBadge.CHEAPEST_AND_NEAREST -> Res.string.home_fuel_badge_cheapest_nearest
        },
    )
    val isPriceClaim = badge != FuelStandoutBadge.NEAREST
    PapBadge(
        label = label,
        containerColor = if (isPriceClaim) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (isPriceClaim) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        textStyle = PaparcarType.current.badge,
    )
}

/** Divider inset to clear the glyph, so the rows read as one column of names. */
@Composable
fun HomeFuelStationRows(
    stations: List<NearbyFuelStation>,
    modifier: Modifier = Modifier,
    onStationClick: ((String) -> Unit)? = null,
    hasFix: Boolean = true,
) {
    // Read ONCE for the whole answer, never per row: "the cheapest" is a claim about the list, and
    // asking each row to work it out would let two rows disagree.
    // [FUEL-THE-CHEAPEST-AND-THE-NEAREST-WEAR-A-BADGE-001]
    val standouts = remember(stations) { stations.fuelStandouts() }
    Column(modifier = modifier) {
        stations.forEachIndexed { index, station ->
            HomeFuelStationRow(
                station = station,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                onClick = onStationClick?.let { cb -> { cb(station.station.id) } },
                hasFix = hasFix,
                // ⛔ Only when the distance is a real distance to the user: without a fix, "nearest"
                // is a claim about nothing. [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
                badge = standouts.badgeFor(station.stationKey)
                    ?.takeIf { hasFix || it == FuelStandoutBadge.CHEAPEST },
            )
            if (index < stations.lastIndex) {
                PapDivider(modifier = Modifier.padding(start = 70.dp, end = 16.dp))
            }
        }
    }
}
