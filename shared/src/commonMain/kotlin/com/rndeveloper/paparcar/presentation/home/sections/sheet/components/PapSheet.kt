package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.rndeveloper.paparcar.domain.model.SpotTone
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.VehicleColor
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.ui.components.DrivingRadarHalo
import com.rndeveloper.paparcar.ui.components.PapListItem
import com.rndeveloper.paparcar.ui.components.PapShimmerBox
import com.rndeveloper.paparcar.ui.components.PapStepperSlot
import com.rndeveloper.paparcar.ui.components.SpotPuckIcon
import com.rndeveloper.paparcar.ui.components.VehicleGlyph
import com.rndeveloper.paparcar.domain.model.SpotFreshness
import com.rndeveloper.paparcar.ui.theme.PapBorders
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PapFonts
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.greenOutline
import com.rndeveloper.paparcar.ui.components.PapShimmerBlockScale
import com.rndeveloper.paparcar.ui.components.PapStepperButtonSize
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PapGap
import com.rndeveloper.paparcar.ui.theme.PaparcarSpacing
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_peek_dismiss_cd

/**
 * The unified bottom-sheet molde for every Home state — browse, selected
 * parking, selected spot, add parking, add spot (and the AddingZone escape
 * hatch). One anatomy, five optional slots: [UI-SHEET-001]
 *
 *  ┌──────────────────────────────────────────────────────────────────────┐
 *  │ ‹  [LEAD]  EYEBROW (state-tinted caps)                       [×]   › │
 *  │     46dp   Title — `cardTitle`; hasta 2 lineas en un modal           │
 *  │            subtitle (optional, muted)                                │
 *  ├──────────────────────────────────────────────────────────────────────┤
 *  │  banner  — info strip (icon + title + sub) on surfaceContainerHigh   │
 *  │  meta    — icon+value rows       [metaAction: 38dp edit icon-button] │
 *  │  chips   — filter / size selector row                                │
 *  │  content — el DESGLOSE que respalda los hechos                       │
 *  │  actions — PapFooterButton stack (max 1 Filled = the loop action)    │
 *  └──────────────────────────────────────────────────────────────────────┘
 *
 * ⛔ **Gramatica de un MODAL de detalle — cinco bandas, siempre en este orden**
 * [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
 *
 * Un modal contesta cinco preguntas de arriba abajo, y cada banda contesta **una**. Si un dato no
 * sabe a que pregunta responde, no es que falte un slot: es que no se ha decidido que es.
 *
 * | Banda | Pregunta | Slot | Que va aqui |
 * |---|---|---|---|
 * | 1 · Identidad | ¿que es esto? | header | el tile con su color + el NOMBRE, y nada mas |
 * | 2 · Aviso | ¿algo que saber antes de leer? | `banner` | solo cuando algo no se cumple |
 * | 3 · Hechos | ¿donde esta y como es? | `meta` | filas de un glifo y una linea, en `body` |
 * | 4 · Desglose | la prueba, el detalle | `chips` / `content` | precios, notas, fiabilidad |
 * | 5 · Accion | ¿que hago? | `actions` | un CTA canonico |
 *
 * De ahi salen tres reglas que **no** son de gusto — cada una con su medicion en el Oppo:
 *
 *  1. **La cabecera NOMBRA; el cuerpo DESARROLLA.** El sitio y la distancia **no** van en el
 *     `subtitleSlot` de un modal: ahi comparten ancho con el racimo «‹ › ×» —432 px de 1080, los
 *     tres botones de 48 dp— y salen recortados como «Calle de …». Abren la banda 3, a todo el
 *     ancho, via `PeekPlaceRow` + `DistanceAndTimeRow`. El peek COLAPSADO si usa su linea 2: no
 *     tiene cuerpo donde desarrollar nada.
 *  2. **El titulo gana la jerarquia por ROL, no por color.** `cardTitle` (18sp Bold) contra `body`
 *     (14sp Normal) en las filas. Cuando el titulo bajo a `rowName` —el mismo 14sp SemiBold que
 *     `rowTitle`— tres filas de cuerpo le ganaban por acumulacion. Y el nombre va en `onSurface`:
 *     la identidad la lleva el TILE, que es justo lo que manda [UI-COLOR-DOCTRINE-001].
 *  3. **Una sola columna de hechos.** Todo lo de la banda 3 cuelga del MISMO canal de glifo, tenga
 *     glifo o no (`PeekMetaRow(icon = null)` lo deja vacio, no lo colapsa). Tres hechos del parking
 *     publico se pintaban 28 dp a la izquierda de sus vecinos por venir de un componente propio.
 *
 * ⛔ Y `content` **ya no es un «escape hatch»**: cuatro modales lo usaban de cuerpo y los cuatro
 * cerraban con el mismo `Spacer(PapGap.block)` escrito a mano — la firma exacta de un hueco que
 * falta. El aire de cada banda lo pone la hoja, una vez.
 *
 * Un FORMULARIO sigue viviendo ahi y es legitimo (`AddingZonePeek`, `ReportPeek`): un formulario
 * **es** el cuerpo de su hoja, no el desglose de unos hechos, asi que ocupa la banda 4 entera y no
 * tiene banda 3. Lo que ya no vale es colgar de `content` un HECHO —eso es banda 3— ni un AVISO
 * —eso es `banner`— solo porque el slot aceptaba cualquier cosa.
 *
 * ⚠️ Fuera de esta gramatica queda `ParkingHistoryDetailScreen`, que compone su banda 1 con
 * eyebrow + titulo + subtitulo (la ciudad). No es Home y su cabecera no pelea con un `×`, asi que
 * no se ha tocado; si alguna vez se unifica, la ciudad baja a la banda 3 como una fila mas.
 *
 * **Subject rule** (decided by the caller): the lead tile is the SUBJECT of
 * the sheet. With a parked car the subject is the vehicle (lead = car glyph,
 * free-spot count moves to the trailing pill); with no parked car the subject
 * is the zone (lead = counter tile).
 */
