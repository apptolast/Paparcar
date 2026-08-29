package com.rndeveloper.paparcar.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Paparcar type system — the SINGLE source of truth for text styling in the feature layer.
 *
 * ## Three voices, decided by an objective precondition [UI-TYPE-TWO-VOICES-ONE-ROW-001]
 * The old rule asked *"is this data or prose?"*, and "is this data?" is subjective — so it drifted.
 * A meta line with room to spare got classified as DATA and rendered condensed next to a name in
 * the brand face: the two most dissimilar faces of the set, one line apart, at 14 vs 13sp. It read
 * as a rendering error, not as hierarchy.
 *
 * The rule now:
 * - **MARCA** — names of real things and titles. *Is it a proper name or a title?*
 * - **CIFRA** — a figure that is the SUBJECT OF ITS OWN BLOCK: the sheet counter, the vehicle stat
 *   cards, chart axes. *Never inside a line of text.*
 * - **LECTURA** — everything else: prose, actions, structure, meta lines and taxonomy.
 *
 * A voice is a QUESTION, not a font. Which letter each one is painted with lives in [PapFontSet],
 * and since `UI-TYPE-RETIRE-THE-OLD-FAMILIES-001` all three point at the same family (Plus Jakarta
 * Sans). That does not merge them: they still decide size, weight, and whether a text is a name, a
 * figure or prose. Naming the voices after their font — the way this file said "Marca · Outfit" —
 * is what let a role be picked by the letter someone remembered instead of by the question.
 * [UI-TYPE-SYSTEM-HYGIENE-001]
 *
 * Measured on device (Redmi, 29-08) before writing this: a figure aligned in a COLUMN does not need
 * its own voice — the column is carried by position and weight. That is why the spot row's distance
 * token reads as LECTURA, and why **CIFRA no longer appears in any ROW**. Its only remaining place
 * in Home is the sheet header's free-spot counter, which is a block of its own and meets the
 * precondition.
 *
 * ## The contract
 * - Feature code (`presentation` / `ui.components`) styles text ONLY with a role:
 *   `Text(..., style = type.rowName)`.
 * - A role owns **family, size AND weight**. The call site overrides none of the three — only
 *   `color`, which belongs to the colour doctrine. (Before this ticket, 50 call sites rewrote the
 *   weight of the role they had just asked for; `rowTitle`'s declared Medium was used by exactly
 *   zero of them.) Enforced by `TypographyGuardrailTest`.
 * - It does NOT use `MaterialTheme.typography.*` and NEVER sets `fontSize` / `letterSpacing` inline.
 *   If a size is missing, add/adjust a role here — do not override at the call site.
 * - `.uppercase()` stays a caller concern (Compose `TextStyle` has no text-transform). Roles that
 *   are conventionally caps (`sectionHeader`, `badge`, `statLabel`) are uppercased by their callers
 *   / by `PapSectionHeader`.
 * - Allowed exceptions (documented, non-drifting): canvas / `TextMeasurer` map-marker labels, and
 *   already-tokenised chrome one-offs (bottom-nav, connectivity banner). These do not go through
 *   `PaparcarType`.
 *
 * ⛔ Do not propose "a condensed cut" of the shipped family to tell CIFRA apart. Read from the
 * `fvar` table of `plus_jakarta_sans_variable.ttf`: **one axis, `wght` 200–800, no `wdth`.** The
 * same check killed the idea for the previous set (Outfit was static; Inter had `opsz` + `wght` and
 * no `wdth`; Inter Tight is tighter spacing, not a narrower width). CIFRA is told apart by size,
 * weight and a trimmed line box — not by width.
 */
