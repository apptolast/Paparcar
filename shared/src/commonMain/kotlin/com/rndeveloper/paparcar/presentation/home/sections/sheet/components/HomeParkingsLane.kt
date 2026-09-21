package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.onboarding.NearbyComplement
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery
import com.rndeveloper.paparcar.presentation.home.model.HomeParkingsSlice
import com.rndeveloper.paparcar.presentation.home.model.PublicParkingFilter
import com.rndeveloper.paparcar.presentation.home.model.ViewportRefetch
import com.rndeveloper.paparcar.presentation.util.radiusString
import com.rndeveloper.paparcar.presentation.util.relativeTimeText
import com.rndeveloper.paparcar.ui.components.chips.PaparcarFilterChip
import com.rndeveloper.paparcar.ui.components.papHorizontalFadingEdges
import com.rndeveloper.paparcar.ui.illustrations.EmptyParkingsIllustration
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_parkings_empty
import paparcar.composeapp.generated.resources.home_parkings_empty_filtered
import paparcar.composeapp.generated.resources.home_parkings_error
import paparcar.composeapp.generated.resources.home_parkings_filter_covered
import paparcar.composeapp.generated.resources.home_parkings_remembered
import paparcar.composeapp.generated.resources.home_parkings_too_far
import paparcar.composeapp.generated.resources.parking_fee_free
import paparcar.composeapp.generated.resources.parking_fee_paid

/**
 * "Car parks nearby" — the lane the sheet shows while the lens sits on `HomeNearbyLens.PARKINGS`.
 * [PARKINGS-ON-THE-MAP-001] [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
 *
 * Twin of [HomeFuelLane], including the rule that matters: nothing is fetched until this lane is
 * entered, because most sessions never ask this question. What went away with the fold is the header,
 * the chevron and the "third section, under refuel" placement — a subject's distance from the top of
 * the sheet no longer depends on how many rows the subjects above it happened to have.
 */
@Composable
fun HomeParkingsLane(
    slice: HomeParkingsSlice,
    modifier: Modifier = Modifier,
    /** A row opens the car park's peek — the same one its map capsule opens. */
    onParkingClick: ((String) -> Unit)? = null,
    /** First visit: the lane says where its data comes from, above whatever else it has to say.
     *  [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001] */
    isIntroducing: Boolean = false,
    /** Whether Home has a position at all: without one the rows drop the distance, which is the one
     *  thing they say ABOUT THE USER. [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001] */
    hasFix: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // ⛔ ABOVE the rows and above every other sentence, including the failure one: "we could not
        // ask" only means something to someone who already knows what was being asked and of whom.
        if (isIntroducing) LaneIntroNote(NearbyComplement.CAR_PARKS)

        // ⛔ La barra de filtros NO se dibuja aquí: vive en la cabecera fijada de la hoja, junto a
        // los tabs, para que siga visible al desplazar la lista. Ver [laneFilterBar] en
        // `HomeSheetContent`. [PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001]

        when {
            // ⛔ Too far from the user to follow: nothing was asked, so neither the empty sentence nor
            // the failure one is true. The map is empty for the same reason, at the same moment.
            // [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
            slice.tooFar -> LaneNote(
                stringResource(
                    Res.string.home_parkings_too_far,
                    radiusString(ViewportRefetch.MAX_FROM_USER_METERS.toFloat()),
                ),
            )

            // ⛔ A failure is NOT an empty list: saying "none nearby" when we could not ask sends the
            // user driving on a claim we never checked.
            // [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
            slice.failed -> LaneNote(stringResource(Res.string.home_parkings_error))

            // The skeleton only when there is nothing to show: while a new area loads, the rows keep
            // matching the pins still on the map, as the spots do.
            // [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
            slice.isLoading && slice.parkings.isEmpty() -> NearbyRowsSkeleton()

            // ⛔ «Ninguno cumple lo que has pedido» NO es «aquí no hay parkings». La segunda es un
            // veredicto sobre el mundo; la primera, sobre tu filtro — y sólo una de las dos se
            // arregla tocando un chip. [PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001]
            slice.parkings.isEmpty() && slice.filter != null && slice.hasAnyBeforeFilter ->
                LaneNote(stringResource(Res.string.home_parkings_empty_filtered))

            slice.parkings.isEmpty() -> EmptyParkings()

            else -> HomePublicParkingRows(
                parkings = slice.parkings,
                onParkingClick = onParkingClick,
                hasFix = hasFix,
            )
        }

        // ⛔ Una lista RECORDADA tiene que DECIR que lo es.
        // [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
        //
        // Es la tercera frase, y va DEBAJO de las filas en vez de en su lugar: la app no pudo
        // preguntar ahora, pero ha estado aqui antes y esto es lo que habia. Sin ella el carril
        // ensena parkings de hace un mes con la misma cara que una lista traida hace un segundo.
        //
        // La EDAD, no la fecha: `relativeTimeText` ya responde "cuanto de vieja es" en los nueve
        // locales, asi que cuesta una key en vez de un formateador de meses por nueve.
        slice.rememberedAtMs?.takeUnless { slice.isLoading }?.let { rememberedAt ->
            LaneNote(
                stringResource(
                    Res.string.home_parkings_remembered,
                    relativeTimeText(rememberedAt),
                ),
            )
        }
    }
}

