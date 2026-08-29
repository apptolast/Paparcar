package com.rndeveloper.paparcar.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

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
        assertEquals(SpotTtlPolicy.PROVISIONAL_SPOT_TTL_MS, SpotTtlPolicy.ttlMs(provisional = true))
        assertEquals(12 * 60 * 1_000L, SpotTtlPolicy.PROVISIONAL_SPOT_TTL_MS)
    }

    @Test
    fun should_keepRetractionGrace_when_spotIsWithdrawn() {
        assertEquals(2 * 60 * 1_000L, SpotTtlPolicy.RETRACTION_GRACE_MS)
    }
}
