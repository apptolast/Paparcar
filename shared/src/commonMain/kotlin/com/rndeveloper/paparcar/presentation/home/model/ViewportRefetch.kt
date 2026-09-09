package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * When does a map layer owe the settled camera a fresh ask? [FUEL-LAYER-FOLLOWS-THE-CAMERA-001]
 *
 * Pure on purpose, and separate from any one layer: this is the reusable half of
 * "the layer shows what you look at" — the fuel layer is its first consumer, and the spots and
 * parkings layers are expected to be its next two (`spots-follow-the-camera-001.md`). The half a
 * layer keeps for itself is WHAT to fetch and where to store it.
 *
 * The rule: refetch when there is no anchor yet, or when the camera settled more than HALF the
 * query radius away from the last ask. Half, because a full radius would let the visible edge run
 * completely dry before anyone asks again, and much less would re-ask on every nervous pan.
 */
object ViewportRefetch {

    fun shouldRefetch(
        anchorLat: Double?,
        anchorLon: Double?,
        lat: Double,
        lon: Double,
        radiusMeters: Int,
    ): Boolean {
        if (anchorLat == null || anchorLon == null) return true
        return haversineMeters(anchorLat, anchorLon, lat, lon) > radiusMeters * REFETCH_AT_FRACTION
    }

    /**
     * Is this point somewhere the layer should even look?
     *
     * ⛔ **(0, 0) is not a place**, it is the Gulf of Guinea — the coordinate a map reports before
     * it has a camera. Asking there was measured on device (`0 stations around 0.0,0.0`), and it is
     * the same trap `SPOT-A-REPORT-WITHOUT-A-PLACE-MUST-NOT-HAPPEN-001` closed for reports.
     *
     * And the layer stops following past [MAX_FROM_USER_METERS]: refuelling somewhere you would
     * take a day to reach is not a question anyone is asking, and each settle costs a request to a
     * public service. Beyond it the layer shows NOTHING rather than the stations of your own city
     * over a foreign one — which is the lie this whole ticket exists to stop.
     */
    fun isWorthAsking(lat: Double, lon: Double, userLat: Double?, userLon: Double?): Boolean {
        if (lat == 0.0 && lon == 0.0) return false
        if (userLat == null || userLon == null) return true
        return haversineMeters(userLat, userLon, lat, lon) <= MAX_FROM_USER_METERS
    }

    private const val REFETCH_AT_FRACTION = 0.5

    /** Your city / your province — "I am driving there", the trip a refuel question belongs to. */
    const val MAX_FROM_USER_METERS = 50_000.0
}