@Composable
internal fun PapSheet(
    lead: PapSheetLead,
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    /**
     * How this sheet is CLOSED — and, by the same token, whether it wears a ✕ at all: the button is
     * drawn if and only if there is a close to run. Null (the default) = this surface has no way out
     * of its own (browse, a screen whose exit is the top bar's back), so no ✕.
     *
     * ⛔ One parameter on purpose. It used to be two — a `trailing = Dismiss` default that drew the
     * button and an `onDismiss = {}` default that did nothing — and every combination compiled,
     * including the one that makes no sense. Browse fell into it the moment
     * [UI-NO-LOCATION-WEARS-THE-ACCURACY-PILL-001] removed the badge that had been occupying its
     * trailing slot: the header inherited the default ✕, wired to the default no-op, and promised an
     * exit that did not exist. [UI-A-CLOSE-X-MUST-CLOSE-SOMETHING-001]
     */
    onDismiss: (() -> Unit)? = null,
    eyebrowTone: PapSheetEyebrowTone = PapSheetEyebrowTone.Neutral,
    /** Overrides the tone colour — the reliability palette of a community spot. */
    eyebrowColor: Color? = null,
    /** Substring of [eyebrow] (the vehicle NAME) tinted with [eyebrowHighlightColor]; the state
     *  words around it keep the base eyebrow colour. [UI-COLOR-DOCTRINE-001] */
    eyebrowHighlight: String? = null,
    eyebrowHighlightColor: Color? = null,
    /**
     * A mark that belongs to the TITLE and not to the sheet: a glyph drawn inline before the name,
     * where a list row would wear it too. Null (the default) ⇒ the header is rendered exactly as it
     * was. [FUEL-A-FOLLOW-MUST-SHOW-ITSELF-001]
     *
     * ⛔ Additive on purpose, rather than the fuel sheet hand-rolling its own header: the one thing
     * every peek must share is the header band, because the collapse cut lands under it and a twin
     * built next door is a second definition of that height. The mark rides INSIDE the one-line
     * title row — it cannot grow the band.
     */
    titleMark: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    /** Modal-only escape hatch: peeks keep the fixed 1-line title (anchoring depends on the
     *  reserved header height); a MODAL sheet whose title carries a vehicle name may pass 2 so
     *  "¿Has aparcado el Škoda Kamiq?" doesn't truncate. [UX-PARK-FLOW-001 C4, device 06-08] */
    titleMaxLines: Int = 1,
    /**
     * Peeks keep ONE line: their header height is a fixed design derivation that the collapse cut
     * depends on. A sheet that exists to EXPLAIN something passes Int.MAX_VALUE — there the height
     * is free, and truncating prose with a "…" while half the screen is empty is the opposite of
     * explaining. [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] [UI-SHEET-006]
     */
    subtitleMaxLines: Int = 1,
    /** Pager chrome: the ‹ / › that open the pin before/after this one. Null (the default) leaves
     *  the header at full width, exactly as it was before the stepper existed.
     *  [UI-PEEK-STEPS-BETWEEN-PINS-001] */
    stepper: PapSheetStepper? = null,
    /**
     * La línea del TÍTULO entera, cuando no es un título sino una **composición de dos piezas**: un
     * nombre y su calificador. [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
     *
     * ⛔ **Existe porque hay cabeceras cuyo sujeto NO es el título.** En el peek del coche aparcado
     * el sujeto es el COCHE — «Seat León · aparcado hace 31m»— y la dirección es dónde está, no su
     * nombre. Con `title` a secas la dirección se llevaba `cardTitle` y el coche quedaba en un
     * eyebrow en caps: la jerarquía, invertida (user, viéndolo en el emulador).
     *
     * Es el mismo contrato que [PapListItem] ya tenía y que la fila de plaza usa para su glifo POI;
     * aquí sólo se reenvía. Gana a [titleMark], que es el caso pequeño del mismo mecanismo.
     * [UI-LIST-ITEM-001]
     */
    titleSlot: (@Composable () -> Unit)? = null,
    /** La línea 2 entera — **dónde · a cuánto**, con la cifra en su propia columna para que lo que
     *  elida sea el sitio y no el número. Ver `PeekCarLines`. */
    subtitleSlot: (@Composable () -> Unit)? = null,
    banner: (@Composable () -> Unit)? = null,
    meta: (@Composable ColumnScope.() -> Unit)? = null,
    metaAction: (@Composable () -> Unit)? = null,
    chips: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .pagerSwipe(stepper)
            .padding(horizontal = PAP_SHEET_HORIZONTAL_PAD_DP.dp),
    ) {

        // ── Slot 1 · Header — same leading + overline + title + subtitle + trailing anatomy as
        // every other row, delegated to the shared PapListItem. The height is RESERVED at the
        // 3-line size (eyebrow + title + subtitle) in every state — 2-line headers breathe —
        // so the sheet's collapsed "header band" is one FIXED design derivation and a collapse
        // cut always lands exactly under the header. [UI-LIST-ITEM-002] [UI-SHEET-006]
        //
        // The pager chevrons join the × in the TRAILING cluster of this same row — chrome next to
        // chrome, so the peek's actions stay actions. A 32dp chevron is shorter than the 46dp lead
        // tile, so the reserved header height (and with it the peek's measured height, the collapse
        // cut and the peek/nav divider) is untouched. [UI-PEEK-STEPS-BETWEEN-PINS-001] [UI-SHEET-006]
        // ── Slot 0 · Chrome — `‹ › ×` en SU PROPIA línea, encima del título ─────────
        // ⛔ **Estaba en el `trailing` de la cabecera y se comía el título** (user, 02-11):
        // *«los iconos ‹ › × deberían ir una línea arriba del título para no comérselo, asumiendo
        // el mismo comportamiento de diseño que otras modales de este estilo»*. Y es el patrón de
        // una bottom sheet de Material 3: el chrome vive arriba, con el asa, y el contenido empieza
        // después — no compartiendo renglón con él.
        //
        // Medido en el Pixel 8: el racimo ocupaba 432 px de los 1080 y al título le dejaba 345
        // —unos 12 caracteres a 18sp Bold—, que es por lo que salían «Parking de …» y «apar…».
        // En su propia fila el título pasa a disponer de ~777 px: **2,25×**.
        // [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001] [UI-PEEK-STEPS-BETWEEN-PINS-001]
        papSheetHeaderChrome(stepper, onDismiss)?.invoke()

        PapListItem(
            modifier = Modifier.defaultMinSize(minHeight = papSheetHeaderReservedHeight()),
            // Blank = no eyebrow at all, not an empty line with its spacing. An embedded sheet (the
            // first-step explainer inside Home's sheet) drops it so the surface does not read as two
            // stacked headers. [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
            overline = eyebrow.takeIf { it.isNotBlank() },
            overlineColor = eyebrowColor ?: eyebrowTone.color(),
            overlineStyle = PaparcarType.current.eyebrow,
            overlineHighlight = eyebrowHighlight,
            overlineHighlightColor = eyebrowHighlightColor ?: (eyebrowColor ?: eyebrowTone.color()),
            title = title,
            titleStyle = PaparcarType.current.cardTitle,
            titleMaxLines = titleMaxLines,
            // Con marca, la línea del título pasa a ser estructura — el mismo `titleSlot` con el que
            // la fila de plaza mete su glifo POI. Sin marca no se toca nada: `title` sigue mandando.
            // [UI-LIST-ITEM-001] [FUEL-A-FOLLOW-MUST-SHOW-ITSELF-001]
            // ⛔ El slot completo manda sobre la marca: son el mismo mecanismo a dos tamaños, y
            // ofrecer los dos a la vez sería dejar que dos composiciones se pisen.
            titleSlot = titleSlot ?: titleMark?.let { mark ->
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TITLE_MARK_GAP_DP.dp),
                    ) {
                        mark()
                        Text(
                            text = title,
                            style = PaparcarType.current.cardTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = titleMaxLines,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            },
            subtitle = subtitle,
            subtitleSlot = subtitleSlot,
            subtitleStyle = PaparcarType.current.caption,
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
            subtitleMaxLines = subtitleMaxLines,
            // Horizontal inset already applied by the parent Column; keep the sheet's own top/bottom.
            // ⛔ **Top 2, no 12** (user, 03-11): *«el peek sigue teniendo la misma altura de cuando
            // tenía 3 líneas de texto, podríamos reducir un pelin su altura»*. El aire de arriba lo
            // pone ahora la FILA DE CHROME —y donde no hay chrome, el bloque del asa—, así que los
            // 12 de la cabecera eran un segundo margen sobre el mismo hueco.
            //
            // ⚠️ Lo que NO se puede recortar es `HEADER_TEXT_STACK_SP`: parece que sobra una línea
            // porque los modales ya no pintan eyebrow, pero `UnmarkedCarHeader` —un estado real del
            // peek: coche sin aparcamiento marcado— sí pinta las tres. Bajarlo a dos líneas haría ESE
            // estado más alto que los demás, que es justo la desigualdad que el user prohibió.
            contentPadding = PaddingValues(top = HEADER_TOP_PAD_DP.dp, bottom = HEADER_BOTTOM_PAD_DP.dp),
            gap = 12.dp,
            leading = { PapSheetLeadTile(lead) },
        )

        // ── Slot 2 · Banner ───────────────────────────────────────────────
        if (banner != null) {
            banner()
            Spacer(Modifier.height(PapGap.block))
        }

        // ── Slot 3 · Meta rows + optional edit action ─────────────────────
        if (meta != null || metaAction != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm),
                ) {
                    meta?.invoke(this)
                }
                if (metaAction != null) {
                    Spacer(Modifier.width(12.dp))
                    metaAction()
                }
            }
            Spacer(Modifier.height(PapGap.block))
        }

        // ── Slot 4 · Chips ────────────────────────────────────────────────
        if (chips != null) {
            chips()
            Spacer(Modifier.height(PapGap.block))
        }

        // ── Slot 5 · Detalle — el desglose que respalda los hechos de arriba
        // ⛔ **Era «escape hatch (forms — AddingZone)» y lo usaban CUATRO modales de detalle**
        // (plaza, parking, gasolinera, zona) para colgar su cuerpo. Un hueco sin gap declarado:
        // los cuatro terminaban con el MISMO `Spacer(PapGap.block)` escrito a mano, que es la firma
        // exacta de un hueco que falta. Ahora la banda existe y el aire lo pone la hoja, una vez.
        // [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
        if (content != null) {
            content(this)
            Spacer(Modifier.height(PapGap.block))
        }

        // ── Slot 6 · Actions ──────────────────────────────────────────────
        actions?.invoke(this)

        // Bottom air only when the sheet has a body — a bare header (browse
        // collapsed) keeps the tight peek rhythm so the peek/nav divider
        // stays seated. [BUG-PEEK-DIVIDER-ALIGN]
        val hasBody = banner != null || meta != null || metaAction != null ||
            chips != null || content != null || actions != null
        if (hasBody) Spacer(Modifier.height(16.dp))
    }
}

