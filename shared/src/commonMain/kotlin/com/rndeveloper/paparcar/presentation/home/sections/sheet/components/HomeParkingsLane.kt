package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rndeveloper.paparcar.domain.onboarding.NearbyComplement
import com.rndeveloper.paparcar.presentation.home.model.HomeParkingsSlice
import com.rndeveloper.paparcar.presentation.util.relativeTimeText
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_parkings_empty
import paparcar.composeapp.generated.resources.home_parkings_error
import paparcar.composeapp.generated.resources.home_parkings_remembered

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
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // ⛔ ABOVE the rows and above every other sentence, including the failure one: "we could not
        // ask" only means something to someone who already knows what was being asked and of whom.
        if (isIntroducing) LaneIntroNote(NearbyComplement.CAR_PARKS)

        when {
            // ⛔ A failure is NOT an empty list: saying "none nearby" when we could not ask sends the
            // user driving on a claim we never checked.
            // [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
            slice.failed -> LaneNote(stringResource(Res.string.home_parkings_error))

            slice.isLoading -> NearbyRowsSkeleton()

            slice.parkings.isEmpty() -> LaneNote(stringResource(Res.string.home_parkings_empty))

            else -> HomePublicParkingRows(
                parkings = slice.parkings,
                onParkingClick = onParkingClick,
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
