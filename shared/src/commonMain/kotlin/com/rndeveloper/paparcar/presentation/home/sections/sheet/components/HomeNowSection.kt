package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.nearby.NearbyNotable
import com.rndeveloper.paparcar.domain.nearby.NearbySubject
import com.rndeveloper.paparcar.domain.nearby.nearbyNotables
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation
import com.rndeveloper.paparcar.domain.usecase.parkings.NearbyPublicParking
import com.rndeveloper.paparcar.presentation.home.HomeBrowseListSlice
import com.rndeveloper.paparcar.presentation.home.HomeIntent
import com.rndeveloper.paparcar.presentation.home.HomeSelection
import com.rndeveloper.paparcar.presentation.home.model.HomeNearbyLens
import com.rndeveloper.paparcar.presentation.home.model.publicParkingKindLabel
import com.rndeveloper.paparcar.presentation.home.sections.sheet.HomeSheetAction
import com.rndeveloper.paparcar.presentation.util.distanceString
import com.rndeveloper.paparcar.presentation.util.fuelTypeLabel
import com.rndeveloper.paparcar.presentation.util.locationDisplayText
import com.rndeveloper.paparcar.presentation.home.model.pumpPriceLabel
import com.rndeveloper.paparcar.presentation.util.subjectWhereLine
import com.rndeveloper.paparcar.ui.components.PapIconTile
import com.rndeveloper.paparcar.ui.theme.outlineSubtle
import com.rndeveloper.paparcar.ui.components.PapOpportunityCard
import com.rndeveloper.paparcar.ui.components.PapSectionHeader
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PaparcarSpacing
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_now_eyebrow_car_park
import paparcar.composeapp.generated.resources.home_now_eyebrow_fuel
import paparcar.composeapp.generated.resources.home_now_eyebrow_fuel_plain
import paparcar.composeapp.generated.resources.home_now_eyebrow_spot
import paparcar.composeapp.generated.resources.home_now_section_header
import paparcar.composeapp.generated.resources.home_now_see_all_car_parks
import paparcar.composeapp.generated.resources.home_now_see_all_fuel_stations
import paparcar.composeapp.generated.resources.home_now_see_all_spots
import paparcar.composeapp.generated.resources.location_fallback_spot
import paparcar.composeapp.generated.resources.location_fallback_station
import paparcar.composeapp.generated.resources.parking_fee_free
import paparcar.composeapp.generated.resources.parking_fee_paid

/**
 * **AQUÍ Y AHORA** — lo que merece el primer vistazo, antes de que el usuario toque nada.
 * [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
 *
 * Es la respuesta a la pregunta del user: *«En Aquí y ahora realmente qué debemos mostrar? los
 * items más llamativos de cada una de las pestañas? Si no hay ninguno, no mostrar esta sección
 * supongo»*. Sí a las dos: **uno por sujeto** y **sin sección cuando no hay ninguno**.
 *
 * ⛔ **Qué entra NO se decide aquí.** Lo decide `nearbyNotables()`, que es una función pura con sus
 * testigos: uno por sujeto, orden fijo, y un umbral por sujeto. Este fichero sólo lo pinta. Si la
 * regla viviera dentro del composable, sería lógica de producto fuera del alcance de los tests.
 *
 * ⛔ **Y lee las MISMAS listas que el carril de abajo**, no una copia: la tarjeta y la fila no pueden
 * nombrar cosas distintas, ni el pie prometer una lista con otra cuenta. El notable trae un id y el
 * item se resuelve contra esa lista.
 *
 * ⛔ **Sin notables no hay ni cabecera.** Ni hueco, ni caja diciendo que no hay nada: cada carril ya
 * dice lo suyo en su sitio, y repetirlo arriba es llenar el primer nivel con la noticia de que no
 * hay noticias.
 *
 * Su sitio es **entre la tira de coches y el carril**: resume lo que hay debajo, así que va antes de
 * la lista que resume y después de lo que es TUYO.
 * [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
 */
