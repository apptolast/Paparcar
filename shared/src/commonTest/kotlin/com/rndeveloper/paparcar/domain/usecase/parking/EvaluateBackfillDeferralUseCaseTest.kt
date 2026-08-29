package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DET-BACKFILL-TAINT-001] The safety net's backfill placer must DEFER to an arrival the
 * coordinator already resolved as nudge-only — and ONLY to that arrival. The field trip is pinned
 * as the replay: Jerez 2026-07-30, `aborted_unattended_gap_anchor` at 20:41, net backfill one
 * minute later AT the same spot (must defer); the SECOND trip's arrival 37 minutes later (must
 * place — outside the window, it is a new arrival).
 */
class EvaluateBackfillDeferralUseCaseTest {

    private val config = ParkingDetectionConfig()
    private val useCase = EvaluateBackfillDeferralUseCase(config)

    private fun fixAt(lat: Double, lon: Double) =
        GpsPoint(lat, lon, accuracy = 38f, timestamp = 0L, speed = 0f)

    // Jerez, Calle Arquímedes (field 2026-07-30): the coordinator's abort fix.
    private val resolutionPoint = fixAt(36.7173, -6.1035)
    private val resolutionAtMs = 1_000_000L

    @Test
    fun should_defer_when_the_net_wakes_a_minute_later_at_the_same_arrival() {
        // The exact field shape: the chained backfill ran +1 min at the very spot the coordinator
        // had just refused to pin ("no place is honest").
        assertTrue(
            useCase(
                backfillFix = fixAt(36.7173, -6.1035),
                nowMs = resolutionAtMs + 60_000L,
                resolutionAtMs = resolutionAtMs,
                resolutionPoint = resolutionPoint,
            ),
            "the net must not re-decide an arrival the coordinator resolved as nudge-only",
        )
    }

    @Test
    fun should_place_when_the_resolution_is_older_than_the_window() {
        // The second Jerez trip re-parked 37 min after the previous resolution: a NEW arrival —
        // a legit backfill for it must keep placing even near the old spot.
        assertFalse(
            useCase(
                backfillFix = fixAt(36.7173, -6.1035),
                nowMs = resolutionAtMs + 37 * 60_000L,
                resolutionAtMs = resolutionAtMs,
                resolutionPoint = resolutionPoint,
            ),
        )
    }

    @Test
    fun should_place_when_the_fix_is_a_different_arrival() {
        // Fresh stamp but the backfill fix is ~2 km away (the 2nd park at 36.69645,-6.13429):
        // a different arrival places normally.
        assertFalse(
            useCase(
                backfillFix = fixAt(36.69645, -6.13429),
                nowMs = resolutionAtMs + 60_000L,
                resolutionAtMs = resolutionAtMs,
                resolutionPoint = resolutionPoint,
            ),
        )
    }

    @Test
    fun should_place_when_no_resolution_is_on_record() {
        // The legit backfill class (2026-07-06 Oppo, 10 steps) has no stamp — untouched.
        assertFalse(
            useCase(
                backfillFix = fixAt(36.7173, -6.1035),
                nowMs = resolutionAtMs + 60_000L,
                resolutionAtMs = null,
                resolutionPoint = null,
            ),
        )
    }

    @Test
    fun should_place_when_the_stamp_carries_no_position() {
        // Defensive: a stamp without a place cannot match a trip.
        assertFalse(
            useCase(
                backfillFix = fixAt(36.7173, -6.1035),
                nowMs = resolutionAtMs + 60_000L,
                resolutionAtMs = resolutionAtMs,
                resolutionPoint = null,
            ),
        )
    }

    @Test
    fun should_place_when_the_stamp_is_future_dated() {
        // A clock change can leave a stamp "from the future" — not interpretable, never suppress.
        assertFalse(
            useCase(
                backfillFix = fixAt(36.7173, -6.1035),
                nowMs = resolutionAtMs - 60_000L,
                resolutionAtMs = resolutionAtMs,
                resolutionPoint = resolutionPoint,
            ),
        )
    }

    @Test
    fun should_defer_exactly_at_the_window_edge_and_place_just_past_it() {
        assertTrue(
            useCase(
                backfillFix = fixAt(36.7173, -6.1035),
                nowMs = resolutionAtMs + config.arrivalResolutionWindowMs,
                resolutionAtMs = resolutionAtMs,
                resolutionPoint = resolutionPoint,
            ),
        )
        assertFalse(
            useCase(
                backfillFix = fixAt(36.7173, -6.1035),
                nowMs = resolutionAtMs + config.arrivalResolutionWindowMs + 1L,
                resolutionAtMs = resolutionAtMs,
                resolutionPoint = resolutionPoint,
            ),
        )
    }
}
