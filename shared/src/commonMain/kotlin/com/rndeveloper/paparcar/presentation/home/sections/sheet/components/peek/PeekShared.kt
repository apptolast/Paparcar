@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.SpotTone
import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.displayName
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.SheetTokens
import com.rndeveloper.paparcar.domain.model.SpotFreshness
import com.rndeveloper.paparcar.presentation.util.distanceString
import com.rndeveloper.paparcar.presentation.util.radiusString
import com.rndeveloper.paparcar.presentation.util.driveTimeString
import com.rndeveloper.paparcar.presentation.util.placeLineText
import com.rndeveloper.paparcar.presentation.util.toPlaceLine
import com.rndeveloper.paparcar.presentation.util.walkTimeString
import com.rndeveloper.paparcar.ui.components.PapSectionHeader
import com.rndeveloper.paparcar.ui.components.ReliabilityMeter
import com.rndeveloper.paparcar.domain.model.RefuelStopAnswer
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.stateColors
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.home_spot_releasing_eyebrow
import paparcar.composeapp.generated.resources.notif_saved_approximate_text
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.refuel_answer_no
import paparcar.composeapp.generated.resources.refuel_answer_yes
import paparcar.composeapp.generated.resources.refuel_ask_title
import paparcar.composeapp.generated.resources.refuel_confirmed_fact
import paparcar.composeapp.generated.resources.refuel_possible_label
import paparcar.composeapp.generated.resources.home_address_unknown
import paparcar.composeapp.generated.resources.home_peek_parking_approximate
import paparcar.composeapp.generated.resources.home_peek_spot_fresh
import paparcar.composeapp.generated.resources.home_peek_spot_stale
import paparcar.composeapp.generated.resources.home_peek_spot_recent
import paparcar.composeapp.generated.resources.home_peek_spot_freshness_label
import paparcar.composeapp.generated.resources.home_vehicle_fallback_name
import com.rndeveloper.paparcar.ui.theme.PapAlpha
import com.rndeveloper.paparcar.ui.theme.PapIconSize

// ─────────────────────────────────────────────────────────────────────────────
// PeekShared — helpers common to the peek variants (meta rows, palettes,
// title resolvers, live minute clock). [HOME-ATOMIZE-001 F3]
// ─────────────────────────────────────────────────────────────────────────────

internal const val MS_PER_MINUTE = 60_000L
private const val FIABILITY_SEG_HEIGHT_DP = 4

internal enum class TravelMode { WALKING, DRIVING }

/** The vehicle's display name, or null when it has none worth showing. */
@Composable
internal fun vehicleSummary(vehicle: Vehicle?): String? {
    if (vehicle == null) return null
    val fallback = stringResource(Res.string.home_vehicle_fallback_name)
    return vehicle.displayName(fallback = fallback).takeIf { it.isNotBlank() }
}

/**
 * Canonical peek meta row — accent icon + one SemiBold value line. The concrete
 * rows (distance, spot age, en-route, parking duration) are thin wrappers over
 * this molde so their visuals can't drift apart. [HOME-VEH-REFINE-001]
 */
