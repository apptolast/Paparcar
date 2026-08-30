package com.rndeveloper.paparcar.data.repository

import com.rndeveloper.paparcar.domain.geocoder.GeocoderDataSource
import com.rndeveloper.paparcar.domain.geocoder.LocalAddressAndPlaceDataSource
import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.places.PlacesDataSource
import com.rndeveloper.paparcar.domain.repository.AddressAndPlaceRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "AddressAndPlaceRepo"

/**
 * Two-phase reverse geocoding with a Room cache in front.
 *
 * Invariants: [GEOCODE-DEADLINE-001]
 * - **Every remote lookup has a deadline.** The API-33 `Geocoder` listener can
 *   simply never call back (GmsCore), and an unbounded await here froze the
 *   whole address/POI pipeline (stuck shimmer at app open). Phase 2's HTTP
 *   timeouts don't cover DNS stalls, so it gets a belt too.
 * - **Only real answers reach the cache.** A timeout/failure emits a fallback
 *   for display but writes nothing: cells live for 30 days, so caching an
 *   empty address (or sealing `poiChecked=true` after a network failure) used
 *   to poison the user's own street for a month. `success(null)` from Places
 *   IS a real answer ("no POI here") and does seal the cell.
 */
class AddressAndPlaceRepositoryImpl(
    private val local: LocalAddressAndPlaceDataSource,
    private val geocoder: GeocoderDataSource,
    private val places: PlacesDataSource,
) : AddressAndPlaceRepository {

    private var evictDone = false

    override fun getAddressAndPlace(lat: Double, lon: Double): Flow<AddressAndPlace> = flow {
        if (!evictDone) {
            evictDone = true
            local.evictExpired()
        }

        // Local is the single source of truth. Cache hit → emit and done.
        val cached = local.get(lat, lon)
        if (cached != null) { emit(cached); return@flow }

        // Cache miss — fetch from remote sources, write to local, emit.

        // Phase 1: address (platform geocoder). Not cached yet — local.get() only
        // serves sealed cells, so an unsealed write would be dead weight AND could
        // race a concurrent lookup of the same cell (user + camera at app open),
        // overwriting its freshly sealed row. The single write point is the seal.
        val fetchedAddress: AddressInfo? = withTimeoutOrNull(PHASE1_TIMEOUT_MS) {
            geocoder.getAddress(lat, lon).getOrNull()
        }
        // Phase 1 failed (offline / GMS mute): borrow the nearest cached street as a
        // display-only approximation instead of an empty address. The answer carries
        // approximate=true so persisting consumers skip it, and it is never written back
        // to the cache — the seal below still requires a real Phase-1 answer.
        // [GEO-CACHE-ANSWERS-NEARBY-001]
        //
        // A borrowed cell lends its STREET and never its POI: the neighbour may sit up to 250 m away
        // (RoomLocalAddressAndPlaceDataSource.MAX_NEAREST_DISTANCE_METERS), and while a street that
        // far is still an honest "you are around here", a PLACE that far is exactly the claim
        // [POI-A-PLACE-IS-NAMED-ONLY-IF-YOU-ARE-AT-IT-001] forbids. Phase 2 below is the only thing
        // allowed to name a place, and it measures.
        val nearest = if (fetchedAddress == null) local.getNearest(lat, lon) else null
        val approximate = nearest != null
        val address = fetchedAddress ?: nearest?.address ?: AddressInfo(null, null, null, null)
        emit(AddressAndPlace(address = address, placeInfo = null, approximate = approximate))

        // Phase 2: POI (network, best-effort). Seals the entry with poiChecked=true
        // so subsequent visits get a full cache hit without hitting Overpass again.
        PaparcarLogger.d(TAG, "Phase 2 start — querying Overpass for ($lat, $lon)")
        val placeResult = withTimeoutOrNull(PHASE2_TIMEOUT_MS) {
            runCatching { places.getNearbyPlace(lat, lon) }.getOrElse { e -> Result.failure(e) }
        }
        val placeInfo = placeResult?.getOrNull()
        PaparcarLogger.d(TAG, "Phase 2 result — placeInfo=$placeInfo answered=${placeResult?.isSuccess}")
        if (fetchedAddress != null && placeResult?.isSuccess == true) {
            local.put(lat, lon, AddressAndPlace(address = address, placeInfo = placeInfo), poiChecked = true)
        }
        // A real POI upgrade keeps the address's provenance: if the line under it is still a
        // borrowed neighbour, the emission stays approximate.
        if (placeInfo != null) emit(AddressAndPlace(address = address, placeInfo = placeInfo, approximate = approximate))
    }

    private companion object {
        /** Phase 1 is a local/GMS lookup — healthy answers arrive in well under a second. */
        const val PHASE1_TIMEOUT_MS = 5_000L
        /** Safety net ABOVE Overpass's own HTTP timeouts (6s connect + 10s read). */
        const val PHASE2_TIMEOUT_MS = 20_000L
    }
}
