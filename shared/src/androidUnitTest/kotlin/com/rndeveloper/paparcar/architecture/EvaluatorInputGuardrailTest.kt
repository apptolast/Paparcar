package com.rndeveloper.paparcar.architecture

import org.junit.Test
import kotlin.test.assertTrue

/**
 * [DET-A-DOUBT-FIELD-MUST-NOT-DEFAULT-TO-CERTAINTY-001] **An evaluator's input declares no
 * defaults.**
 *
 * A default parameter on a decision input is a permanent, silent answer to a question about
 * evidence. The two inputs the detection verdicts read carried fourteen of them between them, every
 * one justified in its own KDoc as being "for legacy callers" — and the legacy callers did not
 * exist: each class has exactly ONE production call site (`StageInputs.kt`) and one test helper, and
 * the production site has always passed every field by name.
 *
 * What the defaults bought was the ability to ADD a signal without anybody having to answer it, and
 * three of them answered permissively while they lived: `egressBornAtAnchor = true` (no doubt about
 * the anchor), `lastSpeedMps = 0f` (not rolling, so the speed gate never fires) and
 * `humanPoweredRide = false` (a motor, so auto-confirm is allowed). The last one is the FIRST guard
 * `EvaluateUnattendedParkingSaveUseCase` runs — the one whose absence let a 59-minute bicycle ride
 * become a parking 4,8 km from the car — and the unattended test helper, which named all seventeen
 * other fields, never mentioned it. Every one of those scenarios ran the permissive answer without
 * saying so, and nothing could notice.
 *
 * ⚠️ Nullability is not what this forbids. `drivingEvidence: DrivingEvidence?` stays nullable
 * because "this input carries no verdict" is a real state a replay can be in. What is forbidden is
 * *not having to say it*.
 */
class EvaluatorInputGuardrailTest {

    /**
     * The two inputs, picked out of the project's classes. The population itself is asked of
     * [GuardrailScope] (which refuses an empty one), and the second test below is this rule's real
     * witness: it proves the name filter still matches both classes, since a rename would leave
     * this prohibition passing over nothing. [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
     */
    private val inputClasses get() = GuardrailScope.allClasses().filter { it.name in INPUT_CLASSES }

    @Test
    fun `no evaluator input parameter carries a default`() {
        val violations = inputClasses.flatMap { klass ->
            klass.primaryConstructor?.parameters.orEmpty()
                .filter { it.defaultValue != null }
                .map { "${klass.name}.${it.name} = ${it.defaultValue}" }
        }
        assertTrue(
            violations.isEmpty(),
            "[a decision input with a default] a default here is a permanent, silent answer to a " +
                "question about evidence, and the next signal added inherits it instead of being " +
                "answered. Pass it from the one production call site (StageInputs.kt) and from the " +
                "test helper. ${violations.size} violation(s):\n" +
                violations.joinToString("\n") { "  - $it" },
        )
    }

    /**
     * The witness for the rule above: it filters classes by NAME, so a rename would leave it
     * asserting nothing. Both classes must be found, and with the field count they actually have —
     * a rule over a two-field input is not the rule this file describes.
     */
    @Test
    fun `both decision inputs are found and are the size they claim`() {
        val found = inputClasses.associate { it.name to (it.primaryConstructor?.parameters?.size ?: 0) }
        assertTrue(
            found.keys == INPUT_CLASSES,
            "[input class not found] expected $INPUT_CLASSES, found ${found.keys}. The rule above " +
                "filters by these names and is enforcing nothing on whatever was renamed.",
        )
        found.forEach { (name, size) ->
            assertTrue(
                size >= MIN_INPUT_FIELDS,
                "[$name has $size parameter(s)] below $MIN_INPUT_FIELDS this is not the decision " +
                    "input this rule was written for.",
            )
        }
    }

    private companion object {
        val INPUT_CLASSES = setOf("ParkingDecisionInput", "UnattendedSaveInput")

        /** Both sit at 18-19 today; the floor is roughly half, per GuardrailScope's doctrine. */
        const val MIN_INPUT_FIELDS = 9
    }
}
