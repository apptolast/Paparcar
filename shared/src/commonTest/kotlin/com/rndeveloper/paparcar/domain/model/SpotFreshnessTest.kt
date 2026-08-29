package com.rndeveloper.paparcar.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001] The single freshness ramp: one input (age), three
 * levels, and the same answer for every consumer that asks.
 */
class SpotFreshnessTest {

    private val minute = 60 * 1_000L

    @Test
    fun should_beFresh_when_justPublished() {
        assertEquals(SpotFreshness.FRESH, SpotFreshnessPolicy.ofAge(0L))
    }

    @Test
    fun should_beFresh_when_ageIsExactlyTheFreshBoundary() {
        assertEquals(SpotFreshness.FRESH, SpotFreshnessPolicy.ofAge(10 * minute))
    }

    @Test
    fun should_beRecent_when_ageIsJustPastTheFreshBoundary() {
        assertEquals(SpotFreshness.RECENT, SpotFreshnessPolicy.ofAge(10 * minute + 1))
    }

    @Test
    fun should_beRecent_when_ageIsExactlyTheRecentBoundary() {
        assertEquals(SpotFreshness.RECENT, SpotFreshnessPolicy.ofAge(30 * minute))
    }

    @Test
    fun should_beStale_when_ageIsJustPastTheRecentBoundary() {
        assertEquals(SpotFreshness.STALE, SpotFreshnessPolicy.ofAge(30 * minute + 1))
    }

    @Test
    fun should_stayStale_when_spotIsOlderThanItsWholeTtl() {
        // A spot outliving its TTL is not a contradiction here: the sweep deletes documents, this
        // ramp only describes them. Nothing about being past expiry needs a fourth level.
        assertEquals(SpotFreshness.STALE, SpotFreshnessPolicy.ofAge(SpotTtlPolicy.AUTO_SPOT_TTL_MS + minute))
    }

    @Test
    fun should_treatFutureTimestampAsFresh_when_reporterClockRunsAhead() {
        // Clock skew between the reporting device and this one must never read as "very old".
        assertEquals(
            SpotFreshness.FRESH,
            SpotFreshnessPolicy.of(reportedAtMs = 10_000_000L, nowMs = 9_000_000L),
        )
    }

    @Test
    fun should_notCondemnASpot_when_itCarriesNoTimestamp() {
        // 0 means "we never learned when", not "published at the epoch". Showing it at face value
        // is the honest failure here — the alternative paints every timestamp-less spot red.
        assertEquals(
            SpotFreshness.FRESH,
            SpotFreshnessPolicy.of(reportedAtMs = 0L, nowMs = 9_000_000L),
        )
    }

    @Test
    fun should_ageASpotFromItsPublishTime_when_bothClocksAgree() {
        val publishedAt = 1_000_000L
        assertEquals(
            SpotFreshness.RECENT,
            SpotFreshnessPolicy.of(reportedAtMs = publishedAt, nowMs = publishedAt + 20 * minute),
        )
    }
}
