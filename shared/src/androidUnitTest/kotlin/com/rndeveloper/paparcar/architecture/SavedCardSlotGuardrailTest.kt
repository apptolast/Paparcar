package com.rndeveloper.paparcar.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [DET-A-SAVED-CARD-MUST-NOT-OUTLIVE-ITS-TRIP-001] Guardrail for the parking flow's single slot.
 *
 * The saved-parking card («Kamiq aparcado · Cancelar») talks about ONE car, and its buttons act on
 * ONE session. When a new trip begins, that card has to be re-examined — which the Coordinator did
 * at session start and the **Bluetooth lane did not do at all**, because the rule was an inline
 * expression inside a function the BT lane never enters. A whole BT trip could run with the previous
 * parking's card sitting in the tray.
 *
 * Neither check can be a unit test of the service: [com.rndeveloper.paparcar.detection.service.CoordinatorDetectionService]'s
 * Robolectric harness deliberately covers only the null-intent path, which is the one that touches
 * no Koin-injected dependency. So the wiring is guarded by reading it.
 *
 * ⚠️ Konsist tests do not touch bytecode → Gradle reports them UP-TO-DATE. Falsify with
 * `--rerun-tasks`.
 */
class SavedCardSlotGuardrailTest {

    private val scope = Konsist.scopeFromProject()

    /**
     * The BT lane's ACL CONNECT is where a Bluetooth trip begins — the same moment a Coordinator
     * session start is. It must ask for the slot back.
     */
    @Test
    fun `the bluetooth lane reclaims the parking slot when a trip begins`() {
        val connect = scope.classes()
            .single { it.name == SERVICE }
            .functions()
            .single { it.name == "handleBtConnected" }

        assertTrue(
            connect.text.contains(RECLAIM),
            "[a trip that begins must re-examine the card about the car it just left] " +
                "$SERVICE.handleBtConnected does not call $RECLAIM — the previous parking's card " +
                "would survive the whole BT trip with its buttons still aimed at a session that ended.",
        )
    }

    /**
     * …and it must ASK, not carry its own copy of the rule. The stamp the decision reads is owned by
     * one object; a second reader would be a second place for the 15-minute undo window to drift.
     */
    @Test
    fun `only the card's owner reads when it was posted`() {
        val readers = scope.files
            .filter { MAIN_SOURCE_SET.containsMatchIn(it.path.replace('\\', '/')) }
            .filter { it.text.contains(STAMP) }
            .map { it.name }
            .sorted()

        assertEquals(
            listOf("CoordinatorParkingDetector", "DetectionEffectExecutor"),
            readers,
            "[the undo window is decided in ONE place — see savedCardIsStale] $STAMP is read in " +
                "${readers.size} file(s): ${readers.joinToString()}",
        )
    }

    private companion object {
        const val SERVICE = "CoordinatorDetectionService"
        const val RECLAIM = "dismissSavedCardIfStale"
        const val STAMP = "savedConfirmPostedAt"

        /** `.../src/<something>Main/...` — commonMain, androidMain, iosMain, mock, prod. */
        val MAIN_SOURCE_SET = Regex("""/src/[a-zA-Z]*[Mm]ain/""")
    }
}