/**
 * The pager chrome of a sheet header: which pin the ‹ / › open, or null on the side where there is
 * none. Both null ⇒ the header renders untouched, at full width. [UI-PEEK-STEPS-BETWEEN-PINS-001]
 */
internal data class PapSheetStepper(
    val prevContentDescription: String,
    val nextContentDescription: String,
    val onPrev: (() -> Unit)?,
    val onNext: (() -> Unit)?,
) {
    val hasAny: Boolean get() = onPrev != null || onNext != null
}

/**
 * El chrome de la hoja: `‹ › ×`, **en su propia fila sobre el título y repartido a los extremos**
 * — el par de chevrones al principio, el × al final.
 *
 * ⛔ **No lleva flecha de atrás, y es deliberado** (preguntado por el user el 03-11). Tres razones:
 * un `←` al lado del `‹` son dos glifos apuntando al mismo sitio con significados distintos;
 * «atrás» promete una pila de navegación que aquí no existe —seleccionar un pin no navega, cambia
 * la selección—; y el atrás del sistema ya cierra la hoja, así que pintarlo gastaría 48 dp en
 * repetir lo que el móvil ya hace. [UI-A-CLOSE-X-MUST-CLOSE-SOMETHING-001] The chevrons come FIRST and the dismiss × stays last,
 * where the user already reaches for it — no state loses its way out. [UI-PEEK-STEPS-BETWEEN-PINS-001]
 *
 * The three don't look alike on purpose: a filled circle acts on THIS card (dismiss), a bare glyph
 * moves BETWEEN cards. With three identical tonal pills in a row, the × and the › read as twins and
 * closing by accident is one mis-tap away.
 *
 * The × is drawn from [onDismiss] itself — no separate "should there be one" flag — so a header can
 * never show a way out it does not have. [UI-A-CLOSE-X-MUST-CLOSE-SOMETHING-001]
 */
