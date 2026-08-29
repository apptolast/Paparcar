package io.apptolast.paparcar.domain.geocoder

import io.apptolast.paparcar.domain.model.AddressAndPlace

interface LocalAddressAndPlaceDataSource {
    /** Returns a cached entry only if Phase-2 (POI check) has completed. */
    suspend fun get(lat: Double, lon: Double): AddressAndPlace?

    /**
     * Nearest cached entry with a street, within the implementation's radius —
     * the offline near-miss answer, returned with `approximate = true` so no
     * consumer can mistake it for the exact address of (lat, lon).
     * [GEO-CACHE-ANSWERS-NEARBY-001]
     */
    suspend fun getNearest(lat: Double, lon: Double): AddressAndPlace?

    suspend fun put(lat: Double, lon: Double, info: AddressAndPlace, poiChecked: Boolean)
    suspend fun evictExpired()
}
