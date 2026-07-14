package io.apptolast.paparcar.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Guardrail for the Home per-section slices [HOME-ATOMIZE-001 F1]: no composable
 * under `presentation/home/sections/` may take the whole `HomeState` — each
 * section receives only its slice (HomeSlices.kt), so an unrelated state change
 * can't recompose it and the slice contract can't silently regress back to
 * "thread the entire state everywhere".
 */
class HomeSliceGuardrailTest {

    private val scope = Konsist.scopeFromProject()

    @Test
    fun `home section composables take slices, not HomeState`() {
        val violations = scope.files
            .filter { it.path.contains("commonMain") }
            .filter { it.packagee?.name?.startsWith(SECTIONS_PACKAGE) == true }
            .filter { HOME_STATE_REGEX.containsMatchIn(it.text) }
            .map { it.name }
        assertTrue(
            violations.isEmpty(),
            "[HomeState threaded into a section — pass its slice from HomeSlices.kt instead] " +
                "${violations.size} violation(s):\n${violations.joinToString("\n") { "  - $it.kt" }}",
        )
    }

    private companion object {
        const val SECTIONS_PACKAGE = "io.apptolast.paparcar.presentation.home.sections"

        // Matches a `HomeState`-typed declaration (parameter, property or import),
        // not prose in comments that merely mentions related names.
        val HOME_STATE_REGEX = Regex(""":\s*HomeState\b|import .*\.HomeState$""", RegexOption.MULTILINE)
    }
}
