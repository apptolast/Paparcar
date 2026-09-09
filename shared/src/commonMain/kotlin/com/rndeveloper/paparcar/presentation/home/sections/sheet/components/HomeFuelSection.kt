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
import com.rndeveloper.paparcar.ui.components.PapSectionHeader
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_fuel_empty
import paparcar.composeapp.generated.resources.home_fuel_error
import paparcar.composeapp.generated.resources.home_fuel_no_prices
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
                    // ⛔ A failure is NOT an empty list. Saying "none nearby" when we could not ask
                    // sends the user driving on a claim we never verified.
                    slice.failed -> SectionNote(stringResource(Res.string.home_fuel_error))

                    slice.isLoading -> Unit

                    slice.stations.isEmpty() -> SectionNote(stringResource(Res.string.home_fuel_empty))

                    else -> {
                        // Said BEFORE the rows where a country publishes no prices, so nobody reads
                        // a nameless column of distances as "the prices failed to load".
                        if (!slice.hasPrices) {
                            SectionNote(stringResource(Res.string.home_fuel_no_prices))
                        }
                        HomeFuelStationRows(stations = slice.stations)
                        // A price with no hour beside it is a price nobody can weigh — this whole
                        // phase exists because a proxy of the same data ran twelve hours behind.
                        slice.reportedAt?.let { reportedAt ->
                            SectionNote(stringResource(Res.string.home_fuel_reported_at, reportedAt))
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

private const val SPINNER_DP = 18
private const val SPINNER_STROKE_DP = 2