@Immutable
class PaparcarType(
    // ── MARCA — titles and the names of real things ─────────────────────────────────────────────
    /** Top-bar / screen title. "Mis coches". (== the old `appBarTitle`.) */
    val screenTitle: TextStyle,
    /** Hero title on full-screen surfaces (onboarding, permissions, explainers). Resolves the old
     *  Black-vs-Bold / headlineMedium-vs-Small drift to one value. */
    val heroTitle: TextStyle,
    /** In-content section title — "Activity", "History". Bigger than [cardTitle] so a single-word
     *  section heading doesn't read as small. */
    val sectionTitle: TextStyle,
    /** Card title — vehicle name, peek title, dialog title. (titleMedium weight-bumped to Bold.) */
    val cardTitle: TextStyle,
    /** The NAME of a real thing inside a row: this spot, this vehicle, this history location.
     *  Identity, so it keeps the brand voice. This is the ONLY 14sp role in MARCA.
     *
     *  ⚠️ Not for a row's structural title (the detection surface heading, an onboarding step, an
     *  empty state) — that is [rowTitle], in LECTURA. The question is *"is this a proper name?"*, and
     *  it has one answer per call site. Was `rowTitle` before this ticket, declared Medium and
     *  overridden to Bold or SemiBold at all 12 of its call sites; now it owns SemiBold.
     *  [UI-TYPE-TWO-VOICES-ONE-ROW-001] [CARD-ONE-BADGE-001] */
    val rowName: TextStyle,

    // ── LECTURA — structure, prose, actions, data inside a line ─────────────────────────────────
    /** Section header eyebrow — "TUS VEHÍCULOS", "ACTIVIDAD". Uppercased by `PapSectionHeader`. */
    val sectionHeader: TextStyle,
    /** SUB-section header — a separator that opens a group INSIDE a section already headed by a
     *  [sectionHeader]: the timeline's day rows ("HOY", "AYER", "VIERNES, 14 AGO 2026") under
     *  "APARCADO ACTUALMENTE". Same LECTURA recipe as [sectionHeader], one step down in size and
     *  weight so the hierarchy is legible without changing family. Reached via `PapSectionHeaderRow`
     *  (`dense = true`), never by hand. [UI-HISTORY-IDENTITY-AND-SOURCE-001] */
    val subsectionHeader: TextStyle,
    /** Sheet-header eyebrow — the small caps line ABOVE a title ("FORD FOCUS · APARCADO",
     *  "TU ZONA"). Smaller and wider-tracked than [sectionHeader]: it qualifies a title directly
     *  below it instead of opening a section, and carries a state tint. Uppercased by caller.
     *  [UI-SHEET-001] */
    val eyebrow: TextStyle,
    /** Primary CTA / button label. */
    val cta: TextStyle,
    /** A row's STRUCTURAL title — the detection surface heading, an onboarding step, an empty
     *  state, a Settings row. Not a name: see [rowName].
     *
     *  This is the default title of `PapListItem`, which is what Settings has always rendered
     *  (`body` + a SemiBold override at the call site). Promoted from "default plus override" to a
     *  role of its own, so it says what it is. [UI-TYPE-TWO-VOICES-ONE-ROW-001] */
    val rowTitle: TextStyle,
    /** Prominent body — hero/onboarding subtitles, lead paragraphs. (== bodyLarge.) */
    val subtitle: TextStyle,
    /** Body copy — descriptions, helper paragraphs. (bodyMedium.) */
    val body: TextStyle,
    /** Small standalone label / chip text / secondary link. Owns SemiBold: 8 of its 11 former
     *  overrides already asked for it. */
    val label: TextStyle,
    /** Secondary / caption text — subtitles, hints. (bodySmall.) */
    val caption: TextStyle,
    /** Meta line under a row title — "FIABLE · 1 min en coche · 3 en camino". Was CIFRA
     *  (`metadata`, Barlow Condensed at the time): it sat one line under a name in the brand face
     *  and was the visible clash. LECTURA, because it shares a line box with prose and taxonomy. */
    val meta: TextStyle,
    /** Status / count token inside a line — "FIABLE", "SIN CONFIRMAR", "ACTIVO", "MEDIANO",
     *  "3 en camino". Uppercased by caller. Taxonomy is something you READ, so it is LECTURA, not
     *  condensed; the colour carries the tier. Absorbs the former `sizeToken`. */
    val badge: TextStyle,

    // ── CIFRA — a figure that is the subject of its own block ───────────────────────────────────
    /** Prominent stat readout — "1.284", "92%". Fixed at 25sp, no per-call overrides. */
    val statNumber: TextStyle,
    /** The caption under a [statNumber] — "PLAZAS CEDIDAS". Stays condensed so icon + figure +
     *  label read as ONE data unit; this is the only caps token left outside LECTURA, and it never
     *  shares a line with a name. Uppercased by caller. */
    val statLabel: TextStyle,
    /** Count digit inside a lead tile — the free-spot number of the sheet-header counter. Between
     *  [statLabel] and [statNumber]: big enough to be the tile's subject, small enough for a 46dp
     *  box. Trimmed line box like [statNumber] so it centres optically. [UI-SHEET-001] */
    val counter: TextStyle,
    /** Unit caption under a [counter] digit — "LIBRES". Uppercased by caller. [UI-SHEET-001] */
    val counterUnit: TextStyle,
    /** Chart axis label — month / day names under the bars. */
    val chartLabel: TextStyle,
    /** Chart per-bar value — the small count above a bar. */
    val chartValue: TextStyle,

    /** Cap height of the CIFRA family as a fraction of em — see [PapFontSet.figureCapHeightEm].
     *  Anything aligning a glyph to the digit band reads it from here, never from a constant of
     *  its own. [UI-STAT-ICON-CENTERS-ON-DIGITS-001] */
    val figureCapHeightEm: Float,
    /** Ascenso/descenso de la familia CIFRA — ver [PapFontSet.figureAscentEm]. */
    val figureAscentEm: Float,
    val figureDescentEm: Float,
) {
    companion object {
        /** The role table for the current composition. Read as `PaparcarType.current.meta`.
         *  Provided by [PaparcarTheme]; reading it outside the theme is a programming error. */
        val current: PaparcarType
            @Composable @ReadOnlyComposable
            get() = LocalPaparcarType.current
    }
}

