package com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.AccessTimeFilled
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import com.rndeveloper.paparcar.domain.fuel.FuelAvailability
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation
import com.rndeveloper.paparcar.presentation.home.HomeIntent
import com.rndeveloper.paparcar.presentation.home.PeekStep
import com.rndeveloper.paparcar.presentation.home.sections.sheet.HomeSheetAction
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.FuelFollowMark
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheet
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetEyebrowTone
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetStepper
import com.rndeveloper.paparcar.presentation.home.model.isAttendedService
import com.rndeveloper.paparcar.presentation.home.model.pumpPriceLabel
import com.rndeveloper.paparcar.presentation.util.LocalPumpGallon
import com.rndeveloper.paparcar.presentation.util.distanceMeters
import com.rndeveloper.paparcar.presentation.util.distanceString
import com.rndeveloper.paparcar.presentation.util.subjectWhereLine
import com.rndeveloper.paparcar.presentation.util.fuelTypeLabel
import com.rndeveloper.paparcar.presentation.util.relativeTimeText
import com.rndeveloper.paparcar.ui.components.PapFooterButton
import com.rndeveloper.paparcar.ui.components.PapFooterButtonStyle
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.PapGap
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.common_directions
import paparcar.composeapp.generated.resources.fuel_peek_eyebrow
import paparcar.composeapp.generated.resources.fuel_peek_not_sold
import paparcar.composeapp.generated.resources.fuel_peek_follow
import paparcar.composeapp.generated.resources.fuel_peek_follow_promise
import paparcar.composeapp.generated.resources.fuel_peek_follow_promise_plus
import paparcar.composeapp.generated.resources.fuel_peek_following
import paparcar.composeapp.generated.resources.fuel_peek_step_next
import paparcar.composeapp.generated.resources.fuel_peek_step_prev
import paparcar.composeapp.generated.resources.home_fuel_attended_price
import paparcar.composeapp.generated.resources.home_fuel_live_prices
import paparcar.composeapp.generated.resources.home_fuel_reported_at
import paparcar.composeapp.generated.resources.location_fallback_station

// ═════════════════════════════════════════════════════════════════════════════
// FuelStationPeek — selected fuel station, from the map layer or the list.
// [FUEL-STATIONS-MAP-AND-PEEK-001]
// ═════════════════════════════════════════════════════════════════════════════

private const val NOT_SOLD_ALPHA = 0.6f

/** Aire entre la estrella y la frase que dice qué va a hacer con ella — el mismo 8 dp que separa
 *  los dos botones del pie. [FUEL-A-FOLLOW-MUST-SHOW-ITSELF-001] */
private const val FOLLOW_PROMISE_GAP_DP = 8

