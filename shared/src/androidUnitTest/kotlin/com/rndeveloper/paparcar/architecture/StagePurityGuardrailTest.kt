package com.rndeveloper.paparcar.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.Test
import kotlin.test.assertTrue

/**
 * [09 §4] **A stage decides; it does not act.** The acceptance criterion of P3.11, enforced the way
 * this project already enforces its other doctrines.
 *
 * The rule is not "stages should be tidy". It is that a decision has to be assertable WITHOUT being
 * performed. `runConfirm` used to both decide and save, so there was no way to ask what a branch
 * would do except by letting it do it — which is why the branch order went untested for so long and
 * why `DET-CONFIRM-BRANCH-ORDER-MUST-BE-TESTABLE-001` could not write the tests it set out to write.
 *
 * A stage that reaches a repository re-opens that hole one import at a time, so the import is what
 * gets checked. Everything a stage wants done comes back as a `DetectionEffect`, and the executor is
 * the only place in the core that performs anything.
 */
class StagePurityGuardrailTest {

    private val scope = Konsist.scopeFromProject()

    @Test
    fun `no stage imports a repository or a notification port`() {
        val offenders = scope.files
            .filter { STAGES_PACKAGE.containsMatchIn(it.path.replace('\\', '/')) }
            .flatMap { file -> file.imports.map { file.name to it.name } }
            .filter { (_, import) -> FORBIDDEN.any { import.contains(it) } }
            .map { (file, import) -> "$file → $import" }

        assertTrue(
            offenders.isEmpty(),
            "[a stage must DECIDE, not act — ask for a DetectionEffect instead] " +
                "${offenders.size} forbidden import(s):\n" + offenders.joinToString("\n") { "  - $it" },
        )
    }

    /**
     * The other half of the same property: the executor exists, and it is where the I/O went. A
     * guardrail that only forbids is one refactor away from being satisfied by an empty package.
     */
    @Test
    fun `the effect executor is the one place that talks to a repository`() {
        val executor = scope.files.singleOrNull { it.name == "DetectionEffectExecutor" }
        assertTrue(executor != null, "the executor must exist — the ban above is meaningless without it")
        assertTrue(
            executor.imports.any { it.name.contains("domain.repository") },
            "the executor is where the I/O lives; if it no longer talks to a repository, this " +
                "doctrine has quietly moved somewhere else and nobody wrote it down",
        )
    }

    private companion object {
        val STAGES_PACKAGE = Regex("""/domain/detection/stages/""")

        /** Repositories and the notification port: the two ways a stage could start ACTING. */
        val FORBIDDEN = listOf("domain.repository", "domain.notification")
    }
}