@Composable
internal fun PeekMetaRow(
    /** `null` → la fila deja el **canal del glifo vacío**, no lo colapsa.
     *
     *  ⛔ Nace de un defecto MEDIDO en el Oppo: el modal del parking público ponía «1 min en coche»
     *  (con glifo) en x=126 y justo debajo «Altura máx. 1,9 m» (sin glifo) en **x=48** — dos hechos
     *  consecutivos del mismo tipo desalineados 28 dp, porque eran dos componentes distintos que no
     *  se conocían. Un hecho sin glifo sigue siendo un hecho: cuelga del mismo canal.
     *
     *  ⚠️ **El nulo es el último recurso, no el atajo.** Cuando escribí esto afirmé que el set
     *  extendido de Material no estaba en el classpath, y era FALSO — la app ya usa
     *  `AccessTimeFilled`, `Adjust`, `Bolt` y `Campaign`, que salen todos de ahí. Así que los tres
     *  hechos del parking que justificaron este parámetro **ya llevan su glifo**. Antes de pasar
     *  `null`, comprobar que de verdad no hay ninguno que diga la verdad sobre ese dato. */
    icon: ImageVector?,
    text: String,
    tint: Color,
    maxLines: Int = 1,
    /** Non-null → the whole row is tappable and wears a trailing chevron. The row's own copy must
     *  already say what tapping does — the chevron only signals that it CAN be done.
     *  [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001] */
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        // A one-liner centres against its icon; a wrapped explanation (the approximate-zone row)
        // has to hang from the top or the icon floats in the middle of the paragraph.
        verticalAlignment = if (maxLines == 1) Alignment.CenterVertically else Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(PapIconSize.withLabel),
            )
        } else {
            Spacer(Modifier.size(PapIconSize.withLabel))
        }
        Text(
            text = text,
            // LECTURA, no CIFRA: estas filas van solas a todo el ancho, así que la precondición del
            // rol DATA —un token peleando por el espacio horizontal— no se cumple.
            // [PEEK-META-INTER-001]
            //
            // ⛔ **`body`, no `rowTitle`, y eso cambia desde que el modal DESARROLLA la cabecera**
            // (user, 30-10): *«tenemos más grandes el contenido del body que el del head del título,
            // habría que adaptar el contenido del body para que no se lleve jerarquía visual»*.
            // Medído: `rowTitle` y `rowName` son **el mismo 14sp SemiBold**, así que tres filas de
            // cuerpo con su glifo en color pesaban más que una línea de cabecera — la jerarquía
            // quedaba invertida por acumulación.
            //
            // El KDoc anterior decía que estas filas SON la información principal de la tarjeta, y
            // era verdad mientras la cabecera no nombraba al sujeto. Ahora sí lo nombra, en MARCA y
            // con su color de identidad, así que esto pasa a ser **desarrollo**: mismo tamaño, un
            // peso menos. [UI-TYPE-TWO-VOICES-ONE-ROW-001]
            style = PaparcarType.current.body,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SheetTokens.META_VALUE_ALPHA),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = if (onClick != null) Modifier.weight(1f) else Modifier,
        )
        if (onClick != null) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(PapIconSize.withLabel),
            )
        }
    }
}

/**
 * The doubt row of an approximate session: the map draws the circle, this says what it means and
 * what to do about it. Also shown for inferred points without a radius. Copy is cause + consequence
 * + remedy with no internal mechanics — never "steps", "anchor" or "honest close".
 * [UI-APPROXIMATE-PARKING-DRAWS-ITS-DOUBT-001]
 */
@Composable
internal fun ApproximateZoneRow(
    zoneRadiusMeters: Float?,
    accentColor: Color,
    isApproximate: Boolean = zoneRadiusMeters != null,
    /** Non-null → the row honours its own copy ("Edit to place it exactly"): one tap opens the
     *  pin-positioning flow, instead of making the user hunt for the round edit button. Validating
     *  WHERE the car really is turns the zone back into an exact pin — which is also what lets the
     *  eventual freed spot publish exactly. [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001] */
    onAdjust: (() -> Unit)? = null,
) {
    if (!isApproximate) return
    val radius = zoneRadiusMeters
    PeekMetaRow(
        icon = Icons.Rounded.Adjust,
        text = if (radius != null) stringResource(Res.string.home_peek_parking_approximate, radiusString(radius))
            else stringResource(Res.string.notif_saved_approximate_text),
        tint = accentColor,
        maxLines = APPROXIMATE_ROW_MAX_LINES,
        onClick = onAdjust,
    )
}

private const val APPROXIMATE_ROW_MAX_LINES = 3