// `FAR_FOR_DRIVE_TIME_M` vivía aquí y murió con su caso — ver `DistanceAndTimeRow` en el `meta`.

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
    /** True when the feed guarantees near-real-time prices instead of stamping an hour (Germany's
     *  MTS-K) — the third freshness sentence. [FUEL-GERMANY-THE-FEED-ANSWERS-WHOLE-001] */
    pricesAreLive: Boolean = false,
    /** Neighbours in the "nearest first" order — the footer ‹ / ›.
     *  [FUEL-PEEK-STEPS-BETWEEN-STATIONS-001] */
    step: PeekStep,
    /**
     * Whether this forecourt is one the user follows.
     * [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
     *
     * ⛔ The star is FREE and stays free. Following is the app answering something you just told it;
     * what Plus buys is the app watching that station while you are not looking. Hiding the gesture
     * behind the paywall would leave it selling a thing the user has never seen work.
     */
    isFollowed: Boolean = false,
    /**
     * Whether this driver holds Plus — read ONLY to word what the star promises.
     * [FUEL-A-FOLLOW-MUST-SHOW-ITSELF-001]
     *
     * ⛔ It never hides or disables the star: following stays free. It decides between the two
     * honest sentences a follow can say — "we will tell you if the price changes" and "watching it
     * daily is Plus". Without it, a free user's follow did nothing, said nothing, and left the
     * paywall selling a feature they had never seen work.
     */
    isPlus: Boolean = false,
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
        lead = PapSheetLead.GenericIcon(PaparcarIcons.Fuel),
        // ⛔ La MISMA gramática que el resto de peeks (decisión del user, 30-10). El calificador es el
        // PRODUCTO por el que está cotizada — más útil que repetir «GASOLINERA», que el propio glifo
        // ya dice. [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
        eyebrow = "",
        title = title,
        titleSlot = peekSubjectTitleSlot(
            name = title,
            // ⛔ `onSurface`, no el verde del tile: el verde es ACCIÓN en esta app, y el nombre de una
            // gasolinera no lo es — teñirlo lo leería como un enlace. Una gasolinera no tiene
            // identidad propia como la tienen un coche (su vigilancia), una plaza (su frescura) o un
            // parking (su azul). [UI-COLOR-DOCTRINE-001]
            nameColor = MaterialTheme.colorScheme.onSurface,
            // ⛔ **Sin calificador, y no es un hueco olvidado.** Esta hoja ITEMIZA todos los
            // carburantes que la fuente nombró, así que no hay UN producto que cualifique a la
            // estación — ponerle uno sería elegir por ella. La estructura es la misma; lo que no hay
            // es dato verdadero que meter. No preguntado ≠ vacío.
            // [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
            qualifier = null,
            maxLines = PEEK_MODAL_TITLE_MAX_LINES,
        ),
        // La misma estrella que lleva su fila en la lista, en la misma relación con el nombre: lo
        // que distingue una gasolinera seguida no puede ser el estado de un botón que está al final
        // de la hoja. [FUEL-A-FOLLOW-MUST-SHOW-ITSELF-001]
        titleMark = if (isFollowed) {
            { FuelFollowMark() }
        } else {
            null
        },
        // La LÍNEA 2 de la gramática: **dónde · a cuánto**, en una sola línea y sin repetir la 1.
        //
        // ⛔ Aquí estaba el defecto que el user midió —*«las 2 últimas muestran dirección y a veces es
        // repetitivo»*—: con nombre, el título era el nombre y esto la calle; **sin** nombre el título
        // era la calle y esto la población, con la distancia aún en una fila aparte. Tres líneas para
        // dos preguntas. [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
        // La cabecera NOMBRA; el cuerpo DESARROLLA. El sitio y la distancia abren los hechos.
        // [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
        onDismiss = { onIntent(HomeIntent.SelectItem(null)) },
        // Step through the SAME action a row or a capsule fires — select, fly the camera, peek —
        // so the arrows cannot mean something a tap does not. [FUEL-PEEK-STEPS-BETWEEN-STATIONS-001]
        stepper = PapSheetStepper(
            prevContentDescription = stringResource(Res.string.fuel_peek_step_prev),
            nextContentDescription = stringResource(Res.string.fuel_peek_step_next),
            onPrev = step.prevId?.let { id -> { onAction(HomeSheetAction.SelectFuelStation(id)) } },
            onNext = step.nextId?.let { id -> { onAction(HomeSheetAction.SelectFuelStation(id)) } },
        ),
        meta = {
            PeekPlaceRow(
                place = subjectWhereLine(
                    name = title,
                    placeLine = station.address,
                    secondaryLine = station.town,
                    farText = null,
                ),
                tint = MaterialTheme.colorScheme.primary,
            )
            // ⛔ Sin fix no hay distancia que decir: null no es cero.
            // [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
            DistanceAndTimeRow(
                distanceM = distM,
                mode = travelMode,
                accentColor = MaterialTheme.colorScheme.primary,
            )
            station.schedule?.let { schedule ->
                // As the source words it — "24H", "L-D: 07:00-22:00"… never parsed, never judged.
                PeekMetaRow(
                    icon = Icons.Rounded.AccessTimeFilled,
                    text = schedule,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        content = {
            spokenFuels.forEach { fuel ->
                val price = station.prices[fuel]
                FuelPriceRow(
                    label = fuelTypeLabel(fuel),
                    priceLabel = price?.perUnit?.pumpPriceLabel(),
                    // Each pump's OWN hour: this is the surface that itemises fuels, so it is the
                    // one place where France's per-price hours actually differ from each other.
                    // [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
                    reportedAtMs = price?.reportedAtMs,
                    // And each pump's own MODE: this is the surface that itemises fuels, so it is
                    // also where one Italian station can show petrol served and diesel self.
                    // [FUEL-ITALY-A-PRICE-DEPENDS-ON-WHO-PUMPS-IT-001]
                    attended = price?.isAttendedService() == true,
                )
            }
            // Only where the SOURCE stamps the whole response — Spain does, France stamps each
            // price instead and the rows above already say so. ⛔ Quoting one hour for a French
            // response would be a claim no station made: measured, the prices inside a single
            // answer ran from 0,7 h to thirteen months old.
            if (reportedAt != null && spokenFuels.any { station.prices[it] != null }) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.home_fuel_reported_at, reportedAt),
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Germany stamps nothing at all — the honest line is the MTS-K guarantee itself.
            // Mutually exclusive with the two above by construction: a source that stamps a
            // response or a price never sets this. [FUEL-GERMANY-THE-FEED-ANSWERS-WHOLE-001]
            if (pricesAreLive && reportedAt == null && spokenFuels.any { station.prices[it] != null }) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.home_fuel_live_prices),
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            lat = station.latitude,
                            lon = station.longitude,
                            walking = false,
                        ),
                    )
                },
                style = PapFooterButtonStyle.Filled,
                modifier = Modifier.fillMaxWidth(),
            )
            // The star. Outlined, under the primary: it is not what you came here to do — you came
            // to see the price — but it is the gesture that turns this forecourt into a subject the
            // app can watch. The label states the CURRENT state, never the action, so the button
            // reads the same way as the switch it will end up feeding.
            Spacer(Modifier.height(8.dp))
            PapFooterButton(
                label = if (isFollowed) {
                    stringResource(Res.string.fuel_peek_following)
                } else {
                    stringResource(Res.string.fuel_peek_follow)
                },
                leadingIcon = if (isFollowed) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                onClick = { onAction(HomeSheetAction.ToggleFollowFuelStation(nearby.stationKey)) },
                style = PapFooterButtonStyle.Outlined,
                modifier = Modifier.fillMaxWidth(),
            )
            // ⛔ **Y lo que ese gesto PROMETE, en una línea.** Seguir sin más era un botón que
            // cambiaba de palabra y nada más: el aviso llega al día siguiente como muy pronto —hacen
            // falta dos lecturas de días distintos para que exista un movimiento que contar
            // ([EvaluateDailyPriceMoveUseCase])— y además la vigilancia diaria es Plus. Un free
            // seguía una gasolinera y no pasaba nada, nunca, sin una palabra. Eso no es gating
            // blando: es silencio.
            //
            // Solo cuando está seguida: en la otra mitad el botón ya dice lo único que hay que
            // saber («Seguir»), y adelantar la condición de Plus a quien no ha pedido nada es
            // vender antes de contestar.
            if (isFollowed) {
                Spacer(Modifier.height(FOLLOW_PROMISE_GAP_DP.dp))
                Text(
                    text = stringResource(
                        if (isPlus) {
                            Res.string.fuel_peek_follow_promise
                        } else {
                            Res.string.fuel_peek_follow_promise_plus
                        },
                    ),
                    style = PaparcarType.current.caption,
                    // ⛔ Sin Plus la frase **es la puerta del paywall**, así que se viste de enlace:
                    // verde de acción y pulsable. [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
                    // No es adorno — es la ÚNICA puerta que le queda a `PaywallReason.FUEL_ALERTS`
                    // desde que el interruptor de Ajustes dejó de ser de pago, y un motivo que
                    // nadie puede abrir es una promesa que el embudo no sostiene
                    // [PAYWALL-A-GATE-THAT-CHARGES-MUST-BE-SOLD-001]. Con Plus la misma línea es
                    // solo lo que va a pasar, y entonces no se pulsa ni se tiñe.
                    color = if (isPlus) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        PapColor.actionText
                    },
                    // Centrada bajo el botón que explica, que es ancho completo y con su etiqueta
                    // centrada: a la izquierda se leería como una nota de la hoja, no como la
                    // consecuencia de ese gesto.
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isPlus) {
                                Modifier
                            } else {
                                Modifier.clickable {
                                    onIntent(HomeIntent.OpenPaywall(PaywallReason.FUEL_ALERTS))
                                }
                            },
                        ),
                )
            }
        },
    )
}