/** Composition-local carrying the active [PaparcarType]. Provided at the [PaparcarTheme] root. */
val LocalPaparcarType = staticCompositionLocalOf<PaparcarType> {
    error("PaparcarType not provided — is the content wrapped in PaparcarTheme?")
}

/**
 * Builds the role table for the current composition (fonts are resolved via the `@Composable`
 * family factories). [PaparcarTheme] provides the result through [LocalPaparcarType] — feature
 * code reads `PaparcarType.current.<role>` and never calls this directly.
 */
@Composable
fun rememberPaparcarType(fonts: PapFontSet = defaultFontSet()): PaparcarType {
    // Las voces se resuelven a familias en [PapFontSet]; aqui solo se reparten los roles. Esa
    // separacion es lo que permite probar otra familia en la app entera sin tocar un solo rol.
    val brand  = fonts.brand
    val text   = fonts.text
    val figure = fonts.figure

    return PaparcarType(
        // ── MARCA ──────────────────────────────────────────────────────────────────────────────
        screenTitle = TextStyle(
            fontFamily = brand, fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp,
        ),
        heroTitle = TextStyle(
            fontFamily = brand, fontWeight = FontWeight.Bold,
            fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp,
        ),
        sectionTitle = TextStyle(
            fontFamily = brand, fontWeight = FontWeight.Bold,
            fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp,
        ),
        cardTitle = TextStyle(
            fontFamily = brand, fontWeight = FontWeight.Bold,
            fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp,
        ),
        rowName = TextStyle(
            fontFamily = brand, fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
        ),

        // ── LECTURA ────────────────────────────────────────────────────────────────────────────
        sectionHeader = TextStyle(
            fontFamily = text, fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.0.sp,
        ),
        subsectionHeader = TextStyle(
            fontFamily = text, fontWeight = FontWeight.Bold,
            fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.0.sp,
        ),
        eyebrow = TextStyle(
            fontFamily = text, fontWeight = FontWeight.Bold,
            fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp,
        ),
        cta = TextStyle(
            fontFamily = text, fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
        ),
        rowTitle = TextStyle(
            fontFamily = text, fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp,
        ),
        subtitle = TextStyle(
            fontFamily = text, fontWeight = FontWeight.Normal,
            fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp,
        ),
        body = TextStyle(
            fontFamily = text, fontWeight = FontWeight.Normal,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp,
        ),
        label = TextStyle(
            fontFamily = text, fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
        ),
        caption = TextStyle(
            fontFamily = text, fontWeight = FontWeight.Normal,
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
        ),
        meta = TextStyle(
            fontFamily = text, fontWeight = FontWeight.Normal,
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp,
        ),
        badge = TextStyle(
            fontFamily = text, fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
        ),

        // ── CIFRA ──────────────────────────────────────────────────────────────────────────────
        statNumber = TextStyle(
            // Tight lineHeight + centred/trimmed line box so the digits' box hugs the glyphs and is
            // symmetric — a leading icon set to CenterVertically then lands on the numeral's optical
            // centre instead of floating high. [CARD-ONE-BADGE-001]
            fontFamily = figure, fontWeight = FontWeight.Bold,
            fontSize = 25.sp, lineHeight = 25.sp, letterSpacing = (-0.5).sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
        statLabel = TextStyle(
            // Trimmed like [statNumber]: the label sits under a figure inside a fixed cell, so its
            // box must hug its glyphs or the pair drifts off centre when the family changes.
            //
            // `lineHeight` above the font size is deliberate and does NOT undo that: with
            // `Trim.Both` the surplus is cut off the top of the first line and the bottom of the
            // last, so it only ever lands BETWEEN lines. A two-word label ("PLAZAS CEDIDAS") wraps,
            // and at lineHeight == fontSize the two lines sit on top of each other.
            fontFamily = figure, fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
        counter = TextStyle(
            fontFamily = figure, fontWeight = FontWeight.Bold,
            fontSize = 21.sp, lineHeight = 21.sp, letterSpacing = (-0.3).sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
        counterUnit = TextStyle(
            // Same trimmed box as [counter]. Without it the unit carries the font's full ascent and
            // descent, and the pair sinks against the bottom of its 46dp tile — visible the moment
            // the family changed, because Jakarta's ascent (1038) is taller than Barlow's (1000).
            fontFamily = figure, fontWeight = FontWeight.SemiBold,
            fontSize = 8.5.sp, lineHeight = 8.5.sp, letterSpacing = 0.5.sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
        // Los dos unicos roles que se dibujan en CANVAS: `HistoryWeeklyChart` los mide con un
        // TextMeasurer y coloca cada etiqueta restando la ALTURA DE LA CAJA. Sin `lineHeight` esa
        // altura sale del ascenso/descenso del TTF, o sea que la posicion de las etiquetas del
        // grafico dependia de la familia. Caja recortada como el resto del grupo CIFRA.
        // [UI-TYPE-SYSTEM-HYGIENE-001]
        chartLabel = TextStyle(
            fontFamily = figure, fontWeight = FontWeight.Normal,
            fontSize = 11.sp, lineHeight = 11.sp, letterSpacing = 0.sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
        chartValue = TextStyle(
            fontFamily = figure, fontWeight = FontWeight.Bold,
            fontSize = 10.sp, lineHeight = 10.sp, letterSpacing = 0.sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
        figureCapHeightEm = fonts.figureCapHeightEm,
        figureAscentEm = fonts.figureAscentEm,
        figureDescentEm = fonts.figureDescentEm,
    )
}
