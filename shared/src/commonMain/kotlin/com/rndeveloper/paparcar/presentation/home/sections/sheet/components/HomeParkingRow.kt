@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.displayName
import com.rndeveloper.paparcar.domain.model.monitoringStatus
import com.rndeveloper.paparcar.presentation.home.VehicleCard
import com.rndeveloper.paparcar.presentation.util.PlaceLine
import com.rndeveloper.paparcar.presentation.util.distanceMeters
import com.rndeveloper.paparcar.presentation.util.distanceString
import com.rndeveloper.paparcar.presentation.util.placeLineText
import com.rndeveloper.paparcar.presentation.util.relativeTimeText
import com.rndeveloper.paparcar.ui.components.DrivingRadarHalo
import com.rndeveloper.paparcar.ui.components.DrivingRouteGlyph
import com.rndeveloper.paparcar.ui.components.PapCtaPill
import com.rndeveloper.paparcar.ui.components.UnmarkedParkingIcon
import com.rndeveloper.paparcar.ui.components.VehicleGlyph
import com.rndeveloper.paparcar.ui.components.VehicleIdentityHeader
import com.rndeveloper.paparcar.ui.components.VehicleWatchLeadingIcon
import com.rndeveloper.paparcar.ui.components.rememberDrivingStatePulse
import com.rndeveloper.paparcar.ui.theme.vehicleChassisBorder
import com.rndeveloper.paparcar.ui.theme.vehicleIdentityColor
import com.rndeveloper.paparcar.ui.theme.watch
import com.rndeveloper.paparcar.ui.theme.PapBorders
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.PapColor
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_peek_parked_label
import paparcar.composeapp.generated.resources.home_vehicle_card_parked_at
import paparcar.composeapp.generated.resources.home_vehicle_card_parked_meta
import paparcar.composeapp.generated.resources.home_vehicle_chip_mark_parking
import paparcar.composeapp.generated.resources.home_det_awaiting_sub
import paparcar.composeapp.generated.resources.home_det_driving_stop_cta
import paparcar.composeapp.generated.resources.home_det_monitoring
import paparcar.composeapp.generated.resources.home_vehicle_chip_status_candidate
import paparcar.composeapp.generated.resources.home_vehicle_chip_unmarked
import paparcar.composeapp.generated.resources.home_vehicle_fallback_name
import paparcar.composeapp.generated.resources.location_approximate_near

/**
 * Compact vehicle chip in the Home vehicles LazyRow (2+ vehicles). A vertical card: an identity row
 * (car glyph + **watch glyph + name in the identity colour** — green = active detection, blue = BT,
 * grey = unwatched) over a parking row. The parking row is the actionable fact: **parked →
 * location icon + address** (max 2 lines), **not marked → the "not marked" glyph**, **driving → a
 * self-drawing route glyph + the pulsing state words AND where the car is right now**. No method
 * label, no corner badge. The border carries the same identity colour, dimmed. Tapping transforms
 * the sheet to the vehicle's state.
 * [HOME-VEH-REFINE-001] [HOME-CARDS-001] [UI-COLOR-DOCTRINE-001] [UI-CHIP-ROUTE-GLYPH-001]
 * [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]
 */
