package com.rndeveloper.paparcar.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [AUDIT-ARCH-001 M13] The spot-TTL rule, the single source of truth for Android + iOS. */
class SpotTtlPolicyTest {

    @Test
    fun should_giveTheSameTtl_when_reportIsManualOrAutomatic() {
        // [SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001] The 15-minute manual window is gone: it
        // encoded how much we trusted the reporter, not how fast a parking space fills.
        assertEquals(SpotTtlPolicy.AUTO_SPOT_TTL_MS, SpotTtlPolicy.ttlMs())
        assertEquals(2 * 60 * 60 * 1_000L, SpotTtlPolicy.AUTO_SPOT_TTL_MS)
    }

    @Test
    fun should_shortenTtl_when_departureIsOnlyDeduced() {
        // The one surviving reason to cut a lifetime short, and it is about blast radius rather
        // than trust. [DET-HANDOFF-NOT-MANUAL-001 §B]
        assertEquals(SpotTtlPolicy.PROVISIONAL_SPOT_TTL_MS, SpotTtlPolicy.ttlMs(SpotStatus.PROVISIONAL))
        assertEquals(12 * 60 * 1_000L, SpotTtlPolicy.PROVISIONAL_SPOT_TTL_MS)
    }

    @Test
    fun should_shortenTtlFurther_when_theCarHasNotLeftYet() {
        // [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] Shorter than provisional, and for a stronger
        // reason: a provisional spot is probably free, a releasing one definitely is not YET.
        assertEquals(SpotTtlPolicy.RELEASING_SPOT_TTL_MS, SpotTtlPolicy.ttlMs(SpotStatus.RELEASING))
        assertEquals(4 * 60 * 1_000L, SpotTtlPolicy.RELEASING_SPOT_TTL_MS)
    }

    @Test
    fun should_orderTheThreeLifetimes_when_readAsASet() {
        // The ordering IS the doctrine: the less sure we are the space exists, the shorter the
        // blast radius of being wrong. A future claim that breaks this ordering is a bug.
        assertTrue(
            SpotTtlPolicy.ttlMs(SpotStatus.RELEASING) < SpotTtlPolicy.ttlMs(SpotStatus.PROVISIONAL),
            "a plaza that is not free yet must not outlive one that probably is",
        )
        assertTrue(
            SpotTtlPolicy.ttlMs(SpotStatus.PROVISIONAL) < SpotTtlPolicy.ttlMs(SpotStatus.CONFIRMED),
        )
    }

    @Test
    fun should_keepRetractionGrace_when_spotIsWithdrawn() {
        assertEquals(2 * 60 * 1_000L, SpotTtlPolicy.RETRACTION_GRACE_MS)
    }
}
