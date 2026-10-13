package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.onboarding.NearbyComplement
import com.rndeveloper.paparcar.presentation.home.model.FuelLaneSort
import com.rndeveloper.paparcar.presentation.home.model.HomeFuelSlice
import com.rndeveloper.paparcar.presentation.home.model.ViewportRefetch
import com.rndeveloper.paparcar.presentation.util.fuelTypeLabel
import com.rndeveloper.paparcar.presentation.util.radiusString
import com.rndeveloper.paparcar.presentation.util.relativeTimeText
import com.rndeveloper.paparcar.ui.components.PapVerticalDivider
import com.rndeveloper.paparcar.ui.components.chips.PaparcarFilterChip
import com.rndeveloper.paparcar.ui.components.papHorizontalFadingEdges
import com.rndeveloper.paparcar.ui.illustrations.EmptyFuelStationsIllustration
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.PapGap
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_fuel_empty
import paparcar.composeapp.generated.resources.home_fuel_error
import paparcar.composeapp.generated.resources.home_fuel_live_prices
import paparcar.composeapp.generated.resources.home_fuel_missing_fuel_type
import paparcar.composeapp.generated.resources.home_fuel_no_prices
import paparcar.composeapp.generated.resources.home_fuel_oldest_price
import paparcar.composeapp.generated.resources.home_fuel_prices_unavailable
import paparcar.composeapp.generated.resources.home_fuel_remembered
import paparcar.composeapp.generated.resources.home_fuel_reported_at
import paparcar.composeapp.generated.resources.home_fuel_sort_cheapest
import paparcar.composeapp.generated.resources.home_fuel_sort_nearest

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
    /** Whether Home has a position at all: without one the rows drop the distance, which is the one
     *  thing they say ABOUT THE USER. [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001] */
    hasFix: Boolean = true,
    /**
     * `source|id` de las gasolineras que el usuario sigue, para que la fila lo diga.
     * [FUEL-A-FOLLOW-MUST-SHOW-ITSELF-001]
     *
     * ⛔ Va como parámetro y **no dentro de [HomeFuelSlice]**, por la misma razón por la que el
     * follow no es una columna de la tabla de caché: el slice es la RESPUESTA (se pide al entrar al
     * carril, se reemplaza al mover la cámara, se vacía al fallar) y esto es lo único del carril que
     * lo puso el usuario. Vidas opuestas, sitios opuestos.
     */
    followedStationKeys: Set<String> = emptySet(),
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // ⛔ ABOVE the five sentences below, the fuel-less one included: "your car has no fuel type"
        // is an answer, and an answer lands better once the user knows what the question was.
        if (isIntroducing) LaneIntroNote(NearbyComplement.FUEL)

        // ⛔ La barra NO se dibuja aquí: vive en la cabecera fijada de la hoja, junto a los tabs,
        // igual que la de plazas y la de parkings. Ver [LaneFilterBar] en `HomeSheetContent`.
        // [PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001] [FUEL-THE-LANE-IS-FILTERED-BY-WHAT-YOU-BURN-001]

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
            // …and only when there is nothing to show: while a new area loads, the rows keep
            // matching the capsules still on the map. [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
            slice.isLoading && slice.stations.isEmpty() -> NearbyRowsSkeleton()

            slice.stations.isEmpty() -> EmptyFuelStations()

            else -> {
                // Said BEFORE the rows where a country publishes no prices, so nobody reads a
                // nameless column of distances as "the prices failed to load".
                if (!slice.hasPrices) {
                    // ⛔ DOS frases, no una. «Este país no publica precios» es verdad donde no hay
                    // feed nacional; donde SÍ lo hay y no contestó, lo verdadero es que no pudimos
                    // traerlos. Decir la primera por la segunda le contaba al usuario que España no
                    // publica precios de carburante mientras el Ministerio los servía para su misma
                    // calle. [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
                    // [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
                    LaneNote(
                        stringResource(
                            if (slice.didFallBack) {
                                Res.string.home_fuel_prices_unavailable
                            } else {
                                Res.string.home_fuel_no_prices
                            },
                        ),
                    )
                }
                HomeFuelStationRows(
                    stations = slice.stations,
                    onStationClick = onStationClick,
                    hasFix = hasFix,
                    followedStationKeys = followedStationKeys,
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

                    //  · Germany stamps NOTHING — MTS-K prices are near-real-time by law, so the
                    //    honest line is the guarantee itself, not an hour nobody wrote. Without
                    //    this branch the first priced list with no stamp fell silent exactly where
                    //    its feed is freshest. [FUEL-GERMANY-THE-FEED-ANSWERS-WHOLE-001]
                    slice.pricesAreLive && slice.hasPrices ->
                        LaneNote(stringResource(Res.string.home_fuel_live_prices))
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
            // ⛔ The radius is [FuelStationsQuery.DEFAULT_RADIUS_METERS], not a number typed into
            // nine translations. [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001]
            text = stringResource(
                Res.string.home_fuel_empty,
                radiusString(FuelStationsQuery.DEFAULT_RADIUS_METERS.toFloat()),
            ),
            style = PaparcarType.current.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val EMPTY_ILLUSTRATION_W = 112
private const val EMPTY_ILLUSTRATION_H = 96

/**
 * Los filtros del carril: **cómo se ordena** y **qué producto se cotiza**.
 * [FUEL-THE-LANE-IS-FILTERED-BY-WHAT-YOU-BURN-001]
 *
 * Misma gramática que la barra de tamaños de las plazas —fila que se desplaza, chips de
 * [PaparcarFilterChip]— porque es la misma pregunta hecha sobre otro sujeto, y el usuario pidió
 * explícitamente alinearse con ella.
 *
 * ⛔ **Dos grupos, y se nota que son dos.** El orden (cercanas / baratas) y el producto (diésel,
 * gasolina, GLP) no son la misma pregunta: uno reordena, el otro requota. Van en la misma fila
 * porque comparten sitio y gesto, pero separados por un divisor vertical — sin él, «Más baratas» y
 * «Diésel» se leen como opciones de una misma lista donde elegir una apaga la otra, que es
 * exactamente lo que NO pasa.
 *
 * ⛔ **Sin chip «Todos» en el producto.** Una gasolinera no tiene «un precio»: tiene el de cada
 * producto. «Todos» tendría que enseñar cuál, y elegirlo por su cuenta es justo lo que hace hoy el
 * coche activo. Volver al coche activo se hace pulsando el producto elegido otra vez, que es lo
 * que ya hace el resto de la app con un chip encendido.
 */
/*
 * ⛔ **Pública, y por una razón concreta**: el user pidió que estos chips se queden fijos al
 * desplazar, como los tabs. Esa fijación NO se resuelve aquí — la resuelve `LaneFilterBar`, el
 * bloque de cabecera que [PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001] (#43) introduce en
 * `HomeSheetContent`, donde tabs y filtros comparten un único `stickyHeader`. Duplicar ese seam en
 * esta rama sería escribir dos veces la misma decisión y garantizar un conflicto al mergear.
 *
 * Así que la barra se hace pública y se sigue dibujando dentro del carril mientras #43 no esté
 * dentro. En cuanto lo esté, el cableado es sustituir el `HomeNearbyLens.FUEL -> Unit` de
 * `LaneFilterBar` por una llamada a esta función y quitar el bloque de abajo.
 * [FUEL-THE-LANE-IS-FILTERED-BY-WHAT-YOU-BURN-001]
 */
@Composable
fun HomeFuelFilterBar(
    sort: FuelLaneSort,
    product: FuelType?,
    availableProducts: List<FuelType>,
    onSortSelect: (FuelLaneSort) -> Unit,
    onProductSelect: ((FuelType?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier.papHorizontalFadingEdges(scrollState).horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(PapGap.chipRow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaparcarFilterChip(
            label = stringResource(Res.string.home_fuel_sort_nearest),
            selected = sort == FuelLaneSort.NEAREST,
            onClick = { onSortSelect(FuelLaneSort.NEAREST) },
        )
        PaparcarFilterChip(
            label = stringResource(Res.string.home_fuel_sort_cheapest),
            selected = sort == FuelLaneSort.CHEAPEST,
            onClick = { onSortSelect(FuelLaneSort.CHEAPEST) },
        )
        if (onProductSelect != null && availableProducts.isNotEmpty()) {
            PapVerticalDivider(modifier = Modifier.height(FILTER_DIVIDER_HEIGHT_DP.dp))
            availableProducts.forEach { type ->
                PaparcarFilterChip(
                    label = fuelTypeLabel(type),
                    selected = product == type,
                    // ⛔ Elección ÚNICA, como «Cercanas / Más baratas»: no hay apagado. El carril
                    // siempre está cotizado por algún combustible —sin elección, el del coche— así
                    // que un chip «apagado» no describía ningún estado del mundo. Lo que hacía
                    // `takeUnless` era devolver el carril al coche, que la mitad de las veces es el
                    // MISMO combustible: el chip se apagaba y la lista no cambiaba.
                    // [FUEL-THE-CHIP-SHOWS-THE-FUEL-THE-LANE-IS-QUOTED-IN-001]
                    onClick = { onProductSelect(type) },
                )
            }
        }
    }
}

/** Alto del divisor entre los dos grupos de chips: el de un chip, para que no sobresalga. */
private const val FILTER_DIVIDER_HEIGHT_DP = 24