/**
 * Lo que esta sesión dice sobre el surtidor: el hecho, y la pregunta si sigue abierta.
 * [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001]
 *
 * ⛔ **Un solo componente para las DOS superficies** que la ofrecen —la fila del historial del
 * vehículo y la ficha del detalle—, por lo mismo que [PeekMetaRow] existe: dos copias de la misma
 * pregunta son dos sitios donde puede decir cosas distintas, y aquí la segunda llegó precisamente
 * por quedarse un hueco (la ficha no decía nada y perdías la pregunta al entrar en ella).
 *
 * Tres estados y sólo tres:
 * - **pendiente** → el hecho («Posible repostaje») y, si [onAnswer] existe, la pregunta con sus dos
 *   respuestas. Sin [onAnswer] la superficie no contesta (previews, galería) y se queda en el hecho.
 * - **confirmado** → el hecho, en pasado. Es un dato de la sesión igual que la plaza que te cedió
 *   la comunidad, y la cifra del periodo lo cuenta.
 * - **descartado o nunca preguntado** → **nada**. Un «no» no deja rastro que enseñar, y no haber
 *   preguntado todavía menos.
 */
@Composable
internal fun RefuelStopRow(
    refuelStop: RefuelStopAnswer?,
    tint: Color,
    onAnswer: ((refuelled: Boolean) -> Unit)? = null,
) {
    if (refuelStop != RefuelStopAnswer.PENDING && refuelStop != RefuelStopAnswer.CONFIRMED) return
    PeekMetaRow(
        icon = PaparcarIcons.Fuel,
        text = stringResource(
            if (refuelStop == RefuelStopAnswer.CONFIRMED) {
                Res.string.refuel_confirmed_fact
            } else {
                Res.string.refuel_possible_label
            },
        ),
        tint = tint,
    )
    if (refuelStop != RefuelStopAnswer.PENDING || onAnswer == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.refuel_ask_title),
            style = PaparcarType.current.caption,
            color = tint,
            modifier = Modifier.weight(1f),
        )
        // Mismo reparto que la pregunta por la ruta inferida: el «no» en tono neutro y el «sí» en el
        // verde de acción, porque sólo una de las dos añade algo. [ROUTE-GAP-HONEST-001]
        TextButton(onClick = { onAnswer(false) }) {
            Text(
                text = stringResource(Res.string.refuel_answer_no),
                style = PaparcarType.current.cta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { onAnswer(true) }) {
            Text(
                text = stringResource(Res.string.refuel_answer_yes),
                style = PaparcarType.current.cta,
                color = PapColor.actionText,
            )
        }
    }
}

// `DistanceRow` vivió aquí y **murió con su caso**: desde que los cuatro peeks ponen la distancia en
// su línea 2, ninguno la renderiza. Un componente que nadie pinta no es un componente. Lo que
// añadía de verdad —el TIEMPO— sobrevive justo debajo.
// [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]

/**
 * **A cuánto y cuánto se tarda**, en una fila del CONTENIDO — no de la cabecera.
 * [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
 *
 * ⚠️ Esto es la antigua `DistanceRow`, que **murió y vuelve**, y el viaje de ida y vuelta es la
 * historia: se retiró cuando los seis peeks pasaron a poner la distancia en su línea 2, y vuelve
 * cuando el user decidió lo contrario para el MODAL — *«los datos que había en el peek deberán ir
 * desarrollados en el contenido de abajo, ya que está redundante»*. La regla que queda es más simple
 * que las dos: **el peek RESUME en dos líneas, el modal DESARROLLA abajo.**
 *
 * Y aquí abajo no hay `‹ › ×` que le quite ancho — medido en el Oppo, el stepper se queda 204 px y
 * al texto de la cabecera le sobraban 538 de 1080 —, así que las dos cifras caben sin recortar.
 *
 * Null en el tiempo = no hay tiempo que sostener a esa distancia, y entonces la fila es SÓLO la
 * distancia: ni tiempo ni el separador que lo anunciaba.
 * [COPY-A-DURATION-ROLLS-INTO-HOURS-AND-A-CAPACITY-IS-NOT-FREE-SPACES-001]
 */
