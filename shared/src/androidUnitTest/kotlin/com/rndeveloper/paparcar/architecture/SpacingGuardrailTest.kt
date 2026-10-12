package com.rndeveloper.paparcar.architecture

import org.junit.Test
import kotlin.test.assertTrue

/**
 * Guardrail for the metric half of the design system: **the height and the air of a control are
 * properties of its ROLE, never of its content and never of its call site.**
 * [UI-A-SCALE-NOBODY-USES-IS-NOT-A-SYSTEM-001]
 *
 * The roles live in `ui/theme` — `PapControlHeight`, `PapIconSize`, `PapGap`, `PapChipPadding` —
 * which is outside [GuardrailScope.featureFiles] by construction, so the declarations themselves are
 * never the violation; re-declaring them next to a consumer is.
 *
 * ## Why there were only three checks for a while, and why there are four now
 *
 * The ticket drafted four, and one did not survive contact with the tree:
 *
 *  - *"no `Row` with `weight(1f)` children without a declared `verticalAlignment`"* — **retired**:
 *    the sweep found zero cases where that produced a defect (the weighted siblings are either
 *    already centred, or fixed-height components, or text), and `Alignment.Top` is correct when the
 *    siblings are text columns of different lengths. A prohibition with no witness is not a check.
 *
 * The fourth — *"no inline `dp` literal in `Modifier.size(...)`"* — was **deferred, not dropped**
 * [UI-THE-SPACING-SWEEP-BY-VOLUME-001]: it had 30 live violations when #136 closed, and a check
 * that cannot be green on the day it lands gets disabled, not fixed. The screen-by-screen sweep
 * (#137) brought it to 0 — bar two literals with their own written reason — so it joins the other
 * three here.
 *
 * ## What these checks do NOT catch
 *
 * A control height re-declared under a name this file's regex does not recognise, or an 18dp glyph
 * hidden behind a freshly named constant. That is deliberate: the project's magic-number rule makes
 * a NAMED constant the legal form, so the line these checks draw is at the anonymous literal and at
 * the recognisable duplicate. Catching the rest means reading the diff.
 *
 * ⛔ Falsifying any of these requires `--rerun-tasks`: none of them changes bytecode, so Gradle
 * reports the task UP-TO-DATE and never runs it. Same trap as every guardrail in this package.
 */
class SpacingGuardrailTest {

    /**
     * A control's height is declared by the ladder, so no feature file declares one of its own.
     *
     * Where it bit: `STOP_PILL_HEIGHT_DP = 34` (the driving card's button), `ZONE_CHIP_HEIGHT = 34`
     * — which `HomeLocationPill` had resorted to IMPORTING out of a chip's file, because the role it
     * wanted had no home — `VEHICLE_PILL_HEIGHT_DP = 36` and a second, independent `TAB_HEIGHT_DP =
     * 36` in another file, and `SKELETON_CHIP_HEIGHT_DP = 32` for a placeholder standing in for a
     * 34dp chip. Seven declared heights, three of them within 2dp of each other, is what the user
     * saw as *"cada botón tiene distinto tamaño"*.
     */
    @Test
    fun `feature code declares no control height of its own`() {
        val violations = GuardrailScope.featureFiles()
            .filter { CONTROL_HEIGHT_DECL_REGEX.containsMatchIn(it.text) }
            .map { it.name }
        assertTrue(
            violations.isEmpty(),
            "[control height declared in feature code — use PapControlHeight.chip/pill/button] " +
                "${violations.size} violation(s):\n${violations.joinToString("\n") { "  - $it.kt" }}",
        )
    }

    /**
     * And the canonical controls take theirs from the ladder too — a literal there would be the
     * ladder's own step, written twice.
     *
     * `Spacer(Modifier.height(…))` is explicitly NOT a control height: it is a gap, it answers to
     * [com.rndeveloper.paparcar.ui.theme.PapGap] or to the spacing scale, and lumping the two
     * together is the mistake that made a single "no literal dp" rule unusable — the 606 literals in
     * this tree are five different vocabularies, and only one of them is spacing.
     */
    @Test
    fun `the canonical controls take their height from the ladder`() {
        val violations = GuardrailScope.featureFiles()
            .filter { it.name in CANONICAL_CONTROLS }
            .flatMap { file ->
                file.text.lines()
                    .filter { line -> LITERAL_HEIGHT_REGEX.containsMatchIn(line) }
                    .filterNot { line -> line.contains("Spacer(") }
                    .map { line -> "${file.name}.kt: ${line.trim()}" }
            }
        assertTrue(
            violations.isEmpty(),
            "[literal height inside a canonical control — use PapControlHeight] " +
                "${violations.size} violation(s):\n${violations.joinToString("\n") { "  - $it" }}",
        )
    }

