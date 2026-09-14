package com.rndeveloper.paparcar.data.overpass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The arithmetic of the walk, on its own. [OVERPASS-A-FAILOVER-MUST-HAVE-A-DEADLINE-001]
 * [OVERPASS-A-MIRROR-WALK-MUST-OUTLIVE-THE-SERVER-IT-ASKS-001]
 */
class OverpassWalkPatienceTest {

    private val carParksBudgetMs = 20_000L

    @Test
    fun should_shareTheWalkEvenly_when_twoMirrorsAreLeft() {
        val attempt = assertNotNull(
            OverpassWalkPatience.attempt(spentMs = 0, serverBudgetMs = carParksBudgetMs, mirrorsLeft = 2),
        )

        // Half of the 30 s ceiling — which is the live list's shape today.
        assertEquals(15_000L, attempt.firstByteMs)
        // …while the whole call still gets the patience the server itself was granted.
        assertEquals(carParksBudgetMs + OverpassWalkPatience.NETWORK_GRACE_MS, attempt.requestMs)
    }

    @Test
    fun should_neverCutBelowTheSlowestMeasuredAnswer_when_theListIsLongEnoughToSqueezeTheShare() {
        // Three mirrors would divide into 10 s each, and Málaga answered 200 at 7,72 s over FIBRE —
        // a phone pays more. The floor is what stops a longer list from silently going back to the
        // 8 s guillotine this ticket exists to remove.
        val attempt = assertNotNull(
            OverpassWalkPatience.attempt(spentMs = 0, serverBudgetMs = carParksBudgetMs, mirrorsLeft = 3),
        )

        assertEquals(OverpassWalkPatience.SLOWEST_MEASURED_ANSWER_MS, attempt.firstByteMs)
        assertTrue(
            attempt.firstByteMs > 8_000L,
            "8 s was the old cut and it threw away answers that were on their way",
        )
    }

    @Test
    fun should_giveTheLastMirrorEveryThingLeft_when_nobodyRemainsToFailOverTo() {
        val attempt = assertNotNull(
            OverpassWalkPatience.attempt(spentMs = 24_000, serverBudgetMs = carParksBudgetMs, mirrorsLeft = 1),
        )

        // 30 s ceiling − 24 s already spent. Patience is free when there is no next server, but it
        // is still the WALK's remaining time, never a fresh 25 s.
        assertEquals(6_000L, attempt.requestMs)
        assertEquals(6_000L, attempt.firstByteMs, "the last mirror's silence is worth waiting on")
    }

    @Test
    fun should_handTheWholeServerBudget_when_thereIsOnlyOneMirrorInTheList() {
        val attempt = assertNotNull(
            OverpassWalkPatience.attempt(spentMs = 0, serverBudgetMs = carParksBudgetMs, mirrorsLeft = 1),
        )

        assertEquals(
            carParksBudgetMs + OverpassWalkPatience.NETWORK_GRACE_MS,
            attempt.firstByteMs,
            "with nobody to fail over to, cutting the only server short buys nothing",
        )
    }

    @Test
    fun should_stopAsking_when_theBudgetCannotFundARealTry() {
        assertNull(
            OverpassWalkPatience.attempt(spentMs = 28_500, serverBudgetMs = carParksBudgetMs, mirrorsLeft = 1),
            "asking with 1,5 s left is theatre: the answer could not arrive before the walk is over",
        )
    }

    @Test
    fun should_neverExceedTheWalkCeiling_when_everyMirrorIsSilent() {
        // Every mirror in the live list burns exactly the patience it was handed.
        val mirrors = OverpassEndpoints.ALL.size
        var spent = 0L
        var asked = 0
        while (true) {
            val attempt = OverpassWalkPatience
                .attempt(spent, carParksBudgetMs, mirrorsLeft = mirrors - asked) ?: break
            spent += attempt.firstByteMs
            asked++
            if (asked > 10) break
        }

        assertEquals(mirrors, asked, "every configured mirror gets asked before the budget runs out")
        assertTrue(
            spent <= OverpassWalkPatience.WALK_CEILING_MS,
            "$mirrors mirrors cost $spent ms; the old walk cost 75 s and that is the bug",
        )
    }

    @Test
    fun should_readTheBudgetTheQueryDeclared_when_itOpensWithATimeout() {
        // The two that exist today: the POI lookup asks for 8 s, everything else for 20 s.
        assertEquals(8_000L, OverpassWalkPatience.declaredServerBudgetMs("[out:json][timeout:8];node(1);out;"))
        assertEquals(20_000L, OverpassWalkPatience.declaredServerBudgetMs("[out:json][timeout:20];nwr(1);out center 80;"))
    }

    @Test
    fun should_fallBackToTheUsualBudget_when_theQueryDeclaresNone() {
        assertEquals(
            OverpassWalkPatience.DEFAULT_SERVER_BUDGET_S * 1_000L,
            OverpassWalkPatience.declaredServerBudgetMs("[out:json];node(1);out;"),
        )
    }
}