@Composable
internal fun DistanceAndTimeRow(distanceM: Float?, mode: TravelMode, accentColor: Color) {
    if (distanceM == null) return
    val timeText = when (mode) {
        TravelMode.WALKING -> walkTimeString(distanceM)
        TravelMode.DRIVING -> driveTimeString(distanceM)
    }
    PeekMetaRow(
        icon = when (mode) {
            TravelMode.WALKING -> Icons.AutoMirrored.Rounded.DirectionsWalk
            TravelMode.DRIVING -> Icons.Rounded.Navigation
        },
        text = distanceString(distanceM) + timeText?.let { "${SheetTokens.META_SEPARATOR}$it" }.orEmpty(),
        tint = accentColor,
    )
}

// ═══════════════════════════════════════════════════════════════════════
// La GRAMÁTICA de dos líneas del peek — **una sola para TODOS sus estados** (decisión del user,
// 30-10): coche aparcado, viaje vivo, plaza, parking público y gasolinera dicen su nombre y su
// calificador arriba, y dónde están y a cuánto abajo. Misma altura, misma estructura.
// [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
// ═══════════════════════════════════════════════════════════════════════

/**
 * LÍNEA 1 — **quién · qué le pasa**, con el nombre al frente.
 *
 * ⛔ **El NOMBRE es el sujeto, no la dirección**, y esto corrige una jerarquía invertida que el user
 * vio en el emulador: la dirección se llevaba `cardTitle` —la voz grande— y el sujeto quedaba en un
 * eyebrow en caps. En el prototipo que aprobó es al revés.
 *
 * ⛔ **Sólo el nombre lleva color de identidad**; el calificador —«aparcado hace 31m», «en ruta»,
 * «hace 3 min»— es ESTADO y se queda neutro. El estado se escribe, no se tiñe.
 * [UI-COLOR-DOCTRINE-001]
 *
 * ⛔⛔ **El nombre va en `cardTitle`, que es el rol que `PapSheet` YA declaraba para su título**
 * —y su KDoc nombra este caso con todas las letras: *«vehicle name, **peek title**, dialog
 * title»*—, no en `rowName`. Esto nace de un defecto MÍO, dicho por el user el 30-10:
 * *«el nombre… se puede poner en negrita»* y *«tenemos más grandes el contenido del body que el del
 * head del título»*. Al meter el `titleSlot` sustituí el 18sp Bold de la hoja por un 14sp
 * SemiBold — **degradé el título de una hoja a rol de nombre-de-fila**, sin querer y en los seis
 * peeks a la vez. La jerarquía invertida no era del cuerpo: era del título, que había bajado.
 *
 * Medido: `papSheetHeaderReservedHeight()` reserva su pila de texto con **`lineHeight` 24sp**, que
 * es justo la de `cardTitle` — así que lo que no encajaba era el 20 de `rowName`, y el alto de la
 * cabecera no se mueve al arreglarlo.
 *
 * El calificador cae a `body` (14sp Normal): al lado de un 18sp Bold, mantenerlo en `rowTitle`
 * (14sp SemiBold) lo leía como un segundo título. [UI-TYPE-TWO-VOICES-ONE-ROW-001]
 */