private fun papSheetHeaderChrome(
    stepper: PapSheetStepper?,
    onDismiss: (() -> Unit)?,
): (@Composable () -> Unit)? {
    val pager = stepper?.takeIf { it.hasAny }
    if (pager == null && onDismiss == null) return null
    return {
        Row(
            modifier = Modifier.fillMaxWidth().height(PapSheetChromeRowHeight),
            // ⛔ **A los EXTREMOS, no apelotonados al final** (user, 03-11): *«redistribuir
            // horizontalmente los botones ‹ › ×»*. Moverse ENTRE pines y CERRAR este son dos
            // trabajos distintos, y ahora que el chrome tiene fila propia a todo el ancho se separan
            // por la máxima distancia posible. Eso resuelve de raíz el mal toque que el KDoc de
            // abajo describía y que el hueco de 10 dp sólo mitigaba — con él murió
            // `STEPPER_TO_DISMISS_GAP_DP`, porque el reparto ya es el separador.
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // El par ES un solo control, así que sigue junto; la fila vacía cuando no hay pager deja
            // que `SpaceBetween` empuje el × a su esquina de siempre.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pager != null) {
                    PapStepperSlot(
                        icon = Icons.Rounded.ChevronLeft,
                        contentDescription = pager.prevContentDescription,
                        onClick = pager.onPrev,
                    )
                    PapStepperSlot(
                        icon = Icons.Rounded.ChevronRight,
                        contentDescription = pager.nextContentDescription,
                        onClick = pager.onNext,
                    )
                }
            }
            if (onDismiss != null) PapSheetDismissButton(onDismiss = onDismiss)
        }
    }
}

/**
 * Swiping the card is the chevrons' gesture twin: dragging LEFT pulls the next pin in from the right
 * (same as ›), dragging RIGHT the previous one (same as ‹) — and the header's page-turn plays the
 * matching slide, so finger and motion agree. Mirrors the history detail's gesture verbatim.
 * [UI-PEEK-STEPS-BETWEEN-PINS-001] [HISTORY-DETAIL-002]
 *
 * Horizontal only: it waits for HORIZONTAL touch slop, so the sheet's own vertical drag underneath
 * still gets every up/down gesture. Trigger-on-release with a distance threshold, so taps on the
 * card's buttons pass through untouched.
 */
