package com.rndeveloper.paparcar.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// --- 4. SPACING ---
object PaparcarSpacing {
    val xs    =  4.dp   // chip inner padding, icon gaps
    val sm    =  8.dp   // tight list spacing, small padding
    val md    = 12.dp   // card inner padding (compact)
    val lg    = 16.dp   // standard content padding
    val xl    = 20.dp   // section spacing
    val xxl   = 24.dp   // card padding / bottom-sheet top
    val xxxl  = 32.dp   // hero sections
    val huge  = 48.dp   // splash / onboarding top margin
}

/**
 * The air BETWEEN siblings, when that air is a role and not a free choice.
 * [UI-A-SCALE-NOBODY-USES-IS-NOT-A-SYSTEM-001]
 *
 * [PaparcarSpacing] is the scale of a PAGE and of a CARD, and its 118 uses are right. These are the
 * gaps it could not express: both fall BETWEEN two of its steps, so every call site that needed one
 * invented it — and the ones that did not reach for a literal landed on the neighbouring step
 * instead. That is what reads as inconsistency: not too much air, but **two airs** for one job.
 *
 * ⛔ The fix was NOT to re-cut the scale at 2dp. Measured on master `adffbbe43`, the 606 literal
 * `dp` in `presentation/` + `ui/` are FIVE vocabularies, not one — gap, control height, icon size,
 * stroke width, corner radius — and only this one is spacing. A finer scale would have given the
 * other four a number to copy and still no owner. Each family goes to its owner instead:
 * [PapControlHeight], [PapIconSize], [PapBorders], [PapShapes].
 *
 * Rule of thumb: if two siblings sit side by side inside one control or one block, the air between
 * them is a role — ask here. If you are spacing a card from a card, or content from the screen
 * edge, that is [PaparcarSpacing].
 */
object PapGap {
    /**
     * The air inside a ROW OF CHIPS (6dp) — between the chips, and between a chip's glyph and its
     * label.
     *
     * Measured in ten places that did not know about each other: `HomeFuelLane`,
     * `HomeParkingsLane`, `HomeSheetContent`, `HomeZoneChips`, `SpotFitRow`, `StatsPeriodBar`,
     * `CarbodyInfoCard`, `PaparcarFilterChip`, `NearbyLaneParts`, `BrowsePeek`. It is NOT in the
     * scale — it falls between `xs` 4 and `sm` 8 — which is exactly why it was re-decided every
     * time, and why the rows that did not re-decide it came out at 8 and read as looser.
     *
     * Why 6 and not 8: a chip already carries its own horizontal padding, so the gap is read on top
     * of that padding, not instead of it. At 8 the pair stops reading as one group.
     */
    val chipRow = 6.dp

    /**
     * The separation between BLOCKS of a peek or a sheet (14dp) — a title block from the meta rows,
     * the meta rows from the actions.
     *
     * Measured in nine places: `PapSheet` (×3), `AddingZonePeek` (×2), `FuelStationPeek`,
     * `PublicParkingPeek`, `SpotPeek`, `PapAlertDialog`. Also between two steps (`md` 12, `lg` 16),
     * same story as [chipRow].
     *
     * It is deliberately NOT `PaparcarSpacing.lg`: a peek is a dense surface that owns the bottom of
     * the screen, and 16 between every block is what pushed its actions off the fold.
     */
    val block = 14.dp

    /**
     * Glyph to label INSIDE a control (8dp) — a button's leading icon, a chip's icon, a meta row's
     * icon.
     *
     * One role, and it had two values: `PapButton` used 8 and `PapFooterButton` used 10. Nothing
     * distinguished the two cases — same glyph size, same label role — so the 10 was a coin toss
     * that happened to land in the other file.
     */
    val iconLabel = 8.dp

    /**
     * El `spacedBy` que hay que PEDIR para que se VEAN [visible] dp entre dos controles pulsables de
     * [controlHeight]. [UI-THE-SPACING-SWEEP-IS-SCREEN-BY-SCREEN-001]
     *
     * ⛔ **Una constante de hueco junto a un control no dice lo que se ve.** Material3 infla todo
     * `Surface(onClick = …)` hasta el mínimo táctil de 48 dp y pinta su altura real centrada, así
     * que cada control aporta [PapControlHeight.overhang] de aire invisible por lado:
     *
     * > hueco visible = `spacedBy` + (48 − altura) ... con un control a cada lado
     *
     * Medido: dos cápsulas de 40 dp con `spacedBy(12)` dejaban **20,2 dp** a la vista. Esto es lo
     * que explica por qué los huecos se re-decidieron a mano por todo el árbol: nadie estaba
     * afinando la constante, estaban afinando lo que SE VE contra una constante que no lo gobierna
     * sola. Misma enfermedad que la altura emergente de
     * [UI-A-SCALE-NOBODY-USES-IS-NOT-A-SYSTEM-001], un nivel más abajo.
     *
     * ⛔ Y **no se arregla quitando el mínimo táctil**: son 48 dp de área de toque: accesibilidad.
     * Recortarlos para ganar píxeles es pagar el diseño con el dedo del usuario. Lo que se corrige
     * es la CUENTA, no el área.
     *
     * Para un hueco entre un control y algo que NO lo es (un texto, un divisor), hay overhang de un
     * solo lado — pásalo con [sides] = 1.
     */
    fun betweenControls(visible: Dp, controlHeight: Dp, sides: Int = 2): Dp =
        (visible - PapControlHeight.overhang(controlHeight) * sides).coerceAtLeast(0.dp)
}

