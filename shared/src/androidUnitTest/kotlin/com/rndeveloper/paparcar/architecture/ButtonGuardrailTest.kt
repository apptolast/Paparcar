package com.rndeveloper.paparcar.architecture

import org.junit.Test
import kotlin.test.assertTrue

/**
 * [UI-BUTTON-ONE-CANONICAL-CTA-001] Guardrail for the canonical CTA
 * [com.rndeveloper.paparcar.ui.components.PapPrimaryButton]: feature code must not instantiate the
 * raw Material `Button` — that is how three call sites grew three private recipes for the same
 * silhouette (a hand-rolled destructive fill, an unmigrated CTA, a row-trailing compact). Intention
 * is asked for with `tone`/`size`; the silhouette is decided in one file.
 *
 * Allowed to touch the raw `Button`:
 *  - `PapButton` — the canonical implementation itself;
 *  - `PapAlertDialog` — a system component that parameterizes its accent on purpose (the one
 *    documented exception in the ticket);
 *  - `PapFooterButton` — the OTHER canonical button (the universal full-width footer with its
 *    Filled/Outlined/Tonal styles and single height); it owns its raw `Button` the same way
 *    `PapButton` does. Found by this guard's first run, not assumed.
 *
 * `IconButton` / `TextButton` / `OutlinedButton` / `RadioButton` are NOT this rule's subject —
 * the regex matches only the bare filled `Button(`.
 */
class ButtonGuardrailTest {

    /** Population from [GuardrailScope], which cannot come back empty.
     *  [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001] */
    @Test
    fun `feature code uses PapPrimaryButton, not the raw Material Button`() {
        val violations = GuardrailScope.featureFiles()
            .filter { it.name !in ALLOWED_FILES }
            .filter { RAW_BUTTON_REGEX.containsMatchIn(it.text) }
            .map { it.name }
        assertTrue(
            violations.isEmpty(),
            "[raw Material Button( in feature code — use PapPrimaryButton with tone/size] " +
                "${violations.size} violation(s):\n${violations.joinToString("\n") { "  - $it.kt" }}",
        )
    }

    private companion object {
        val ALLOWED_FILES = setOf("PapButton", "PapAlertDialog", "PapFooterButton")

        // A bare `Button(` call: not preceded by an identifier character, so `IconButton(`,
        // `TextButton(`, `OutlinedButton(`, `RadioButton(` and `PapPrimaryButton(` never match.
        val RAW_BUTTON_REGEX = Regex("""(?<![A-Za-z0-9_])Button\s*\(""")
    }
}
