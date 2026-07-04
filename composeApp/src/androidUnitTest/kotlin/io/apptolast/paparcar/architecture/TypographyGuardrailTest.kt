package io.apptolast.paparcar.architecture

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
                pkg.startsWith("io.apptolast.paparcar.presentation") ||
                    pkg.startsWith("io.apptolast.paparcar.ui.components")
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
                pkg.startsWith("io.apptolast.paparcar.presentation") ||
                    pkg.startsWith("io.apptolast.paparcar.ui.components")
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
                pkg.startsWith("io.apptolast.paparcar.presentation") ||
                    pkg.startsWith("io.apptolast.paparcar.ui.components")
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

    private fun buildViolationMessage(rule: String, violations: List<String>): String =
        "[$rule] ${violations.size} violation(s):\n${violations.joinToString("\n") { "  - $it.kt" }}"

    private companion object {
        val INLINE_SP_REGEX = Regex("""\b(fontSize|letterSpacing)\s*=""")

        // Legit inline-sp: canvas map labels drawn via TextMeasurer, and chrome one-offs whose sizes
        // are their own tokenised constants (documented exceptions in CLAUDE.md).
        val INLINE_SP_ALLOWLIST = setOf(
            "PaparcarMapMarkers",
            "AppBottomNavigation",
            "ConnectivityBanner",
            "PaparcarBottomActionBar",
        )

        // Rule 4 — the only feature file allowed to resolve a family itself is the canvas
        // map-marker painter: its label sizes scale with the marker geometry, so its styles
        // can't come from the fixed role table (same exception as rule 2).
        val FONT_FAMILY_REGEX = Regex("""remember(Outfit|Inter|BarlowCondensed)FontFamily|FontFamily\s*\(""")
        val FONT_FAMILY_ALLOWLIST = setOf("PaparcarMapMarkers")
    }
}
