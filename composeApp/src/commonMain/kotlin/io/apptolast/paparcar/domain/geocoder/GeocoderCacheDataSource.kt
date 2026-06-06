package io.apptolast.paparcar.domain.geocoder

import io.apptolast.paparcar.domain.model.AddressAndPlace

interface LocalAddressAndPlaceDataSource {
    /** Returns a cached entry only if Phase-2 (POI check) has completed. */
    suspend fun get(lat: Double, lon: Double): AddressAndPlace?
    suspend fun put(lat: Double, lon: Double, info: AddressAndPlace, poiChecked: Boolean)
    suspend fun evictExpired()
}
