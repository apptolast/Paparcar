package com.rndeveloper.paparcar.data.overpass

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shared budget, proved. [OVERPASS-A-MIRROR-WALK-MUST-OUTLIVE-THE-SERVER-IT-ASKS-001]
 *
 * What this is defending against, measured against the live server on 14-09-2026: four asks issued
 * at once from one IP came back as one 429 and **three 504s at 14,1 s**, for a query that answers in
 * 1,4 s when it is asked alone.
 */
class OverpassQuotaTest {

    @Test
    fun should_serialiseTwoLanes_when_bothAskAtOnce() = runTest {
        val quota = OverpassQuota()
        val order = mutableListOf<String>()

        // The real pair: the map matcher fetches roads DURING a trip, and the driver opens the
        // car-parks lane on arrival — i.e. while the first is still in flight.
        val roads = async {
            quota.withSlot {
                order += "roads in"
                delay(2_000)
                order += "roads out"
            }
        }
        val parkings = async {
            // Let the first lane take the slot before the second asks for it.
            delay(10)
            quota.withSlot {
                order += "parkings in"
                delay(2_000)
                order += "parkings out"
            }
        }
        roads.await()
        parkings.await()

        assertEquals(
            listOf("roads in", "roads out", "parkings in", "parkings out"),
            order,
            "the second lane must not overlap the first: overlapping is what produces the 504s",
        )
    }

    @Test
    fun should_giveUpItsTurn_when_theLaneAheadHoldsTheSlotTooLong() = runTest {
        val quota = OverpassQuota()

        val hog = async {
            quota.withSlot { delay(OverpassQuota.WAIT_CEILING_MS + 5_000) }
        }
        val waiter = async {
            delay(10)
            quota.withSlot { "asked" }
        }

        assertNull(
            waiter.await(),
            "queueing behind a walk that is already failing would just make the failure twice as long",
        )
        hog.await()
    }

    @Test
    fun should_handTheSlotBack_when_theBlockThrows() = runTest {
        val quota = OverpassQuota()

        runCatching { quota.withSlot { throw IllegalStateException("mirror said 504") } }

        // ⛔ A leaked permit would wedge every Overpass lane in the app for good — the failure mode
        // is silent and permanent, so it gets its own witness.
        assertEquals("got in", quota.withSlot { "got in" })
    }

    @Test
    fun should_letLanesThroughOneAfterAnother_when_nobodyContends() = runTest {
        val quota = OverpassQuota()

        // A null here would mean the slot was not free — i.e. the previous ask never handed it
        // back. Back-to-back lanes are the normal case and must never queue.
        assertEquals("first", quota.withSlot { "first" })
        assertEquals("second", quota.withSlot { "second" })
        assertEquals("third", quota.withSlot { "third" })
    }
}