@Composable
private fun Modifier.pagerSwipe(stepper: PapSheetStepper?): Modifier {
    if (stepper == null || !stepper.hasAny) return this
    val latest by rememberUpdatedState(stepper)
    return this.pointerInput(Unit) {
        var dragged = 0f
        detectHorizontalDragGestures(
            onDragStart = { dragged = 0f },
            onDragEnd = {
                val threshold = SWIPE_TRIGGER_DP.dp.toPx()
                when {
                    dragged <= -threshold -> latest.onNext?.invoke()
                    dragged >= threshold -> latest.onPrev?.invoke()
                }
            },
        ) { _, dragAmount -> dragged += dragAmount }
    }
}

/**
 * The 46dp rounded-square lead tile — ALWAYS boxed, never a bare glyph. The
 * variant is the sheet's subject; its container colour is the state colour
 * (own = surface/green, manual report = green announce, community = blue).
 */
internal sealed interface PapSheetLead {
    /** The user's vehicle — full-colour illustration on a quiet surface tile. [loading] shows a
     *  skeleton instead of the pictogram while the vehicle is still resolving from Room, so the
     *  tile never flashes the generic fallback car before the real one arrives. */
    data class Vehicle(
        val carbody: CarbodyType?,
        val size: VehicleSize?,
        val color: VehicleColor? = null,
        val loading: Boolean = false,
        /** Non-null ⇒ this car's trip is running RIGHT NOW: the tile breathes the radar halo in
         *  this colour, the same motion the vehicle chip already shows. Pass the vehicle's identity
         *  colour, resolved at the call site by the one resolver (`vehicleIdentityColor`) — the tile
         *  never re-derives it. Null (default) ⇒ a still tile. [UI-PEEK-DRIVING-HAS-NO-MOTION-001] */
        val drivingHaloColor: Color? = null,
    ) : PapSheetLead

    /** Free-spot counter — digit + unit. Green with n>0, amber with 0. */
    data class SpotCounter(val count: Int) : PapSheetLead

    /** A community spot — the SAME reliability puck drawn on the map marker and the
     *  spot list row (colour + TTL ring + badge encode the tier), never a flat "P".
     *  [HOME-PUCK-001] */
    data class CommunitySpot(
        val reliability: SpotTone,
        val enRouteCount: Int = 0,
        /** Eyewitness report. Passed through to the puck, which draws it the same. [UI-COLOR-DOCTRINE-001 F5] */
        val isManual: Boolean = false,
        /** La salida se dedujo: el puck lo dice en su disco.
         *  [SPOT-A-DEDUCED-SPOT-LOOKS-LIKE-A-SURE-ONE-001] */
        val unconfirmed: Boolean = false,
    ) : PapSheetLead

    /** Reporting a free spot — megaphone on the action-green tile. */
    data object Announce : PapSheetLead

    /**
     * A COMPLEMENT's count — car parks, fuel — in the expanded header.
     * [HOME-THE-WATCH-COMES-FIRST-AND-THE-HEADER-FOLLOWS-THE-LANE-001]
     *
     * Two faces, one variant: the figure once its lane has been asked, the subject's own mark while
     * it has not. ⛔ [count] is nullable for exactly that reason — a `0` nobody measured is a claim
     * about the world. [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
     *
     * ⛔ Never green, whatever the figure. The green counter says «this is the product», and free
     * spots are the product; handing the same tile to a complement spends the one accent this
     * screen just finished rationing. [UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001]
     */
    data class NearbyCounter(
        val count: Int?,
        val icon: ImageVector,
        /**
         * El carril está preguntando ahora mismo.
         * [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
         *
         * ⛔ **Late sólo cuando no hay cifra que enseñar**, que es la MISMA frase que dicen las filas
         * de debajo (`isLoading && lista vacía` en `HomeFuelLane` / `HomeParkingsLane`). Con la
         * respuesta recordada del disco pintada mientras vuela la de red
         * [PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001], esta cápsula escondía en un
         * shimmer el mismo 5 que la pestaña y las cinco filas estaban enseñando a 300 dp de ahí.
         * [HOME-A-SKELETON-SAYS-WE-ARE-ASKING-001]
         */
        val isLoading: Boolean = false,
    ) : PapSheetLead

    /** Generic escape (AddingZone's picked icon).
     *
     * [container]/[tint] override the default tonal pair for a subject that already HAS a colour of
     * its own on the map: a car park's peek must wear the same blue disc as its marker and its row,
     * or the same thing is three different colours across three surfaces. Null = the generic tonal
     * pair, which is what an icon with no subject colour (a zone's picked glyph) wants.
     * [MAP-A-CAR-PARK-MARKER-WEARS-ITS-OWN-BLUE-001] */
    data class GenericIcon(
        val icon: ImageVector,
        val container: Color? = null,
        val tint: Color? = null,
    ) : PapSheetLead
}