internal fun LazyListScope.nowSection(
    slice: HomeBrowseListSlice,
    filteredSpots: List<Spot>,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
    onOpenNearbyList: (NearbySubject) -> Unit,
) {
    val notables = nearbyNotables(
        spots = filteredSpots,
        parkings = slice.parkings.parkings,
        fuel = slice.fuel.stations,
        from = slice.userGpsPoint,
    )
    if (notables.isEmpty()) return

    item("now_header") {
        PapSectionHeader(
            title = stringResource(Res.string.home_now_section_header),
            modifier = Modifier.padding(
                start = NOW_SIDE_INSET_DP.dp,
                end = NOW_SIDE_INSET_DP.dp,
                top = NOW_HEADER_TOP_DP.dp,
                bottom = PaparcarSpacing.sm,
            ),
        )
    }

    notables.forEach { notable ->
        item("now_${notable.subject.name}_${notable.id}") {
            NowCard(
                notable = notable,
                slice = slice,
                filteredSpots = filteredSpots,
                onIntent = onIntent,
                onAction = onAction,
                onOpenNearbyList = onOpenNearbyList,
                modifier = Modifier.padding(
                    start = NOW_SIDE_INSET_DP.dp,
                    end = NOW_SIDE_INSET_DP.dp,
                    bottom = PaparcarSpacing.sm,
                ),
            )
        }
    }
}

/**
 * Una tarjeta, resuelta contra la lista que la fila de abajo lee.
 *
 * ⛔ **El item puede no estar**, y entonces no se dibuja nada: el notable se calculó sobre la lista
 * de este mismo fotograma, así que no debería pasar — pero una tarjeta que sobrevive a su item es
 * exactamente cómo se enseña un precio de una gasolinera que ya no está en la respuesta.
 */
@Composable
private fun NowCard(
    notable: NearbyNotable,
    slice: HomeBrowseListSlice,
    filteredSpots: List<Spot>,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
    onOpenNearbyList: (NearbySubject) -> Unit,
    modifier: Modifier = Modifier,
) {
    val far = notable.distanceMeters?.let { distanceString(it.toFloat()) }
    when (notable.subject) {
        NearbySubject.SPOT -> {
            val spot = filteredSpots.firstOrNull { it.id == notable.id } ?: return
            SpotNowCard(spot, filteredSpots.size, far, onIntent, onAction, onOpenNearbyList, modifier)
        }

        NearbySubject.CAR_PARK -> {
            val nearby = slice.parkings.parkings.firstOrNull { it.parking.id == notable.id } ?: return
            CarParkNowCard(nearby, slice.parkings.parkings.size, far, onIntent, onAction, onOpenNearbyList, modifier)
        }

        NearbySubject.FUEL -> {
            val nearby = slice.fuel.stations.firstOrNull { it.stationKey == notable.id } ?: return
            FuelNowCard(nearby, slice.fuel.product, slice.fuel.stations.size, far, onIntent, onAction, onOpenNearbyList, modifier)
        }
    }
}

/**
 * La plaza más cercana.
 *
 * ⛔ **Sin cifra grande.** El sujeto de una plaza es DÓNDE está, y su antigüedad es un sello, no una
 * magnitud que comparar: en una tarjeta hay una plaza, así que no hay con qué compararla. La cifra
 * grande está reservada a lo que decide entre varios, que en este primer nivel es el precio.
 */
@Composable
private fun SpotNowCard(
    spot: Spot,
    laneCount: Int,
    far: String?,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
    onOpenNearbyList: (NearbySubject) -> Unit,
    modifier: Modifier,
) {
    val name = locationDisplayText(placeInfo = spot.placeInfo, address = spot.address)
        ?: stringResource(Res.string.location_fallback_spot)
    PapOpportunityCard(
        eyebrow = stringResource(Res.string.home_now_eyebrow_spot).uppercase(),
        name = name,
        // El MISMO par de acciones que pulsar su fila: vuela la cámara y abre su ficha. Una
        // selección que el mapa no acusa se lee como un toque muerto.
        onOpen = {
            onAction(HomeSheetAction.MoveCamera(spot.location.latitude, spot.location.longitude))
            onIntent(HomeIntent.SelectItem(HomeSelection.Spot(spot.id)))
        },
        footerLabel = pluralStringResource(Res.plurals.home_now_see_all_spots, laneCount, laneCount),
        onOpenList = { onOpenNearbyList(NearbySubject.SPOT) },
        modifier = modifier,
        leading = { PapIconTile(icon = PaparcarIcons.CommunitySpot, tint = PapColor.action, border = outlineSubtle) },
        caption = subjectWhereLine(name = name, placeLine = null, secondaryLine = null, farText = far),
    )
}

