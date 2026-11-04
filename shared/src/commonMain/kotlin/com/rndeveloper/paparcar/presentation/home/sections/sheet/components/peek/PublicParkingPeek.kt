package com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.AccessTimeFilled
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.usecase.parkings.NearbyPublicParking
import com.rndeveloper.paparcar.presentation.home.HomeIntent
import com.rndeveloper.paparcar.presentation.home.PeekStep
import com.rndeveloper.paparcar.presentation.home.model.publicParkingKindLabel
import com.rndeveloper.paparcar.presentation.home.sections.sheet.HomeSheetAction
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheet
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetEyebrowTone
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetStepper
import com.rndeveloper.paparcar.presentation.util.distanceMeters
import com.rndeveloper.paparcar.presentation.util.distanceString
import com.rndeveloper.paparcar.ui.components.PapFooterButton
import com.rndeveloper.paparcar.ui.components.PapFooterButtonStyle
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.PapGap
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.common_directions
import paparcar.composeapp.generated.resources.parking_capacity
import paparcar.composeapp.generated.resources.parking_fee_free
import paparcar.composeapp.generated.resources.parking_fee_paid
import paparcar.composeapp.generated.resources.parking_max_height
import paparcar.composeapp.generated.resources.parking_peek_eyebrow
import paparcar.composeapp.generated.resources.parking_peek_step_next
import paparcar.composeapp.generated.resources.parking_peek_step_prev

// ═════════════════════════════════════════════════════════════════════════════
// PublicParkingPeek — the selected car park, from the map layer or the list.
// [PARKINGS-ON-THE-MAP-001]
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Everything the source ACTUALLY said about this car park, and not one line more.
 *
 * ⛔ Each fact is rendered only where it exists — measured coverage says most of them usually do
 * not: `capacity` 1-16%, `maxheight` 0-7%, `opening_hours` 0-3%, `fee` 19-29%. So the body of this
 * peek is short by design, and its shortness is the honest answer rather than a missing feature.
 * Inventing "free" out of an untagged fee would send a driver into a barrier.
 *
 * Geometry belongs to [PapSheet] untouched: no inner scroll, one-line title. Distance is always in
 * DRIVING mode — you do not walk to a car park, you arrive at one.
 */
