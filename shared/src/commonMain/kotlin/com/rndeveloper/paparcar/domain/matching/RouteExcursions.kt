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

    /** [step] with its invented interior removed — just the point it left from — or [step] itself
     *  when it actually went somewhere. */
    fun collapseReturnExcursion(step: List<GpsPoint>): List<GpsPoint> =
        if (isReturnExcursion(step)) listOf(step.first()) else step
}
