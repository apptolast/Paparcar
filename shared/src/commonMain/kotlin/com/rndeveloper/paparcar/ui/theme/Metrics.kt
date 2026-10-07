package com.rndeveloper.paparcar.ui.theme

import androidx.compose.ui.unit.dp

/**
 * How TALL a tappable control is. The third leg of the same doctrine as type and colour:
 * **height is a property of the ROLE, never of the content and never of the call site.**
 * [UI-A-SCALE-NOBODY-USES-IS-NOT-A-SYSTEM-001]
 *
 * The invariant is not new — it was discovered once, for two neighbouring chips, and written down
 * there instead of here:
 *
 * > *"The height is DECLARED, not left to whichever child happens to be tallest: the add chip next
 * > door has to match it, and an emergent height gives it nothing to match."*
 * > — `HomeZoneChips`, [UI-THE-ADD-ZONE-CHIP-IS-ONE-CHIP-IN-EVERY-STATE-001]
 *
 * What the sweep found (master `adffbbe43`): **seven** declared control heights — 32, 34, 36, 40,
 * 48 (twice, in two files), 52 — and one control, `PapButton`, that declared NONE, so its height
 * was `content + 14dp` with no `maxLines`. A label that wraps to two lines made that button taller
 * than its sibling, which is the defect the user reported on the driving card (07-10): *"la altura
 * del botón depende del tamaño del contenido"*. With nine locales, labels wrap.
 *
 * ⛔ **Three steps, and 32 / 36 are NOT among them.** They sat 2dp from 34, and this project has
 * already retired a ladder for exactly that reason — `PapShapes` dropped `cardSmall` 14 and
 * `cardLarge` 18 because *"nadie distingue 14 de 16 de 18 en un móvil, pero cada componente nuevo
 * tenía que elegir entre los tres — y esa moneda al aire es lo que después se lee como
 * inconsistencia. Una escala solo sirve si sus escalones se ven."*
 * [UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001] A height ladder earns a step the same way: the step
 * has to be visible on a phone, or it is a coin toss with a constant name.
 *
 * ⛔ And a skeleton takes the height of the control it stands in for — never its own. A placeholder
 * 2dp shorter than the chip it replaces makes the list jump when the data lands.
 */
object PapControlHeight {
    /**
     * A chip or a selector pill (34dp) — something you tap to filter, choose or toggle, sitting in a
     * row of its own kind.
     *
     * Absorbs the 36 of the vehicle selector pill (and the tab height pinned to it) and the 32 of
     * the history skeleton chip.
     */
    val chip = 34.dp

    /**
     * A CTA pill (40dp) — one action, inside a card or a row, that is not the surface's main button.
     *
     * The step above [chip] is real: a pill carries a sentence-shaped label and reads as an action,
     * not as a choice. 6dp is the smallest difference that survives a phone screen.
     */
    val pill = 40.dp

    /**
     * The button that anchors a surface (48dp) — a peek's footer, a dialog's confirm, a form's
     * submit.
     *
     * Was already the de-facto anchor, declared twice: `PapFooterButton` and `PapAlertDialog` both
     * wrote their own 48. Intermediate between Material's 40dp default and a 56dp hero: tall enough
     * to be the visual anchor of a peek, short enough not to dominate it.
     */
    val button = 48.dp
}

/**
 * The padding INSIDE a chip. [UI-A-SCALE-NOBODY-USES-IS-NOT-A-SYSTEM-001]
 *
 * ⛔ **There is deliberately NO universal `PapChip` composable**, and that is a finding, not a
 * shortcut. Opened one by one, the things this project calls chips are four different compositions:
 *
 *  - an **info pill** — full width, not tappable, tinted background (`SpotFitRow`);
 *  - a **choice chip** — selectable, carries a selected state (`PaparcarFilterChip`);
 *  - an **action chip** — tappable, outlined, no icon (`CarbodyInfoCard`'s "change");
 *  - a **named-object chip** — a zone, with its identity colour and an edit affordance
 *    (`HomeZoneChips`).
 *
 * One composable covering the four would need six style axes, which is the thing `PapButton`'s own
 * KDoc refuses: *"a canonical button with five style parameters is M3 with another name"*. What they
 * genuinely share is not a skeleton, it is the MEASUREMENTS — and those are what drifted. So the
 * metrics are centralised here and the compositions stay apart.
 */
object PapChipPadding {
    /**
     * 12dp horizontal · 8dp vertical, the pair that reads as one group against
     * [PapControlHeight.chip].
     *
     * Measured spread before this token: 12/8 (`PaparcarFilterChip`), 10/6 (`CarbodyInfoCard`),
     * 12/10 (`SpotFitRow`), 12/— (`HomeZoneChips`, which pinned a height instead). Four chips, four
     * paddings, no reason any of them could state.
     */
    val horizontal = 12.dp
    val vertical = 8.dp
}

/**
 * How BIG a glyph is, by where it sits. [UI-A-SCALE-NOBODY-USES-IS-NOT-A-SYSTEM-001]
 *
 * ⛔ This is NOT a free size. `18.dp` was declared **six times under six different names**
 * (`LeadingIconSize`, `FOOTER_BUTTON_ICON_SIZE`, `CHIP_ICON_SIZE`, `ZONE_CHIP_ICON_DP`,
 * `HEADER_ICON_SIZE`, `META_ICON_DP`) for one single job: the glyph that travels with a label.
 * Six names for one number is how a system stops being one.
 *
 * Icon sizes that are NOT this role keep their own constant where they are drawn, because they
 * answer to the drawing and not to a label: map markers, the vehicle top-down glyph, illustration.
 * Those are Level 3 of the icon doctrine, which brings its own geometry.
 */
object PapIconSize {
    /**
     * The glyph that travels with a label (18dp) — inside a button, a chip, a meta row, a section
     * header.
     *
     * Sized against the label beside it, not against its own box: at 20 it outweighs a `label`
     * role's cap height, at 16 it reads as decoration.
     */
    val withLabel = 18.dp
}
