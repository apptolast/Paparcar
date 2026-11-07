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
import androidx.compose.material.icons.rounded.LocationOn
import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import paparcar.composeapp.generated.resources.home_det_ask_title
import paparcar.composeapp.generated.resources.home_det_ask_title_vehicle
import paparcar.composeapp.generated.resources.home_det_ask_sub_at
import paparcar.composeapp.generated.resources.home_det_ask_sub_at_place
import paparcar.composeapp.generated.resources.home_det_ask_cta_yes
import paparcar.composeapp.generated.resources.home_det_ask_cta_no
import androidx.compose.material.icons.rounded.AccessTimeFilled
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.ui.components.PapNavChevron
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
import com.rndeveloper.paparcar.presentation.util.formatClockTime
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
import com.rndeveloper.paparcar.ui.theme.PapControlHeight
import com.rndeveloper.paparcar.ui.theme.PapGap
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.home_trip_park_now
import paparcar.composeapp.generated.resources.home_trip_continue
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_peek_parked_label
import paparcar.composeapp.generated.resources.home_vehicle_card_parked_at
import paparcar.composeapp.generated.resources.home_vehicle_chip_mark_parking
import paparcar.composeapp.generated.resources.home_det_awaiting_sub
import paparcar.composeapp.generated.resources.home_det_driving_stop_cta
import paparcar.composeapp.generated.resources.home_trip_park_compact
import paparcar.composeapp.generated.resources.home_trip_continue_compact
import paparcar.composeapp.generated.resources.home_trip_wrong_car_compact
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
    onParkNow: (() -> Unit)? = null,
    onKeepDriving: (() -> Unit)? = null,
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
            //
            // Parked is the one state with TWO facts (where, and since when), so it is the one
            // state that gets two lines, each with its own icon: pin over clock. Every other
            // branch has a single fact and stays a single row — a second line there would be
            // empty height. [UI-A-PARKED-CARD-SAYS-WHERE-AND-WHEN-IN-TWO-LINES-001]
            if (!isDriving && session != null) {
                ParkedMetaLines(
                    session = session,
                    // The narrow chip spends its width on the PLACE: no "Parked at" prefix, which
                    // the pin above already says. [UI-A-PARKED-CARD-SAYS-WHERE-AND-WHEN-IN-TWO-LINES-001]
                    placeText = parkedPlaceLine(session) ?: stringResource(Res.string.home_peek_parked_label),
                    metaText = relativeTimeText(session.location.timestamp),
                    accent = accent,
                    iconSize = FOOT_ICON_DP.dp,
                    gap = FOOT_GAP_DP.dp,
                    maxPlaceLines = FOOT_MAX_LINES,
                )
            } else Row(
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
            if (isDriving) {
                Spacer(Modifier.height(CHIP_FOOT_GAP_DP.dp))
                TripActions(accent, onParkNow, onKeepDriving.takeIf { isCandidate }, onStopTrip, compact = true)
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
    onParkNow: (() -> Unit)? = null,
    onKeepDriving: (() -> Unit)? = null,
    /**
     * El ESTADO de este coche, pintado dentro de la card. [HOME-AN-OPEN-QUESTION-IS-THE-CARS-NEWS-001]
     *
     * Regla del user (22-09): **la card de un coche gestiona todos los estados de ese coche**, y el
     * badge suelto sólo queda para cuando no hay ningún coche registrado — porque entonces no hay
     * card que gestione nada. Así que aquí caben la pregunta con plazo, el aviso sin plazo, la
     * vigilancia frágil o interrumpida, la detección apagada y el permiso que falta.
     *
     * ⛔ Es un HUECO, no diez callbacks. La card no sabe nada de detección: quien lo rellena es la
     * hoja, que ya tiene los intents a mano, y lo que mete dentro es la MISMA fila que pintaría el
     * badge (`HomeDetectionSurface(embedded = true)`) — el mismo objeto, no una copia con el mismo
     * aspecto. Dos renderizadores del mismo estado es exactamente cómo se llega a que la hoja y la
     * card digan cosas distintas del mismo coche.
     *
     * Sustituye a la línea de estado —«En ruta», «Aparcado», «sin marcar»—: si se sumara,
     * volveríamos a la card afirmando «En ruta» tres milímetros debajo de una pregunta que duda de
     * eso mismo, que es el defecto de la issue #129.
     */
    status: (@Composable () -> Unit)? = null,
) {
    val vehicle = card.vehicle
    val session = card.session
    val cs = MaterialTheme.colorScheme
    val watch = vehicle.monitoringStatus().watch()
    // Identity colour = watch method; the state below stays neutral. [UI-COLOR-DOCTRINE-001]
    val accent = vehicleIdentityColor(watch)
    /** Hay cápsula de stop debajo del pie — y entonces el aire inferior es SUYO, no del pie.
     *  [UI-A-PILL-IS-FOR-A-LABEL-NOT-A-SENTENCE-002] */
    // ⛔⛔ La cápsula se queda TAMBIÉN con la pregunta abierta, y esto estuvo mal un rato: se quitó
    // razonando que «Aún no» ya significaba «sigo conduciendo». No significa lo mismo, y no es una
    // cuestión de matiz — son dos mecánicas distintas:
    //
    //   · «Aún no»             → ACTION_PARKING_DENIED → `onUserDeniedParking()`: descarta el aviso
    //                            y MANTIENE la sesión viva con su autorización. Sigue vigilando.
    //   · «No voy en mi coche» → ACTION_USER_STOP → `handleUserStop()`: cierra el viaje sin guardar
    //                            plaza, suelta el confirm retenido y abre el PERIODO DE SILENCIO.
    //
    // Y la diferencia muerde justo en el caso que la trajo (user, 22-09): un viaje falso positivo en
    // casa. Ahí «Aún no» es mentira —no estás conduciendo— y encima mantiene vivo el viaje fantasma;
    // la única salida honesta es la que además calla los avisos automáticos un rato. Quitarla habría
    // dejado al usuario sin esa salida justo cuando la necesita.
    // [HOME-AN-OPEN-QUESTION-IS-THE-CARS-NEWS-001]
    val stopStacksBelow = isDriving && onStopTrip != null

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
        // ⛔⛔ SIN BARRA DE ACENTO — retirada el 23-09, y el motivo no es estético.
        // [UI-AN-ACCENT-RAIL-MEANS-ONE-THING-001]
        //
        // La tuvo, y decía el MÉTODO de vigilancia (verde asistido, azul Bluetooth, gris sin
        // vigilar). El mismo raíl de 4 dp en el canto de un badge dice otra cosa completamente:
        // la GRAVEDAD del aviso (ámbar advierte, rojo urge). Dos significados para el mismo
        // dispositivo, y desde [HOME-AN-OPEN-QUESTION-IS-THE-CARS-NEWS-001] se ven a la vez —
        // badge arriba, card debajo, dos franjas verticales de colores distintos que no significan
        // lo mismo. El user lo leyó como gravedad y preguntó por qué su coche en ruta no llevaba el
        // tono del viaje: exactamente el malentendido que produce un símbolo con dos sentidos.
        //
        // Se quita de la CARD y no del badge, por tres razones y en este orden:
        //  1. Teñir el raíl por estado —«en ruta en azul de ruta»— rompe dos reglas escritas: el
        //     estado se ESCRIBE y se anima, nunca se tiñe, y tu propio viaje lleva la identidad de
        //     vigilancia de SU coche, porque el azul de movimiento es el de los viajes AJENOS sobre
        //     el mapa. [UI-COLOR-DOCTRINE-001] [UI-A-TRACKED-TRIP-WEARS-ITS-WATCH-001]
        //  2. En la card no informaba de nada nuevo: la identidad ya la llevan el glifo del coche,
        //     el icono de vigilancia junto al nombre y —conduciendo— el marco entero. Era la cuarta
        //     copia del mismo dato.
        //  3. En el badge sí es el único portador de su significado, y ahí se queda. Resultado: un
        //     raíl vertical en Home quiere decir SIEMPRE «esto es un aviso, y así de serio es».
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Column(modifier = Modifier.weight(1f)) {
                // ⛔ Con NOTICIA, la noticia OCUPA la fila del nombre — no se apila debajo de él.
                // [HOME-A-CARS-NEWS-RIDES-ITS-OWN-CARD-001]
                //
                // Petición del user el 08-10, viendo la card en mano: *«¿para qué poner Second car
                // si ya tenemos la pregunta? la pregunta y la info deberían ir ahí»*. Y midiendo le
                // daba la razón dos veces:
                //  · la card salía a **246 dp**, el 39 % de la hoja visible, porque apilaba
                //    identidad + divisor + pregunta + dos cápsulas;
                //  · y el coche quedaba nombrado DOS VECES y de dos maneras — «Second car» (su
                //    `name`) arriba y «your Toyota Corolla» (`displayName()`) en la pregunta. En
                //    superficies separadas se leían como dos cosas; pegados, como una contradicción.
                //
                // La pregunta ya nombra al coche, así que la fila de identidad no añadía dato: es la
                // misma razón por la que este fichero ya retiró el raíl de color de la card — *«en
                // la card no informaba de nada nuevo»*.
                if (status == null) {
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
                    modifier = Modifier.padding(
                        start = CARD_PAD_START_DP.dp,
                        end = CARD_PAD_END_DP.dp,
                        top = CARD_V_PAD_DP.dp,
                    ),
                    // El chevron canónico de fila, no uno propio: era un `ChevronRight` de 22 dp,
                    // un tercer tamaño para el mismo hecho. [UI-A-CHEVRON-MUST-SIT-ON-THE-CARD-RAIL-001]
                    trailing = { PapNavChevron() },
                )

                Spacer(Modifier.height(CHIP_FOOT_GAP_DP.dp))
                // [UX-PARKED-STATE-001] Restore the divider between identity and status, matching the
                // Home vehicle chip style.
                PapDivider()
                Spacer(Modifier.height(CHIP_FOOT_GAP_DP.dp))
                }

                // Parked = two facts, two lines, one icon each: pin over clock. Same skeleton as
                // the compact chip's and as the peek's meta rows, so the same car reads the same
                // way whichever surface shows it.
                // [UI-A-PARKED-CARD-SAYS-WHERE-AND-WHEN-IN-TWO-LINES-001]
                if (status != null) {
                    // El glifo del coche lo pone la PROPIA fila de la noticia, en su slot `leading`
                    // — no un `Row` por fuera. Envolverlo aquí dejaba la baldosa centrada contra el
                    // bloque entero, o sea a la altura de los botones, mientras el título corría por
                    // encima a todo el ancho: dos columnas que no se conocen.
                    // [UI-LIST-ITEM-001] [HOME-A-CARS-NEWS-RIDES-ITS-OWN-CARD-001]
                    Box(
                        modifier = Modifier.padding(
                            bottom = if (stopStacksBelow) 0.dp else CARD_V_PAD_DP.dp,
                        ),
                    ) { status() }
                } else if (!isDriving && session != null) {
                    ParkedMetaLines(
                        session = session,
                        // The roomy card keeps the state IN WORDS ("Parked at X" / "Near X"): it
                        // has the width for it, and the state is written, never tinted.
                        // [UI-COLOR-DOCTRINE-001]
                        placeText = parkedTitle(session),
                        // Los metros van CON el lugar — es lo que contestan — y la hora se queda
                        // sola detrás del reloj. [UI-A-PARKED-CARD-SAYS-WHERE-AND-WHEN-IN-TWO-LINES-001]
                        placeSuffix = parkedDistance(session, userLocation),
                        metaText = relativeTimeText(session.location.timestamp),
                        accent = accent,
                        iconSize = CARD_FOOT_ICON_DP.dp,
                        gap = CARD_FOOT_GAP_DP.dp,
                        maxPlaceLines = FOOT_MAX_LINES,
                        modifier = Modifier
                            .padding(
                                start = CARD_PAD_START_DP.dp,
                                end = CARD_PAD_END_DP.dp,
                                bottom = CARD_V_PAD_DP.dp,
                            )
                            .fillMaxWidth(),
                    )
                } else Row(
                    modifier = Modifier
                        .padding(
                            start = CARD_PAD_START_DP.dp,
                            end = CARD_PAD_END_DP.dp,
                            // El aire de abajo se lo queda la cápsula cuando hay cápsula: si lo
                            // llevaran los dos, la card conduciendo crecería 14 dp respecto a sí
                            // misma aparcada. [UI-A-PILL-IS-FOR-A-LABEL-NOT-A-SENTENCE-002]
                            bottom = if (stopStacksBelow) 0.dp else CARD_V_PAD_DP.dp,
                        )
                        .heightIn(min = FOOT_MIN_HEIGHT_DP.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CARD_FOOT_GAP_DP.dp),
                ) {
                    // The glyph keeps its meaning and its identity colour; what it loses is the
                    // 44dp tinted box it used to sit in. [UI-CHIP-ROUTE-GLYPH-001]
                    when {
                        isDriving -> DrivingRouteGlyph(
                            color = accent,
                            glyphSize = CARD_FOOT_ICON_DP.dp,
                        )
                        else -> UnmarkedParkingIcon(tint = accent)
                    }
                    when {
                        // Neutral state words with the driving breath. [UI-COLOR-DOCTRINE-001]
                        // WHERE the trip is rides on the same line now, after the state, instead of
                        // costing a second one. [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]
                        isDriving -> {
                            // Los dos textos comparten una fila que se queda TODO el ancho que
                            // sobra — y desde que la cápsula no compite por él, eso es el ancho
                            // entero de la card: la calle es la que se elide, por su propio
                            // espacio. [UI-A-PILL-IS-FOR-A-LABEL-NOT-A-SENTENCE-002]
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(CARD_FOOT_GAP_DP.dp),
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
                            // ⛔ AQUÍ YA NO HAY BOTÓN. Estuvo inline, y el comentario que lo
                            // defendía decía «a four-letter CTA does not earn a full-width band»
                            // — cierto cuando el label era «Parar», falso desde que es «No voy en
                            // mi coche» (23 chars en italiano). La cápsula se medía a ancho
                            // intrínseco y le comía la línea a lo único vivo del viaje: dónde está
                            // el coche AHORA. Baja, entera, debajo.
                            // [UI-A-PILL-IS-FOR-A-LABEL-NOT-A-SENTENCE-002] (issue #116)
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

                // «No voy en mi coche» — APILADA a ancho completo, exactamente igual que en el
                // chip. Antes cada superficie colocaba este botón a su manera y la diferencia se
                // defendía por el hueco disponible; con el label convertido en frase, el hueco
                // dejó de existir en las dos. Un solo botón, un solo sitio.
                // [UI-A-PILL-IS-FOR-A-LABEL-NOT-A-SENTENCE-002] [DET-STOP-BUTTON-001]
                //
                // ⛔ Y no pasa por `ctaFitsInline`: ese predicado gobierna el hueco *trailing* de
                // una fila, donde el vecino es copy acotado que PUEDE envolver a dos líneas. Aquí
                // el vecino es una sola línea con el nombre de una calle, que no tiene tope; un
                // botón al lado se lo come siempre, mida lo que mida.
                if (isDriving) {
                    Spacer(Modifier.height(CHIP_FOOT_GAP_DP.dp))
                    TripActions(
                        accent = accent,
                        onParkNow = onParkNow.takeIf { status == null },
                        onKeepDriving = onKeepDriving.takeIf { isCandidate && status == null },
                        onStopTrip = onStopTrip,
                        modifier = Modifier.padding(
                            start = CARD_PAD_START_DP.dp,
                            end = CARD_PAD_END_DP.dp,
                            bottom = CARD_V_PAD_DP.dp,
                        ),
                    )
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

@Composable
private fun TripActions(
    accent: Color,
    onParkNow: (() -> Unit)?,
    onKeepDriving: (() -> Unit)?,
    onStopTrip: (() -> Unit)?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(
            PapGap.betweenControls(visible = PapGap.block, controlHeight = PapControlHeight.chip),
        ),
    ) {
        onParkNow?.let {
            PapCtaPill(
                label = stringResource(if (compact) Res.string.home_trip_park_compact else Res.string.home_trip_park_now),
                container = accent.copy(alpha = STOP_PILL_TINT_ALPHA),
                content = accent,
                onClick = it,
                modifier = Modifier.fillMaxWidth(),
                fillWidth = true,
                height = PapControlHeight.chip,
            )
        }
        onKeepDriving?.let {
            PapCtaPill(
                label = stringResource(if (compact) Res.string.home_trip_continue_compact else Res.string.home_trip_continue),
                container = Color.Transparent,
                content = MaterialTheme.colorScheme.onSurface,
                onClick = it,
                modifier = Modifier.fillMaxWidth(),
                fillWidth = true,
                height = PapControlHeight.chip,
            )
        }
        onStopTrip?.let {
            PapCtaPill(
                label = stringResource(if (compact) Res.string.home_trip_wrong_car_compact else Res.string.home_det_driving_stop_cta),
                container = Color.Transparent,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = it,
                modifier = Modifier.fillMaxWidth(),
                fillWidth = true,
                height = PapControlHeight.chip,
            )
        }
    }
}

/**
 * The parked footer of a vehicle card: **where** over **since when**, each fact with its own icon.
 * [UI-A-PARKED-CARD-SAYS-WHERE-AND-WHEN-IN-TWO-LINES-001]
 *
 * The two facts used to share one sentence behind one pin ("Parked at X · 6 min ago · 180 m"),
 * which made the pin head a line that was half time, and left the wrapped remainder hanging with
 * no icon at all. Split, each icon means exactly one thing — pin (or zone ring) is PLACE, clock is
 * TIME — which is the anatomy the peek's meta rows have always had.
 *
 * Both Home surfaces share this skeleton and keep their own wording and their own metrics: the
 * roomy card says "Parked at X" and carries the distance, the narrow chip spends its width on the
 * place alone.
 */
@Composable
private fun ParkedMetaLines(
    session: UserParking,
    placeText: String,
    metaText: String,
    accent: Color,
    iconSize: Dp,
    gap: Dp,
    maxPlaceLines: Int,
    modifier: Modifier = Modifier,
    /** Los metros hasta el coche, que son un hecho del LUGAR. Null cuando no sabemos dónde estamos
     *  — el chip estrecho nunca los pasa, porque gasta su anchura en la calle. */
    placeSuffix: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(META_LINE_GAP_DP.dp),
    ) {
        VehicleMetaLine(
            // An AREA wears the zone glyph, never the pin — the pin claims a point the session
            // refused to claim. [UI-APPROXIMATE-ZONE-IN-HISTORY-001]
            icon = if (session.isApproximate) Icons.Rounded.Adjust else Icons.Rounded.LocationOn,
            text = placeText,
            tint = accent,
            textColor = cs.onSurface,
            iconSize = iconSize,
            gap = gap,
            maxLines = maxPlaceLines,
            // ⛔ Va como pieza PROPIA, no concatenado al texto: la calle se elide y los metros no.
            // Pegados detrás de una dirección larga serían lo primero que desaparece, y son el único
            // dato de la línea que cambia mientras miras.
            suffix = placeSuffix,
        )
        VehicleMetaLine(
            icon = Icons.Rounded.AccessTimeFilled,
            text = metaText,
            tint = accent,
            // Quieter than the place: the address is what the card is FOR, the clock qualifies it.
            textColor = cs.onSurfaceVariant,
            iconSize = iconSize,
            gap = gap,
        )
    }
}

/**
 * One meta line: its icon, its fact, nothing else. The icon carries the vehicle's identity colour
 * and the words stay neutral — the state is written, never tinted. [UI-COLOR-DOCTRINE-001]
 */
@Composable
private fun VehicleMetaLine(
    icon: ImageVector,
    text: String,
    tint: Color,
    textColor: Color,
    iconSize: Dp,
    gap: Dp,
    maxLines: Int = 1,
    /** Un dato corto que cierra la línea y NO se elide: hoy, los metros hasta el coche. */
    suffix: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        // Same rule as `PeekMetaRow`: a one-liner centres against its icon, a wrapping address has
        // to hang from the top or the icon floats in the middle of the paragraph.
        verticalAlignment = if (maxLines == 1) Alignment.CenterVertically else Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
        Text(
            text = text,
            // An address is a phrase you read → LECTURA (caption). Unchanged by the split: a line
            // that gained an icon did not change voice. [UI-TYPE-TWO-VOICES-ONE-ROW-001]
            style = PaparcarType.current.caption,
            color = textColor,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            // Con sufijo, la calle se queda el hueco que sobre; sin él, la línea entera.
            modifier = if (suffix != null) Modifier.weight(1f, fill = false) else Modifier,
        )
        if (suffix != null) {
            Text(
                // El separador va aquí y no en el recurso: el punto medio es pegamento entre dos
                // piezas, igual que en la línea del viaje («En ruta · calle»), y meterlo en un
                // string obligaría a componer la frase entera para poder partirla luego.
                text = "· $suffix",
                style = PaparcarType.current.caption,
                // Más callado que la calle: la dirección es lo que se busca, los metros la califican.
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

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

/**
 * Cuánto hay de aquí al coche, o null si no sabemos dónde estamos.
 *
 * ⛔⛔ **Los metros son del LUGAR, no del reloj.** Viajaban pegados a la hora
 * («hace 34 min · 180 m») detrás del icono de reloj, que es exactamente el defecto que
 * [UI-A-PARKED-CARD-SAYS-WHERE-AND-WHEN-IN-TWO-LINES-001] partió las dos líneas para quitar: cada
 * icono significa UNA cosa. Un pin que dice dónde y un reloj que dice cuándo no pueden llevar la
 * distancia en el segundo. El comentario que lo defendía («ambos contestan cuán lejos / cuánto
 * hace») juntaba dos preguntas que la propia anatomía ya había separado.
 */
@Composable
private fun parkedDistance(session: UserParking, userLocation: Pair<Double, Double>?): String? =
    userLocation?.let { (uLat, uLon) ->
        distanceString(distanceMeters(uLat, uLon, session.location.latitude, session.location.longitude))
    }

/** Aire entre la línea del lugar y la del tiempo: menos que el que separa bloques, porque las dos
 *  son el MISMO hecho contado en dos mitades. [UI-A-PARKED-CARD-SAYS-WHERE-AND-WHEN-IN-TWO-LINES-001] */
private const val META_LINE_GAP_DP = 4

// Adaptive width bounds — card grows to fit "Toyota Corolla" without truncating, capped so a long
// name never dominates the LazyRow. [HOME-CARDS-001]
/** Aire vertical de la card, arriba y abajo. UN valor, para que no puedan divergir. */
private const val CARD_V_PAD_DP = 14

/** La franja de estado del canto: mismo grosor que la de la superficie de deteccion. */
private const val ACCENT_BAR_DP = 4

/** Aire lateral del contenido de la card grande. Asimétrico a propósito: el `start` arranca
 *  después de la franja de acento de 4 dp, el `end` no tiene franja que descontar. Estaban
 *  escritos a mano en tres sitios y la cápsula apilada iba a ser el cuarto.
 *  [UI-A-PILL-IS-FOR-A-LABEL-NOT-A-SENTENCE-002] */
private const val CARD_PAD_START_DP = 14
private const val CARD_PAD_END_DP = 12

private const val CHIP_MIN_WIDTH_DP = 150
private const val CHIP_MAX_WIDTH_DP = 200
private const val CHIP_PADDING_DP = 10
private const val CHIP_TOP_GAP_DP = 6
private const val CHIP_FOOT_GAP_DP = 8
private const val ICON_BOX_DP = 32
private const val FOOT_MIN_HEIGHT_DP = 34
private const val FOOT_GAP_DP = 7
private const val FOOT_ICON_DP = 14
// The foot's three branches share one line shape ("fact · detail") and one wrap budget, so a chip
// in a trip and a chip parked next to it stay the same object. [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]
private const val FOOT_SEPARATOR = " · "
private const val FOOT_MAX_LINES = 2
// Full-width single-vehicle card. [HOME-CARDS-001]
private const val CARD_FOOT_ICON_BOX_DP = 34
private const val CARD_FOOT_ICON_CORNER_DP = 10
private const val CARD_FOOT_ICON_DP = 16
/** Hueco icono→texto del pie de la card grande: era un 6 inline en dos sitios. */
private const val CARD_FOOT_GAP_DP = 6
private const val FOOT_ICON_BOX_ALPHA = 0.15f
private const val BORDER_DP = 1
private const val DRIVING_BORDER_DP = 1.5f // thicker live-blue border while driving [CHIP-DRIVING-001]
// The stop pill: shorter than the story rows' 40dp CTA so the foot keeps its height, tonal in the
// identity colour — same container language as the detection surface's identity tone.
// [DET-STOP-BUTTON-001] [HOME-A-DRIVE-IS-ONE-NEWS-AND-THE-CARD-CARRIES-THE-STOP-001]
private const val STOP_PILL_TINT_ALPHA = 0.16f
