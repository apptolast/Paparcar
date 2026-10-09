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
