package com.rndeveloper.paparcar.domain.matching

import com.rndeveloper.paparcar.domain.model.GpsPoint

/**
 * Ground the matcher drew but nobody covered. [ROUTE-A-SNAPPED-LINE-MUST-NOT-INVENT-GROUND-001]
 *
 * Between two consecutive measurements the map matcher emits the routed street geometry that joins
 * them. When both measurements project onto the SAME coordinate — the car standing at a light, or
 * two ways meeting at a junction the graph did not join — that routed path can still walk out to a
 * node and straight back. What gets drawn is a spike of up to ~22 m, and what gets measured is
 * length on a trip that never moved.
 *
 * ⛔ The collapse is safe ONLY because the step returns to its own starting coordinate: net
 * displacement is zero, so no measured ground is lost. A real out-and-back — into a cul-de-sac and
 * out again — carries its own fix at the far end, so a measurement sits INSIDE the excursion and
 * it is never one single inter-measurement step. That is why the caller applies this per
 * transition and never over a whole stretch.
 *
 * Field evidence (47 trips, Oppo + Redmi, sep-oct 2026): 206 such excursions across 29 trips,
 * adding 2.345 km of phantom line to 295 km drawn. The 36 spikes in the same corpus whose
 * neighbours do NOT coincide are left alone — they are not provably invented.
 */
object RouteExcursions {

    /**
     * True when [step] — one inter-measurement step, first element where it left from, last where
     * it arrived — ends on the very coordinate it started from after visiting something in
     * between. A step of two points or fewer has no interior to invent anything with.
     */
    fun isReturnExcursion(step: List<GpsPoint>): Boolean {
        if (step.size <= 2) return false
        val from = step.first()
        val back = step.last()
        return from.latitude == back.latitude && from.longitude == back.longitude
    }

    /**
     * [step] with every out-and-back excursion inside it collapsed away, however deeply nested.
     *
     * ⛔ The whole step returning to its origin is only ONE shape of the defect, and measured on
     * field data it is the rarer one: the matcher's round trips usually sit INSIDE the emitted node
     * chain (…X → Y → X…) while the step as a whole does go somewhere. A guard that only compared
     * the two ends fired zero times on 6 real sessions that still carried 10 such spikes — which is
     * why this walks the step instead of inspecting its extremes.
     *
     * The first element never moves: it is the measurement the step left from, and the caller
     * applies this per step precisely so no OTHER measurement can be inside. A real out-and-back —
     * into a cul-de-sac and out — carries its own fix at the far end, so it is never one step.
     */
    fun collapseExcursions(step: List<GpsPoint>): List<GpsPoint> {
        if (step.size <= 2) return step
        val out = ArrayList<GpsPoint>(step.size)
        for (p in step) {
            val n = out.size
            when {
                // Walking back onto the point before the last one: the pair between them is a
                // round trip over ground nobody covered. Drop it, and keep checking — excursions
                // nest.
                n >= 2 && same(out[n - 2], p) -> out.removeAt(n - 1)
                // addPoint already drops exact repeats; doing it here too keeps the rule total.
                n >= 1 && same(out[n - 1], p) -> Unit
                else -> out.add(p)
            }
        }
        return out
    }

    private fun same(a: GpsPoint, b: GpsPoint): Boolean =
        a.latitude == b.latitude && a.longitude == b.longitude
}