/**
 * ¿Late la cápsula del contador en vez de enseñar una cifra?
 * [HOME-A-SKELETON-SAYS-WE-ARE-ASKING-001]
 *
 * **Late cuando el carril pregunta y no hay nada que enseñar** — exactamente la misma frase que
 * dicen las filas de debajo (`isLoading && lista vacía`, en `HomeFuelLane` y `HomeParkingsLane`).
 *
 * ⛔ El «y no hay nada» faltaba, y la contradicción se veía en una captura: parkings pintando su
 * respuesta RECORDADA del disco mientras vuela la de red
 * [PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001] — pestaña «5», cinco filas, y la
 * cápsula de arriba escondiendo ese mismo 5 detrás de un shimmer. Es el defecto que el KDoc de
 * `laneAnsweringHere` ya describía desde la otra punta, arreglado para las filas y no para la cifra.
 *
 * Un `0` o un `null` no son cifra que enseñar: ahí el latido es correcto y sigue.
 */
internal fun nearbyCounterPulses(count: Int?, isLoading: Boolean): Boolean =
    isLoading && (count ?: 0) == 0

/** Eyebrow tint: green = own action, muted = neutral context. (The old blue "Manual" tone died
 *  with the tertiary retirement — reporting a spot IS an action, and a report's provenance is not a
 *  colour's job.) [UI-COLOR-DOCTRINE-001 F6] */
internal enum class PapSheetEyebrowTone { Action, Neutral }

@Composable
private fun PapSheetEyebrowTone.color(): Color = when (this) {
    // The eyebrow is a WORD, so it takes the readable leg of the brand green.
    // [UI-COLOR-GREEN-TEXT-EARNS-ITS-CONTRAST-001]
    PapSheetEyebrowTone.Action -> PapColor.actionText
    PapSheetEyebrowTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
}

// ⛔ There was a `PapSheetTrailing` sealed interface here, deciding what the header's trailing slot
// held. It ended up with ONE member, `Dismiss`, once [UI-NO-LOCATION-WEARS-THE-ACCURACY-PILL-001]
// deleted the `Warning` badge — and a one-member choice that answers "is there a ✕?" is the same
// question `onDismiss` already answers, asked twice. Asked twice, the two can disagree, and they
// did: Browse drew the × from the `trailing` default and ran the `onDismiss` no-op.
// Now the lambda alone decides. [UI-A-CLOSE-X-MUST-CLOSE-SOMETHING-001]

