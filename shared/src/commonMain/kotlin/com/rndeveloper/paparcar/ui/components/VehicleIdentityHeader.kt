package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.displayName
import com.rndeveloper.paparcar.domain.model.monitoringStatus
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.vehicleIdentityColor
import com.rndeveloper.paparcar.ui.theme.watch
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.my_car_unnamed_vehicle

/**
 * Shared identity header for a vehicle card — ONE anatomy consumed by the Vehicles ficha and the
 * Home single-vehicle card so gaps, sizes and the reading order (status → name → carbody · size)
 * are defined in exactly one place:
 *
 * ```
 * [ tile glyph ]  ◉ Name (MARCA, cardTitle)                   [trailing action]
 *                 Sedán · Mediano  (quiet metadata, no chips)
 *                 Híbrido · 5 l/100 km   (metaSecondLine)
 *                 📍 Marx-Engels-Forum · Berlin                              ›
 *                 🕐 Aparcado · hace 22 h        (statusLine, a ancho completo)
 * ```
 *
 * El `statusLine` es una FILA propia, no la última línea de la columna de texto: es lo único de
 * esta anatomía que además se toca, así que su trailing tiene que caer en el mismo raíl que el
 * resto de la card. Ver el comentario del bloque, abajo.
 * [UI-A-CHEVRON-MUST-SIT-ON-THE-CARD-RAIL-001]
 *
 * **NO boxed element and no method label.** The watch tier is a glyph immediately before the name
 * ([VehicleWatchLeadingIcon]) in the identity colour — the exact anatomy of the Home vehicle chip,
 * which is what `HOME-VEH-REFINE-001` meant by *"status is colour-only, never a method label"*.
 *
 * The pill that used to live here (`ACTIVO` / `BT` in a tonal box, per `CARD-ONE-BADGE-001`) was the
 * deviation, and it cost twice: the LENGTH of the status word decided how much width was left for
 * the name — `ACTIVO` wrapped a 9-char "Oppo Test" into two lines while `BT` left a 12-char "Skoda
 * Kamiq" whole — and it said in words what the glyph and the colour already said. The word is not
 * lost, it changed channel: it is now the glyph's `contentDescription`, so a screen reader still
 * speaks the tier that colour alone could not carry.
 * [UI-VEH-STATUS-IS-A-GLYPH-NOT-A-LABEL-001] [UI-COLOR-DOCTRINE-001]
 *
 * The illustration sits on a rounded tonal tile so every carbody (a low coupé, a thin motorcycle)
 * carries the same visual weight. Status is colour-only, never a method label. [HOME-VEH-REFINE-001]
 */