/**
 * El ritmo VERTICAL de una página: cuánto aire hay entre dos bloques de una pantalla que se
 * scrollea. [UI-A-PAGE-KEEPS-ONE-VERTICAL-RHYTHM-001]
 *
 * ## Por qué hay un tercer objeto de espaciado y no una constante más
 *
 * [PaparcarSpacing] es una ESCALA —una lista de números legales— y [PapGap] es el aire dentro de un
 * control o de un bloque. Ninguno de los dos dice cuál de sus pasos separa dos CARDS de una página,
 * y por eso cada pantalla lo eligió por su cuenta: medido el 19-10 sobre master, el margen de una
 * página estaba declarado **cinco veces en privado** (`CONTENT_H_PADDING`/`CONTENT_V_PADDING`,
 * `SCREEN_H_PADDING`/`LIST_V_PADDING` ×3, `H_PAD_DP`/`LIST_V_PAD_DP`) con dos valores distintos
 * para el mismo rol, y el hueco entre hermanos valía 8 en Ajustes, 12 en Vehículos y 16 entre las
 * dos cards de Actividad. Que cuatro de los cinco coincidieran en 16 a lo ancho era suerte: nada
 * los ataba.
 *
 * La ley se lee de **Ajustes**, que es la única pantalla que ya tenía su ritmo entero declarado en
 * un sitio —y la que el user señaló como la que quiere ver en todas (19-10).
 *
 * ## ⛔ Estos cinco números son huecos que se VEN, no `spacedBy` que se escriben
 *
 * Es la ley de [UI-THE-SPACING-SWEEP-IS-SCREEN-BY-SCREEN-001] un nivel más arriba: un control
 * pulsable más bajo de 48 dp arrastra [PapControlHeight.overhang] de aire invisible por lado, así
 * que una fila de chips pegada a una card con `spacedBy(8)` deja **15 dp** a la vista. Cuando uno
 * de los lados del hueco es un control, el call site pide el suyo así:
 *
 * ```kotlin
 * PapGap.betweenControls(PapPageRhythm.block, PapControlHeight.chip, sides = 1)
 * ```
 *
 * No se toca a mano: ese 15 es exactamente lo que el user vio como «en vehicles el espaciado
 * vertical es más alto», y lo que lo producía era la cuenta, no el gusto de nadie.
 */
object PapPageRhythm {
    /**
     * Dos bloques HERMANOS de una página (8dp) — card con card, card con una fila de chips, card
     * con un banner.
     *
     * Es el `spacedBy` del `LazyColumn` de Ajustes, y el paso de escala que hace que una lista de
     * cards se lea como una lista y no como cinco superficies sueltas. Absorbe el `12` de la
     * cabecera de la página de vehículo y el `16` entre las dos cards de Actividad.
     */
    val block = PaparcarSpacing.sm

    /**
     * El último bloque de una sección y el TEXTO de la cabecera de la siguiente (20dp).
     *
     * En Ajustes no es una constante sino una suma —los 8 del `spacedBy` más los 12 que
     * `SectionHeaderMuted` se pone encima—, y ese es justo el motivo por el que nadie podía
     * copiarla: para repetir el ritmo de otra pantalla había que reconstruir su aritmética. Aquí es
     * el hueco entero, que es lo que se ve.
     *
     * ⛔ No es `PaparcarSpacing.xl` por casualidad de valor: la escala no sabe nada de cabeceras.
     * Si la ley cambia, cambia aquí.
     */
    val beforeSection = 20.dp

    /**
     * La cabecera de sección y el PRIMER bloque de su sección (10dp).
     *
     * La mitad de [beforeSection], y esa asimetría es el trabajo que hace una cabecera: pegada a lo
     * que encabeza, despegada de lo que acaba de terminar. En Ajustes son los 2 dp de
     * `SECTION_HEADER_BOTTOM_DP` más los 8 del `spacedBy`.
     */
    val afterSection = 10.dp

    /** El contenido y el borde lateral de la pantalla (16dp). El `lg` de siempre, dicho por el rol
     *  que lo usa en vez de por cinco constantes privadas que se llamaban casi igual. */
    val sideInset = PaparcarSpacing.lg

    /** El contenido y el principio/final de la lista (8dp) — el aire que separa la primera card de
     *  la cabecera de la pantalla, y la última de la barra de navegación. */
    val edgeInset = PaparcarSpacing.sm
}
