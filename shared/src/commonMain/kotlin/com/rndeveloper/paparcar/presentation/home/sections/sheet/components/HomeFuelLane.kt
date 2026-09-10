package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.onboarding.NearbyComplement
import com.rndeveloper.paparcar.presentation.home.model.HomeFuelSlice
import com.rndeveloper.paparcar.presentation.util.relativeTimeText
import com.rndeveloper.paparcar.ui.illustrations.EmptyFuelStationsIllustration
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_fuel_empty
import paparcar.composeapp.generated.resources.home_fuel_error
import paparcar.composeapp.generated.resources.home_fuel_missing_fuel_type
import paparcar.composeapp.generated.resources.home_fuel_no_prices
import paparcar.composeapp.generated.resources.home_fuel_oldest_price
import paparcar.composeapp.generated.resources.home_fuel_remembered
import paparcar.composeapp.generated.resources.home_fuel_reported_at

/**
 * "Where to refuel" — the lane, i.e. what the sheet shows while the lens sits on
 * `HomeNearbyLens.FUEL`. [FUEL-STATIONS-LIST-001] [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
 *
 * It used to be a foldable SECTION with its own header, chevron and spinner, stacked under the
 * free-spots feed — which is exactly how it ended up 1.100+ dp down the scroll. The header is now the
 * chip that selected this lane, so nothing here names the subject again; the fetch still happens on
 * entry and never before, unchanged.
 *
 * The lane says one of five things, and they are five different sentences on purpose — an empty list,
 * a failure, a country that publishes no prices, and a car with no fuel type all look like "no
 * prices" if you let them collapse into one. [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
 */
@Composable
fun HomeFuelLane(
    slice: HomeFuelSlice,
    modifier: Modifier = Modifier,
    /** A row opens the station's peek — the same one its map capsule opens.
     *  [FUEL-STATIONS-MAP-AND-PEEK-001] */
    onStationClick: ((String) -> Unit)? = null,
    /** First visit: the lane says where the prices come from, above whatever else it has to say.
     *  [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001] */
    isIntroducing: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // ⛔ ABOVE the five sentences below, the fuel-less one included: "your car has no fuel type"
        // is an answer, and an answer lands better once the user knows what the question was.
        if (isIntroducing) LaneIntroNote(NearbyComplement.FUEL)

        when {
            // ⛔ No fuel on the car means NO question was asked — its own sentence, never the empty
            // state, which is a verdict about the world.
            slice.missingFuelType ->
                LaneNote(stringResource(Res.string.home_fuel_missing_fuel_type))

            // ⛔ A failure is NOT an empty list. Saying "none nearby" when we could not ask sends the
            // user driving on a claim we never verified.
            slice.failed -> LaneNote(stringResource(Res.string.home_fuel_error))

            // The wait now shows WHERE the answer will appear. The spinner used to live in the
            // section header's chevron slot, and that header is gone with the fold — a skeleton in
            // the lane keeps the wait in the place that is loading.
            // [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001]
            slice.isLoading -> NearbyRowsSkeleton()

            slice.stations.isEmpty() -> EmptyFuelStations()

            else -> {
                // Said BEFORE the rows where a country publishes no prices, so nobody reads a
                // nameless column of distances as "the prices failed to load".
                if (!slice.hasPrices) {
                    LaneNote(stringResource(Res.string.home_fuel_no_prices))
                }
                HomeFuelStationRows(
                    stations = slice.stations,
                    onStationClick = onStationClick,
                )
                // A price with no hour beside it is a price nobody can weigh — this whole phase
                // exists because a proxy of the same data ran twelve hours behind.
                //
                // Each source says what it can actually say, and no more:
                //  · Spain stamps the RESPONSE → quote that hour.
                //  · France stamps each PRICE → quote the OLDEST as a bound. ⛔ Not "the prices are
                //    from X": inside one French response the ages ran from 0,7 h to thirteen months,
                //    so the only true one-liner is that none is older.
                // [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
                val reportedAt = slice.reportedAt
                val oldestMs = slice.oldestPriceAtMs
                when {
                    // ⛔ Primero, porque manda sobre las otras: si la lista salio del DISCO no hubo
                    // respuesta que sellar, asi que `reportedAt` es null por construccion y la frase
                    // que toca es otra — no se pudo preguntar, y esto es lo que habia.
                    // [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
                    //
                    // ⛔ Las horas POR PRECIO no se silencian debajo: cada precio sigue diciendo su
                    // edad, igual que en una respuesta viva. Un precio leido de disco no es menos
                    // honesto que uno del cable — dice cuando se midio en los dos casos. Lo que
                    // anade el disco es que la peticion de HOY fallo, que es lo que dice esta linea
                    // y no lo dice nada mas.
                    slice.rememberedAtMs != null ->
                        LaneNote(
                            stringResource(
                                Res.string.home_fuel_remembered,
                                relativeTimeText(slice.rememberedAtMs),
                            ),
                        )

                    reportedAt != null ->
                        LaneNote(stringResource(Res.string.home_fuel_reported_at, reportedAt))

                    oldestMs != null ->
                        LaneNote(
                            stringResource(
                                Res.string.home_fuel_oldest_price,
                                relativeTimeText(oldestMs),
                            ),
                        )
                }
            }
        }
    }
}

/** The honest "none within 5 km": illustrated, because it is the sentence this lane says most. */
@Composable
private fun EmptyFuelStations() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        EmptyFuelStationsIllustration(
            modifier = Modifier.size(EMPTY_ILLUSTRATION_W.dp, EMPTY_ILLUSTRATION_H.dp),
        )
        Text(
            text = stringResource(Res.string.home_fuel_empty),
            style = PaparcarType.current.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val EMPTY_ILLUSTRATION_W = 112
private const val EMPTY_ILLUSTRATION_H = 96
