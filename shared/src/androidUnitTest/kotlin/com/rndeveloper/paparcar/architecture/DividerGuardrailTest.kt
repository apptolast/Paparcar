package com.rndeveloper.paparcar.architecture

import org.junit.Test
import kotlin.test.assertTrue

/**
 * Guardrail for the shared divider [com.rndeveloper.paparcar.ui.components.PapDivider] — one divider
 * for the whole app so a tweak to weight/tone lands everywhere at once. Feature code must NOT
 * hand-roll `HorizontalDivider`/`VerticalDivider` (that's how the 0.08–0.5 alpha zoo grew).
 * The only place the raw Material dividers may appear is `PapDivider.kt` itself. [UI-METRICS-POLISH-001]
 */
class DividerGuardrailTest {

    /**
     * The population comes from [GuardrailScope] — the same one the colour and type guardrails
     * read, and one that will not come back empty. This file used to carry its own copy of the
     * filter, so a package move could have retired the rule without turning it red.
     * [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
     */
    @Test
    fun `feature code uses PapDivider, not raw Material dividers`() {
        val violations = GuardrailScope.featureFiles()
            .filter { it.name != "PapDivider" }
            .filter { RAW_DIVIDER_REGEX.containsMatchIn(it.text) }
            .map { it.name }
        assertTrue(
            violations.isEmpty(),
            "[raw HorizontalDivider/VerticalDivider in feature — use PapDivider/PapVerticalDivider] " +
                "${violations.size} violation(s):\n${violations.joinToString("\n") { "  - $it.kt" }}",
        )
    }

    private companion object {
        // Matches a divider CALL (followed by `(`), not the import or the Pap* wrappers.
        val RAW_DIVIDER_REGEX = Regex("""(?<!Pap)\b(HorizontalDivider|VerticalDivider)\s*\(""")
    }
}