@Composable
private fun PapSheetLeadTile(lead: PapSheetLead) {
    val cs = MaterialTheme.colorScheme
    when (lead) {
        is PapSheetLead.Vehicle -> LeadTileBox(container = cs.surfaceContainerHigh) {
            if (lead.loading) {
                // Vehicle not resolved yet: breathe a placeholder instead of flashing the generic
                // fallback car (the tile would otherwise show a wrong default for one frame). [UI-VEHICLE-ICON-SKELETON-001]
                PapShimmerBox(modifier = Modifier.size(LEAD_GLYPH_DP.dp), shape = CircleShape)
            } else {
                Box(contentAlignment = Alignment.Center) {
                    // A running trip breathes the SAME halo as the vehicle chip's identity row, at
                    // the same glyph-to-halo proportion — one motion vocabulary across both
                    // surfaces. Contained in the glyph box, so the tile never resizes.
                    // [UI-PEEK-DRIVING-HAS-NO-MOTION-001] [UI-COLOR-DOCTRINE-001]
                    lead.drivingHaloColor?.let { DrivingRadarHalo(diameter = LEAD_GLYPH_DP.dp, color = it) }
                    // Full-colour brand illustration (level-3) — never tinted. [INACTIVE-OPAQUE-001]
                    VehicleGlyph(
                        carbody = lead.carbody,
                        size = lead.size,
                        glyphSize = LEAD_GLYPH_DP.dp,
                        color = lead.color,
                    )
                }
            }
        }

        is PapSheetLead.SpotCounter -> {
            val hasSpots = lead.count > 0
            // ⛔ Cero plazas NO es ámbar. El ámbar es "esto te va a caducar" — la rampa de frescura de
            // las plazas — y un contador a cero no es una advertencia: es un hecho tranquilo sobre el
            // mundo, del que además el usuario no tiene la culpa ni el remedio. Pintarlo de alerta era
            // gastar el segundo acento de la app en no decir nada.
            // [UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001] [UI-COLOR-DOCTRINE-001]
            val accent = if (hasSpots) PapColor.brandData else cs.onSurfaceVariant
            LeadTileBox(container = if (hasSpots) cs.primaryContainer else cs.surfaceContainerHighest) {
                // ⛔ **Sólo la cifra: «LIBRES» se fue** (user, 03-11): *«6 libres en plazas
                // comunitarias, quita libres, es redundante»*. Y lo era por partida doble — el
                // nombre que hay al lado ya dice de qué se cuentan, y una plaza comunitaria **es**
                // libre por definición: la palabra no añadía un hecho, repetía el sujeto.
                //
                // Con ella se fueron sus dos acompañantes, que existían sólo para sostenerla: el
                // `Column` y el `opticalLift` de `figureOpticalLiftSp`, que compensaba el hueco
                // muerto ENTRE el dígito y la unidad. Sin unidad no hay par que equilibrar, y el
                // tile queda exactamente igual que el de parkings y gasolineras — que nunca
                // tuvieron unidad, y por eso era este el que desentonaba. [UI-SHEET-001]
                Text(
                    text = "${lead.count}",
                    style = PaparcarType.current.counter,
                    color = accent,
                    maxLines = 1,
                )
            }
        }

        is PapSheetLead.NearbyCounter -> run {
            // La cifra del sujeto que estás mirando va en verde, como su pestaña: esta cápsula
            // SIEMPRE habla del carril activo, así que sigue habiendo un solo verde en pantalla.
            // Sin cifra no hay nada que destacar — la marca a secas se queda neutra.
            // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
            val count = lead.count
            // La misma regla que la cápsula de plazas, dicha una vez: **hay algo → verde; cero o
            // nada que contar → neutro**. Un cero no es una advertencia ni un logro, es un hecho
            // tranquilo. [UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001]
            val hasAny = (count ?: 0) > 0
            val accent = if (hasAny) PapColor.brandData else cs.onSurfaceVariant
            // ⛔ …y sólo cuando NO hay cifra: esconder el número que las filas ya están pintando es
            // contarle al usuario dos cosas distintas sobre lo mismo.
            // [HOME-A-SKELETON-SAYS-WE-ARE-ASKING-001]
            if (nearbyCounterPulses(count = count, isLoading = lead.isLoading)) {
                // ⛔ El latido ES la cápsula, no algo DENTRO de ella. Con el shimmer metido en un
                // rectangulito centrado se leían DOS cosas — una caja quieta con algo moviéndose
                // dentro — y lo que está esperando es el contador entero. Misma huella (el tile
                // canónico) y mismo radio, así que al llegar la cifra nada salta de sitio.
                // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
                PapShimmerBox(
                    modifier = Modifier.size(LEAD_TILE_DP.dp),
                    shape = PapShapes.card,
                    alphaScale = PapShimmerBlockScale,
                )
                return@run
            }
            LeadTileBox(
                container = if (hasAny) cs.primaryContainer else cs.surfaceContainerHighest,
            ) {
            if (count != null) {
                Text(
                    text = "$count",
                    style = PaparcarType.current.counter,
                    color = accent,
                    maxLines = 1,
                )
            } else {
                // Nobody has asked yet: the tile says WHAT it would count, never how many.
                Icon(
                    imageVector = lead.icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(LEAD_ICON_DP.dp),
                )
            }
            }
        }

        is PapSheetLead.CommunitySpot ->
            // Bare (no tile box): the puck is a self-coloured level-3 marker, exactly as it
            // renders on the map and in the spot list — one shared subject across all three
            // surfaces. [HOME-PUCK-001]
            SpotPuckIcon(
                reliability = lead.reliability,
                enRouteCount = lead.enRouteCount,
                isManual = lead.isManual,
                unconfirmed = lead.unconfirmed,
                modifier = Modifier.size(LEAD_TILE_DP.dp),
            )

        PapSheetLead.Announce -> LeadTileBox(container = cs.primaryContainer) {
            Icon(
                imageVector = Icons.Rounded.Campaign,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(LEAD_ICON_DP.dp),
            )
        }

        is PapSheetLead.GenericIcon -> LeadTileBox(container = lead.container ?: cs.primaryContainer) {
            Icon(
                imageVector = lead.icon,
                contentDescription = null,
                tint = lead.tint ?: cs.primary,
                modifier = Modifier.size(LEAD_ICON_DP.dp),
            )
        }
    }
}

