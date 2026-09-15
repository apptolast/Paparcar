package com.rndeveloper.paparcar.presentation.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.stats.MIN_PARKINGS_FOR_PLACE
import com.rndeveloper.paparcar.domain.stats.ParkingCluster
import com.rndeveloper.paparcar.presentation.util.durationText
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.ui.components.PapIconTile
import com.rndeveloper.paparcar.ui.components.PapListItem
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.stats_places_empty
import paparcar.composeapp.generated.resources.stats_places_row_searching
import paparcar.composeapp.generated.resources.stats_places_row_stay
import paparcar.composeapp.generated.resources.stats_places_row_times
import paparcar.composeapp.generated.resources.stats_places_title

/**
 * "Your places" — where this car repeats, and what parking there costs.
 * [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001]
 *
 * The figures grid can only hold ONE place, in a tile. This is the same question answered at
 * length, which is what the panel exists for [STATS-PANEL-001] — and it is the first surface where
 * a figure is attached to a place rather than to a period: the minutes hunting a spot at work are
 * not the minutes hunting one at home, and until now nothing could say so.
 *
 * A place with no name is not listed. It is a real place and it counted towards the period's
 * figures, but a row that cannot say where it is helps nobody.
 */
@Composable
internal fun StatsPlacesCard(places: List<ParkingCluster>) {
    val named = places.filter { !it.name.isNullOrBlank() }.take(MAX_PLACES)
    PapOutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = CARD_V_PAD_DP.dp)) {
            Text(
                text = stringResource(Res.string.stats_places_title),
                style = PaparcarType.current.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = CARD_H_PAD_DP.dp),
            )
            Spacer(Modifier.height(TITLE_GAP_DP.dp))
            if (named.isEmpty()) {
                // Not "no places": not YET. The empty state says what is missing and how much,
                // which is the only honest thing to say about a threshold.
                Text(
                    text = pluralStringResource(
                        Res.plurals.stats_places_empty,
                        MIN_PARKINGS_FOR_PLACE,
                        MIN_PARKINGS_FOR_PLACE,
                    ),
                    style = PaparcarType.current.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = CARD_H_PAD_DP.dp),
                )
            } else {
                named.forEachIndexed { index, place ->
                    if (index > 0) PapDivider()
                    PlaceRow(place)
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(place: ParkingCluster) {
    PapListItem(
        title = place.name.orEmpty(),
        titleStyle = PaparcarType.current.rowName,
        subtitle = placeSubtitle(place),
        leading = {
            // A private zone wears the lock the rest of the app already gives it — a garage is a
            // place you park, but never one that frees a spot. [UI-COLOR-DOCTRINE-001]
            PapIconTile(icon = if (place.isPrivateZone) Icons.Rounded.Lock else Icons.Rounded.Place)
        },
    )
}

/**
 * "12 times · 2 h 10 min on average · 8 min hunting" — only the parts that exist.
 *
 * The hunting figure is the one that makes the list worth reading, and it is also the one most
 * likely to be missing (it needs a measured drive), so it is written LAST: a sentence that ends
 * with the interesting part when it has it, and reads fine when it does not.
 */
@Composable
private fun placeSubtitle(place: ParkingCluster): String = listOfNotNull(
    pluralStringResource(Res.plurals.stats_places_row_times, place.parkings, place.parkings),
    place.avgStayMs?.let { stringResource(Res.string.stats_places_row_stay, durationText(it)) },
    place.searchTime?.let { stringResource(Res.string.stats_places_row_searching, durationText(it.value)) },
).joinToString(SEPARATOR)

/** Five is what fits before a list stops being a summary and becomes a directory. */
private const val MAX_PLACES = 5
private const val CARD_H_PAD_DP = 16
private const val CARD_V_PAD_DP = 14
private const val TITLE_GAP_DP = 10
private const val SEPARATOR = " · "
