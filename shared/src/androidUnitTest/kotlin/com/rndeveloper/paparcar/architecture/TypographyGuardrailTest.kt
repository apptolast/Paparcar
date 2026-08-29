package com.rndeveloper.paparcar.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Guardrail for the Paparcar type system [UI-TYPE-SYSTEM-001].
 *
 * The system only stays solid if feature code never picks fonts/sizes ad-hoc. These tests fail the
 * build when it does, so the drift we kept hand-fixing cannot come back:
 *  1. The deprecated `DataTypography` API is gone — nothing may reference it (its roles moved to
 *     `PaparcarType`).
 *  2. Feature code (`presentation.*`, `ui.components.*`) must NOT inline `fontSize` / `letterSpacing`
 *     on a `Text`/`TextStyle`. Sizes live in `PaparcarType` roles, decided once.
 *  3. Feature code must NOT read `MaterialTheme.typography.*` — it speaks `PaparcarType.current.<role>`.
 *     MD3 typography is only the framework baseline defined in `Typography.kt`.
 *  4. Feature code must NOT build font families directly (`rememberXxxFontFamily()` / `FontFamily(...)`)
 *     — that swaps the family while dodging rules 2/3. Only `ui.theme` constructs families.
 *
 * A short allowlist covers legit exceptions: canvas/`TextMeasurer` map-marker labels and
 * already-tokenised chrome one-offs (bottom-nav, connectivity banner, primary action bar).
 */
class TypographyGuardrailTest {

    private val scope = Konsist.scopeFromProject()

    @Test
    fun `no code references the removed DataTypography API`() {
        val violations = scope.files
            .filter { !it.path.contains("Test") }
            .filter { it.text.contains("DataTypography") }
            .map { it.name }
        assertTrue(
            violations.isEmpty(),
            buildViolationMessage("DataTypography is removed — use PaparcarType roles", violations),
        )
    }

    @Test
    fun `feature code does not inline fontSize or letterSpacing`() {
        val violations = scope.files
            // Shared runtime UI only — androidMain @Preview exploration files are dev tooling.
            .filter { it.path.contains("commonMain") }
            .filter { file ->
                val pkg = file.packagee?.name ?: ""
                pkg.startsWith("com.rndeveloper.paparcar.presentation") ||
                    pkg.startsWith("com.rndeveloper.paparcar.ui.components")
            }
            .filter { it.name !in INLINE_SP_ALLOWLIST }
            .filter { INLINE_SP_REGEX.containsMatchIn(it.text) }
            .map { it.name }
        assertTrue(
            violations.isEmpty(),
            buildViolationMessage(
                "inline fontSize/letterSpacing in feature code — use a PaparcarType role",
                violations,
            ),
        )
    }

    @Test
    fun `feature code uses PaparcarType roles, not MaterialTheme typography`() {
        val violations = scope.files
            .filter { it.path.contains("commonMain") }
            .filter { file ->
                val pkg = file.packagee?.name ?: ""
                pkg.startsWith("com.rndeveloper.paparcar.presentation") ||
                    pkg.startsWith("com.rndeveloper.paparcar.ui.components")
            }
            .filter { it.name !in INLINE_SP_ALLOWLIST }
            .filter { it.text.contains("MaterialTheme.typography") }
            .map { it.name }
        assertTrue(
            violations.isEmpty(),
            buildViolationMessage(
                "MaterialTheme.typography in feature code — use a PaparcarType role",
                violations,
            ),
        )
    }

    @Test
    fun `feature code does not build font families directly`() {
        val violations = scope.files
            .filter { it.path.contains("commonMain") }
            .filter { file ->
                val pkg = file.packagee?.name ?: ""
                pkg.startsWith("com.rndeveloper.paparcar.presentation") ||
                    pkg.startsWith("com.rndeveloper.paparcar.ui.components")
            }
            .filter { it.name !in FONT_FAMILY_ALLOWLIST }
            .filter { FONT_FAMILY_REGEX.containsMatchIn(it.text) }
            .map { it.name }
        assertTrue(
            violations.isEmpty(),
            buildViolationMessage(
                "direct font-family construction in feature code — use a PaparcarType role",
                violations,
            ),
        )
    }

    /**
     * A role owns its WEIGHT too [UI-TYPE-TWO-VOICES-ONE-ROW-001].
     *
     * Before this rule existed, 50 call sites rewrote the `fontWeight` of the role they had just
     * asked for — `rowTitle` declared Medium and not one of its 12 call sites used it, splitting
     * instead into Bold (Home) and SemiBold (Vehicles). That is the same drift the size rules
     * already stop, just moved to another property, and it slipped through because `fontWeight`
     * was explicitly allowed.
     *
     * Weight that encodes SELECTION is not an exception either: selection is carried by colour,
     * border or the control's own check. If a design genuinely needs two weights at one size,
     * that is two roles, not an override.
     */
    @Test
    fun `feature code does not override the weight of a role`() {
        val violations = scope.files
            .filter { it.path.contains("commonMain") }
            .filter { file ->
                val pkg = file.packagee?.name ?: ""
                pkg.startsWith("com.rndeveloper.paparcar.presentation") ||
                    pkg.startsWith("com.rndeveloper.paparcar.ui.components")
            }
            .filter { it.name !in WEIGHT_ALLOWLIST }
            .filter { WEIGHT_REGEX.containsMatchIn(it.text) }
            .map { it.name }
        assertTrue(
            violations.isEmpty(),
            buildViolationMessage(
                "fontWeight/titleWeight in feature code — the role owns its weight, add or adjust " +
                    "a role in PaparcarType instead",
                violations,
            ),
        )
    }

    private fun buildViolationMessage(rule: String, violations: List<String>): String =
        "[$rule] ${violations.size} violation(s):\n${violations.joinToString("\n") { "  - $it.kt" }}"

    private companion object {
        val INLINE_SP_REGEX = Regex("""\b(fontSize|letterSpacing)\s*=""")

        // Legit inline-sp: canvas map labels drawn via TextMeasurer, and chrome one-offs whose sizes
        // are their own tokenised constants (documented exceptions in CLAUDE.md).
        // ConnectivityBanner sale de aqui: estar en la allowlist es lo que le permitio quedarse sin
        // familia y renderizarse con la fuente del sistema. La exencion tapaba el fallo, no lo
        // documentaba. [UI-TYPE-ONE-VOICE-REACHES-MATERIAL-001]
        val INLINE_SP_ALLOWLIST = setOf(
            "PaparcarMapMarkers",
            "AppBottomNavigation",
            "PaparcarBottomActionBar",
        )

        // Rule 4 — nobody resolves a family any more. The map-marker painter used to be the
        // exception; it now reads the brand family from the theme, so the allowlist is empty and
        // the rule has no way out. [UI-TYPE-RETIRE-THE-OLD-FAMILIES-001]
        val FONT_FAMILY_REGEX = Regex("""rememberJakartaFontFamily|FontFamily\s*\(""")
        val FONT_FAMILY_ALLOWLIST = emptySet<String>()

        // Rule 5 — weight belongs to the role.
        val WEIGHT_REGEX = Regex("""\b(fontWeight|titleWeight)\s*=""")

        // Same chrome/canvas exceptions as rule 2, plus HistoryWeeklyChart: it emphasises a number
        // INSIDE a sentence via `SpanStyle` in a `buildAnnotatedString`, and draws its axis labels
        // through a `TextMeasurer`. Neither is styling a role at a call site.
        val WEIGHT_ALLOWLIST = INLINE_SP_ALLOWLIST + setOf("HistoryWeeklyChart")
    }
}