@Composable
private fun LeadTileBox(container: Color, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(LEAD_TILE_DP.dp)
            .clip(PapShapes.card)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Slot-2 banner — info strip on `surfaceContainerHigh` with a small circular
 * icon badge. Replaces the old `HelperRow`.
 */
@Composable
internal fun PapSheetBanner(
    title: String,
    modifier: Modifier = Modifier,
    /** Two lines by default (it sits under a header whose height is derived); an explainer passes
     *  Int.MAX_VALUE so the caveat is READ, not cut mid-sentence.
     *  [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] */
    maxLines: Int = 2,
    subtitle: String? = null,
    icon: ImageVector = Icons.Rounded.Info,
    iconTint: Color = PapColor.attention,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PapShapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(BANNER_BADGE_DP.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = BANNER_BADGE_BG_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(BANNER_ICON_DP.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = PaparcarType.current.rowTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Twin round icon-button for the meta-action row — a bare 38dp circle with a green
 * outline and a primary-tinted glyph. Groups low-emphasis utilities (navigate to the
 * car, edit the pin) side by side, so the full-width footer stays for the ONE loop
 * action ("I'm leaving"). [contentDescription] replaces the visible label for
 * accessibility. [UX-PARKED-STATE-001]
 */
@Composable
internal fun PapSheetRoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            // Área de toque al mínimo de 48 sin tocar lo que pinta: `Modifier.clickable` no lo
            // aplica solo. [UI-THE-SPACING-SWEEP-IS-SCREEN-BY-SCREEN-001]
            .minimumInteractiveComponentSize()
            .size(SHEET_ICON_BUTTON_DP.dp)
            .clip(CircleShape)
            .border(BorderStroke(PapBorders.medium, greenOutline), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(SHEET_ICON_GLYPH_DP.dp),
        )
    }
}

/** Dismiss × — 34dp circle on `surfaceContainerHigh` so it reads as a real control. */
@Composable
private fun PapSheetDismissButton(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(DISMISS_BUTTON_DP.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Close,
            contentDescription = stringResource(Res.string.home_peek_dismiss_cd),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(DISMISS_ICON_DP.dp),
        )
    }
}

/** Horizontal content inset of every sheet state — the 16dp sheet grid, so text
 *  doesn't step sideways when the sheet expands. [HOME-VEH-REFINE-001] */
internal const val PAP_SHEET_HORIZONTAL_PAD_DP = 16

/**
 * The collapsed "header band" of the sheet — drag pill block + the reserved 3-line
 * header. A design DERIVATION, not a measurement: deterministic for a given font
 * scale, and font-scale AWARE (the text stack is sp) so a larger system font never
 * clips the 46dp lead tile out of the band. [UI-SHEET-006]
 */
@Composable
internal fun papSheetHeaderBandHeight(): Dp =
    // The cut bites into the header's BOTTOM PADDING (14dp) instead of landing exactly on its
    // edge: with a zero-margin cut, the next block starts on the very cut line and any rounding
    // (or a meta icon standing taller than its text line) leaks a sliver above the nav. 8dp of
    // clearance keeps everything below the header reliably hidden. [UI-SHEET-006]
    // ⛔ **La fila de chrome se cuenta SIEMPRE, tenga chrome o no**, y es deliberado: el user fijó
    // que *«el peek tendrá siempre la misma altura y estructura para todos sus estados»*. Contarla
    // sólo cuando hay `‹ › ×` dejaría el peek de browse —que no los tiene— más bajo que el de un pin.
    PILL_BLOCK_DP.dp + PapSheetChromeRowHeight + papSheetHeaderReservedHeight() -
        BAND_BOTTOM_CLEARANCE_DP.dp

/** Reserved header min-height — vertical padding + max(lead tile, 3-line text stack).
 *  The RESERVE stays full-size; only the band cut above subtracts clearance. */
@Composable
private fun papSheetHeaderReservedHeight(): Dp {
    val textStack = with(LocalDensity.current) { HEADER_TEXT_STACK_SP.sp.toDp() } + HEADER_OVERLINE_GAP_DP.dp
    // ⛔ **Sin la fila de chrome**: esto es el `minHeight` del PROPIO `PapListItem`, y la fila vive
    // fuera de él. Sumárselo aquí lo contó dos veces y abrió ~130 px de aire muerto entre el
    // `‹ › ×` y el título — visto en el Pixel 8 nada más moverlo. Quien tiene que contar las dos
    // cosas es `papSheetHeaderBandHeight`, que mide la hoja PLEGADA.
    return HEADER_V_PAD_DP.dp + max(LEAD_TILE_DP.dp, textStack)
}

// Text stack of a 3-line header: eyebrow lh 14sp + title lh 24sp + caption lh 16sp.
// Keep in sync with the header roles used above.
private const val HEADER_TEXT_STACK_SP = 54
// PapListItem's overline→title gap (dp).
private const val HEADER_OVERLINE_GAP_DP = 2
// Aire entre la marca del título y el nombre — el mismo 5 dp que dejan las filas entre su glifo
// inline y el suyo, para que la marca se lea igual en la hoja y en la lista.
// [FUEL-A-FOLLOW-MUST-SHOW-ITSELF-001]
private const val TITLE_MARK_GAP_DP = 5
// Header contentPadding, declarado por partes para que el reserve no pueda derivar del total viejo.
private const val HEADER_TOP_PAD_DP = 2
private const val HEADER_BOTTOM_PAD_DP = 14
private const val HEADER_V_PAD_DP = HEADER_TOP_PAD_DP + HEADER_BOTTOM_PAD_DP
// Drag pill block above the header: top 8 + pill 4 + bottom 2. Keep in sync with HomePeekHandle.
internal const val PILL_BLOCK_DP = 14

// Alto de la fila de chrome (`‹ › ×`). Es el del propio botón: la fila no añade aire porque
// arriba ya está el bloque del asa y abajo el padding superior de la cabecera.
internal val PapSheetChromeRowHeight: Dp = PapStepperButtonSize
// How far the band cut bites into the header's bottom padding (< HEADER_V_PAD_DP's bottom 14).
private const val BAND_BOTTOM_CLEARANCE_DP = 8


// Swipe distance that commits a page step — same threshold as the history detail's, so the two
// surfaces need the same flick. Comfortably above tap slop, well below half the card's width.
private const val SWIPE_TRIGGER_DP = 64

// Component dimensions (not spacing tokens — they belong to this molde).
private const val LEAD_TILE_DP = 46
private const val LEAD_GLYPH_DP = 38
private const val LEAD_ICON_DP = 24
private const val DISMISS_BUTTON_DP = 34
private const val DISMISS_ICON_DP = 18
/** Lo que PINTA un redondo del peek. Lo que OCUPA es el mínimo táctil de 48 dp, que es más —
 *  quien calcule un hueco a su lado necesita este número. [UI-THE-SPACING-SWEEP-IS-SCREEN-BY-SCREEN-001] */
internal val PapSheetRoundButtonSize: Dp = SHEET_ICON_BUTTON_DP.dp
private const val SHEET_ICON_BUTTON_DP = 38
private const val SHEET_ICON_GLYPH_DP = 18
private const val BANNER_BADGE_DP = 20
private const val BANNER_ICON_DP = 13
private const val BANNER_BADGE_BG_ALPHA = 0.18f
