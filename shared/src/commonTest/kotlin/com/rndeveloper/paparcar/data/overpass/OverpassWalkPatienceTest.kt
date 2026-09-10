package com.rndeveloper.paparcar.data.overpass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The arithmetic of the walk, on its own. [OVERPASS-A-FAILOVER-MUST-HAVE-A-DEADLINE-001]
 */
class OverpassWalkPatienceTest {

    private val carParksBudgetMs = 20_000L

    @Test
    fun should_giveAQuietMirrorOnlyItsTurn_when_thereIsSomebodyElseToAsk() {
        val attempt = assertNotNull(
            OverpassWalkPatience.attempt(spentMs = 0, serverBudgetMs = carParksBudgetMs, isLast = false),
        )

        // The whole point: `private.coffee` held the socket open and said nothing for over a minute.
        // It gets eight seconds of silence, not twenty-five.
        assertEquals(OverpassWalkPatience.QUIET_MIRROR_MS, attempt.firstByteMs)
        // …while a mirror that IS streaming keeps the patience the server itself was granted.
        assertEquals(carParksBudgetMs + OverpassWalkPatience.NETWORK_GRACE_MS, attempt.requestMs)
    }

    @Test
    fun should_giveTheLastMirrorEveryThingLeft_when_nobodyRemainsToFailOverTo() {
        val attempt = assertNotNull(
            OverpassWalkPatience.attempt(spentMs = 16_000, serverBudgetMs = carParksBudgetMs, isLast = true),
        )

        // 30 s ceiling − 16 s already spent: patience is free when there is no next server, but it
        // is still the WALK's remaining time, never a fresh 25 s.
        assertEquals(14_000L, attempt.requestMs)
        assertEquals(14_000L, attempt.firstByteMs, "the last mirror's silence is worth waiting on")
    }

    @Test
    fun should_stopAsking_when_theBudgetCannotFundARealTry() {
        assertNull(
            OverpassWalkPatience.attempt(spentMs = 28_500, serverBudgetMs = carParksBudgetMs, isLast = true),
            "asking with 1,5 s left is theatre: the answer could not arrive before the walk is over",
        )
    }

    @Test
    fun should_neverExceedTheWalkCeiling_when_everyMirrorIsSilent() {
        // Three silent mirrors, each spending exactly what it was given.
        var spent = 0L
        var asked = 0
        while (true) {
            val attempt = OverpassWalkPatience.attempt(spent, carParksBudgetMs, isLast = asked == 2) ?: break
            spent += attempt.firstByteMs
            asked++
            if (asked > 10) break
        }

        assertEquals(3, asked, "all three mirrors get asked before the budget runs out")
        assertTrue(
            spent <= OverpassWalkPatience.WALK_CEILING_MS,
            "three mirrors cost $spent ms; the old walk cost 75 s and that is the bug",
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
