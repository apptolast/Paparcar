package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation
import com.rndeveloper.paparcar.presentation.util.formatDistance
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.ui.components.PapIconTile
import com.rndeveloper.paparcar.ui.components.PapListItem
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
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
        modifier = modifier,
        contentPadding = PaddingValues(0.dp),
        titleStyle = PaparcarType.current.rowName,
        titleMaxLines = 1,
        subtitle = subtitleFor(station, title),
        subtitleMaxLines = 1,
        leading = { PapIconTile(icon = PaparcarIcons.Fuel) },
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
 */
@Composable
private fun subtitleFor(station: NearbyFuelStation, title: String): String {
    val distance = formatDistance(station.distanceMeters.toFloat())
    val where = listOfNotNull(station.station.address, station.station.town)
        .firstOrNull { it != title }
    return if (where != null) "$distance · $where" else distance
}

/**
 * The price, at the pump's own precision.
 *
 * ⛔ **Three decimals, not two.** Fuel is quoted per litre to a tenth of a cent, and that tenth is
 * frequently the entire difference between two forecourts — rounding 1,879 and 1,875 both to "1,88"
 * would erase exactly the comparison this list exists to make.
 *
 * LECTURA, not CIFRA: it shares a line box with a name in MARCA, and a figure inside a ROW stays in
 * the reading voice. [UI-TYPE-TWO-VOICES-ONE-ROW-001]
 */
@Composable
private fun FuelPriceLabel(price: Money) {
    Text(
        text = "${formatPumpPrice(price.amount)} ${price.currency}",
        style = PaparcarType.current.subtitle,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** `1.879` — always three decimals, so a column of prices lines up digit for digit. */
private fun formatPumpPrice(amount: Double): String {
    val thousandths = (amount * PRICE_SCALE).roundToInt()
    val whole = thousandths / PRICE_SCALE.toInt()
    val fraction = (thousandths % PRICE_SCALE.toInt()).toString().padStart(PRICE_DECIMALS, '0')
    return "$whole.$fraction"
}

private const val PRICE_SCALE = 1_000.0
private const val PRICE_DECIMALS = 3

/** Divider inset to clear the glyph, so the rows read as one column of names. */
@Composable
fun HomeFuelStationRows(
    stations: List<NearbyFuelStation>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        stations.forEachIndexed { index, station ->
            HomeFuelStationRow(
                station = station,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (index < stations.lastIndex) {
                PapDivider(modifier = Modifier.padding(start = 70.dp, end = 16.dp))
            }
        }
    }
}
