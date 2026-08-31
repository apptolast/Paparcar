package com.rndeveloper.paparcar.architecture

import org.junit.Test
import kotlin.test.assertTrue

/**
 * Guardrail for the wildcard-import ban in `CLAUDE.md` ("No usar wildcard imports").
 *
 * ## Why this exists
 *
 * The rule was written down and nothing enforced it, so by the time anyone counted there were
 * **five** of them — in `FakeAuthRepository`, `FakeDetectionSources`, `FakeUserParkingRepository`
 * and twice in `MockModule`. None arrived by decision; each one is what you get when a file grows a
 * sixth import from the same package and the IDE offers to collapse them.
 *
 * A wildcard is not a style preference here. `import …domain.model.*` makes every future type added
 * to that package silently visible in a file that never asked for it, which is how a name collision
 * turns into a compile error in a file nobody touched, and how a reader loses the one cheap answer
 * to "what does this file actually depend on?". The `:shared` and `:app` modules both compile with
 * `allWarningsAsErrors`, so the explicit list also stays honest for free: an import that stops being
 * used fails the build instead of rotting.
 *
 * [DEPS-BASELOGIN-LEAVES-JITPACK-FOR-MAVEN-CENTRAL-001] — found while sweeping the package rename,
 * because the rename touched one of the five and the ban had no witness.
 *
 * ## The population
 *
 * Everything that SHIPS, from [GuardrailScope.productionSourceFiles] — the same population the other
 * "what production code may say" rules read, so a module split or a package move cannot retire this
 * rule by quietly selecting nothing. [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
 *
 * ## ⛔ Falsifying a Konsist rule needs `--rerun-tasks`
 *
 * This test was watched going red with a wildcard restored, and only after a false negative worth
 * recording: the first two attempts to falsify it came back GREEN, and the rule looked fine.
 *
 * **Imports do not change bytecode.** Swapping an explicit import for a wildcard leaves
 * `:shared:compileDebugKotlinAndroid` producing identical classes, so `testDebugUnitTest` sees
 * unchanged inputs and Gradle skips the run entirely — the guardrail never executes and the build
 * reports success. Konsist reads the SOURCE tree at test time, and those files are not declared
 * inputs of the test task, so nothing about this is visible from the outside.
 *
 * That applies to every Konsist guardrail in this package, not just this one: **a prohibition here
 * can only be validated with `--rerun-tasks`**, or the falsification is measuring a task that never
 * ran. [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
 */
class ImportGuardrailTest {

    @Test
    fun `production code names its imports, never a wildcard`() {
        val violations = GuardrailScope.productionSourceFiles()
            .flatMap { file -> file.imports.map { file.name to it } }
            .filter { (_, import) -> import.isWildcard }
            .map { (fileName, import) -> "$fileName.kt — import ${import.name}" }
        assertTrue(
            violations.isEmpty(),
            "[wildcard import in production code — name the symbols] ${violations.size} " +
                "violation(s):\n${violations.joinToString("\n") { "  - $it" }}\n" +
                "Expand the import to the symbols the file actually uses. With " +
                "allWarningsAsErrors on, an over-long list fails the build too, so the compiler " +
                "keeps the expansion honest.",
        )
    }

}
