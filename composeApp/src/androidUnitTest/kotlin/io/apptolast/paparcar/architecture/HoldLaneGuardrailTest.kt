package io.apptolast.paparcar.architecture

import com.lemonappdev.konsist.api.Konsist
import io.apptolast.paparcar.domain.detection.HoldAction
import org.junit.Test
import kotlin.test.assertTrue

/**
 * [DET-HOLD-BRANCHES-MUST-SPEAK-001] Guardrail for the post-confirm hold lane, same two properties
 * the trigger lane is held to (`TriggerLaneGuardrailTest`) and for the same reason.
 *
 * The hold is where this project learned the lesson the hard way: six of its seven exits were
 * local-log only, and `DET-CONFIRM-BRANCH-ORDER-MUST-BE-TESTABLE-001` consequently could not write
 * **any** of the three precedence tests it set out to write — a neutralised branch left the output
 * byte-identical. A branch that says nothing cannot be discriminated by any test. These two checks
 * are what stop that state from coming back one edit at a time.
 */
class HoldLaneGuardrailTest {

    private val scope = Konsist.scopeFromProject()

    /**
     * The property is **no dead exit**, not "in this file". When the hold branch moved into
     * `HoldResolutionStage` (P3.10) the actions started being NAMED by the stage and LOGGED by the
     * orchestrator, which is the whole point of a stage — so the check follows them across the two
     * files that make up the lane instead of pinning one name. It fails on exactly what it failed on
     * before: an action nobody ever produces.
     */
    @Test
    fun `every hold action is actually emitted somewhere in the hold lane`() {
        val text = scope.files
            .filter { it.name in LANE }
            .joinToString("\n") { it.text }
        val unemitted = HoldAction.entries
            .filter { !text.contains("HoldAction.${it.name}") }
            .map { it.name }
        assertTrue(
            unemitted.isEmpty(),
            "[dead hold action — an exit nobody emits is a branch no test can discriminate] " +
                "${unemitted.size} of ${HoldAction.entries.size} never emitted in ${LANE.joinToString()}:\n" +
                unemitted.joinToString("\n") { "  - HoldAction.$it" },
        )
    }

    @Test
    fun `the hold event is constructed in exactly one place`() {
        val builders = scope.files
            .filter { MAIN_SOURCE_SET.containsMatchIn(it.path.replace('\\', '/')) }
            .filter { CONSTRUCTION_REGEX.containsMatchIn(it.text) }
            .map { it.name }
        assertTrue(
            builders.size == 1 && builders.single() == TAP,
            "[the lane must have ONE door — see DetectionDiagnosticsTap.hold] " +
                "DetectionEvent.Hold is built in " +
                "${builders.size} file(s): ${builders.joinToString()}",
        )
    }

    private companion object {
        const val COORDINATOR = "CoordinatorParkingDetector"

        /** Where the branches live since P3.10. */
        const val HOLD_STAGE = "HoldResolutionStage"

        /** Where the I/O lives since P3.11. */
        const val EXECUTOR = "DetectionEffectExecutor"

        /**
         * The single DOOR since P3.12: the only emitter of remote diagnostics.
         *
         * It has moved twice while this check watched — coordinator → executor → tap — and each time
         * the check followed the code rather than the code being bent back. That is the difference
         * between maintaining a guardrail and defeating one: the property (**one door**) never
         * changed, only the address.
         */
        const val TAP = "DetectionDiagnosticsTap"

        /**
         * The whole lane. Actions are NAMED by the stage and by the orchestrator's own lifecycle
         * exits, and EMITTED by the executor — three files, one property: no dead exit.
         */
        val LANE = setOf(COORDINATOR, HOLD_STAGE, EXECUTOR, TAP)

        /** A CONSTRUCTION of the event, not the `is DetectionEvent.Hold ->` branch of the DTO
         *  mapper nor an import. */
        val CONSTRUCTION_REGEX = Regex("""DetectionEvent\.Hold\s*\(""")

        /** `.../src/<something>Main/...` — commonMain, androidMain, iosMain, mock, prod. */
        val MAIN_SOURCE_SET = Regex("""/src/[a-zA-Z]*[Mm]ain/""")
    }
}