/**
 * One pump: its name in the reading voice, its price — or the declared negative — trailing, and
 * under the price the age of that very figure.
 *
 * The age is an AGE and not the feed's wording on purpose: the two feeds word it differently
 * (`"08/09/2026 23:52:46"` vs `"2026-09-10T00:01:00+00:00"`) and neither is a sentence a driver
 * reads. `relativeTimeText` already says it in all nine locales, so this costs no new copy.
 * [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
 */
@Composable
private fun FuelPriceRow(
    label: String,
    priceLabel: String?,
    reportedAtMs: Long?,
    attended: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = PaparcarType.current.body,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (priceLabel != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = priceLabel,
                    style = PaparcarType.current.subtitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Who pumps it, then how old it is — in that order, because the mode changes WHICH
                // price this is and the age only changes how much to trust it. Both are captions on
                // purpose: a figure qualified in two voices would read as two figures.
                if (attended) {
                    Text(
                        text = stringResource(Res.string.home_fuel_attended_price),
                        style = PaparcarType.current.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Only when the source dated THIS price. OSM has none, and an undated price simply
                // says nothing rather than borrowing a neighbour's hour.
                if (reportedAtMs != null) {
                    Text(
                        text = relativeTimeText(reportedAtMs),
                        style = PaparcarType.current.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Text(
                text = stringResource(Res.string.fuel_peek_not_sold),
                style = PaparcarType.current.caption,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = NOT_SOLD_ALPHA),
            )
        }
    }
}
