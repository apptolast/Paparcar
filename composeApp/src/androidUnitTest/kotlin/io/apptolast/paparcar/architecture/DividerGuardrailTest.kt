package io.apptolast.paparcar.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Guardrail for the shared divider [io.apptolast.paparcar.ui.components.PapDivider] — one divider
 * for the whole app so a tweak to weight/tone lands everywhere at once. Feature code must NOT
 * hand-roll `HorizontalDivider`/`VerticalDivider` (that's how the 0.08–0.5 alpha zoo grew).
 * The only place the raw Material dividers may appear is `PapDivider.kt` itself. [UI-METRICS-POLISH-001]
 */
class DividerGuardrailTest {

    private val scope = Konsist.scopeFromProject()

    @Test
    fun `feature code uses PapDivider, not raw Material dividers`() {
        val violations = scope.files
            .filter { it.path.contains("commonMain") }
            .filter { it.name != "PapDivider" }
            .filter { file ->
                val pkg = file.packagee?.name ?: ""
                pkg.startsWith("io.apptolast.paparcar.presentation") ||
                    pkg.startsWith("io.apptolast.paparcar.ui.components")
            }
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
