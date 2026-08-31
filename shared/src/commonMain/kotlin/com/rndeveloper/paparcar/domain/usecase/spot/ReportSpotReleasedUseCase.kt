package com.rndeveloper.paparcar.domain.usecase.spot

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.service.ReportSpotScheduler
import com.rndeveloper.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withTimeoutOrNull
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
        /** [DET-HANDOFF-NOT-MANUAL-001 §B] The departure behind this spot was deduced, not measured:
         *  publish now (freshness is the value) but with the provisional lifetime. */
        provisional: Boolean = false,
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
            provisional = provisional,
        )
    }

    companion object {
        // Overpass API: 6 s connect + 8 s read = 14 s max. Cap at 5 s so the WorkManager
        // job is always enqueued quickly, even on slow or unreachable networks.
        private const val GEOCODE_TIMEOUT_MS = 5_000L
    }
}