@Composable
fun VehicleIdentityHeader(
    vehicle: Vehicle,
    modifier: Modifier = Modifier,
    // While a trip is being detected, the en-route radar halo pulses behind the glyph.
    isDriving: Boolean = false,
    // The carbody · size subtitle is a detail-screen attribute — the Vehicles ficha shows it; the
    // glanceable Home card hides it (name → status → parked-at is what matters). The status badge
    // itself always shows. [CARD-META-POLISH-001]
    showSize: Boolean = true,
    /** Optional second quiet line under the carbody · size subtitle — the ficha uses it for the
     *  fuel data line. Same caption voice; the component stays agnostic of what it says.
     *  [VEH-FUEL-001] */
    metaSecondLine: String? = null,
    /**
     * How this car IS right now — "📍 Calle de Alcalá 42 · hace 2 h" when it is parked, "Último
     * aparcamiento · hace 3 d" when it is not. Not a statistic: it moved here from the ficha's stats
     * row precisely because "última vez" answers a question about STATE, and sitting among counters
     * made the card mix the car's whole life with the selected period.
     *
     * A slot rather than a String because the parked line carries a place glyph, and this component
     * has no business knowing what a parking looks like. Its opinions son dos: el aire extra encima
     * —las líneas de arriba son atributos del coche, esta es lo que el coche ESTÁ HACIENDO— y que
     * ocupa el ancho de la card, sangrada hasta el nombre pero llegando al borde, para que lo que
     * lleve al final aterrice en el raíl de acciones.
     * [STATS-UI-001] [UI-COLOR-DOCTRINE-001] [UI-A-CHEVRON-MUST-SIT-ON-THE-CARD-RAIL-001]
     */
    statusLine: (@Composable () -> Unit)? = null,
    /**
     * Una marca junto al NOMBRE — hoy, que el coche es compartido. [VEH-SHARED-001]
     *
     * Va aquí y no en el color del nombre a propósito: el color de este bloque ya dice CÓMO se
     * vigila el coche (verde, azul BT, gris), y meter «compartido» en esa misma señal haría que dos
     * hechos distintos pelearan por un canal. [UI-COLOR-DOCTRINE-001]
     */
    nameSuffix: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val watch = vehicle.monitoringStatus().watch()
    // The vehicle's identity colour — its watch method. ⛔ NO pinta el nombre: pinta el GLIFO de
    // vigilancia que va delante (y el halo mientras conduce). El nombre se queda en `onSurface`,
    // como se lee 50 líneas más abajo y como hace el chip de Home.
    //
    // Este comentario decía lo contrario y sobrevivió al cambio que lo desmintió. Costó una
    // auditoría de color entera: al ver verde junto al nombre en device, el comentario "confirmaba"
    // que el nombre era el teñido — y la propuesta era arreglar algo que ya estaba bien.
    // [UI-COLOR-DOCTRINE-001]
    val identity = vehicleIdentityColor(watch)
    val cs = MaterialTheme.colorScheme

    // Centred while the text is short (the Home card: name only). Once the ficha adds the
    // vehicle-data lines, centring floats the glyph halfway down the block and the card stops
    // reading as "this is the car, and these are its facts": the glyph's top belongs on the name's
    // line. Derived from the CONTENT so no call site has to know. (User, 08-09-2026.) [STATS-UI-001]
    //
    // ⛔ Ya NO lee `statusLine`: el estado dejó de vivir en esta columna (ver abajo), así que dejó
    // de poder decidir cómo se alinea el tile con ella. Leerlo de todas formas daba el mismo valor
    // en los dos call sites —la ficha trae `showSize`— pero por la razón equivocada, que es
    // exactamente el tipo de predicado que sobrevive a la siguiente edición mintiendo.
    // [UI-A-CHEVRON-MUST-SIT-ON-THE-CARD-RAIL-001]
    val alignsWithFirstLine = showSize || metaSecondLine != null
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = if (alignsWithFirstLine) Alignment.Top else Alignment.CenterVertically,
        ) {
            VehicleGlyphTile(vehicle = vehicle, isDriving = isDriving, haloColor = identity)
            Spacer(Modifier.width(TILE_TEXT_GAP_DP.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Identity line — watch glyph, then the name. Same order and same gap as the Home chip:
                // the tier is a SHAPE (radar / Bluetooth / hollow ring) in the identity colour, never a
                // word and never a box, so a long status can no longer eat the name's width.
                // [UI-VEH-STATUS-IS-A-GLYPH-NOT-A-LABEL-001]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WATCH_NAME_GAP_DP.dp),
                ) {
                    // The word the pill used to show survives HERE, as the glyph's description: shape
                    // and colour say the tier to the eye, this says it to a screen reader — and to
                    // anyone for whom green and grey are the same colour.
                    VehicleWatchLeadingIcon(
                        watch = watch,
                        contentDescription = vehicleWatchPinLabel(watch),
                    )
                    // Name stays onSurface — the glyph beside it already wears the identity colour;
                    // tinting both is over-information. [UI-COLOR-DOCTRINE-001]
                    //
                    // `maxLines = 2` stays for a genuinely long name ("Volkswagen Transporter"): a name
                    // is identity and an ellipsis loses it, so wrapping is the better failure. What the
                    // glyph removed is the wrap CAUSED by the status, not the ability to wrap.
                    Text(
                        text = vehicle.displayName(fallback = stringResource(Res.string.my_car_unnamed_vehicle)),
                        style = PaparcarType.current.cardTitle,
                        color = cs.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    nameSuffix?.invoke()
                }
                // Quiet descriptive subtitle (carbody · size) — no chips. Detail-screen only.
                if (showSize) {
                    Spacer(Modifier.height(NAME_META_GAP_DP.dp))
                    val subtitle = listOfNotNull(
                        vehicle.carbodyType?.label(),
                        vehicleSizeLabel(vehicle.sizeCategory),
                    ).joinToString(SUBTITLE_SEPARATOR)
                    Text(
                        text = subtitle,
                        // App convention for an icon·title·subtitle row: subtitle is quiet PROSE (LECTURA
                        // caption), same as PapListItem — NOT a CIFRA data token. [CARD-ONE-BADGE-001]
                        style = PaparcarType.current.caption,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (metaSecondLine != null) {
                    Spacer(Modifier.height(NAME_META_GAP_DP.dp))
                    Text(
                        text = metaSecondLine,
                        style = PaparcarType.current.caption,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(TRAILING_GAP_DP.dp))
                // ⛔ Centrado POR SÍ MISMO, no por la fila. La fila se alinea arriba cuando el texto
                // manda (`alignsWithFirstLine`), porque el tile del coche tiene que casar con la primera
                // línea del nombre — pero eso arrastraba también al trailing, y con una dirección que
                // envuelve a dos líneas el chevron quedaba colgando del borde superior de la card.
                // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
                //
                // Y centrado ¿respecto a QUÉ? Cuando el bloque de texto manda, la fila mide cuatro
                // líneas y el centro de la fila cae a media card: el lápiz de la ficha quedaba flotando
                // entre el nombre y el estado, sin casar con nada. Centrarlo sobre la ALTURA DEL TILE lo
                // pone a la altura del coche, que es el ancla visual de la card. Cuando el texto es
                // corto (la card de Home: nombre + estado) la fila ya está centrada y no hay diferencia
                // que corregir. Derivado del contenido, como la propia alineación de la fila.
                // (User, 15-09.) [UI-A-PARKED-CARD-SAYS-WHERE-AND-WHEN-IN-TWO-LINES-001]
                Box(
                    modifier = if (alignsWithFirstLine) {
                        Modifier.height(TILE_DP.dp)
                    } else {
                        Modifier.align(Alignment.CenterVertically)
                    },
                    contentAlignment = Alignment.Center,
                ) { trailing() }
            }
        }
        // ⛔ FUERA de la columna de texto, y a ancho completo. Vivía dentro de ella, o sea que
        // terminaba antes del hueco del trailing y de la caja de 40 dp del lápiz: su chevron caía a
        // 74 dp del borde interior de la card mientras el lápiz estaba a 36 y el de «Compartir este
        // coche» a 28. No es que estuviera lejos del texto — es que no estaba en ningún raíl, y un
        // chevron a mitad de aire no lee como aire, lee como una alineación rota.
        //
        // El sangrado izquierdo SÍ se conserva (`start = tile + gap`): lo que estaba mal era el
        // extremo derecho. Llevarse también el pin al margen de la card habría reflujado la lectura
        // de la ficha entera sin que nadie lo pidiera.
        // [UI-A-CHEVRON-MUST-SIT-ON-THE-CARD-RAIL-001] [UI-LIST-ITEM-001]
        if (statusLine != null) {
            Spacer(Modifier.height(STATUS_LINE_GAP_DP.dp))
            Box(modifier = Modifier.padding(start = (TILE_DP + TILE_TEXT_GAP_DP).dp)) {
                statusLine()
            }
        }
    }
}

/**
 * La baldosa del coche: el glifo sobre su cuadro, con el halo de radar cuando va en ruta.
 *
 * Extraída de [VehicleIdentityHeader] porque desde
 * [HOME-A-CARS-NEWS-RIDES-ITS-OWN-CARD-001] hay una segunda fila que tiene que decir de qué coche
 * habla sin repetir su nombre: la card con noticia sustituye la fila de identidad por la noticia, y
 * la identidad se queda donde no estorba — en el glifo. Copiar los cuatro números aquí al lado
 * habría dado dos baldosas que se separan a la primera edición.
 */
@Composable
fun VehicleGlyphTile(
    vehicle: Vehicle,
    modifier: Modifier = Modifier,
    isDriving: Boolean = false,
    haloColor: Color = Color.Unspecified,
) {
    Box(
        modifier = modifier
            .size(TILE_DP.dp)
            .clip(RoundedCornerShape(TILE_CORNER_DP.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (isDriving && haloColor != Color.Unspecified) {
            DrivingRadarHalo(diameter = GLYPH_DP.dp, color = haloColor)
        }
        VehicleGlyph(
            carbody = vehicle.carbodyType,
            size = vehicle.sizeCategory,
            glyphSize = GLYPH_DP.dp,
            color = vehicle.color,
        )
    }
}

private const val TILE_DP = 56
private const val TILE_CORNER_DP = 14
private const val GLYPH_DP = 44
private const val TILE_TEXT_GAP_DP = 12
private const val NAME_META_GAP_DP = 6
/** A touch more air than between the two attribute lines: the state is a different KIND of fact. */
private const val STATUS_LINE_GAP_DP = 7
// 6 — the Home chip's glyph-to-name gap (`CHIP_TOP_GAP_DP`), duplicated as a value rather than
// shared: it is the same NUMBER, not the same decision, and the chip owns its own rhythm.
// [UI-VEH-STATUS-IS-A-GLYPH-NOT-A-LABEL-001]
private const val WATCH_NAME_GAP_DP = 6
private const val TRAILING_GAP_DP = 8
private const val SUBTITLE_SEPARATOR = " · "