@Composable
internal fun peekSubjectTitleSlot(
    name: String,
    nameColor: Color,
    qualifier: String?,
    qualifierAlpha: Float = 1f,
    maxLines: Int = 1,
    /** Glifo que va **DELANTE** del nombre, en su color. Para un coche es su método de vigilancia
     *  (`VehicleWatchLeadingIcon`), la misma anatomía que la pantalla de vehículos y el chip de
     *  Home: *«el nombre siempre en onSurface y su respectivo icono de detección en su color, como
     *  en vehicle screen»* (user, 03-11).
     *
     *  ⛔ **Delante, no detrás** — lo puse detrás y el user lo corrigió al verlo. El propio
     *  componente lo dice en su nombre: es un *leading* icon, y así lo colocan ya `HomeParkingRow`
     *  y `VehicleIdentityHeader`. Detrás el glifo se lee como un sufijo del nombre; delante es lo
     *  que el nombre ES. [UI-COLOR-DOCTRINE-001] [UI-VEH-STATUS-IS-A-GLYPH-NOT-A-LABEL-001]
     */
    leadingGlyph: (@Composable () -> Unit)? = null,
): @Composable () -> Unit = {
    // ⛔⛔ **UN solo `Text`, no dos en un `Row`**, y esto es un bug medido en el emulador, no una
    // preferencia. Con dos `Text` el calificador no llevaba peso, así que se medía primero y se
    // quedaba el ancho entero: el NOMBRE colapsaba a cero y el peek del parking salía literalmente
    // como «· Underground car …», empezando por el separador. Justo al revés de lo que hace falta.
    //
    // En una sola cadena la elipsis cae al FINAL por construcción: el nombre —que es el sujeto—
    // sobrevive siempre y lo que se recorta es el calificador, que es lo prescindible. Es la misma
    // técnica con la que `PapListItem` tiñe el nombre del vehículo dentro de su eyebrow.
    // [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
    val qualifierStyle = PaparcarType.current.body.toSpanStyle().copy(
        // El pulso del estado en movimiento viaja por aquí, así que el nombre nunca parpadea.
        // [UI-PEEK-DRIVING-HAS-NO-MOTION-001]
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = qualifierAlpha),
    )
    val subject: @Composable (Modifier) -> Unit = { mod ->
    Text(
        text = buildAnnotatedString {
            append(name)
            if (qualifier != null) {
                val start = length
                append(" $PEEK_LINE_SEPARATOR $qualifier")
                addStyle(qualifierStyle, start, length)
            }
        },
        style = PaparcarType.current.cardTitle,
        color = nameColor,
        // ⛔ **Una línea en el carril, DOS en el modal** (user, 30-10): *«si queda muy largo el
        // título que pueda ser 2 líneas»*. Y el reparto no es un gusto, sale de lo medido: el racimo
        // «‹ › ×» se come 432 px de los 1080 del Oppo —los tres botones de 48 dp, el ‹ deshabilitado
        // INCLUIDO, que no sale en el volcado de uiautomator pero sí ocupa—, así que al título le
        // quedan 345 px: unos 12 caracteres a 18sp Bold. «Parking de varias plantas» no cabe en una
        // línea y no va a caber nunca.
        //
        // El carril se queda en una porque ahí el nombre es corto por construcción («Plazas»,
        // «Parkings», «Gasolineras») y porque su cabecera es la que define la altura de la hoja
        // plegada — envolver ahí cambiaría el alto del peek, que el user fijó para todos sus estados.
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = mod,
    )
    }
    if (leadingGlyph == null) {
        subject(Modifier)
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PEEK_SUBJECT_GLYPH_GAP_DP.dp),
        ) {
            // ⛔ El GLIFO no cede ancho y el nombre sí: sin `weight` en el glifo para que no
            // desaparezca cuando falta sitio, y `fill = false` en el nombre para que no estire
            // cuando sobra. Es el mismo reparto que `peekWhereLine` hace con su cifra.
            leadingGlyph()
            subject(Modifier.weight(1f, fill = false))
        }
    }
}

// Aire entre el nombre y su glifo de vigilancia — el mismo 5 dp que deja el título con su marca.
private const val PEEK_SUBJECT_GLYPH_GAP_DP = 5

/**
 * LÍNEA 2 — **dónde · a cuánto**, con la cifra en su propia columna.
 *
 * ⛔ **Y esa columna es la decisión que el emulador corrigió.** Con el «a 180 m» dentro del mismo
 * `Text`, en pantalla salió `Repsol Av. Castellana · 1450.6 …`: el texto elide por el FINAL, así que
 * la cifra era siempre lo primero en caerse — al revés de lo que hace falta, porque un nombre se
 * abrevia sin perder la respuesta y una distancia no. Aquí el sitio lleva el `weight` y la cifra no,
 * así que **elide el sitio y el número sobrevive**, que es lo que hace el prototipo.
 *
 * ⛔ **LECTURA, no CIFRA**: comparte caja de línea con un nombre en MARCA, y una cifra dentro de una
 * FILA se queda en la voz de lectura — medido en device el 29-08. [PEEK-META-INTER-001]
 */
