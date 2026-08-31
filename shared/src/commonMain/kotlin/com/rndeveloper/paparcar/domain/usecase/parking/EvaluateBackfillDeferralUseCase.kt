package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * Whether the safety net's backfill placer must DEFER to an arrival the coordinator already
 * resolved as nudge-only. [DET-BACKFILL-TAINT-001]
 *
 * *An arrival the coordinator RESOLVED (nudge-only, GAP-ENTERED anchor) cannot be re-decided by
 * the safety net.* The coordinator abort `aborted_unattended_gap_anchor` is a reasoned verdict:
 * the car's rest was never witnessed (GPS hole at driving speed), the forward error is
 * unboundable, so NO place is honest — only the user can mark it. One minute later the 15-min
 * net's departure chain reaches [the backfill placer] with LESS information (one wake-up fix, no
 * stream) and used to plant the pin anyway (field 2026-07-30 20:42, Redmi/Jerez: it happened to
 * land right; over a 2 km hole it would land 2 km wrong with the same confidence). Two deciders,
 * one arrival — the second, blinder one must yield. This is the face DET-ARRIVAL-DOUBLE-PIN-001
 * left open: that guard closed "both place" (live session + backfill); this one closes "one
 * vetoed, the other places".
 *
 * Pure decision — the Android worker owns the I/O (reading the persisted resolution stamp,
 * skipping the placement). Deferral requires BOTH:
 *  - the resolution is FRESH ([ParkingDetectionConfig.arrivalResolutionWindowMs]): outside the
 *    window it is someone else's trip — a later legit backfill must keep placing;
 *  - the backfill fix matches the SAME arrival
 *    ([ParkingDetectionConfig.arrivalResolutionMatchRadiusMeters], generous — after a nudge-only
 *    abort the body walks at most a few hundred meters before the next net tick): a distant fix
 *    is a different arrival and places normally.
 *
 * Only the PLACEMENT defers. The departure side (freeing the OLD spot) and the net's cure/reseal
 * are untouched — the release was correct, the nudge remains the honest exit for the new spot.
 */
class EvaluateBackfillDeferralUseCase(
    private val config: ParkingDetectionConfig,
) {
    /**
     * @param backfillFix      Where the backfill is about to place the new pin.
     * @param nowMs            The placer's wall clock.
     * @param resolutionAtMs   WHEN the coordinator stamped the nudge-only resolution, or null when
     *                         no resolution is on record.
     *
     *   [DET-A-RESOLVED-ARRIVAL-IS-RESOLVED-FOR-ALL-EIGHT-REASONS-001] ⛔ **This null used to be the
     *   NORMAL case, and that is why failure #6 of the redesign audit was not fixable on its own.**
     *   The stamp was written for one of the eight unattended reasons, so seven resolved arrivals
     *   arrived here indistinguishable from an arrival nobody had resolved — and making the null
     *   defer would have suppressed every legitimate backfill instead. The seal is written for all
     *   eight now, so a null here finally means what it says: *nothing resolved this arrival*, and
     *   placing normally is the right answer. #6 closes with #7, which is what the audit predicted.
     * @param resolutionPoint  WHERE the resolved arrival's last fix was, or null when the stamp
     *                         carries no position (defensive: a stamp without a place cannot match
     *                         a trip — place normally).
     * @return true → defer to the nudge (skip the placement); false → place normally.
     */
    operator fun invoke(
        backfillFix: GpsPoint,
        nowMs: Long,
        resolutionAtMs: Long?,
        resolutionPoint: GpsPoint?,
    ): Boolean {
        if (resolutionAtMs == null || resolutionPoint == null) return false
        val ageMs = nowMs - resolutionAtMs
        // A future-dated stamp (clock change) is not interpretable — never suppress on it.
        if (ageMs < 0L || ageMs > config.arrivalResolutionWindowMs) return false
        val distanceMeters = haversineMeters(
            backfillFix.latitude, backfillFix.longitude,
            resolutionPoint.latitude, resolutionPoint.longitude,
        )
        return distanceMeters <= config.arrivalResolutionMatchRadiusMeters
    }
}
