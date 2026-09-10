package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.presentation.home.model.HomeFuelSlice
import com.rndeveloper.paparcar.presentation.util.relativeTimeText
import com.rndeveloper.paparcar.ui.components.PapSectionHeader
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
import paparcar.composeapp.generated.resources.home_fuel_title

/**
 * "Where to refuel", folded until asked for. [FUEL-STATIONS-LIST-001]
 *
 * Collapsed by default and **fetched only on the first unfold**: most sessions never ask this
 * question, and a province of stations downloaded for a list nobody opened is the user's data spent
 * on our convenience.
 *
 * The section says one of four things, and they are four different sentences on purpose — an empty
 * list, a failure and a country without published prices all look like "no prices" if you let them
 * collapse into one.
 */
@Composable
fun HomeFuelSection(
    slice: HomeFuelSlice,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    /** A row opens the station's peek — the same one its map capsule opens.
     *  [FUEL-STATIONS-MAP-AND-PEEK-001] */
    onStationClick: ((String) -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // ⛔ `weight(1f)`, no el `SpaceBetween` a secas: `PapSectionHeader` aplica
            // `fillMaxWidth()` a su propio Text, así que sin peso se comía la fila entera y el
            // chevron quedaba fuera de la pantalla — una sección plegable SIN NADA que diga que se
            // pliega. Visto en el Pixel 8, no en la galería, donde la variante plegada existía y
            // nadie la había abierto.
            PapSectionHeader(
                title = stringResource(Res.string.home_fuel_title),
                modifier = Modifier.weight(1f),
            )

            if (slice.isLoading) {
                // The spinner belongs to the row that was pressed, not to the screen.
                // [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001]
                CircularProgressIndicator(
                    modifier = Modifier.size(SPINNER_DP.dp),
                    strokeWidth = SPINNER_STROKE_DP.dp,
                )
            } else {
                Icon(
                    imageVector = if (slice.isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(visible = slice.isExpanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    // ⛔ No fuel on the car means NO question was asked — its own sentence, never
                    // the empty state, which is a verdict about the world.
                    // [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
                    slice.missingFuelType ->
                        SectionNote(stringResource(Res.string.home_fuel_missing_fuel_type))

                    // ⛔ A failure is NOT an empty list. Saying "none nearby" when we could not ask
                    // sends the user driving on a claim we never verified.
                    slice.failed -> SectionNote(stringResource(Res.string.home_fuel_error))

                    slice.isLoading -> Unit

                    slice.stations.isEmpty() -> EmptyFuelStations()

                    else -> {
                        // Said BEFORE the rows where a country publishes no prices, so nobody reads
                        // a nameless column of distances as "the prices failed to load".
                        if (!slice.hasPrices) {
                            SectionNote(stringResource(Res.string.home_fuel_no_prices))
                        }
                        HomeFuelStationRows(
                            stations = slice.stations,
                            onStationClick = onStationClick,
                        )
                        // A price with no hour beside it is a price nobody can weigh — this whole
                        // phase exists because a proxy of the same data ran twelve hours behind.
                        //
                        // Each source says what it can actually say, and no more:
                        //  · Spain stamps the RESPONSE → quote that hour.
                        //  · France stamps each PRICE → quote the OLDEST as a bound. ⛔ Not "the
                        //    prices are from X": inside one French response the ages ran from 0,7 h
                        //    to thirteen months, so the only true one-liner is that none is older.
                        // [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
                        val reportedAt = slice.reportedAt
                        val oldestMs = slice.oldestPriceAtMs
                        when {
                            // ⛔ First, because it outranks the others: when the list came off the
                            // disk there was no response to stamp, so `reportedAt` is null by
                            // construction and the sentence to say is a different one — we could not
                            // ask, this is what was here.
                            // [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
                            //
                            // ⛔ The per-price hours are NOT suppressed underneath: each price still
                            // carries and shows its own age, exactly as in a live answer. A price
                            // read off disk is no less honest than one off the wire — it says when
                            // it was measured either way. What the disk adds is that TODAY's asking
                            // failed, which is what this line says and nothing else does.
                            slice.rememberedAtMs != null ->
                                SectionNote(
                                    stringResource(
                                        Res.string.home_fuel_remembered,
                                        relativeTimeText(slice.rememberedAtMs),
                                    ),
                                )

                            reportedAt != null ->
                                SectionNote(
                                    stringResource(Res.string.home_fuel_reported_at, reportedAt),
                                )

                            oldestMs != null ->
                                SectionNote(
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
    }
}

@Composable
private fun SectionNote(text: String) {
    Text(
        text = text,
        style = PaparcarType.current.caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** The honest "none within 5 km": illustrated, because it is the sentence this section says most. */
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

private const val SPINNER_DP = 18
private const val SPINNER_STROKE_DP = 2
private const val EMPTY_ILLUSTRATION_W = 112
private const val EMPTY_ILLUSTRATION_H = 96
