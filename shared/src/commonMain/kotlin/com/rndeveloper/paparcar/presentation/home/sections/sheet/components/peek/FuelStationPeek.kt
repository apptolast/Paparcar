package com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.fuel.FuelAvailability
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation
import com.rndeveloper.paparcar.presentation.home.HomeIntent
import com.rndeveloper.paparcar.presentation.home.sections.sheet.HomeSheetAction
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheet
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import com.rndeveloper.paparcar.presentation.home.model.pumpPriceLabel
import com.rndeveloper.paparcar.presentation.util.distanceMeters
import com.rndeveloper.paparcar.presentation.util.fuelTypeLabel
import com.rndeveloper.paparcar.ui.components.PapFooterButton
import com.rndeveloper.paparcar.ui.components.PapFooterButtonStyle
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.common_directions
import paparcar.composeapp.generated.resources.fuel_peek_eyebrow
import paparcar.composeapp.generated.resources.fuel_peek_not_sold
import paparcar.composeapp.generated.resources.home_fuel_reported_at
import paparcar.composeapp.generated.resources.location_fallback_station

// ═════════════════════════════════════════════════════════════════════════════
// FuelStationPeek — selected fuel station, from the map layer or the list.
// [FUEL-STATIONS-MAP-AND-PEEK-001]
// ═════════════════════════════════════════════════════════════════════════════

private const val NOT_SOLD_ALPHA = 0.6f

/**
 * The whole station, not one price: the list row quotes only the active car's fuel, so this is
 * where every pump the source SPOKE about gets its line — priced when [FuelAvailability.SOLD],
 * "not sold here" when the feed declared the negative, and silence when the source said nothing
 * (an OSM station never claims what it does not know). Geometry belongs to [PapSheet] untouched:
 * no inner scroll, one-line title. [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
 */
@Composable
internal fun FuelStationPeek(
    nearby: NearbyFuelStation,
    userLocation: Pair<Double, Double>?,
    /** The feed's own hour for the prices below, verbatim; null when the source has none (OSM). */
    reportedAt: String?,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
) {
    val station = nearby.station
    val distM = userLocation?.let { (uLat, uLon) ->
        distanceMeters(uLat, uLon, station.latitude, station.longitude)
    }
    // Always DRIVING — the thing that needs the fuel is the car, so you go by car however close
    // the pump is. The spots' walk-when-near heuristic answers a different question ("can I grab
    // it on foot before someone else does") that has no twin here.
    val travelMode = TravelMode.DRIVING
    val title = peekTitle(
        placeName = station.name,
        addressLine = station.address,
        fallback = stringResource(Res.string.location_fallback_station),
    )
    // The fuels this source actually SPOKE about, in the model's stable order.
    val spokenFuels = FuelType.entries.filter { station.availabilityOf(it) != FuelAvailability.UNKNOWN }

    PapSheet(
        lead = PapSheetLead.GenericIcon(Icons.Rounded.LocalGasStation),
        eyebrow = stringResource(Res.string.fuel_peek_eyebrow),
        title = title,
        // Address under the name — unless it already IS the title (a nameless OSM station).
        subtitle = station.address?.takeIf { it != title }
            ?: station.town?.takeIf { it != title },
        onDismiss = { onIntent(HomeIntent.SelectItem(null)) },
        meta = {
            DistanceRow(
                distanceM = distM,
                mode = travelMode,
                accentColor = MaterialTheme.colorScheme.primary,
            )
            station.schedule?.let { schedule ->
                // As the source words it — "24H", "L-D: 07:00-22:00"… never parsed, never judged.
                PeekMetaRow(
                    icon = Icons.Rounded.Schedule,
                    text = schedule,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        content = {
            spokenFuels.forEach { fuel ->
                FuelPriceRow(
                    label = fuelTypeLabel(fuel),
                    priceLabel = station.prices[fuel]?.pumpPriceLabel(),
                )
            }
            // A price with no hour beside it is a price nobody can weigh. Only when something
            // above is actually priced — OSM's hourless station has nothing to stamp.
            if (reportedAt != null && spokenFuels.any { station.prices[it] != null }) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.home_fuel_reported_at, reportedAt),
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
        },
        actions = {
            PapFooterButton(
                label = stringResource(Res.string.common_directions),
                leadingIcon = Icons.Rounded.Navigation,
                onClick = {
                    onAction(
                        HomeSheetAction.NavigateExternal(
                            lat = station.latitude,
                            lon = station.longitude,
                            walking = false,
                        ),
                    )
                },
                style = PapFooterButtonStyle.Filled,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

/** One pump: its name in the reading voice, its price — or the declared negative — trailing. */
@Composable
private fun FuelPriceRow(label: String, priceLabel: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = PaparcarType.current.body,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (priceLabel != null) {
            Text(
                text = priceLabel,
                style = PaparcarType.current.subtitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                text = stringResource(Res.string.fuel_peek_not_sold),
                style = PaparcarType.current.caption,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = NOT_SOLD_ALPHA),
            )
        }
    }
}