/**
 * The honest "none within the radius we actually asked for": illustrated, twin of
 * [EmptyFuelStations] in the fuel lane.
 *
 * ⛔ The radius comes from [PublicParkingsQuery.DEFAULT_RADIUS_METERS], never from the copy. It was
 * typed into the sentence in nine locales, the query moved to 3 km, and the sentence went on saying
 * 1.5 km. [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001]
 */
@Composable
private fun EmptyParkings() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        EmptyParkingsIllustration(
            modifier = Modifier.size(EMPTY_ILLUSTRATION_W.dp, EMPTY_ILLUSTRATION_H.dp),
        )
        Text(
            text = stringResource(
                Res.string.home_parkings_empty,
                radiusString(PublicParkingsQuery.DEFAULT_RADIUS_METERS.toFloat()),
            ),
            style = PaparcarType.current.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val EMPTY_ILLUSTRATION_W = 112
private const val EMPTY_ILLUSTRATION_H = 96

/**
 * Los filtros del carril de parkings: **tarifa y techo**, que es lo que sabemos de verdad.
 * [PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001]
 *
 * Misma gramática que la barra de tamaños de las plazas. Aquí los chips sí FILTRAN (esconden filas),
 * al revés que los de gasolineras, que ordenan — por eso el carril tiene una frase propia para
 * «ninguno cumple lo que has pedido», que es lo que hace que esconder no engañe.
 *
 * ⛔ Pulsar el chip encendido lo apaga: sin chip «Todos», el estado sin filtro es el normal y no
 * necesita su propia opción.
 */
@Composable
fun HomeParkingFilterBar(
    selected: PublicParkingFilter?,
    onSelect: (PublicParkingFilter?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier.papHorizontalFadingEdges(scrollState).horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PublicParkingFilter.entries.forEach { filter ->
            val isOn = selected == filter
            PaparcarFilterChip(
                label = stringResource(filter.labelRes()),
                selected = isOn,
                onClick = { onSelect(filter.takeUnless { isOn }) },
            )
        }
    }
}

/** La palabra de cada filtro. Vive aquí y no en el enum porque el dominio es Kotlin puro. */
private fun PublicParkingFilter.labelRes() = when (this) {
    PublicParkingFilter.FREE -> Res.string.parking_fee_free
    PublicParkingFilter.PAID -> Res.string.parking_fee_paid
    PublicParkingFilter.COVERED -> Res.string.home_parkings_filter_covered
}
