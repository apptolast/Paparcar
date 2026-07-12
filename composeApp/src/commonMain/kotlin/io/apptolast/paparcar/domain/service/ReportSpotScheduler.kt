package io.apptolast.paparcar.domain.service

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.VehicleSize

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
    )
}