@Composable
internal fun PublicParkingPeek(
    nearby: NearbyPublicParking,
    userLocation: Pair<Double, Double>?,
    step: PeekStep,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
) {
    val parking = nearby.parking
    val distM = userLocation?.let { (uLat, uLon) ->
        distanceMeters(uLat, uLon, parking.latitude, parking.longitude)
    }
    val kindLabel = publicParkingKindLabel(parking.kind)
    // Titled by its kind when nobody named it — the common case outside big cities.
    val title = parking.displayName ?: kindLabel

    PapSheet(
        // La misma P que el marcador que acabas de tocar: un parking público se dibuja igual en el
        // mapa, en la fila, en la pestaña y aquí. [UI-THE-CAR-PARK-WEARS-OUR-OWN-P-001]
        lead = PapSheetLead.GenericIcon(
            icon = PaparcarIcons.CarParkMark,
            container = PapColor.carPark,
            tint = PapColor.onCarPark,
        ),
        // ⛔ La MISMA gramática que el resto de peeks (decisión del user, 30-10).
        // [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
        eyebrow = "",
        title = title,
        titleSlot = peekSubjectTitleSlot(
            name = title,
            // ⛔ **`onSurface`, sin color de identidad** (user, 30-10): *«será en color normal, no le
            // metas color»*. En el modal el nombre ES el título de la hoja, no el nombre dentro de
            // una fila, y el color de identidad ya lo lleva el TILE de la izquierda — que es
            // literalmente lo que manda la doctrina: *«el glifo lleva el color; el nombre queda en
            // `onSurface`»*. La excepción escrita ahí es «el eyebrow del peek, donde no hay glifo», y
            // aquí no hay eyebrow **ni** falta glifo: hay tile. [UI-COLOR-DOCTRINE-001]
            //
            // Los CARRILES («Plazas», «Parkings», «Gasolineras») sí siguen en su color, y es otra
            // petición del mismo user: ahí el nombre es la CATEGORÍA, no un sujeto con tile propio.
            nameColor = MaterialTheme.colorScheme.onSurface,
            // Su tipo es el calificador — salvo que el tipo YA sea el nombre, y entonces no se
            // repite. Misma deduplicación que tenía el subtítulo.
            qualifier = null,
            maxLines = PEEK_MODAL_TITLE_MAX_LINES,
        ),
        // ⛔ **El calificador baja a su propia línea, y sin el ·** (user, 03-11): *«cuando tengamos el
        // título seguido de ·, poner esa línea abajo de la primera línea sin ·»*. Con los dos en un
        // solo `Text` de 2 líneas el corte caía a mitad de frase —«Plaza comunitaria · Publicada
        // hace 1 / min»— porque el envoltorio no sabe dónde acaba una idea.
        //
        // Y no hace falta componer nada: es el `subtitle` de la hoja, el slot declarado para la
        // segunda línea bajo el título, que los modales dejaron libre al bajar el sitio al cuerpo.
        // El separador desaparece con él — un · une dos cosas en una línea; en dos líneas sobra.
        // Su tipo — salvo que el tipo YA sea el nombre, y entonces no se repite.
        subtitle = kindLabel.takeIf { it != title },
        // ⛔ **Sin fila de sitio**, y no es un olvido: un parking público no trae dirección en
        // ninguna fuente (`PublicParking` no tiene campo). Sus hechos abren por la distancia.
        // [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
        // La cabecera NOMBRA; el cuerpo DESARROLLA. El sitio y la distancia abren los hechos.
        // [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
        onDismiss = { onIntent(HomeIntent.SelectItem(null)) },
        // ‹ / › walk the car parks in the list's own order, through the SAME action a row or a
        // capsule fires — the same stepper the spot and fuel peeks use.
        // [CARPARK-DETAIL-HAS-PREV-NEXT-CLOSE-001]
        stepper = PapSheetStepper(
            prevContentDescription = stringResource(Res.string.parking_peek_step_prev),
            nextContentDescription = stringResource(Res.string.parking_peek_step_next),
            onPrev = step.prevId?.let { id -> { onAction(HomeSheetAction.SelectPublicParking(id)) } },
            onNext = step.nextId?.let { id -> { onAction(HomeSheetAction.SelectPublicParking(id)) } },
        ),
        meta = {
            DistanceAndTimeRow(
                distanceM = distM,
                mode = TravelMode.DRIVING,
                accentColor = MaterialTheme.colorScheme.primary,
            )
            parking.openingHours?.let { hours ->
                // As the source words it — "24/7", "Mo-Sa 08:00-22:00"… never parsed, never judged:
                // a wrong parse closes a car park that is open.
                PeekMetaRow(
                    icon = Icons.Rounded.AccessTimeFilled,
                    text = hours,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Los tres hechos del parking son HECHOS, no un desglose: viven en la misma banda que
            // la distancia y el horario, colgando del mismo canal. Venían del difunto
            // `ParkingFactRow`, que los pintaba 28 dp a la izquierda de sus vecinos.
            // ⛔ UNKNOWN says nothing at all. It is not "free", and it is not "unknown" written out
            // either — a row that exists only to say we do not know is noise in a peek this small.
            when (parking.fee) {
                FeeInfo.PAID -> ParkingFactRow(Icons.Rounded.Payments, stringResource(Res.string.parking_fee_paid))
                FeeInfo.FREE -> ParkingFactRow(Icons.Rounded.Payments, stringResource(Res.string.parking_fee_free))
                FeeInfo.UNKNOWN -> Unit
            }
            parking.capacity?.let { spaces ->
                // The TOTAL, and it is never dressed as availability: how many are free right now is
                // what `PARKINGS-LIVE-OCCUPANCY-001` will answer, and until then nobody claims it.
                ParkingFactRow(
                    Icons.Rounded.LocalParking,
                    stringResource(Res.string.parking_capacity, spaces.toString()),
                )
            }
            parking.maxHeightMeters?.let { metres ->
                // The one fact a van driver cannot guess from a map.
                ParkingFactRow(
                    Icons.Rounded.Height,
                    stringResource(Res.string.parking_max_height, formatClearance(metres)),
                )
            }
        },
        actions = {
            PapFooterButton(
                label = stringResource(Res.string.common_directions),
                leadingIcon = Icons.Rounded.Navigation,
                onClick = {
                    onAction(
                        HomeSheetAction.NavigateExternal(
                            lat = parking.latitude,
                            lon = parking.longitude,
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

/** One stated fact, in the reading voice. */
@Composable
private fun ParkingFactRow(icon: ImageVector, text: String) {
    // ⛔ **El mismo molde que sus vecinas, y con su glifo.** Era un `Row` propio con su
    // `padding(vertical = 4.dp)` y sin canal, así que estos tres hechos salían 28 dp a la izquierda
    // de la distancia y el horario — medido en el Oppo: x=48 contra x=126.
    //
    // El canal lo arregló primero dejándolo VACÍO, y eso estaba a medias: *«para la modal de parking
    // le faltan un icono al resto de datos»* (user, 02-11). Tenía razón, y mi excusa era falsa —
    // escribí que el set extendido de Material no estaba en el classpath y sí lo está: la app ya usa
    // `AccessTimeFilled`, `Adjust`, `Bolt` y `Campaign`, que son todos de ahí. Nivel 2 del sistema de
    // iconos: concepto de UI → Material Rounded con `tint`. [UI-LIST-ITEM-001]
    PeekMetaRow(
        icon = icon,
        text = text,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * `2.0` reads as "2 m", `2.1` as "2.1 m" — a clearance is quoted the way the sign at the ramp
 * quotes it, and a trailing `.0` looks like a rounding nobody made.
 */
private fun formatClearance(metres: Double): String {
    val rounded = (metres * 10).toInt()
    return if (rounded % 10 == 0) "${rounded / 10}" else "${rounded / 10}.${rounded % 10}"
}
