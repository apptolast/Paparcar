package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
        subtitle = subtitleFor(station, title, hasFix),
        subtitleMaxLines = 1,
        leading = {
            // ⛔ VERDE, no neutro (user, 10-09, viéndolo en el Oppo). El barrido de
            // `UI-A-NEUTRAL-TILE-MUST-NOT-MUTE-AN-ACTION-001` dejó neutro todo tile que ETIQUETA su
            // fila, y por regla esto lo es. En mano la conclusión fue otra: una lista entera de
            // glifos grises no se lee como «esto es una etiqueta», se lee como una lista apagada.
            // El verde aquí no compite con ninguna acción — en estas filas no hay ninguna.
            // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
            PapIconTile(icon = PaparcarIcons.Fuel, tint = PapColor.actionText)
        },
        // ⛔ **El badge va al FINAL de la fila, alineado con el de las plazas** (user, review de #33).
        // Las dos listas viven bajo las mismas pestañas y se recorren con el mismo gesto, así que su
        // token tiene que aparecer en el mismo borde: en Plazas es la antigüedad («Hace 3 min»), aquí
        // la afirmación. Estaba en la línea del subtítulo y eso obligaba al ojo a buscarlo en un
        // sitio distinto según la pestaña.
        //
        // Apilado BAJO el precio, no al lado: el precio es la cifra que decide y manda en la columna;
        // el badge es lo que la cualifica, y una afirmación sobre un número se lee debajo de él.
        // [FUEL-THE-CHEAPEST-AND-THE-NEAREST-WEAR-A-BADGE-001]
        trailing = if (station.price != null || badge != null) {
            {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(TRAILING_STACK_GAP_DP.dp),
                ) {
                    station.price?.let { FuelPriceLabel(it) }
                    badge?.let { FuelStandoutBadgeChip(it) }
                }
            }
        } else {
            null
        },
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
 * La afirmación de esta gasolinera — **el mismo objeto que el badge de antigüedad de una plaza**
 * ([com.rndeveloper.paparcar.ui.components.SpotAgeIndicator]), con el color de su carril.
 *
 * ## Misma FORMA, distinto COLOR (user, review de #33)
 *
 * Los dos badges son el token del borde derecho de su fila, bajo las mismas pestañas y recorridos
 * con el mismo gesto, así que comparten caja, esquina y voz tipográfica: `PapBadge` con su
 * `contentPadding` y su `shape` por defecto y el rol `badge`. Lo único que no se comparte es el
 * color, que es lo que dice de qué carril habla cada uno.
 *
 * ⛔ La caja densa y la píldora de la vuelta anterior **no se pierden por capricho**: existían
 * porque el badge vivía DENTRO de la línea del subtítulo, donde un token tiene que encogerse para no
 * leerse como una segunda fila. Al mudarse al final de la fila esa razón desaparece — ahí tiene su
 * propia columna y nada con lo que competir por el ancho.
 *
 * ## El relleno sigue siendo macizo
 *
 * Review anterior: *«el badge apenas destaca y no se nota debido a sus tonos»*. Un container tonal
 * sobre la hoja se diferencia del fondo en unos pocos puntos de luminancia y de lejos, en la calle y
 * con sol, no hay badge. Verde de marca para lo que habla del PRECIO —la más barata es la respuesta
 * útil a la pregunta que abrió este carril— y neutro invertido para la distancia sola, que la fila
 * ya dice en metros dos palabras después.
 *
 * ⚠️ Esto es lo único donde los dos badges divergen de verdad: el de la plaza usa el **par tonal**
 * de la rampa de frescura y éste va macizo. Al final de la fila el badge ya no compite con un
 * subtítulo, así que puede que el macizo se haya vuelto innecesario — pero eso se decide **viéndolo**,
 * no aquí, y es una línea. [FUEL-THE-CHEAPEST-AND-THE-NEAREST-WEAR-A-BADGE-001]
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
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.inverseSurface
        },
        contentColor = if (isPriceClaim) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.inverseOnSurface
        },
        // ⛔ **Caja y esquina por DEFECTO, las mismas que `SpotAgeIndicator`**: los dos badges son el
        // token del borde derecho de su fila y tienen que ser el mismo objeto. La caja densa y la
        // píldora de la vuelta anterior existían porque el badge vivía DENTRO de la línea del
        // subtítulo, donde un token tiene que encogerse para no leerse como una segunda fila. Al
        // mudarse al final de la fila esa razón desaparece: ahí tiene su propia columna.
        // Lo único que NO se comparte es el color, que es la identidad de cada carril.
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

/** Aire entre el precio y el badge que lo cualifica, en la columna del borde derecho.
 *  [FUEL-THE-CHEAPEST-AND-THE-NEAREST-WEAR-A-BADGE-001] */
private const val TRAILING_STACK_GAP_DP = 4