    /**
     * The glyph that travels with a label is one size, declared once.
     *
     * It was `18.dp` under **six** names — `LeadingIconSize`, `FOOTER_BUTTON_ICON_SIZE`,
     * `CHIP_ICON_SIZE`, `ZONE_CHIP_ICON_DP`, `HEADER_ICON_SIZE`, `META_ICON_DP` — plus five inline
     * copies. Six names for one number is how a system stops being one.
     *
     * Scoped to the INLINE literal on purpose: `NODE_ICON_SIZE = 18.dp` survives this check and
     * should, because it is a named constant for a different role — a glyph centred in a 32dp node,
     * sized against the node and not against a label.
     */
    @Test
    fun `the with-label glyph size is never inlined`() {
        val violations = GuardrailScope.featureFiles()
            .flatMap { file ->
                file.text.lines()
                    .filter { line -> INLINE_WITH_LABEL_ICON_REGEX.containsMatchIn(line) }
                    .map { line -> "${file.name}.kt: ${line.trim()}" }
            }
        assertTrue(
            violations.isEmpty(),
            "[inline 18.dp glyph — use PapIconSize.withLabel] " +
                "${violations.size} violation(s):\n${violations.joinToString("\n") { "  - $it" }}",
        )
    }

    /**
     * The fourth check: no feature file writes `Modifier.size(<literal>.dp)` — a bare number with
     * no name, which is exactly the form the project's own magic-number rule forbids. Every real
     * drawing dimension in this tree already has one: a control's silhouette comes from
     * [com.rndeveloper.paparcar.ui.theme.PapControlHeight], an icon beside a label from
     * [com.rndeveloper.paparcar.ui.theme.PapIconSize], and everything else — marker geometry,
     * shimmer placeholders, a hero illustration — gets a `private val NAME_DP` where it is drawn.
     *
     * Two exemptions, and the allowlist names them instead of hiding them in a regex: the email
     * seal and its checkmark in `SettingsScreen`, each already carrying its own written reason in
     * the code (*"16 dp: la altura de la línea del correo"*) rather than a role this file could
     * recognise. An allowlist that cannot say WHY each entry is in it is a hole, not an exception.
     */
    @Test
    fun `no feature file inlines a literal size`() {
        val violations = GuardrailScope.featureFiles()
            .filterNot { it.name in SIZED_LITERAL_ALLOWLIST }
            .flatMap { file ->
                file.text.lines()
                    .filter { line -> INLINE_SIZE_LITERAL_REGEX.containsMatchIn(line) }
                    .map { line -> "${file.name}.kt: ${line.trim()}" }
            }
        val header = "[Modifier.size(<literal>.dp) - name it: a token if the role is shared, a " +
            "private val if it is not] ${violations.size} violation(s):"
        assertTrue(
            violations.isEmpty(),
            (listOf(header) + violations.map { "  - $it" }).joinToString(System.lineSeparator()),
        )
    }

    private companion object {
        /** A `val`/`const val` whose NAME claims to be a control's height. */
        val CONTROL_HEIGHT_DECL_REGEX =
            Regex("""(?:const\s+)?val\s+\w*(?:BUTTON|PILL|CHIP)_?HEIGHT\w*\s*=""", RegexOption.IGNORE_CASE)

        /** `.height(34.dp)` / `.heightIn(min = 34.dp)` — the argument is a bare literal. */
        val LITERAL_HEIGHT_REGEX = Regex("""\.height(?:In)?\((?:min\s*=\s*)?\d+(?:\.\d+)?\.dp""")

        /** `Modifier.size(18.dp)` written out where the token belongs. */
        val INLINE_WITH_LABEL_ICON_REGEX = Regex("""\.size\(18\.dp\)""")

        /** `Modifier.size(` (or `.height(`/`.width(` standing in for it) with a bare literal — the
         *  general case the three earlier, narrower checks already carve their own slice out of. */
        val INLINE_SIZE_LITERAL_REGEX = Regex("""\.size\(\s*\d+(?:\.\d+)?\.dp\s*\)""")

        /** `SettingsScreen` carries two `.size(<literal>.dp)` calls with their OWN written reason
         *  in the code — not a role this file's regex can name, so it is listed, not matched.
         *  [UI-THE-SPACING-SWEEP-BY-VOLUME-001] */
        val SIZED_LITERAL_ALLOWLIST = setOf("SettingsScreen")

        /**
         * The components that OWN a control's silhouette. Not an exemption list — the opposite: this
         * is where a height is allowed to be applied at all, and the test above is what checks that
         * the value it applies came from the ladder.
         */
        val CANONICAL_CONTROLS = setOf(
            "PapButton",
            "PapFooterButton",
            "PapCtaPill",
            "PapAlertDialog",
            "PaparcarFilterChip",
            "PaparcarAddChip",
        )
    }
}
