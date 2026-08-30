package com.rndeveloper.paparcar.architecture

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Falsifies the witness itself.
 *
 * [GuardrailScope] exists because a prohibition that never sees its population go empty cannot tell
 * "clean" from "blind". A witness that has never been seen failing has that same defect one level
 * up — it is one more thing that is green because nothing exercised it. So the mechanism is tested
 * the only way it can honestly be tested: by asking it for populations that are not there and
 * requiring it to refuse.
 *
 * [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
 */
class GuardrailScopeTest {

    @Test
    fun `a population that selects nothing fails instead of returning an empty list`() {
        val error = assertFailsWith<AssertionError> {
            GuardrailScope.filesInPackage("com.rndeveloper.paparcar.package.that.never.existed", floor = 1)
        }
        assertTrue(
            error.message.orEmpty().contains("empty population"),
            "the failure must name the diagnosis, not just a count: ${error.message}",
        )
    }

    @Test
    fun `a path population that selects nothing fails too`() {
        assertFailsWith<AssertionError> {
            GuardrailScope.filesUnderPath(
                name = "a folder nobody has",
                pathFragment = Regex("""/domain/detection/stages-renamed-by-a-refactor/"""),
                floor = GuardrailScope.DETECTION_STAGES_FLOOR,
            )
        }
    }

    /**
     * The shrinkage case, which is the one a rename actually produces when it moves *most* of a
     * package rather than all of it: the selector still matches something, so a "not empty" check
     * would wave it through. The floor is what catches it.
     */
    @Test
    fun `a population that shrank below its floor fails even though it is not empty`() {
        val error = assertFailsWith<AssertionError> {
            GuardrailScope.population(name = "feature UI files", floor = 100_000) { scope ->
                scope.files.filter { it.path.contains("commonMain") }
            }
        }
        assertTrue(
            error.message.orEmpty().contains("expected at least 100000"),
            "the failure must report the floor it missed: ${error.message}",
        )
    }

    /** A floor of zero would make every population "witnessed" while witnessing nothing. */
    @Test
    fun `a floor of zero is refused`() {
        val error = assertFailsWith<AssertionError> {
            GuardrailScope.filesInPackage(GuardrailScope.DOMAIN, floor = 0)
        }
        assertTrue(
            error.message.orEmpty().contains("floor of 0"),
            "the failure must say the floor is the problem: ${error.message}",
        )
    }

    /**
     * And the other direction: on today's tree the real populations clear their floors with room,
     * so the floors are not sitting so high that the suite is one deletion away from red. This is
     * what makes the four asserts above evidence rather than theatre.
     */
    @Test
    fun `the real populations clear their floors with margin`() {
        val measured = listOf(
            Triple("feature UI", GuardrailScope.featureFiles().size, GuardrailScope.FEATURE_FILES_FLOOR),
            Triple(
                "presentation UI",
                GuardrailScope.presentationFeatureFiles().size,
                GuardrailScope.PRESENTATION_FEATURE_FILES_FLOOR,
            ),
            Triple(
                "domain package",
                GuardrailScope.filesInPackage(GuardrailScope.DOMAIN, GuardrailScope.DOMAIN_PACKAGE_FLOOR).size,
                GuardrailScope.DOMAIN_PACKAGE_FLOOR,
            ),
            Triple(
                "commonMain production",
                GuardrailScope.commonMainProductionFiles().size,
                GuardrailScope.COMMON_MAIN_PRODUCTION_FLOOR,
            ),
        )
        val tight = measured.filter { (_, size, floor) -> size < floor * 6 / 5 }
        assertTrue(
            tight.isEmpty(),
            "[floor too close to the population] a floor within 20% of the real count will trip on " +
                "ordinary churn, and a floor people edit to make the build green stops being a " +
                "witness. Re-measure and halve:\n" +
                tight.joinToString("\n") { (name, size, floor) -> "  - $name: $size files, floor $floor" },
        )
    }

    /** `scopeFromProject()` has to reach BOTH modules; `PromptWindowGuardrailTest` depends on it. */
    @Test
    fun `the scan reaches the app module as well as shared`() {
        val modules = GuardrailScope.scope.files
            .map { it.path.replace('\\', '/') }
            .mapNotNull { path ->
                when {
                    path.contains("/shared/src/") -> "shared"
                    path.contains("/app/src/") -> "app"
                    else -> null
                }
            }
            .toSet()
        assertEquals(
            setOf("shared", "app"),
            modules,
            "the guardrails assume one scan covers both modules — if :app dropped out of scope, " +
                "every rule about the Android shell is now passing on files it cannot see",
        )
    }
}
