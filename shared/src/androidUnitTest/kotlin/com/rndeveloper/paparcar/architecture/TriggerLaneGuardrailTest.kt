package com.rndeveloper.paparcar.architecture

import com.lemonappdev.konsist.api.Konsist
import com.rndeveloper.paparcar.domain.detection.sentry.TriggerDisposition
import org.junit.Test
import kotlin.test.assertTrue

/**
 * [DET-EVERY-TRIGGER-LEAVES-A-TRACE-001] Guardrail for the trigger-disposition lane.
 *
 * The lane's whole value is one property: `type=TRIGGER` grouped by `outcome` is a device's
 * **complete** histogram of what happened to every trigger it received. That holds only while two
 * things stay true, and both are the kind of thing a later edit breaks silently:
 *
 *  1. **No dead vocabulary.** A [TriggerDisposition] nobody emits is a category that reads as "this
 *     never happens" when it actually means "nobody wired it". The whole point of the ticket was
 *     that a mute branch and an OEM-eaten trigger look identical — a never-emitted constant
 *     reintroduces exactly that ambiguity, one level up.
 *  2. **One door.** If a branch builds its own `DetectionEvent.Trigger`, the ledger id and the
 *     column mapping stop being decided in one place, and the day a second call site files under a
 *     synthetic session id the retention sweep silently stops reaching those events.
 */
class TriggerLaneGuardrailTest {

    private val scope = Konsist.scopeFromProject()

    private val serviceText: String
        get() = scope.files.single { it.name == SERVICE }.text

    @Test
    fun `every trigger disposition is actually emitted by the service`() {
        val unemitted = TriggerDisposition.entries
            .filter { !serviceText.contains("TriggerDisposition.${it.name}") }
            .map { it.name }
        assertTrue(
            unemitted.isEmpty(),
            "[dead disposition — a category nobody emits reads as 'never happens'] " +
                "${unemitted.size} of ${TriggerDisposition.entries.size} never emitted in $SERVICE.kt:\n" +
                unemitted.joinToString("\n") { "  - TriggerDisposition.$it" },
        )
    }

    @Test
    fun `the trigger event is constructed in exactly one place`() {
        val builders = scope.files
            // Production source sets only — a test may of course build one to assert its wire form.
            // Matched on the source-set segment so it holds under Windows' backslash paths too.
            .filter { MAIN_SOURCE_SET.containsMatchIn(it.path.replace('\\', '/')) }
            .filter { CONSTRUCTION_REGEX.containsMatchIn(it.text) }
            .map { it.name }
        assertTrue(
            builders.size == 1 && builders.single() == SERVICE,
            "[the lane must have ONE door — see logTrigger] DetectionEvent.Trigger is built in " +
                "${builders.size} file(s): ${builders.joinToString()}",
        )
    }

    private companion object {
        const val SERVICE = "CoordinatorDetectionService"

        /** A CONSTRUCTION of the event (followed by `(`), not the `is DetectionEvent.Trigger ->`
         *  branches of the DTO mapper nor an import. */
        val CONSTRUCTION_REGEX = Regex("""DetectionEvent\.Trigger\s*\(""")

        /** `.../src/<something>Main/...` — commonMain, androidMain, iosMain, mock, prod. */
        val MAIN_SOURCE_SET = Regex("""/src/[a-zA-Z]*[Mm]ain/""")
    }
}
