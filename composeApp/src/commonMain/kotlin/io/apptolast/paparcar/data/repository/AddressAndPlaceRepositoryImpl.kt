package io.apptolast.paparcar.data.repository

import io.apptolast.paparcar.domain.geocoder.GeocoderDataSource
import io.apptolast.paparcar.domain.geocoder.LocalAddressAndPlaceDataSource
import io.apptolast.paparcar.domain.model.AddressAndPlace
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.places.PlacesDataSource
import io.apptolast.paparcar.domain.repository.AddressAndPlaceRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val TAG = "AddressAndPlaceRepo"

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

        // Cache miss — fetch from remote sources, write to local, emit from local.

        // Phase 1: address (local geocoder, no network).
        // Written to local with poiChecked=false so local.get() won't serve it as
        // a complete cache hit on the next visit — Phase 2 must still run.
        val address = geocoder.getAddress(lat, lon)
            .getOrElse { AddressInfo(null, null, null, null) }
        local.put(lat, lon, AddressAndPlace(address = address, placeInfo = null), poiChecked = false)
        emit(local.get(lat, lon) ?: AddressAndPlace(address = address, placeInfo = null))

        // Phase 2: POI (network, best-effort). Seals the entry with poiChecked=true
        // so subsequent visits get a full cache hit without hitting Overpass again.
        PaparcarLogger.d(TAG, "Phase 2 start — querying Overpass for ($lat, $lon)")
        val placeInfo = runCatching { places.getNearbyPlace(lat, lon).getOrNull() }
            .onFailure { e -> PaparcarLogger.w(TAG, "POI fetch failed — address-only result", e) }
            .getOrNull()
        PaparcarLogger.d(TAG, "Phase 2 result — placeInfo=$placeInfo")
        local.put(lat, lon, AddressAndPlace(address = address, placeInfo = placeInfo), poiChecked = true)
        if (placeInfo != null) emit(local.get(lat, lon) ?: AddressAndPlace(address = address, placeInfo = placeInfo))
    }
}
