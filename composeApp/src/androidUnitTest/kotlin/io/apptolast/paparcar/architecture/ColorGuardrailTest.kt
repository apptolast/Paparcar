package io.apptolast.paparcar.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Guardrail for the colour doctrine (`docs/design/COLOR-SYSTEM.md`): the app is brand green; a
 * vehicle's colour is its WATCH METHOD (green = active detection, blue = Bluetooth, grey = off),
 * resolved in exactly one place (`ui/theme/VehicleIdentity.kt`); the state machine is neutral text;
 * spots keep the freshness ramp. The rules below keep the token table from silently growing back
 * into the 8-meanings-of-green zoo. [UI-COLOR-DOCTRINE-001 F6]
 */
class ColorGuardrailTest {

    private val scope = Konsist.scopeFromProject()

    /** Feature files = presentation/ + ui/components/ in commonMain (same net as the other guardrails). */
    private fun featureFiles() = scope.files
        .filter { it.path.contains("commonMain") }
        .filter { file ->
            val pkg = file.packagee?.name ?: ""
            pkg.startsWith("io.apptolast.paparcar.presentation") ||
                pkg.startsWith("io.apptolast.paparcar.ui.components")
        }

    /** `tertiary` is a retired role: its old meanings (BT, manual report, private zone, info) all
     *  moved to the blue story, the person badge, or a neutral+lock. The scheme slots keep backing
     *  values for framework internals, but feature code must never read them. */
    @Test
    fun `feature code never reads the retired tertiary role`() {
        val violations = featureFiles()
            .filter { TERTIARY_REGEX.containsMatchIn(it.text) }
            .map { it.name }
        assertTrue(
            violations.isEmpty(),
            "[colorScheme.tertiary* is retired — Bluetooth blue is papCarBlue, provenance is " +
                "a glyph, private zones are neutral+lock] ${violations.size} violation(s):\n" +
                violations.joinToString("\n") { "  - $it.kt" },
        )
    }

    /** Colour VALUES live in `ui/theme/` — a literal `Color(0x…)` in feature code is a token that
     *  escaped the table and will drift. (`ui/components/` map markers are allowlisted: their
     *  fixed-over-tiles palettes are documented token objects, not strays.) */
    @Test
    fun `presentation declares no literal colours`() {
        val violations = featureFiles()
            .filter { (it.packagee?.name ?: "").startsWith("io.apptolast.paparcar.presentation") }
            .filter { COLOR_LITERAL_REGEX.containsMatchIn(it.text) }
            .map { it.name }
        assertTrue(
            violations.isEmpty(),
            "[literal Color(0x…) in presentation — add a token with its story to ui/theme/Color.kt] " +
                "${violations.size} violation(s):\n${violations.joinToString("\n") { "  - $it.kt" }}",
        )
    }

    private companion object {
        // Any read of the retired tertiary family off a colour scheme reference.
        val TERTIARY_REGEX = Regex("""\b(colorScheme|cs)\s*\.\s*(tertiary|onTertiary|tertiaryContainer|onTertiaryContainer)\b""")

        // A literal ARGB colour constructor. ui/theme is excluded by scope; Color.Transparent etc. don't match.
        val COLOR_LITERAL_REGEX = Regex("""\bColor\s*\(\s*0[xX][0-9a-fA-F]{6,8}\s*\)""")
    }
}
