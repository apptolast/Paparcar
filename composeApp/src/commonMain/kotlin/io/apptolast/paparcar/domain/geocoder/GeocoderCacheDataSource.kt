package io.apptolast.paparcar.domain.geocoder

import io.apptolast.paparcar.domain.model.LocationInfo

interface LocalLocationInfoDataSource {
    /** Returns a cached entry only if Phase-2 (POI check) has completed. */
    suspend fun get(lat: Double, lon: Double): LocationInfo?
    suspend fun put(lat: Double, lon: Double, info: LocationInfo, poiChecked: Boolean)
    suspend fun evictExpired()
}