@Composable
internal fun peekWhereLine(place: String, far: String?): @Composable () -> Unit = {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // ⛔ Un sujeto puede no tener DÓNDE que escribir — un parking público no trae dirección en
        // ninguna fuente — y entonces la línea 2 es sólo la cifra, sin punto medio colgando delante.
        // Blanco no es un sitio. [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
        val hasPlace = place.isNotBlank()
        if (hasPlace) Text(
            text = place,
            style = PaparcarType.current.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (far != null) {
            Text(
                text = if (hasPlace) " $PEEK_LINE_SEPARATOR $far" else far,
                style = PaparcarType.current.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** El separador de las dos piezas de una línea — el mismo punto medio que usa el resto de la hoja. */
private const val PEEK_LINE_SEPARATOR = "·"

/** El mismo separador, con sus espacios, para quien compone la línea ANTES de pasarla. */
/**
 * La PRIMERA fila de hechos de un modal: dónde está esto.
 *
 * ⛔ **Baja de la cabecera al cuerpo** (user, 30-10): *«el resto de info que mostrábamos en peek
 * pasará al body de la modal»*. La cabecera NOMBRA — el cuerpo DESARROLLA. Antes el sitio vivía en
 * el `subtitleSlot`, donde compartía ancho con el racimo «‹ › ×» (432 px de 1080 medidos en el
 * Oppo) y salía recortado como «Calle de …»; aquí va a todo el ancho de la hoja.
 *
 * Sitio en blanco ⇒ **no hay fila**, no una fila vacía: un parking público no trae dirección en
 * ninguna fuente, y callarse es decir la verdad. [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
 */
@Composable
internal fun PeekPlaceRow(place: String?, tint: Color) {
    val text = place?.takeIf { it.isNotBlank() } ?: return
    PeekMetaRow(icon = Icons.Rounded.LocationOn, text = text, tint = tint, maxLines = PEEK_PLACE_MAX_LINES)
}

/** Una dirección puede necesitar dos líneas; ya no compite con el stepper, así que puede gastarlas. */
private const val PEEK_PLACE_MAX_LINES = 2

/** Un título de MODAL puede envolver a dos líneas; el de un carril, no — ver `peekSubjectTitleSlot`. */
internal const val PEEK_MODAL_TITLE_MAX_LINES = 2

internal const val PEEK_LINE_JOINER = " $PEEK_LINE_SEPARATOR "

@Composable
internal fun FiabilityIndicator(level: SpotTone) {
    // [SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001] The right-hand "expires in N min" text is gone.
    // It was a countdown to the sweep that deletes the document, presented as if it said something
    // about the parking space — and it sat directly above a meter that had already been told the
    // spot was stale. How old the offer is now reads once, in the header's subtitle, in words.
    PapSectionHeader(
        title = stringResource(Res.string.home_peek_spot_freshness_label),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(5.dp))

    // Same canonical 5-segment meter as list/ficha, coloured by freshness tier
    // (verde/ámbar/rojo) — no longer always-green. [IDENTITY-ICONS-001 D]
    ReliabilityMeter(
        level = level,
        fillWidth = true,
        barHeight = FIABILITY_SEG_HEIGHT_DP.dp,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Emits the current epoch-millis and re-emits on every whole-minute boundary, so relative-time
 * labels ("Caduca en N min", "Publicada hace N min") count down live while the peek is visible
 * instead of freezing at the value captured on first composition. [SPOT-TTL-LIVE-001]
 */
@Composable
internal fun rememberNowMinuteTick(): Long {
    val nowMs by produceState(initialValue = kotlin.time.Clock.System.now().toEpochMilliseconds()) {
        while (true) {
            val current = kotlin.time.Clock.System.now().toEpochMilliseconds()
            value = current
            // Wait until the next whole minute so the label flips exactly on the boundary.
            kotlinx.coroutines.delay(MS_PER_MINUTE - current % MS_PER_MINUTE)
        }
    }
    return nowMs
}

/**
 * What the peek says and what colour it says it in, for one freshness tier.
 *
 * The fields were `badgeBg`/`badgeFg` from when this tier rendered as a filled badge. It has not
 * been one for a long time: everything that reads this — the eyebrow, the distance row, the
 * en-route row — paints TEXT, so there is one colour and its name says the job. `badgeFg` had no
 * reader at all. [UI-COLOR-THE-RAMP-HAS-ONE-RESOLVER-001]
 */
internal data class SpotPeekPalette(
    val accent: Color,
    val label: String,
)

@Composable
internal fun SpotTone.peekPalette(): SpotPeekPalette {
    val label = when (this) {
        SpotTone.FRESH  -> stringResource(Res.string.home_peek_spot_fresh)
        SpotTone.RECENT -> stringResource(Res.string.home_peek_spot_recent)
        SpotTone.STALE  -> stringResource(Res.string.home_peek_spot_stale)
        // [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] The other three name how FRESH the space is.
        // This one has to name something else entirely, because there is no space yet.
        SpotTone.RELEASING -> stringResource(Res.string.home_spot_releasing_eyebrow)
    }
    // Painted as TEXT: the peek eyebrow and the accent rows. Readable leg, not the fill.
    // [UI-COLOR-GREEN-TEXT-EARNS-ITS-CONTRAST-001]
    return SpotPeekPalette(stateColors().text, label)
}

/**
 * Peek-friendly title resolver. Returns place name OR address line, **never**
 * concatenated — the peek/state cards have tight horizontal space and a long
 * "name · address" line truncates ugly mid-word. When neither exists the caller's
 * [fallback] speaks instead: the label names what the pin IS (a spot, your
 * parking), never its coordinates. [UI-LOCATION-FALLBACK-SPEAKS-HUMAN-001]
 */
internal fun peekTitle(
    placeName: String?,
    addressLine: String?,
    fallback: String,
): String = placeName?.takeIf { it.isNotBlank() }
    ?: addressLine?.takeIf { it.isNotBlank() }
    ?: fallback

/**
 * Camera-anchored title resolver for the pin-mode peek cards. Returns the POI
 * name when the camera sits on a place, the geocoded address line otherwise,
 * and a localized fallback when the camera has no usable location info yet.
 * A borrowed-neighbour answer declares itself as "Near X" — the approximation
 * is never passed off as exact. [GEO-CACHE-ANSWERS-NEARBY-001]
 */
@Composable
internal fun cameraTitleOrFallback(info: AddressAndPlace?): String =
    cameraLine(info) ?: stringResource(Res.string.home_address_unknown)

/**
 * Like [cameraTitleOrFallback] but returns stale data or "…" while the camera
 * is moving or geocoding ([isSettling]), so pin-mode peek cards never flash
 * "unknown address" mid-drag.
 */
@Composable
internal fun cameraTitleWhileSettling(info: AddressAndPlace?, isSettling: Boolean): String =
    if (isSettling) {
        cameraLine(info) ?: "…"
    } else {
        cameraTitleOrFallback(info)
    }

/**
 * POI name > address line, wrapped in "Near X" when the answer is approximate.
 *
 * Delegates to the one resolver every place-naming surface shares — the peek titles here, and the
 * vehicle chip / card footers, which name the SAME point during a trip. This used to be its own
 * copy of the fallback chain, which is how two surfaces get to disagree about one place.
 * [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]
 */
@Composable
private fun cameraLine(info: AddressAndPlace?): String? = placeLineText(info.toPlaceLine())
