@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.spot

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsTracker
import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.service.ReportSpotScheduler
import com.rndeveloper.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * Geocodes the given coordinates (best-effort, inline) and schedules a guaranteed
 * Firebase upload via [ReportSpotScheduler].
 *
 * Fire-and-forget: the caller does not need to handle a Result — the WorkManager job
 * persists across process death and retries automatically when the network is available.
 *
 * Geocoding is bounded by [GEOCODE_TIMEOUT_MS] so [ReportSpotScheduler.schedule] is
 * always called regardless of network conditions — even if the geocoder or Overpass API
 * are unreachable.
 *
 * When the caller already has the address/POI for these coordinates (e.g. the manual
 * report screen geocodes the pin centre as it settles), it passes them via [prefetched]
 * and the inline geocode is skipped — same result, no redundant network round-trip.
 * Callers without a prior lookup (auto departure worker, release-parking) pass null and
 * the inline geocode runs as before. [SPOT-PREFETCH-001]
 */
class ReportSpotReleasedUseCase(
    private val reportSpotScheduler: ReportSpotScheduler,
    private val getAddressAndPlace: GetAddressAndPlaceUseCase,
    private val authRepository: AuthRepository,
    // Optional product-analytics sink. This use case is the single choke point for every spot
    // publication, so spot_released / spot_reported (by [SpotType]) are emitted HERE — the manual
    // wrapper inherits it and can never double-count. Fire-and-forget; nullable for test doubles.
    // [ANALYTICS-EVENTS-001]
    private val analyticsTracker: AnalyticsTracker? = null,
) {
    suspend operator fun invoke(
        lat: Double,
        lon: Double,
        spotId: String,
        spotType: SpotType = SpotType.AUTO_DETECTED,
        confidence: Float = 1f,
        sizeCategory: VehicleSize? = null,
        carbodyType: CarbodyType? = null,
        prefetched: AddressAndPlace? = null,
        /** What this publication claims about the space — see [ReportSpotScheduler.enqueueReportSpot].
         *  Decides the document's status AND its lifetime. */
        claim: SpotStatus = SpotStatus.CONFIRMED,
        /** True when this call RE-publishes an already-published provisional spot under the same id
         *  (the promotion of a deduced departure, REPLACE on the same document). The community sees
         *  one spot, so analytics must count one: a promotion emits nothing. [ANALYTICS-EVENTS-001] */
        promotion: Boolean = false,
        /** [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001] Non-null → the spot goes out as an AREA of this
         *  radius. Callers derive it (and the capped confidence) from
         *  [com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy] — this use case only carries it. */
        zoneRadiusMeters: Float? = null,
    ) {
        // [AUDIT-RULES-001 C4] The spot's identity is the reporter's UID, not their display name —
        // the Firestore rules key owner-only edit/delete off `reportedBy == request.auth.uid`.
        val reportedByUid = authRepository.getCurrentSession()?.userId
        // An approximate prefetch (borrowed-neighbour address, offline camera geocode) must never
        // be published as the spot's real street — treat it as no prefetch and let the inline
        // lookup try for the exact answer. This use case is the single choke point for every spot
        // publication. [GEO-CACHE-ANSWERS-NEARBY-001]
        val exactPrefetch = prefetched?.takeUnless { it.approximate }
        var address: AddressInfo? = exactPrefetch?.address
        var placeInfo: PlaceInfo? = exactPrefetch?.placeInfo
        // Only hit the network when the caller didn't already geocode these coords.
        // AddressAndPlace.address is non-null, so a non-null [prefetched] always
        // gives us an address → skip the redundant, blocking inline lookup. [SPOT-PREFETCH-001]
        if (exactPrefetch == null) {
            withTimeoutOrNull(GEOCODE_TIMEOUT_MS.milliseconds) {
                // ⛔ NOT `persisting = true`, even though this does store the answer.
                // [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001] buys a re-measurement with an Overpass
                // round trip, and this lane has 5 s, one shot and no retry — it would spend the
                // budget to time out and publish with LESS than the cell already knew. The stake is
                // different too: a spot lives minutes, so a slightly stale "no place here" costs a
                // line of copy for a quarter of an hour, where a parking's name is kept for good.
                getAddressAndPlace(lat, lon)
                    .catch { /* best-effort: schedule with whatever info we have */ }
                    .collect { info ->
                        if (info.approximate) return@collect
                        address = info.address
                        placeInfo = info.placeInfo ?: placeInfo
                    }
            }
        }
        reportSpotScheduler.enqueueReportSpot(
            spotId = spotId,
            lat = lat,
            lon = lon,
            address = address,
            placeInfo = placeInfo,
            spotType = spotType,
            confidence = confidence,
            sizeCategory = sizeCategory,
            carbodyType = carbodyType,
            reportedBy = reportedByUid,
            claim = claim,
            zoneRadiusMeters = zoneRadiusMeters,
        )

        // [ANALYTICS-EVENTS-001] The publication is durably enqueued → the fact is countable
        // (cell-only). A promotion re-publishes the same document and counts nothing.
        if (!promotion) {
            val nowMs = Clock.System.now().toEpochMilliseconds()
            val location = GpsPoint(latitude = lat, longitude = lon, accuracy = 0f, timestamp = nowMs, speed = 0f)
            analyticsTracker?.track(
                if (spotType == SpotType.MANUAL_REPORT) {
                    AnalyticsEvent.SpotReported(location)
                } else {
                    AnalyticsEvent.SpotReleased(location)
                }
            )
        }
    }

    companion object {
        // Overpass API: 6 s connect + 8 s read = 14 s max. Cap at 5 s so the WorkManager
        // job is always enqueued quickly, even on slow or unreachable networks.
        private const val GEOCODE_TIMEOUT_MS = 5_000L
    }
}