/**
 * El parking más cercano.
 *
 * ⛔ **Su cola es la TARIFA DECLARADA, no un importe**: `FeeInfo` no tiene precio, y `UNKNOWN` —el
 * 71 % de los parkings— se pinta como **nada**, nunca como «gratis». A un conductor al que le
 * dijimos «gratis» y se encontró una barrera le mintió la app, no OpenStreetMap.
 */
@Composable
private fun CarParkNowCard(
    nearby: NearbyPublicParking,
    laneCount: Int,
    far: String?,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
    onOpenNearbyList: (NearbySubject) -> Unit,
    modifier: Modifier,
) {
    val parking = nearby.parking
    val kindLabel = publicParkingKindLabel(parking.kind)
    val name = parking.displayName ?: kindLabel
    PapOpportunityCard(
        eyebrow = stringResource(Res.string.home_now_eyebrow_car_park).uppercase(),
        name = name,
        onOpen = { onAction(HomeSheetAction.SelectPublicParking(parking.id)) },
        footerLabel = pluralStringResource(Res.plurals.home_now_see_all_car_parks, laneCount, laneCount),
        onOpenList = { onOpenNearbyList(NearbySubject.CAR_PARK) },
        modifier = modifier,
        leading = { PapIconTile(icon = PaparcarIcons.CarParkMark, tint = PapColor.carPark, border = outlineSubtle) },
        // Su tipo es el calificador, así que baja a la línea de dónde está — salvo que el tipo ya
        // SEA el nombre, y entonces la regla de la gramática lo deja fuera.
        caption = subjectWhereLine(name = name, placeLine = kindLabel, secondaryLine = null, farText = far),
        stamp = when (parking.fee) {
            FeeInfo.PAID -> stringResource(Res.string.parking_fee_paid)
            FeeInfo.FREE -> stringResource(Res.string.parking_fee_free)
            FeeInfo.UNKNOWN -> null
        },
    )
}

/**
 * La gasolinera más barata de la respuesta — la única de las tres que trae una CIFRA que decide.
 *
 * ⛔ Y la cifra va a la precisión del surtidor: tres decimales, porque esa décima de céntimo es con
 * frecuencia toda la diferencia entre dos estaciones, que es la comparación que este carril existe
 * para hacer. Lo resuelve el formateador compartido, no este fichero.
 */
@Composable
private fun FuelNowCard(
    nearby: NearbyFuelStation,
    /** El carburante que este carril cotiza — el del coche activo. */
    product: FuelType?,
    laneCount: Int,
    far: String?,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
    onOpenNearbyList: (NearbySubject) -> Unit,
    modifier: Modifier,
) {
    val station = nearby.station
    val name = station.name ?: station.address ?: station.town
        ?: stringResource(Res.string.location_fallback_station)
    // El eyebrow nombra el PRODUCTO: la cifra no se entiende sin saber de qué carburante es. Y el
    // adjetivo concuerda con «precio», no con el producto — ver la nota del string.
    //
    // ⛔ Y si el carril no sabe qué producto cotiza, el eyebrow lo dice SIN producto en vez de
    // dejar el separador colgando: una plantilla a la que le falta su hueco se lee como un error.
    val eyebrow = product
        ?.let { stringResource(Res.string.home_now_eyebrow_fuel, fuelTypeLabel(it)) }
        ?: stringResource(Res.string.home_now_eyebrow_fuel_plain)
    PapOpportunityCard(
        eyebrow = eyebrow.uppercase(),
        name = name,
        onOpen = { onAction(HomeSheetAction.SelectFuelStation(station.id)) },
        footerLabel = pluralStringResource(Res.plurals.home_now_see_all_fuel_stations, laneCount, laneCount),
        onOpenList = { onOpenNearbyList(NearbySubject.FUEL) },
        modifier = modifier,
        leading = { PapIconTile(icon = PaparcarIcons.Fuel, tint = PapColor.actionText, border = outlineSubtle) },
        figure = nearby.price?.perUnit?.pumpPriceLabel(),
        figureColor = MaterialTheme.colorScheme.onSurface,
        caption = subjectWhereLine(
            name = name,
            placeLine = station.address,
            secondaryLine = station.town,
            farText = far,
        ),
    )
}

/** El margen de página de la hoja — el mismo con el que se inseta todo lo demás de esta lista. */
private const val NOW_SIDE_INSET_DP = 16

/** El aire que separa esta sección de la tira de coches de encima, igual que el del corte del carril. */
private const val NOW_HEADER_TOP_DP = 24
