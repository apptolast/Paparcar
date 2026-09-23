package com.rndeveloper.paparcar.domain.service

import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.VehicleSize

/**
 * Schedules guaranteed delivery of a "spot released" report to the remote backend.
 *
 * Fire-and-forget (non-suspending). The Android implementation is backed by WorkManager
 * so the job persists across process death and retries automatically when connected.
 *
 * Called by [ReportSpotReleasedUseCase] immediately after a parking session is cleared
 * from Room. [spotType], [confidence], [sizeCategory] and [carbodyType] are forwarded to
 * Firestore so clients can render reliability, fit, and body-shape indicators on the
 * available spot.
 */
interface ReportSpotScheduler {

    /**
     * Enqueues a spot-released report for [spotId] at the given coordinates.
     * Duplicate enqueues for the same [spotId] are idempotent (REPLACE policy).
     */
    fun enqueueReportSpot(
        spotId: String,
        lat: Double,
        lon: Double,
        address: AddressInfo?,
        placeInfo: PlaceInfo?,
        spotType: SpotType = SpotType.AUTO_DETECTED,
        confidence: Float = 1f,
        sizeCategory: VehicleSize? = null,
        carbodyType: CarbodyType? = null,
        /** [AUDIT-RULES-001 C4] The reporter's UID — stored as the spot's `reportedBy` identity so
         *  the Firestore rules can authorise owner-only edits/deletes. Previously the display name
         *  was written here, which made `reportedBy == uid` false and blocked ALL deletes. */
        reportedBy: String? = null,
        /**
         * **What this publication CLAIMS about the space**, which decides both the document's
         * status and its lifetime ([com.rndeveloper.paparcar.domain.model.SpotTtlPolicy.ttlMs]).
         *
         *  - [SpotStatus.CONFIRMED] — the departure was witnessed. Full lifetime.
         *  - [SpotStatus.PROVISIONAL] — [DET-HANDOFF-NOT-MANUAL-001 §B] the departure was only
         *    DEDUCED. Same speed, short life, so a wrong one expires in minutes not hours.
         *  - [SpotStatus.RELEASING] — [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] nobody has left yet:
         *    somebody boarded the car parked here.
         *
         * Re-publishing the SAME id with a stronger claim promotes the document in place (REPLACE
         * policy, one doc): that is how a releasing spot becomes a real one, and how a provisional
         * one earns the full TTL once the drive is proven.
         *
         * ⛔ This was a `provisional: Boolean` until a third claim existed, and a boolean fails a
         * three-way question in silence — the new case would inherit `false`, i.e. the full
         * two-hour lifetime, by saying nothing.
         */
        claim: SpotStatus = SpotStatus.CONFIRMED,
        /** [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001] Non-null → the spot is an AREA of this radius
         *  (the freeing session was approximate). Callers pass it pre-gated by
         *  [com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy] — never raw session state. */
        zoneRadiusMeters: Float? = null,
    )
}
