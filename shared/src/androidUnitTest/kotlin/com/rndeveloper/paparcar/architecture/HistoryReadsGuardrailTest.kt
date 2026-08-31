package com.rndeveloper.paparcar.architecture

import org.junit.Test
import kotlin.test.assertTrue

/**
 * [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001] **A parking the app itself disproved must not reach a
 * history read.**
 *
 * The withdrawal is a state, not a delete, for the reason `SpotStatus` wrote down when the community
 * spot faced the same choice — *a deleted document just stops arriving, taking the explanation with
 * it.* The whole cost of that choice lands here: the row is still in the table, so every read that
 * feeds the user's history has to say so, and a read added tomorrow that forgets would silently put
 * the phantom back on screen.
 *
 * The split is deliberate and this rule encodes it:
 *
 *  - **History reads** — `observeAll`, `observeByVehicle`, `getSessionsPaged`,
 *    `getEndedSessionsByVehiclePaged`, `getPreviousByVehicle` — carry `retractedAtMs IS NULL`. They
 *    are what the Vehicles history, its stats, its weekly chart and the detail screen's prev/next
 *    read.
 *  - **Diagnostic and detection reads** — `getById`, `getByUser`, `getPendingSync`, the active and
 *    geofence lookups — deliberately do NOT. A withdrawn pin is precisely what a field report is
 *    trying to explain, and the sync layer has to be able to push the withdrawal itself.
 */
class HistoryReadsGuardrailTest {

    /**
     * Asked of [GuardrailScope], which refuses an empty selection: the rule below filters by file
     * name, so a rename or a move would leave it enforcing nothing on a green build.
     * [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
     */
    private val daoText: String
        get() = GuardrailScope.productionFilesMentioning(
            symbol = "interface UserParkingDao",
            floor = 1,
        ).single().text

    @Test
    fun `every history read excludes withdrawn parkings`() {
        val text = daoText
        val missing = HISTORY_READS.filter { fn ->
            val query = queryOf(text, fn)
            requireNotNull(query) { "[$fn not found in UserParkingDao] the rule cannot see it" }
            !query.contains(WITHDRAWAL_CLAUSE)
        }
        assertTrue(
            missing.isEmpty(),
            "[a history read that would show a withdrawn parking] the row is kept on purpose (a " +
                "withdrawal is a state, not a delete), so every read the user's history feeds on " +
                "must carry `$WITHDRAWAL_CLAUSE`. ${missing.size} violation(s):\n" +
                missing.joinToString("\n") { "  - $it" },
        )
    }

    /**
     * The other half, and the one a well-meaning sweep would break: hiding the row from the
     * DIAGNOSTIC reads too would take the explanation with it — exactly the failure the "state, not
     * a delete" choice exists to avoid — and would stop the sync layer pushing the withdrawal at all.
     */
    @Test
    fun `no diagnostic read hides a withdrawn parking`() {
        val text = daoText
        val overreaching = DIAGNOSTIC_READS.filter { fn ->
            val query = queryOf(text, fn)
            requireNotNull(query) { "[$fn not found in UserParkingDao] the rule cannot see it" }
            query.contains(WITHDRAWAL_CLAUSE)
        }
        assertTrue(
            overreaching.isEmpty(),
            "[a diagnostic read that hides a withdrawn parking] a withdrawn pin is what the next " +
                "field report needs to read, and `getPendingSync` is how the withdrawal reaches " +
                "Firestore at all. ${overreaching.size} violation(s):\n" +
                overreaching.joinToString("\n") { "  - $it" },
        )
    }

    /** The `@Query` string that precedes the declaration of [fn], or null if the function is gone. */
    private fun queryOf(text: String, fn: String): String? {
        val declaration = text.indexOf("fun $fn(")
        if (declaration < 0) return null
        val queryStart = text.lastIndexOf("@Query", declaration)
        if (queryStart < 0) return null
        return text.substring(queryStart, declaration)
    }

    private companion object {
        const val WITHDRAWAL_CLAUSE = "retractedAtMs IS NULL"

        val HISTORY_READS = listOf(
            "observeAll",
            "observeByVehicle",
            "getSessionsPaged",
            "getEndedSessionsByVehiclePaged",
            "getPreviousByVehicle",
        )

        val DIAGNOSTIC_READS = listOf(
            "getById",
            "getByUser",
            "getPendingSync",
            "getActiveByGeofence",
            "getActiveByVehicle",
            "observeActive",
            "getAllActive",
        )
    }
}