@Composable
internal fun HomeVehicleChip(
    card: VehicleCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDriving: Boolean = false,
    // Candidate phase: the trip stopped and the user is walking away — a distinct "parking…" hint.
    isCandidate: Boolean = false,
    /** Where the trip is right now, resolved by the slice from the car's OWN geocode. Only read
     *  while [isDriving]; null until the first geocode lands. [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001] */
    drivingPlace: PlaceLine? = null,
    /** [DET-STOP-BUTTON-001] The way OUT of a trip the user doesn't want followed. It lives here
     *  because this chip is now the ONE surface that claims to be following them — the detection
     *  story stood down. Only read while [isDriving].
     *  [HOME-A-DRIVE-IS-ONE-NEWS-AND-THE-CARD-CARRIES-THE-STOP-001] */
    onStopTrip: (() -> Unit)? = null,
) {
    val vehicle = card.vehicle
    val session = card.session
    val cs = MaterialTheme.colorScheme
    val watch = vehicle.monitoringStatus().watch()
    // The vehicle's ONE colour: its watch method (green = active detection, blue = BT, grey = off).
    // The state below never re-colours it. [UI-COLOR-DOCTRINE-001]
    val accent = vehicleIdentityColor(watch)
    val vehicleName = vehicle.displayName(fallback = stringResource(Res.string.home_vehicle_fallback_name))

    Surface(
        onClick = onClick,
        // Adaptive width so the name breathes without truncating in the horizontal strip.
        modifier = modifier.widthIn(min = CHIP_MIN_WIDTH_DP.dp, max = CHIP_MAX_WIDTH_DP.dp),
        shape = PapShapes.card,
        // A trip in motion thickens the frame to the full identity colour — more energy, same hue.
        border = BorderStroke(
            if (isDriving) DRIVING_BORDER_DP.dp else BORDER_DP.dp,
            if (isDriving) accent else vehicleChassisBorder(watch),
        ),
        color = cs.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(CHIP_PADDING_DP.dp)) {
            // Identity row — glyph, then the status icon RIGHT BEFORE the name (never a corner badge,
            // which collides with the illustrative car glyph). [HOME-VEH-REFINE-001]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CHIP_TOP_GAP_DP.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isDriving) DrivingRadarHalo(diameter = ICON_BOX_DP.dp, color = accent)
                    VehicleGlyph(
                        carbody = vehicle.carbodyType,
                        size = vehicle.sizeCategory,
                        glyphSize = ICON_BOX_DP.dp,
                        color = vehicle.color,
                    )
                }
                // The watch glyph carries the identity colour; the name stays onSurface — tinting
                // both is over-information. [UI-COLOR-DOCTRINE-001]
                VehicleWatchLeadingIcon(watch = watch)
                Text(
                    vehicleName,
                    style = PaparcarType.current.rowName,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(CHIP_FOOT_GAP_DP.dp))
            PapDivider()
            Spacer(Modifier.height(CHIP_FOOT_GAP_DP.dp))

            // Parking row — the actionable fact.
            Row(
                modifier = Modifier.heightIn(min = FOOT_MIN_HEIGHT_DP.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FOOT_GAP_DP.dp),
            ) {
                when {
                    // A trip keeps the icon+text anatomy of the other two states, but the icon is a
                    // route drawing itself — never the location pin, which would claim a place the
                    // trip hasn't reached. State machine = neutral text; its liveness is the
                    // breathing pulse, not a hue. [UI-CHIP-ROUTE-GLYPH-001] [UI-COLOR-DOCTRINE-001]
                    isDriving -> {
                        DrivingRouteGlyph(color = accent, glyphSize = FOOT_ICON_DP.dp)
                        val phase = stringResource(
                            if (isCandidate) Res.string.home_vehicle_chip_status_candidate
                            else Res.string.home_det_monitoring,
                        )
                        // Same shape as the parked line ("place · time"), so the foot keeps ONE
                        // anatomy across its three branches. The phase word breathes; the place
                        // does NOT — a fact does not pulse — which is why the place carries its own
                        // opaque span over the pulsing base colour. Weight can't do that job here:
                        // it belongs to the role, not to the call site. [UI-COLOR-DOCTRINE-001]
                        // [UI-TYPE-TWO-VOICES-ONE-ROW-001] [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]
                        val place = placeLineText(drivingPlace)
                        Text(
                            text = buildAnnotatedString {
                                append(phase)
                                if (place != null) {
                                    withStyle(SpanStyle(color = cs.onSurface)) {
                                        append(FOOT_SEPARATOR)
                                        append(place)
                                    }
                                }
                            },
                            // Same voice as the other two branches — the foot is one line of fact,
                            // whatever the state. [UI-TYPE-TWO-VOICES-ONE-ROW-001]
                            style = PaparcarType.current.caption,
                            color = cs.onSurface.copy(alpha = rememberDrivingStatePulse()),
                            maxLines = FOOT_MAX_LINES,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    session != null -> {
                        Icon(
                            // An AREA wears the zone glyph, never the pin — the pin claims a point
                            // the session refused to claim. [UI-APPROXIMATE-ZONE-IN-HISTORY-001]
                            imageVector = if (session.isApproximate) Icons.Rounded.Adjust else Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(FOOT_ICON_DP.dp),
                        )
                        Text(
                            text = parkedAddressLine(session),
                            // An address is a phrase you read → LECTURA (caption), same voice as the
                            // single-vehicle card's footer. CIFRA is for repeating data
                            // tokens, not prose — keeps 1-vehicle and 2+-vehicle Home consistent.
                            // [CARD-ONE-BADGE-001]
                            style = PaparcarType.current.caption,
                            color = cs.onSurface,
                            maxLines = FOOT_MAX_LINES,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    else -> {
                        UnmarkedParkingIcon(tint = accent)
                        Text(
                            text = stringResource(Res.string.home_vehicle_chip_unmarked),
                            style = PaparcarType.current.caption,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }

            // «Parar» — stacked full-width because the compact foot has no inline room, and only on
            // the chip that IS the trip. Tonal in the vehicle's identity colour: stopping is an act
            // on this car's watch, not a brand action and not an error. [DET-STOP-BUTTON-001]
            // [HOME-A-DRIVE-IS-ONE-NEWS-AND-THE-CARD-CARRIES-THE-STOP-001] [UI-COLOR-DOCTRINE-001]
            if (isDriving && onStopTrip != null) {
                Spacer(Modifier.height(CHIP_FOOT_GAP_DP.dp))
                PapCtaPill(
                    label = stringResource(Res.string.home_det_driving_stop_cta),
                    container = accent.copy(alpha = STOP_PILL_TINT_ALPHA),
                    content = accent,
                    onClick = onStopTrip,
                    modifier = Modifier.fillMaxWidth(),
                    fillWidth = true,
                    height = STOP_PILL_HEIGHT_DP.dp,
                )
            }
        }
    }
}

/**
 * Full-width single-vehicle card (exactly one registered vehicle). Roomier identity — big glyph,
 * name, a **text status pin + size chip** — over a footer that carries the **parked address**
 * (location icon + "Parked at …" + relative time / distance + chevron), the **self-drawing route
 * glyph over where the trip is right now** while a trip runs, or a mark-parking CTA when the
 * vehicle has no active session.
 * [HOME-VEH-REFINE-001] [HOME-CARDS-001] [UI-CHIP-ROUTE-GLYPH-001] [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]
 */
@Composable
internal fun HomeVehicleCard(
    card: VehicleCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDriving: Boolean = false,
    isCandidate: Boolean = false,
    // User position, to show the distance to the parked car ("2 h ago · 180 m away").
    userLocation: Pair<Double, Double>? = null,
    /** Where the trip is right now — same subject and same resolver as the chip's and the peek's.
     *  Only read while [isDriving]. [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001] */
    drivingPlace: PlaceLine? = null,
    /** [DET-STOP-BUTTON-001] Same stop as the chip's: the way out of a followed trip, on the one
     *  surface that claims to be following it. Only read while [isDriving].
     *  [HOME-A-DRIVE-IS-ONE-NEWS-AND-THE-CARD-CARRIES-THE-STOP-001] */
    onStopTrip: (() -> Unit)? = null,
) {
    val vehicle = card.vehicle
    val session = card.session
    val cs = MaterialTheme.colorScheme
    val watch = vehicle.monitoringStatus().watch()
    // Identity colour = watch method; the state below stays neutral. [UI-COLOR-DOCTRINE-001]
    val accent = vehicleIdentityColor(watch)

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = PapShapes.card,
        // La identidad la lleva el GLIFO de vigilancia, no el marco. Un borde teñido alrededor de la
        // card entera era otro verde compitiendo con el CTA, y encima repetia lo que el glifo ya
        // dice. Conduciendo SI se tine: ahi el marco no marca identidad, marca que esto esta pasando
        // AHORA. [UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001] [UI-COLOR-DOCTRINE-001]
        border = BorderStroke(
            if (isDriving) DRIVING_BORDER_DP.dp else BORDER_DP.dp,
            if (isDriving) accent else cs.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA),
        ),
        color = cs.surfaceContainerHigh,
    ) {
        // ⛔ ONE row, not a header + divider + foot row. The card used to stack the identity block
        // over a second full-width row with its own 44dp glyph box, and with a single vehicle that is
        // ~230dp of a sheet whose whole point is now that everything is reachable without scrolling
        // (user, 10-09). What it says is unchanged: the same name, the same status line, the same
        // tap target — [VehicleIdentityHeader] already had the `statusLine` and `trailing` slots this
        // needed, so the discreet version is the component used as designed, not a second anatomy.
        // [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001] [UI-LIST-ITEM-001]
        // LA BARRA DE ACENTO, ahora aqui: con la fila calmada de vigilancia apartada, esta card es
        // la que cuenta el estado del coche, y lo cuenta como lo contaba aquella. Una franja del
        // color de SU metodo de vigilancia (verde asistido, azul Bluetooth, gris sin vigilar): el
        // estado se ve sin leer y sin tenir una palabra.
        // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001] [UI-COLOR-DOCTRINE-001]
        //
        // Una BARRA, no el borde entero de la card: el borde tenido se retiro en
        // UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001 por competir con el CTA, y con razon. Una
        // franja de 4 dp en el canto no compite con nada, y es la misma tinta que la superficie de
        // deteccion usa para decir lo mismo.
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(ACCENT_BAR_DP.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(modifier = Modifier.weight(1f)) {
                VehicleIdentityHeader(
                    vehicle = vehicle,
                    isDriving = isDriving,
                    // Home is glanceable — size lives on the Vehicles ficha, not here. [CARD-META-POLISH-001]
                    showSize = false,
                    // ⛔ Arriba y abajo el MISMO valor, escrito una vez. Estaban los dos a 12 dp y aun así la
                    // card se leia hundida: con la fila alineada arriba (`alignsWithFirstLine`), el tile
                    // arranca pegado al borde mientras la ultima linea de texto trae su descenso debajo, asi
                    // que 12 arriba se ven menos que 12 abajo. Subir el par empata lo que se VE, que es lo
                    // que se estaba mirando. [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
                    modifier = Modifier.padding(start = 14.dp, end = 12.dp, top = CARD_V_PAD_DP.dp),
                    trailing = {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(CHEVRON_DP.dp),
                        )
                    },
                )

                Spacer(Modifier.height(CHIP_FOOT_GAP_DP.dp))
                // [UX-PARKED-STATE-001] Restore the divider between identity and status, matching the
                // Home vehicle chip style.
                PapDivider()
                Spacer(Modifier.height(CHIP_FOOT_GAP_DP.dp))

                Row(
                    modifier = Modifier
                        .padding(start = 14.dp, end = 12.dp, bottom = CARD_V_PAD_DP.dp)
                        .heightIn(min = FOOT_MIN_HEIGHT_DP.dp)
                        .fillMaxWidth(),
                    verticalAlignment = if (session != null) Alignment.Top else Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // The glyph keeps its meaning and its identity colour; what it loses is the
                    // 44dp tinted box it used to sit in. Same priority as the text beside it: a
                    // running trip outranks the parked session. [UI-CHIP-ROUTE-GLYPH-001]
                    when {
                        isDriving -> DrivingRouteGlyph(
                            color = accent,
                            glyphSize = CARD_FOOT_ICON_DP.dp,
                        )
                        session != null -> Icon(
                            // Same rule as the chip: an approximate session shows the zone glyph,
                            // not the pin. [UI-APPROXIMATE-ZONE-IN-HISTORY-001]
                            imageVector = if (session.isApproximate) Icons.Rounded.Adjust else Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(CARD_FOOT_ICON_DP.dp),
                        )
                        else -> UnmarkedParkingIcon(tint = accent)
                    }
                    when {
                        // Neutral state words with the driving breath. [UI-COLOR-DOCTRINE-001]
                        // WHERE the trip is rides on the same line now, after the state, instead of
                        // costing a second one. [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]
                        isDriving -> {
                            // The texts share an inner row that claims the leftover width, so the
                            // stop pill sits at the trailing edge and a long street can never push
                            // it off. [HOME-A-DRIVE-IS-ONE-NEWS-AND-THE-CARD-CARRIES-THE-STOP-001]
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = stringResource(
                                        if (isCandidate) Res.string.home_vehicle_chip_status_candidate
                                        else Res.string.home_det_monitoring,
                                    ),
                                    style = PaparcarType.current.caption,
                                    color = cs.onSurface.copy(alpha = rememberDrivingStatePulse()),
                                    maxLines = 1,
                                )
                                // No geocode yet → no place at all. A line that says "unknown address"
                                // spends its width saying nothing.
                                placeLineText(drivingPlace)?.let { place ->
                                    Text(
                                        text = "· $place",
                                        style = PaparcarType.current.caption,
                                        color = cs.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                            }
                            // «Parar» INLINE: this foot has the width the chip's lacks, and a
                            // four-letter CTA does not earn a full-width band (measured for the
                            // story row it replaces). [UI-DET-STOP-BELONGS-BESIDE-THE-STORY-001]
                            // [DET-STOP-BUTTON-001]
                            if (onStopTrip != null) {
                                PapCtaPill(
                                    label = stringResource(Res.string.home_det_driving_stop_cta),
                                    container = accent.copy(alpha = STOP_PILL_TINT_ALPHA),
                                    content = accent,
                                    onClick = onStopTrip,
                                    height = STOP_PILL_HEIGHT_DP.dp,
                                )
                            }
                        }
                        session != null -> {
                            // UNA frase: «Calle X · 6 min · 3 m». (user, 10-09) — dos líneas para
                            // decir esto costaban altura en el bloque que más se mira.
                            //
                            // ⛔ Pero con `maxLines = 2`, no una. Estas dos líneas ya se probaron
                            // fusionadas y se volvieron a separar porque la meta se quedaba el ancho
                            // y la dirección se cortaba en «Route d…» — justo el dato por el que uno
                            // mira la card. Envolver cuando no cabe ahorra la línea siempre que
                            // sobre sitio, y no parte el nombre de la calle cuando no.
                            //
                            // El tono se mantiene DENTRO de la frase: la calle en `onSurface`, la
                            // meta en `onSurfaceVariant`. Concatenar un String plano habría igualado
                            // las dos, que es perder la jerarquía para ahorrar una línea.
                            // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
                            val title = parkedTitle(session)
                            val meta = parkedMeta(session, userLocation)
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = cs.onSurface)) { append(title) }
                                    withStyle(SpanStyle(color = cs.onSurfaceVariant)) {
                                        append(" $META_SEPARATOR $meta")
                                    }
                                },
                                style = PaparcarType.current.caption,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // ⛔ La PREGUNTA, no la etiqueta. Decia «Mark parking» en verde, que es el
                        // nombre del boton puesto donde va el estado; la fila de arranque en frio
                        // decia lo mismo una card mas arriba. Ahora la card hace la pregunta y la
                        // responde con su propio boton, y aquella fila se calla.
                        // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
                        else -> Text(
                            text = stringResource(Res.string.home_det_awaiting_sub),
                            style = PaparcarType.current.caption,
                            color = cs.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        // ⛔ SIN boton. Lo tuvo un rato y se quito al mirarlo (user, 10-09): su `onClick` era la
        // MISMA lambda que la de la card, o sea chrome que repetia el gesto de su propio contenedor.
        //
        // La fila que esta card sustituye si necesitaba boton, porque era un MENSAJE y a los mensajes
        // no se les toca. Una card de coche es un objeto que se toca, y lo dice con su chevron.
        //
        // Y el momento en que un boton explicito si se gana el sitio —el primer aparcamiento de
        // alguien que acaba de instalar— lo cubre el checklist con su paso `MARK_PARKING` y su CTA.
        // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
        // [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001]
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Resolved address / place name for the parked session, or null before enrichment. */
private fun parkedName(session: UserParking): String? =
    session.placeInfo?.name?.takeIf { it.isNotBlank() }
        ?: session.address?.displayLine?.takeIf { it.isNotBlank() }

/**
 * [UI-APPROXIMATE-ZONE-IN-HISTORY-001] An approximate session (an AREA, not a point) says so at
 * card level with the same word the app already uses for borrowed locations: "Near X". The full
 * explanation (radius, cause, remedy) stays in the peek — a glanceable card only needs to stop
 * CLAIMING exactness it does not have.
 */
@Composable
private fun parkedPlaceLine(session: UserParking): String? {
    val name = parkedName(session) ?: return null
    return if (session.isApproximate) stringResource(Res.string.location_approximate_near, name) else name
}

/** Compact chip parking line: "address · 2 h ago" (max 2 lines), falling back to just the time. */
@Composable
private fun parkedAddressLine(session: UserParking): String {
    val time = relativeTimeText(session.location.timestamp)
    val name = parkedPlaceLine(session)
    return if (name != null) "$name$FOOT_SEPARATOR$time"
    else "${stringResource(Res.string.home_peek_parked_label)}$FOOT_SEPARATOR$time"
}

/** Single-card footer title: "Parked at {address}" — or "Near {address}" for an approximate
 *  session, or a plain "Parked" before enrichment. */
@Composable
private fun parkedTitle(session: UserParking): String {
    val name = parkedName(session)
    return when {
        name != null && session.isApproximate ->
            stringResource(Res.string.location_approximate_near, name)
        name != null -> stringResource(Res.string.home_vehicle_card_parked_at, name)
        else -> stringResource(Res.string.home_peek_parked_label)
    }
}

/** Single-card footer subline: "2 h ago · 180 m away", or just the time when position is unknown. */
@Composable
private fun parkedMeta(session: UserParking, userLocation: Pair<Double, Double>?): String {
    val time = relativeTimeText(session.location.timestamp)
    val distance = userLocation?.let { (uLat, uLon) ->
        distanceString(distanceMeters(uLat, uLon, session.location.latitude, session.location.longitude))
    }
    return if (distance != null) stringResource(Res.string.home_vehicle_card_parked_meta, time, distance)
    else time
}

/** El punto medio que ya separa hora y distancia dentro de `home_vehicle_card_parked_meta`. */
private const val META_SEPARATOR = "·"

// Adaptive width bounds — card grows to fit "Toyota Corolla" without truncating, capped so a long
// name never dominates the LazyRow. [HOME-CARDS-001]
/** Aire vertical de la card, arriba y abajo. UN valor, para que no puedan divergir. */
private const val CARD_V_PAD_DP = 14

/** La franja de estado del canto: mismo grosor que la de la superficie de deteccion. */
private const val ACCENT_BAR_DP = 4

private const val CHIP_MIN_WIDTH_DP = 150
private const val CHIP_MAX_WIDTH_DP = 200
private const val CHIP_PADDING_DP = 10
private const val CHIP_TOP_GAP_DP = 6
private const val CHIP_FOOT_GAP_DP = 8
private const val ICON_BOX_DP = 32
private const val FOOT_MIN_HEIGHT_DP = 34
private const val FOOT_GAP_DP = 7
private const val FOOT_ICON_DP = 15
// The foot's three branches share one line shape ("fact · detail") and one wrap budget, so a chip
// in a trip and a chip parked next to it stay the same object. [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]
private const val FOOT_SEPARATOR = " · "
private const val FOOT_MAX_LINES = 2
// Full-width single-vehicle card. [HOME-CARDS-001]
private const val CHEVRON_DP = 22
private const val CARD_FOOT_ICON_BOX_DP = 34
private const val CARD_FOOT_ICON_CORNER_DP = 10
private const val CARD_FOOT_ICON_DP = 18
private const val FOOT_ICON_BOX_ALPHA = 0.15f
private const val BORDER_DP = 1
private const val DRIVING_BORDER_DP = 1.5f // thicker live-blue border while driving [CHIP-DRIVING-001]
// The stop pill: shorter than the story rows' 40dp CTA so the foot keeps its height, tonal in the
// identity colour — same container language as the detection surface's identity tone.
// [DET-STOP-BUTTON-001] [HOME-A-DRIVE-IS-ONE-NEWS-AND-THE-CARD-CARRIES-THE-STOP-001]
private const val STOP_PILL_HEIGHT_DP = 34
private const val STOP_PILL_TINT_ALPHA = 0.16f
